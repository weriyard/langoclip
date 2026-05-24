package com.floatingclipboard.llm

import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.JsonElement

interface LlmClient {
    suspend fun complete(
        systemPrompt: String,
        userPrompt: String,
        jsonSchema: JsonElement? = null,
    ): Result<String>

    fun stream(
        systemPrompt: String,
        userPrompt: String,
        jsonSchema: JsonElement? = null,
    ): Flow<String>
}

sealed class LlmError(message: String, cause: Throwable? = null) : Exception(message, cause) {
    data object MissingApiKey : LlmError("Brak klucza API — skonfiguruj w ustawieniach")
    data object Unauthorized : LlmError("Nieprawidłowy klucz API")
    data object RateLimited : LlmError("Limit zapytań przekroczony, spróbuj za chwilę")
    data object EmptyResponse : LlmError("Model zwrócił pustą odpowiedź")
    /** OpenRouter fallback chain walked the entire candidate list without one succeeding. */
    data object AllCandidatesExhausted : LlmError("Wszystkie próbowane modele OpenRouter zwróciły błąd lub pustkę")
    class Network(cause: Throwable) : LlmError("Brak połączenia z internetem", cause)
    class Server(val code: Int, body: String) : LlmError("Błąd serwera $code: $body")
    class Unknown(cause: Throwable) : LlmError(cause.message ?: "Nieznany błąd", cause)
}
