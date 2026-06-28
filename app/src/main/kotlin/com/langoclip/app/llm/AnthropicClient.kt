package com.langoclip.app.llm

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
 * Anthropic Messages API client with native **structured outputs** (GA since 2025-11).
 *
 * Schema goes in `output_config.format` (type=json_schema) — the model is constrained at the
 * tokenization level, guaranteeing valid JSON conforming to the schema. The response arrives as
 * stringified JSON in `content[0].text`.
 *
 * No tool use, no prefill, no schema-in-system-prompt — clean integration.
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
                setBody(buildRequest(
                    systemPrompt,
                    listOf(AnthropicMessage(role = "user", content = userPrompt)),
                    jsonSchema,
                    stream = false,
                ))
            }
            when (val code = response.status.value) {
                401, 403 -> throw LlmError.Unauthorized
                429 -> throw LlmError.RateLimited
                else -> if (!response.status.isSuccess()) {
                    throw LlmError.Server(code, response.bodyAsText().take(500))
                }
            }
            val body = response.body<AnthropicResponse>()
            body.content.firstOrNull { it.type == "text" }
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
    ): Flow<String> = streamMessages(
        systemPrompt,
        listOf(AnthropicMessage(role = "user", content = userPrompt)),
        jsonSchema,
        onUsage,
    )

    /**
     * Multi-turn streaming. Each [ChatTurn] is mapped to an Anthropic message; full conversation
     * history must be passed every call (the API is stateless). The Flow emits text deltas exactly
     * like single-shot [stream], so callers can append into a StringBuilder and rebuild the
     * assistant turn progressively.
     */
    override fun streamChat(
        systemPrompt: String,
        turns: List<ChatTurn>,
        onUsage: ((TokenUsage) -> Unit)?,
    ): Flow<String> =
        streamMessages(
            systemPrompt,
            turns.map { AnthropicMessage(role = it.role, content = it.content) },
            jsonSchema = null,
            onUsage = onUsage,
        )

    private fun streamMessages(
        systemPrompt: String,
        messages: List<AnthropicMessage>,
        jsonSchema: JsonElement?,
        onUsage: ((TokenUsage) -> Unit)?,
    ): Flow<String> = flow {
        if (apiKey.isBlank()) throw LlmError.MissingApiKey
        val parser = Json { ignoreUnknownKeys = true }
        // Anthropic splits usage across two events: message_start carries input_tokens (+ an
        // initial output_tokens estimate, usually 1) and message_delta updates output_tokens at
        // the very end with the final count.
        var inputTokens = 0
        var outputTokens = 0

        llmHttpClient.preparePost(BASE_URL) {
            header(HEADER_API_KEY, apiKey)
            header(HEADER_VERSION, ANTHROPIC_VERSION)
            contentType(ContentType.Application.Json)
            setBody(buildRequest(systemPrompt, messages, jsonSchema, stream = true))
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
                when (event?.type) {
                    "content_block_delta" ->
                        event.delta?.text?.takeIf { it.isNotEmpty() }?.let { emit(it) }
                    "message_start" -> event.message?.usage?.let { u ->
                        inputTokens = u.inputTokens
                        outputTokens = u.outputTokens
                    }
                    "message_delta" -> event.usage?.let { u ->
                        // Last message_delta has the final output count.
                        outputTokens = u.outputTokens
                    }
                }
            }
        }
        if (inputTokens > 0 || outputTokens > 0) {
            onUsage?.invoke(TokenUsage(inputTokens, outputTokens))
        }
    }
        .catch { e -> throw mapError(e) }
        .flowOn(Dispatchers.IO)

    private fun buildRequest(
        systemPrompt: String,
        messages: List<AnthropicMessage>,
        jsonSchema: JsonElement?,
        stream: Boolean,
    ) = AnthropicRequest(
        model = model,
        system = systemPrompt.takeIf { it.isNotBlank() },
        messages = messages,
        maxTokens = MAX_TOKENS,
        stream = stream,
        // GA structured outputs — schema constraint at the tokenization level.
        // Uses toOpenAiStrictSchema: lowercase types + additionalProperties:false (both required by
        // Anthropic) + strips minItems/maxItems (Anthropic only supports minItems 0 or 1).
        // Count enforcement (exactly 5 examples) falls back to the system prompt.
        outputConfig = jsonSchema?.let {
            AnthropicOutputConfig(
                format = AnthropicOutputFormat(
                    type = "json_schema",
                    schema = toOpenAiStrictSchema(it),
                )
            )
        },
    )

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
    @SerialName("output_config")
    val outputConfig: AnthropicOutputConfig? = null,
)

@Serializable
private data class AnthropicOutputConfig(
    val format: AnthropicOutputFormat,
)

@Serializable
private data class AnthropicOutputFormat(
    val type: String,
    val schema: JsonElement,
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
    val message: AnthropicStreamMessage? = null,
    val usage: AnthropicStreamUsage? = null,
)

@Serializable
private data class AnthropicStreamDelta(
    val type: String? = null,
    val text: String? = null,
)

@Serializable
private data class AnthropicStreamMessage(
    val usage: AnthropicStreamUsage? = null,
)

@Serializable
private data class AnthropicStreamUsage(
    @SerialName("input_tokens")
    val inputTokens: Int = 0,
    @SerialName("output_tokens")
    val outputTokens: Int = 0,
)
