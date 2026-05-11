package com.floatingclipboard.llm

import io.ktor.client.call.body
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.preparePost
import io.ktor.client.request.setBody
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
import kotlinx.serialization.SerialName
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import java.io.IOException

/**
 * Klient Anthropic Messages API. Streaming SSE z eventami `message_start`, `content_block_delta`,
 * `message_stop` itp. Tekstowe delty mają `delta.type == "text_delta"`.
 *
 * Structured output: porzucamy tool use (Sonnet 4.6 przy długich schemach zwraca arrays jako
 * stringified JSON ze zepsutymi escape'ami — niemożliwe do zdeserializowania). Zamiast tego:
 * dorzucamy schemę do system promptu jako tekst i prosimy model o plain JSON response w content.
 * Trade-off: brak hard validation, ale Claude w plain text mode konsystentnie respektuje opisaną
 * strukturę i nie ma layeru "tool input encoded as escaped string".
 */
class AnthropicClient(
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
            val response = llmHttpClient.post(BASE_URL) {
                header(HEADER_API_KEY, apiKey)
                header(HEADER_VERSION, ANTHROPIC_VERSION)
                contentType(ContentType.Application.Json)
                setBody(buildRequest(systemPrompt, userPrompt, jsonSchema, stream = false))
            }
            when (val code = response.status.value) {
                401, 403 -> throw LlmError.Unauthorized
                429 -> throw LlmError.RateLimited
                else -> if (!response.status.isSuccess()) {
                    throw LlmError.Server(code, response.bodyAsText().take(500))
                }
            }
            val body = response.body<AnthropicResponse>()
            val text = body.content.firstOrNull { it.type == "text" }
                ?.text
                ?.takeIf { it.isNotBlank() }
                ?: throw LlmError.EmptyResponse
            // Prefilled "{" nie wraca w response, doklejamy z przodu dla structured output.
            if (jsonSchema != null) "{$text" else text
        }.recoverCatching { e ->
            throw mapError(e)
        }
    }

    override fun stream(
        systemPrompt: String,
        userPrompt: String,
        jsonSchema: JsonElement?,
    ): Flow<String> = flow {
        if (apiKey.isBlank()) throw LlmError.MissingApiKey
        val parser = Json { ignoreUnknownKeys = true }
        // Prefilled assistant message z `{` nie wraca w response — doklejamy go z przodu.
        var prefillEmitted = jsonSchema == null

        llmHttpClient.preparePost(BASE_URL) {
            header(HEADER_API_KEY, apiKey)
            header(HEADER_VERSION, ANTHROPIC_VERSION)
            contentType(ContentType.Application.Json)
            setBody(buildRequest(systemPrompt, userPrompt, jsonSchema, stream = true))
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
                val event: AnthropicStreamEvent? = try {
                    parser.decodeFromString<AnthropicStreamEvent>(payload)
                } catch (e: SerializationException) {
                    null
                }
                // Bez tool use bierzemy tylko text_delta — JSON odpowiedzi siedzi w treści.
                if (event?.type == "content_block_delta") {
                    event.delta?.text?.takeIf { it.isNotEmpty() }?.let { text ->
                        if (!prefillEmitted) {
                            emit("{")
                            prefillEmitted = true
                        }
                        emit(text)
                    }
                }
            }
        }
    }
        .catch { e -> throw mapError(e) }
        .flowOn(Dispatchers.IO)

    private fun buildRequest(
        systemPrompt: String,
        userPrompt: String,
        jsonSchema: JsonElement?,
        stream: Boolean,
    ): AnthropicRequest {
        // Gdy mamy schemę, doklejamy ją do system promptu jako tekstową instrukcję — nie używamy
        // tool use (patrz docstring klasy). Wymuszamy żeby PIERWSZY znak odpowiedzi był `{` —
        // to znana technika: model rzadziej dorzuca prefacing text typu "Here's the JSON:".
        val effectiveSystem = if (jsonSchema != null) {
            buildString {
                if (systemPrompt.isNotBlank()) {
                    append(systemPrompt)
                    append("\n\n")
                }
                append("Output MUST be valid JSON matching this schema exactly. ")
                append("Reply with the JSON object only — no markdown fences, no commentary, ")
                append("no leading or trailing text. Start your response with `{` and end with `}`.\n\n")
                append("Schema:\n```json\n")
                append(jsonSchema.toString())
                append("\n```")
            }
        } else systemPrompt.takeIf { it.isNotBlank() } ?: ""

        // Prefill JSON na "{" sprawia że model jest zmuszony kontynuować jako JSON od pierwszego
        // tokena. To Anthropic-specific trick: assistant message z partial content na końcu.
        val messages = if (jsonSchema != null) {
            listOf(
                AnthropicMessage(role = "user", content = userPrompt),
                AnthropicMessage(role = "assistant", content = "{"),
            )
        } else {
            listOf(AnthropicMessage(role = "user", content = userPrompt))
        }

        return AnthropicRequest(
            model = model,
            system = effectiveSystem.takeIf { it.isNotBlank() },
            messages = messages,
            maxTokens = MAX_TOKENS,
            stream = stream,
        )
    }

    private fun mapError(e: Throwable): Throwable = when (e) {
        is LlmError -> e
        is CancellationException -> e
        is IOException -> LlmError.Network(e)
        else -> LlmError.Unknown(e)
    }

    companion object {
        private const val BASE_URL = "https://api.anthropic.com/v1/messages"
        private const val HEADER_API_KEY = "x-api-key"
        private const val HEADER_VERSION = "anthropic-version"
        private const val ANTHROPIC_VERSION = "2023-06-01"
        private const val MAX_TOKENS = 8192
    }
}

@Serializable
private data class AnthropicRequest(
    val model: String,
    val system: String? = null,
    val messages: List<AnthropicMessage>,
    @SerialName("max_tokens")
    val maxTokens: Int,
    val stream: Boolean = false,
)

@Serializable
private data class AnthropicMessage(
    val role: String,
    val content: String,
)

@Serializable
private data class AnthropicResponse(
    val content: List<AnthropicContentBlock> = emptyList(),
    @SerialName("stop_reason")
    val stopReason: String? = null,
)

@Serializable
private data class AnthropicContentBlock(
    val type: String,
    val text: String? = null,
)

@Serializable
private data class AnthropicStreamEvent(
    val type: String,
    val delta: AnthropicStreamDelta? = null,
)

@Serializable
private data class AnthropicStreamDelta(
    val type: String? = null,
    val text: String? = null,
)
