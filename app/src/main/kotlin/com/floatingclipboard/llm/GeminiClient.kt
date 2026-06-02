package com.floatingclipboard.llm

import io.ktor.client.call.body
import io.ktor.client.request.post
import io.ktor.client.request.preparePost
import io.ktor.client.request.setBody
import io.ktor.client.request.url
import io.ktor.client.statement.bodyAsChannel
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.utils.io.readUTF8Line
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.serialization.SerializationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import java.io.IOException

class GeminiClient(
    private val apiKey: String,
    private val model: String,
) : LlmClient {

    override suspend fun complete(
        systemPrompt: String,
        userPrompt: String,
        jsonSchema: JsonElement?,
    ): Result<String> {
        if (apiKey.isBlank()) return Result.failure(LlmError.MissingApiKey)
        return runCatching {
            val response = llmHttpClient.post("$BASE_URL/models/$model:generateContent") {
                url { parameters.append("key", apiKey) }
                contentType(ContentType.Application.Json)
                setBody(buildRequest(systemPrompt, userPrompt, jsonSchema))
            }
            when (val code = response.status.value) {
                401, 403 -> throw LlmError.Unauthorized
                429 -> throw LlmError.RateLimited
                else -> if (!response.status.isSuccess()) {
                    throw LlmError.Server(code, response.bodyAsText().take(500))
                }
            }
            val body = response.body<GeminiResponse>()
            body.candidates.firstOrNull()
                ?.content?.parts?.firstOrNull()
                ?.text
                ?.takeIf { it.isNotBlank() }
                ?: throw LlmError.EmptyResponse
        }.recoverCatching { e ->
            throw mapError(e)
        }
    }

    override fun stream(
        systemPrompt: String,
        userPrompt: String,
        jsonSchema: JsonElement?,
        onUsage: ((TokenUsage) -> Unit)?,
    ): Flow<String> = flow {
        // Gemini reports usageMetadata.{prompt,candidates}TokenCount in the final SSE chunk; not
        // wired up yet — leaving onUsage as a no-op for this provider keeps the contract honest.
        if (apiKey.isBlank()) throw LlmError.MissingApiKey
        val parser = Json { ignoreUnknownKeys = true }

        llmHttpClient.preparePost("$BASE_URL/models/$model:streamGenerateContent") {
            url {
                parameters.append("key", apiKey)
                parameters.append("alt", "sse")
            }
            contentType(ContentType.Application.Json)
            setBody(buildRequest(systemPrompt, userPrompt, jsonSchema))
        }.execute { response ->
            if (!response.status.isSuccess()) {
                val body = runCatching { response.bodyAsText() }.getOrDefault("")
                throw when (response.status.value) {
                    401, 403 -> LlmError.Unauthorized
                    429 -> LlmError.RateLimited
                    else -> LlmError.Server(response.status.value, body.take(500))
                }
            }
            val channel = response.bodyAsChannel()
            while (!channel.isClosedForRead) {
                val line = channel.readUTF8Line() ?: break
                if (!line.startsWith("data: ")) continue
                val payload = line.removePrefix("data: ").trim()
                if (payload.isEmpty()) continue
                val chunk: GeminiResponse? = try {
                    parser.decodeFromString<GeminiResponse>(payload)
                } catch (e: SerializationException) {
                    null
                }
                chunk?.candidates?.firstOrNull()
                    ?.content?.parts?.firstOrNull()
                    ?.text
                    ?.takeIf { it.isNotEmpty() }
                    ?.let { emit(it) }
            }
        }
    }
        .catch { e -> throw mapError(e) }
        .flowOn(Dispatchers.IO)

    /**
     * Multi-turn streaming. Gemini's `contents` array is natively multi-turn — each [ChatTurn] maps
     * to one entry, with the "assistant" role renamed to Gemini's "model". Emits text deltas exactly
     * like [stream], so chat callers accumulate into a StringBuilder.
     */
    override fun streamChat(
        systemPrompt: String,
        turns: List<ChatTurn>,
        onUsage: ((TokenUsage) -> Unit)?,
    ): Flow<String> = flow {
        if (apiKey.isBlank()) throw LlmError.MissingApiKey
        val parser = Json { ignoreUnknownKeys = true }

        llmHttpClient.preparePost("$BASE_URL/models/$model:streamGenerateContent") {
            url {
                parameters.append("key", apiKey)
                parameters.append("alt", "sse")
            }
            contentType(ContentType.Application.Json)
            setBody(buildChatRequest(systemPrompt, turns))
        }.execute { response ->
            if (!response.status.isSuccess()) {
                val body = runCatching { response.bodyAsText() }.getOrDefault("")
                throw when (response.status.value) {
                    401, 403 -> LlmError.Unauthorized
                    429 -> LlmError.RateLimited
                    else -> LlmError.Server(response.status.value, body.take(500))
                }
            }
            val channel = response.bodyAsChannel()
            while (!channel.isClosedForRead) {
                val line = channel.readUTF8Line() ?: break
                if (!line.startsWith("data: ")) continue
                val payload = line.removePrefix("data: ").trim()
                if (payload.isEmpty()) continue
                val chunk: GeminiResponse? = try {
                    parser.decodeFromString<GeminiResponse>(payload)
                } catch (e: SerializationException) {
                    null
                }
                chunk?.candidates?.firstOrNull()
                    ?.content?.parts?.firstOrNull()
                    ?.text
                    ?.takeIf { it.isNotEmpty() }
                    ?.let { emit(it) }
            }
        }
    }
        .catch { e -> throw mapError(e) }
        .flowOn(Dispatchers.IO)

    private fun buildRequest(
        systemPrompt: String,
        userPrompt: String,
        jsonSchema: JsonElement?,
    ) = GeminiRequest(
        systemInstruction = systemPrompt.toSystemInstruction(),
        contents = listOf(
            GeminiContent(role = "user", parts = listOf(GeminiPart(text = userPrompt)))
        ),
        generationConfig = jsonSchema?.let {
            GeminiGenerationConfig(
                responseMimeType = "application/json",
                responseSchema = it,
            )
        },
    )

    private fun buildChatRequest(
        systemPrompt: String,
        turns: List<ChatTurn>,
    ) = GeminiRequest(
        systemInstruction = systemPrompt.toSystemInstruction(),
        contents = turns.map { turn ->
            GeminiContent(
                // Gemini uses "model" where the rest of the app says "assistant".
                role = if (turn.role == "assistant") "model" else "user",
                parts = listOf(GeminiPart(text = turn.content)),
            )
        },
    )

    private fun String.toSystemInstruction(): GeminiContent? =
        takeIf { it.isNotBlank() }?.let { GeminiContent(parts = listOf(GeminiPart(text = it))) }

    private fun mapError(e: Throwable): Throwable = when (e) {
        is LlmError -> e
        is CancellationException -> e
        is IOException -> LlmError.Network(e)
        else -> LlmError.Unknown(e)
    }

    companion object {
        private const val BASE_URL = "https://generativelanguage.googleapis.com/v1beta"
    }
}

@Serializable
private data class GeminiRequest(
    val systemInstruction: GeminiContent? = null,
    val contents: List<GeminiContent>,
    val generationConfig: GeminiGenerationConfig? = null,
)

@Serializable
private data class GeminiGenerationConfig(
    val responseMimeType: String? = null,
    val responseSchema: JsonElement? = null,
)

@Serializable
internal data class GeminiContent(
    val role: String? = null,
    val parts: List<GeminiPart>,
)

@Serializable
internal data class GeminiPart(
    val text: String,
)

@Serializable
internal data class GeminiResponse(
    val candidates: List<GeminiCandidate> = emptyList(),
)

@Serializable
internal data class GeminiCandidate(
    val content: GeminiContent? = null,
    val finishReason: String? = null,
)
