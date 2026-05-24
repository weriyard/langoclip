package com.floatingclipboard.llm

import com.floatingclipboard.data.LogStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
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
) : LlmClient {

    override suspend fun complete(
        systemPrompt: String,
        userPrompt: String,
        jsonSchema: JsonElement?,
    ): Result<String> {
        candidates.forEachIndexed { idx, model ->
            logs?.d(TAG, "[${idx + 1}/${candidates.size}] trying $model")
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
        logs?.e(TAG, "all ${candidates.size} candidates exhausted (every model 402/429)")
        return Result.failure(LlmError.AllCandidatesExhausted)
    }

    override fun stream(
        systemPrompt: String,
        userPrompt: String,
        jsonSchema: JsonElement?,
    ): Flow<String> = flow {
        candidates.forEachIndexed { idx, model ->
            logs?.d(TAG, "[${idx + 1}/${candidates.size}] trying $model")
            var emitted = false
            try {
                val client = OpenAiClient(apiKey, model, OpenAiClient.OPENROUTER_BASE_URL)
                client.stream(systemPrompt, userPrompt, jsonSchema).collect { delta ->
                    emitted = true
                    emit(delta)
                }
                // Stream completed normally. Some models (e.g. open-source gpt-oss with strict
                // json_schema) accept the request, finish the SSE stream, but never emit any
                // content delta — treat that as failure and fall through to the next candidate.
                // Safe to do because nothing was painted yet (emitted == false).
                if (!emitted) {
                    logs?.w(TAG, "[${idx + 1}/${candidates.size}] $model emitted 0 deltas, trying next")
                    return@forEachIndexed
                }
                logs?.d(TAG, "[${idx + 1}/${candidates.size}] $model OK (stream)")
                OpenRouterModelHint.record(model)
                return@flow
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                // If we already started painting tokens, we can't fall back without confusing the
                // UI — bubble the error to the caller as-is.
                if (emitted) {
                    logs?.w(TAG, "[${idx + 1}/${candidates.size}] $model mid-stream error, propagating: ${e.message?.take(140)}")
                    throw e
                }
                if (!isQuotaError(e)) {
                    logs?.w(TAG, "[${idx + 1}/${candidates.size}] $model NON-QUOTA error, bubbling up: ${e.message?.take(140)}")
                    throw e
                }
                logs?.w(TAG, "[${idx + 1}/${candidates.size}] $model quota error, trying next: ${e.message?.take(140)}")
            }
        }
        logs?.e(TAG, "all ${candidates.size} candidates exhausted (every model 402/429/empty)")
        throw LlmError.AllCandidatesExhausted
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
 * Process-wide observable of the currently-working OpenRouter model. Settings UI subscribes to
 * this StateFlow to render "Aktualnie używany: X" without having to thread callbacks through
 * the factory. Resets on app process restart (intentional — we want to re-probe on cold start
 * because upstream provider quotas may have reset).
 */
object OpenRouterModelHint {
    private val _current = MutableStateFlow<String?>(null)
    val current: StateFlow<String?> = _current.asStateFlow()

    fun record(model: String) {
        _current.value = model
    }
}
