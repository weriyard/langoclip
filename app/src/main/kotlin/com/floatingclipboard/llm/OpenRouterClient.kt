package com.floatingclipboard.llm

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
) : LlmClient {

    override suspend fun complete(
        systemPrompt: String,
        userPrompt: String,
        jsonSchema: JsonElement?,
    ): Result<String> {
        var lastFailure: Throwable = LlmError.EmptyResponse
        for (model in candidates) {
            val client = OpenAiClient(apiKey, model, OpenAiClient.OPENROUTER_BASE_URL)
            val result = client.complete(systemPrompt, userPrompt, jsonSchema)
            result.onSuccess {
                OpenRouterModelHint.record(model)
                return Result.success(it)
            }
            val err = result.exceptionOrNull() ?: continue
            if (!isQuotaError(err)) return result
            lastFailure = err
        }
        return Result.failure(lastFailure)
    }

    override fun stream(
        systemPrompt: String,
        userPrompt: String,
        jsonSchema: JsonElement?,
    ): Flow<String> = flow {
        var lastFailure: Throwable? = null
        for (model in candidates) {
            var emitted = false
            try {
                val client = OpenAiClient(apiKey, model, OpenAiClient.OPENROUTER_BASE_URL)
                client.stream(systemPrompt, userPrompt, jsonSchema).collect { delta ->
                    emitted = true
                    emit(delta)
                }
                // Stream completed without error → this model is the winner.
                OpenRouterModelHint.record(model)
                return@flow
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                lastFailure = e
                // If we already started painting tokens, we can't fall back without confusing the
                // UI — bubble the error to the caller as-is.
                if (emitted || !isQuotaError(e)) throw e
                // else: continue with next candidate
            }
        }
        throw lastFailure ?: LlmError.EmptyResponse
    }

    private fun isQuotaError(e: Throwable): Boolean = when (e) {
        is LlmError.RateLimited -> true
        is LlmError.Server -> e.code in QUOTA_HTTP_CODES
        else -> false
    }

    companion object {
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
