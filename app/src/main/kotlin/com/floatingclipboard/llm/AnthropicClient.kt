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
 * `message_stop` itp. Tekstowe delty mają `delta.type == "text_delta"`, JSON dla tool use ma
 * `delta.type == "input_json_delta"` z `partial_json` — caller akumuluje, parsuje na końcu.
 *
 * Structured output realizujemy przez **tool use**: rejestrujemy fikcyjne tool z input_schema =
 * naszą schemą, wymuszamy `tool_choice: { type: "tool", name: "submit" }`, model zwraca JSON jako
 * input narzędzia. To wzorzec produkcyjny dla Anthropic, bo nie mają natywnego JSON Schema mode.
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
            extractFinal(body, jsonSchema != null) ?: throw LlmError.EmptyResponse
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
                if (event?.type == "content_block_delta") {
                    event.delta?.text?.takeIf { it.isNotEmpty() }?.let { emit(it) }
                    event.delta?.partialJson?.takeIf { it.isNotEmpty() }?.let { emit(it) }
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
    ) = AnthropicRequest(
        model = model,
        system = systemPrompt.takeIf { it.isNotBlank() },
        messages = listOf(AnthropicMessage(role = "user", content = userPrompt)),
        maxTokens = MAX_TOKENS,
        stream = stream,
        tools = jsonSchema?.let {
            listOf(
                AnthropicTool(
                    name = TOOL_NAME,
                    description = "Submit the structured output requested by the system prompt.",
                    inputSchema = toAnthropicSchema(it),
                )
            )
        },
        toolChoice = jsonSchema?.let { AnthropicToolChoice(type = "tool", name = TOOL_NAME) },
    )

    private fun extractFinal(body: AnthropicResponse, isStructured: Boolean): String? {
        if (isStructured) {
            // Bierzemy input z tool_use bloku i serializujemy z powrotem do JSON-a (cache trzyma string).
            val toolUse = body.content.firstOrNull { it.type == "tool_use" }
            return toolUse?.input?.toString()
        }
        return body.content
            .firstOrNull { it.type == "text" }
            ?.text
            ?.takeIf { it.isNotBlank() }
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
        private const val TOOL_NAME = "submit_structured"
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
    val tools: List<AnthropicTool>? = null,
    @SerialName("tool_choice")
    val toolChoice: AnthropicToolChoice? = null,
)

@Serializable
private data class AnthropicMessage(
    val role: String,
    val content: String,
)

@Serializable
private data class AnthropicTool(
    val name: String,
    val description: String,
    @SerialName("input_schema")
    val inputSchema: JsonElement,
)

@Serializable
private data class AnthropicToolChoice(
    val type: String,
    val name: String,
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
    val name: String? = null,
    val input: JsonElement? = null,
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
    @SerialName("partial_json")
    val partialJson: String? = null,
)
