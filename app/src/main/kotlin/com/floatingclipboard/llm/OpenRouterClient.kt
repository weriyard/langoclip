package com.floatingclipboard.llm

import com.floatingclipboard.data.LogStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.JsonElement

/**
 * OpenRouter routing wrapper. Tries each model from [candidates] in order; on a hard-quota error
 * (402 / 429 / RateLimited) from the upstream provider, jumps to the next candidate. The first
 * candidate that successfully returns content "wins" and is recorded in [OpenRouterModelHint] so
 * the user can see in Settings what the app is actually using right now.
 *
 * Streaming nuance: if a candidate succeeds in opening the SSE stream and emits at least one
 * chunk, we commit to it — a mid-stream error is propagated unchanged because we can't sensibly
 * rewind what was already painted in the UI. Errors that happen BEFORE any chunk lands (HTTP
 * status check, JSON decode of error body) trigger the fallback.
 */
class OpenRouterClient(
    private val apiKey: String,
    private val candidates: List<String>,
    private val logs: LogStore? = null,
    /** Time-to-first-token cap per candidate, in milliseconds. After the first chunk arrives the
     *  request is allowed to take as long as it needs (some upstream providers stream slowly). */
    private val ttftTimeoutMs: Long = 30_000,
) : LlmClient {

    override suspend fun complete(
        systemPrompt: String,
        userPrompt: String,
        jsonSchema: JsonElement?,
    ): Result<String> {
        // complete() doesn't surface usage upward — it's the legacy single-shot path; all our
        // tasks go through stream() which does. Leaving as-is.
        candidates.forEachIndexed { idx, model ->
            logs?.d(TAG, "[${idx + 1}/${candidates.size}] trying $model")
            OpenRouterModelHint.startTrying(model, idx + 1, candidates.size)
            val client = OpenAiClient(apiKey, model, OpenAiClient.OPENROUTER_BASE_URL)
            val result = client.complete(systemPrompt, userPrompt, jsonSchema)
            result.onSuccess {
                logs?.d(TAG, "[${idx + 1}/${candidates.size}] $model OK (complete)")
                OpenRouterModelHint.record(model)
                return Result.success(it)
            }
            val err = result.exceptionOrNull() ?: return@forEachIndexed
            if (!isQuotaError(err)) {
                logs?.w(TAG, "[${idx + 1}/${candidates.size}] $model NON-QUOTA error, bubbling up: ${err.message?.take(140)}")
                return result
            }
            logs?.w(TAG, "[${idx + 1}/${candidates.size}] $model quota error, trying next: ${err.message?.take(140)}")
        }
        OpenRouterModelHint.clearTrying()
        logs?.e(TAG, "all ${candidates.size} candidates exhausted (every model 402/429)")
        return Result.failure(LlmError.AllCandidatesExhausted)
    }

    override fun stream(
        systemPrompt: String,
        userPrompt: String,
        jsonSchema: JsonElement?,
        onUsage: ((TokenUsage) -> Unit)?,
    ): Flow<String> = channelFlow {
        candidates.forEachIndexed { idx, model ->
            logs?.d(TAG, "[${idx + 1}/${candidates.size}] trying $model (ttft cap ${ttftTimeoutMs}ms)")
            OpenRouterModelHint.startTrying(model, idx + 1, candidates.size)

            // Producer pushes upstream deltas + termination signal into a buffered channel.
            // We race the first receive against [ttftTimeoutMs]; once any chunk arrives, the
            // remaining receives drain unbounded (slow per-token rates are acceptable).
            val pipe = Channel<ProducerEvent>(Channel.UNLIMITED)
            val producer = launch {
                try {
                    val client = OpenAiClient(apiKey, model, OpenAiClient.OPENROUTER_BASE_URL)
                    client.stream(
                        systemPrompt = systemPrompt,
                        userPrompt = userPrompt,
                        jsonSchema = jsonSchema,
                        onUsage = onUsage,
                    ).collect { delta ->
                        pipe.send(ProducerEvent.Chunk(delta))
                    }
                    pipe.send(ProducerEvent.Done)
                } catch (e: CancellationException) {
                    // Expected when we cancel on TTFT timeout — just exit.
                } catch (e: Throwable) {
                    pipe.send(ProducerEvent.Failed(e))
                } finally {
                    pipe.close()
                }
            }

            val first = withTimeoutOrNull(ttftTimeoutMs) { pipe.receive() }
            if (first == null) {
                logs?.w(TAG, "[${idx + 1}/${candidates.size}] $model TTFT > ${ttftTimeoutMs}ms, trying next")
                producer.cancel()
                return@forEachIndexed
            }

            when (first) {
                is ProducerEvent.Failed -> {
                    val err = first.cause
                    if (!isQuotaError(err)) {
                        logs?.w(TAG, "[${idx + 1}/${candidates.size}] $model NON-QUOTA error, bubbling up: ${err.message?.take(140)}")
                        throw err
                    }
                    logs?.w(TAG, "[${idx + 1}/${candidates.size}] $model quota error, trying next: ${err.message?.take(140)}")
                    return@forEachIndexed
                }
                ProducerEvent.Done -> {
                    // Producer finished without emitting any chunk — e.g. gpt-oss-120b accepts
                    // the request, completes the SSE, but never produces content. Safe to fall
                    // through because nothing was painted.
                    logs?.w(TAG, "[${idx + 1}/${candidates.size}] $model emitted 0 deltas, trying next")
                    return@forEachIndexed
                }
                is ProducerEvent.Chunk -> {
                    // First chunk landed. Commit to this model and drain the rest.
                    send(first.text)
                    for (event in pipe) {
                        when (event) {
                            is ProducerEvent.Chunk -> send(event.text)
                            ProducerEvent.Done -> {
                                logs?.d(TAG, "[${idx + 1}/${candidates.size}] $model OK (stream)")
                                OpenRouterModelHint.record(model)
                                return@channelFlow
                            }
                            is ProducerEvent.Failed -> {
                                // Mid-stream error — can't fall back without confusing the UI
                                // (some tokens already painted). Propagate.
                                logs?.w(TAG, "[${idx + 1}/${candidates.size}] $model mid-stream error, propagating: ${event.cause.message?.take(140)}")
                                throw event.cause
                            }
                        }
                    }
                    // Channel closed without an explicit Done — treat as success of what we got.
                    logs?.d(TAG, "[${idx + 1}/${candidates.size}] $model OK (stream, channel closed)")
                    OpenRouterModelHint.record(model)
                    return@channelFlow
                }
            }
        }
        OpenRouterModelHint.clearTrying()
        logs?.e(TAG, "all ${candidates.size} candidates exhausted (every model 402/429/empty/TTFT)")
        throw LlmError.AllCandidatesExhausted
    }

    private sealed interface ProducerEvent {
        data class Chunk(val text: String) : ProducerEvent
        data object Done : ProducerEvent
        data class Failed(val cause: Throwable) : ProducerEvent
    }

    private fun isQuotaError(e: Throwable): Boolean = when (e) {
        is LlmError.RateLimited -> true
        is LlmError.Server -> e.code in QUOTA_HTTP_CODES
        else -> false
    }

    companion object {
        private const val TAG = "OpenRouter"
        // 402 Payment Required is what Crucible / other upstream providers return when their own
        // free-tier daily quota is exhausted (OpenRouter forwards the upstream code unchanged).
        // 429 is the standard rate-limit signal.
        private val QUOTA_HTTP_CODES = setOf(402, 429)
    }
}

/**
 * Process-wide observable of OpenRouter routing state. Two flows so the UI can show both:
 *  - [trying]  — set while a candidate is being probed; reads as "↻ qwen3-next (2/7)".
 *  - [current] — set when a candidate finally returned content; the "winner" of the chain.
 * Settings + the top app-bar label subscribe to both. Resets on app process restart so the
 * fallback walker re-probes on cold start (upstream provider quotas may have replenished).
 */
object OpenRouterModelHint {
    data class TryingState(val model: String, val attempt: Int, val total: Int)

    private val _current = MutableStateFlow<String?>(null)
    val current: StateFlow<String?> = _current.asStateFlow()

    private val _trying = MutableStateFlow<TryingState?>(null)
    val trying: StateFlow<TryingState?> = _trying.asStateFlow()

    fun startTrying(model: String, attempt: Int, total: Int) {
        _trying.value = TryingState(model, attempt, total)
    }

    fun record(model: String) {
        _trying.value = null
        _current.value = model
    }

    fun clearTrying() {
        _trying.value = null
    }
}
