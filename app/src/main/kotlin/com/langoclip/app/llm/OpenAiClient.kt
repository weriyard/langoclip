package com.langoclip.app.llm

import io.ktor.client.call.body
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.preparePost
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsChannel
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
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
 * OpenAI Chat Completions API client. Streaming via SSE with `data: {...}` and `data: [DONE]`
 * terminator. Structured output via `response_format: { type: "json_schema", strict: true }` —
 * the schema must be in OpenAI strict format (lowercase types, additionalProperties:false). We
 * convert from our Gemini-format in [toOpenAiStrictSchema].
 */
class OpenAiClient(
    private val apiKey: String,
    private val model: String,
    private val baseUrl: String = OPENAI_BASE_URL,
) : LlmClient {

    override suspend fun complete(
        systemPrompt: String,
        userPrompt: String,
        jsonSchema: JsonElement?,
    ): Result<String> {
        if (apiKey.isBlank()) return Result.failure(LlmError.MissingApiKey)
        return runCatching {
            val response = llmHttpClient.post(baseUrl) {
                header(HttpHeaders.Authorization, "Bearer $apiKey")
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
            val body = response.body<OpenAiResponse>()
            body.choices.firstOrNull()
                ?.message?.content
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
        if (apiKey.isBlank()) throw LlmError.MissingApiKey
        val parser = Json { ignoreUnknownKeys = true }
        var lastUsage: OpenAiUsage? = null

        llmHttpClient.preparePost(baseUrl) {
            header(HttpHeaders.Authorization, "Bearer $apiKey")
            contentType(ContentType.Application.Json)
            // include_usage=true asks OpenAI-compat providers (including OpenRouter) to send a
            // final SSE chunk with prompt_tokens / completion_tokens in `usage`.
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
                if (payload.isEmpty() || payload == "[DONE]") continue
                val chunk: OpenAiStreamChunk? = try {
                    parser.decodeFromString<OpenAiStreamChunk>(payload)
                } catch (e: SerializationException) {
                    null
                }
                chunk?.choices?.firstOrNull()
                    ?.delta?.content
                    ?.takeIf { it.isNotEmpty() }
                    ?.let { emit(it) }
                // Usage typically arrives in the terminal chunk (after the last content delta).
                // Keep updating — last one wins, and providers can send it more than once.
                chunk?.usage?.let { lastUsage = it }
            }
        }
        lastUsage?.let { onUsage?.invoke(TokenUsage(it.promptTokens, it.completionTokens)) }
    }
        .catch { e ->
            throw when (e) {
                is LlmError -> e
                is CancellationException -> e
                is IOException -> LlmError.Network(e)
                else -> LlmError.Unknown(e)
            }
        }
        .flowOn(Dispatchers.IO)

    private fun buildRequest(
        systemPrompt: String,
        userPrompt: String,
        jsonSchema: JsonElement?,
        stream: Boolean,
    ) = OpenAiRequest(
        model = model,
        messages = listOfNotNull(
            systemPrompt.takeIf { it.isNotBlank() }
                ?.let { OpenAiMessage(role = "system", content = it) },
            OpenAiMessage(role = "user", content = userPrompt),
        ),
        stream = stream,
        // Required to make OpenRouter / OpenAI emit a final SSE chunk containing `usage`.
        streamOptions = if (stream) OpenAiStreamOptions(includeUsage = true) else null,
        responseFormat = jsonSchema?.let {
            OpenAiResponseFormat(
                type = "json_schema",
                jsonSchema = OpenAiJsonSchemaSpec(
                    name = "structured_output",
                    schema = toOpenAiStrictSchema(it),
                    strict = true,
                ),
            )
        },
    )

    private fun mapError(e: Throwable): Throwable = when (e) {
        is LlmError -> e
        is IOException -> LlmError.Network(e)
        else -> LlmError.Unknown(e)
    }

    companion object {
        const val OPENAI_BASE_URL = "https://api.openai.com/v1/chat/completions"
        // OpenRouter speaks the OpenAI Chat Completions protocol verbatim — same payload, same SSE
        // framing — so we just point this client at their gateway.
        const val OPENROUTER_BASE_URL = "https://openrouter.ai/api/v1/chat/completions"
    }
}

@Serializable
private data class OpenAiRequest(
    val model: String,
    val messages: List<OpenAiMessage>,
    val stream: Boolean = false,
    @SerialName("stream_options")
    val streamOptions: OpenAiStreamOptions? = null,
    @SerialName("response_format")
    val responseFormat: OpenAiResponseFormat? = null,
)

@Serializable
private data class OpenAiStreamOptions(
    @SerialName("include_usage")
    val includeUsage: Boolean,
)

@Serializable
private data class OpenAiUsage(
    @SerialName("prompt_tokens")
    val promptTokens: Int = 0,
    @SerialName("completion_tokens")
    val completionTokens: Int = 0,
    @SerialName("total_tokens")
    val totalTokens: Int = 0,
)

@Serializable
private data class OpenAiMessage(
    val role: String,
    val content: String,
)

@Serializable
private data class OpenAiResponseFormat(
    val type: String,
    @SerialName("json_schema")
    val jsonSchema: OpenAiJsonSchemaSpec,
)

@Serializable
private data class OpenAiJsonSchemaSpec(
    val name: String,
    val schema: JsonElement,
    val strict: Boolean = true,
)

@Serializable
private data class OpenAiResponse(
    val choices: List<OpenAiChoice> = emptyList(),
)

@Serializable
private data class OpenAiChoice(
    val message: OpenAiMessage? = null,
    @SerialName("finish_reason")
    val finishReason: String? = null,
)

@Serializable
private data class OpenAiStreamChunk(
    val choices: List<OpenAiStreamChoice> = emptyList(),
    val usage: OpenAiUsage? = null,
)

@Serializable
private data class OpenAiStreamChoice(
    val delta: OpenAiStreamDelta? = null,
    @SerialName("finish_reason")
    val finishReason: String? = null,
)

@Serializable
private data class OpenAiStreamDelta(
    val role: String? = null,
    val content: String? = null,
)
