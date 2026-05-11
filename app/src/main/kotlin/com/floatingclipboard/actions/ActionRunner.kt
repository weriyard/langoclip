package com.floatingclipboard.actions

import com.floatingclipboard.data.LlmCache
import com.floatingclipboard.data.LogStore
import com.floatingclipboard.data.Settings
import com.floatingclipboard.data.SettingsRepository
import com.floatingclipboard.llm.BREAKDOWN_SCHEMA
import com.floatingclipboard.llm.EXAMPLES_SCHEMA
import com.floatingclipboard.llm.LlmError
import com.floatingclipboard.llm.createLlmClient
import com.floatingclipboard.ui.ActionState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class ActionRunner(
    private val settingsRepository: SettingsRepository,
    private val prompts: PromptLoader,
    private val cache: LlmCache,
    private val logs: LogStore,
) {

    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }

    val settings: Flow<Settings> get() = settingsRepository.settings

    fun runStreaming(action: Action, userText: String): Flow<ActionState> = flow {
        if (userText.isBlank()) {
            emit(ActionState.Error(action, "Tekst jest pusty"))
            return@flow
        }
        val settings = settingsRepository.settings.first()
        val systemPrompt = prompts.render(
            action.promptFile,
            mapOf("targetLanguage" to settings.targetLanguage),
        )
        val key = LlmCache.keyFor(CACHE_VERSION, "${settings.provider.name}:${settings.activeModel}", systemPrompt, userText)

        val cached = cache.get(key)
        if (cached != null) {
            val parsed = parseAction(action, cached)
            if (parsed.isSuccess) {
                logs.d(TAG, "CACHE HIT ${settings.provider.name}/${settings.activeModel} action=$action")
                emit(ActionState.Success(action, parsed.getOrThrow()))
                return@flow
            }
            // Cache trzymał zły JSON — invaliduj i spadnij do live calla.
            logs.w(TAG, "CACHE_CORRUPT evicting; reason=${parsed.exceptionOrNull()?.message?.take(120)}")
            cache.invalidate(key)
        }

        logs.d(TAG, "CALL  ${settings.provider.name}/${settings.activeModel} action=$action textLen=${userText.length}")
        emit(ActionState.Loading(action))
        val client = createLlmClient(settings)
        val schema = if (action == Action.EXPLAIN_SENTENCE) BREAKDOWN_SCHEMA else null
        val showPartialText = action == Action.TRANSLATE
        val streamBreakdown = action == Action.EXPLAIN_SENTENCE
        val builder = StringBuilder()
        val breakdownParser = if (streamBreakdown) StreamingBreakdownParser(json) else null
        var lastBreakdownEmitted = 0
        var deltaCount = 0
        val startedAt = System.currentTimeMillis()

        try {
            client.stream(systemPrompt, userText, schema).collect { delta ->
                deltaCount++
                if (deltaCount == 1) {
                    logs.d(TAG, "TTFT  ${System.currentTimeMillis() - startedAt}ms (first delta arrived)")
                }
                builder.append(delta)
                if (showPartialText) {
                    emit(ActionState.Loading(action, partialText = builder.toString()))
                } else if (breakdownParser != null) {
                    val items = breakdownParser.extractItems(builder.toString())
                    if (items.size > lastBreakdownEmitted) {
                        lastBreakdownEmitted = items.size
                        emit(ActionState.Loading(action, partialBreakdown = items))
                    }
                }
            }
            logs.d(TAG, "DONE  ${System.currentTimeMillis() - startedAt}ms total, $deltaCount deltas, ${builder.length} chars")
            val full = builder.toString()
            if (full.isBlank()) {
                logs.w(TAG, "EMPTY response from ${settings.provider.name}")
                emit(ActionState.Error(action, "Pusta odpowiedź"))
                return@flow
            }
            parseAction(action, full).fold(
                onSuccess = {
                    // CACHE WRITE TYLKO PO SUKCESIE PARSE — zły JSON nigdy nie trafia do cache.
                    cache.put(key, full)
                    emit(ActionState.Success(action, it))
                },
                onFailure = { err ->
                    logs.e(TAG, "PARSE_ERROR action=$action: ${err.message}")
                    logs.e(TAG, "RAW(prefix): ${full.take(400)}")
                    emit(ActionState.Error(action, "Parse error: ${err.message}"))
                },
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: LlmError) {
            logs.e(TAG, "LLM_ERROR ${e::class.simpleName}: ${e.message}")
            emit(ActionState.Error(action, e.message ?: "Nieznany błąd"))
        } catch (e: Throwable) {
            logs.e(TAG, "UNKNOWN_ERROR ${e::class.simpleName}: ${e.message}")
            emit(ActionState.Error(action, e.message ?: "Nieznany błąd"))
        }
    }

    suspend fun runExamples(phrase: String, variant: Int = 0): Result<PhraseExamples> {
        if (phrase.isBlank()) {
            return Result.failure(IllegalArgumentException("Fraza jest pusta"))
        }
        val settings = settingsRepository.settings.first()
        val systemPrompt = prompts.render(
            "phrase_examples.md",
            mapOf("phrase" to phrase, "targetLanguage" to settings.targetLanguage),
        )
        // variant zmienia klucz cache (różne sety przykładów dla tej samej frazy są niezależnie
        // cache'owane). Doklejamy też do user promptu w wariantach > 0 prośbę o NOWE zdania,
        // żeby model nie zwrócił tych samych co poprzednio.
        val userPrompt = if (variant == 0) phrase else
            "$phrase\n\n(Wygeneruj ${variant + 1}. zestaw — INNE zdania niż wcześniej, inne konteksty.)"
        val key = LlmCache.keyFor(
            CACHE_VERSION,
            "${settings.provider.name}:${settings.activeModel}:v$variant",
            systemPrompt,
            phrase,
        )

        cache.get(key)?.let { rawCached ->
            val result = parseExamples(phrase, rawCached)
            if (result.isSuccess) {
                logs.d(TAG, "CACHE HIT examples phrase='${phrase.take(40)}' variant=$variant")
                return result
            }
            logs.w(TAG, "CACHE_CORRUPT examples; evicting key")
            cache.invalidate(key)
            // Spadamy do live calla.
        }

        logs.d(TAG, "CALL  examples ${settings.provider.name}/${settings.activeModel} phrase='${phrase.take(40)}' variant=$variant")
        val client = createLlmClient(settings)
        return client.complete(systemPrompt, userPrompt, EXAMPLES_SCHEMA)
            .fold(
                onSuccess = { rawText ->
                    parseExamples(phrase, rawText).also { result ->
                        if (result.isSuccess) {
                            cache.put(key, rawText)
                        } else {
                            logs.e(TAG, "PARSE_ERROR examples: ${result.exceptionOrNull()?.message}")
                            logs.e(TAG, "RAW(prefix): ${rawText.take(400)}")
                        }
                    }
                },
                onFailure = {
                    logs.e(TAG, "LLM_ERROR examples: ${it.message}")
                    Result.failure(it)
                },
            )
    }

    private fun parseAction(action: Action, rawText: String): Result<ActionResult> = runCatching {
        when (action) {
            Action.TRANSLATE -> ActionResult.Text(rawText)
            Action.EXPLAIN_SENTENCE -> {
                val items = parseBreakdownItems(rawText)
                    ?: throw IllegalStateException("Breakdown parsowanie nieudane")
                if (items.isEmpty()) throw IllegalStateException("Breakdown bez itemów")
                ActionResult.Breakdown(items = items)
            }
        }
    }

    /**
     * Próba 1: standardowy decodeFromString — działa gdy items to prawdziwy JSON array.
     * Próba 2 (recovery): czasem Claude w tool use wraps tablicę jako stringified JSON,
     *   czyli `{"items": "[\n  {\n    \"original\": ...]"}`. Wtedy items.jsonPrimitive.content
     *   to escapowany JSON, który trzeba odeszczepić i sparsować jako array. Loguje "RECOVERED".
     */
    private fun parseBreakdownItems(rawText: String): List<BreakdownItem>? {
        runCatching {
            return json.decodeFromString<BreakdownDto>(rawText).items.map { it.toDomain() }
        }
        // Recovery — sprawdź czy items jest stringiem.
        runCatching {
            val root = json.parseToJsonElement(rawText)
            if (root !is JsonObject) return null
            val itemsField = root["items"] ?: return null
            if (itemsField is JsonPrimitive && itemsField.isString) {
                val arrayJson = itemsField.jsonPrimitive.content
                val arr = json.parseToJsonElement(arrayJson)
                if (arr is JsonArray) {
                    val recovered = arr.map { json.decodeFromJsonElement(BreakdownItemDto.serializer(), it) }
                    logs.w(TAG, "RECOVERED breakdown items from stringified array (Claude tool-use quirk)")
                    return recovered.map { it.toDomain() }
                }
            }
        }
        return null
    }

    private fun parseExamples(phrase: String, rawText: String): Result<PhraseExamples> = runCatching {
        val examples = parseExamplesList(rawText)
            ?: throw IllegalStateException("Examples parsowanie nieudane")
        if (examples.isEmpty()) throw IllegalStateException("Examples bez przykładów")
        PhraseExamples(phrase = phrase, examples = examples)
    }

    private fun parseExamplesList(rawText: String): List<Example>? {
        runCatching {
            return json.decodeFromString<ExamplesDto>(rawText).examples.map { it.toDomain() }
        }
        runCatching {
            val root = json.parseToJsonElement(rawText)
            if (root !is JsonObject) return null
            val field = root["examples"] ?: return null
            if (field is JsonPrimitive && field.isString) {
                val arr = json.parseToJsonElement(field.jsonPrimitive.content)
                if (arr is JsonArray) {
                    val recovered = arr.map { json.decodeFromJsonElement(ExampleDto.serializer(), it) }
                    logs.w(TAG, "RECOVERED examples from stringified array")
                    return recovered.map { it.toDomain() }
                }
            }
        }
        return null
    }

    companion object {
        // v3: explicit "array not string" reminders + tool description update.
        private const val CACHE_VERSION = "v3"
        private const val TAG = "LLM"
    }
}

@Serializable
private data class BreakdownDto(
    val items: List<BreakdownItemDto> = emptyList(),
)

@Serializable
private data class BreakdownItemDto(
    val original: String = "",
    val translation: String = "",
    val partOfSpeech: String = "OTHER",
    val explanation: String = "",
)

private fun BreakdownItemDto.toDomain() = BreakdownItem(
    original = original,
    translation = translation,
    partOfSpeech = PartOfSpeech.parse(partOfSpeech),
    explanation = explanation,
)

@Serializable
private data class ExamplesDto(
    val examples: List<ExampleDto> = emptyList(),
)

@Serializable
private data class ExampleDto(
    val english: String = "",
    val translation: String = "",
    val usageNote: String = "",
)

private fun ExampleDto.toDomain() = Example(
    english = english,
    translation = translation,
    usageNote = usageNote,
)

private class StreamingBreakdownParser(private val json: Json) {
    private val itemsMarker = "\"items\":["

    fun extractItems(accumulated: String): List<BreakdownItem> {
        val markerIndex = accumulated.indexOf(itemsMarker)
        if (markerIndex < 0) return emptyList()
        val content = accumulated.substring(markerIndex + itemsMarker.length)

        val items = mutableListOf<BreakdownItem>()
        var depth = 0
        var start = -1
        var inString = false
        var escapeNext = false

        for (i in content.indices) {
            val c = content[i]
            when {
                escapeNext -> escapeNext = false
                c == '\\' && inString -> escapeNext = true
                c == '"' -> inString = !inString
                inString -> Unit
                c == '{' -> {
                    if (depth == 0) start = i
                    depth++
                }
                c == '}' -> {
                    depth--
                    if (depth == 0 && start >= 0) {
                        val objJson = content.substring(start, i + 1)
                        runCatching {
                            val dto = json.decodeFromString<BreakdownItemDto>(objJson)
                            items.add(dto.toDomain())
                        }
                        start = -1
                    }
                }
            }
        }
        return items
    }
}
