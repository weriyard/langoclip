package com.langoclip.app.llm

import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.JsonElement

interface LlmClient {
    suspend fun complete(
        systemPrompt: String,
        userPrompt: String,
        jsonSchema: JsonElement? = null,
    ): Result<String>

    /**
     * Streams content tokens. [onUsage] is invoked once at the end of the stream with the
     * provider's reported token counts when available — null callback or providers that don't
     * surface usage are silently no-op.
     */
    fun stream(
        systemPrompt: String,
        userPrompt: String,
        jsonSchema: JsonElement? = null,
        onUsage: ((TokenUsage) -> Unit)? = null,
    ): Flow<String>

    /**
     * Multi-turn streaming chat. Full history is passed every call (the APIs are stateless). The
     * default flattens the turns into a single labelled prompt and delegates to [stream] — fine for
     * providers without a native chat endpoint. Providers that support multi-turn natively
     * (Gemini, Anthropic) override this to preserve role structure.
     */
    fun streamChat(
        systemPrompt: String,
        turns: List<ChatTurn>,
        onUsage: ((TokenUsage) -> Unit)? = null,
    ): Flow<String> = stream(
        systemPrompt = systemPrompt,
        userPrompt = turns.joinToString("\n\n") { "${it.role}: ${it.content}" },
        onUsage = onUsage,
    )
}

/** Single turn in a multi-turn chat — role is "user" or "assistant". */
data class ChatTurn(val role: String, val content: String)

data class TokenUsage(val inputTokens: Int, val outputTokens: Int)

sealed class LlmError(message: String, cause: Throwable? = null) : Exception(message, cause) {
    data object MissingApiKey : LlmError("No API key — configure it in settings")
    data object Unauthorized : LlmError("Invalid API key")
    data object RateLimited : LlmError("Rate limit exceeded, try again in a moment")
    data object EmptyResponse : LlmError("The model returned an empty response")
    /** OpenRouter fallback chain walked the entire candidate list without one succeeding. */
    data object AllCandidatesExhausted : LlmError("All attempted OpenRouter models returned an error or empty response")
    class Network(cause: Throwable) : LlmError("No internet connection", cause)
    class Server(val code: Int, body: String) : LlmError("Server error $code: $body")
    class Unknown(cause: Throwable) : LlmError(cause.message ?: "Unknown error", cause)
}
