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
        val breakdownParser = if (streamBreakdown) {
            StreamingArrayParser(json, "items", BreakdownItemDto.serializer())
        } else null
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
                    val items = breakdownParser.extract(builder.toString()).map { it.toDomain() }
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
                    logs.saveLastRaw("$action @ ${settings.provider.name}/${settings.activeModel}", full)
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

    fun runExamplesStreaming(phrase: String, variant: Int = 0): Flow<com.floatingclipboard.ui.ExamplesState> = flow {
        if (phrase.isBlank()) {
            emit(com.floatingclipboard.ui.ExamplesState.Error("Fraza jest pusta"))
            return@flow
        }
        val settings = settingsRepository.settings.first()
        val systemPrompt = prompts.render(
            "phrase_examples.md",
            mapOf("phrase" to phrase, "targetLanguage" to settings.targetLanguage),
        )
        val userPrompt = if (variant == 0) phrase else
            "$phrase\n\n(Wygeneruj ${variant + 1}. zestaw — INNE zdania niż wcześniej, inne konteksty.)"
        val key = LlmCache.keyFor(
            CACHE_VERSION,
            "${settings.provider.name}:${settings.activeModel}:v$variant",
            systemPrompt,
            phrase,
        )

        val cached = cache.get(key)
        if (cached != null) {
            val parsed = parseExamples(phrase, cached)
            if (parsed.isSuccess) {
                logs.d(TAG, "CACHE HIT examples phrase='${phrase.take(40)}' variant=$variant")
                emit(com.floatingclipboard.ui.ExamplesState.Success(parsed.getOrThrow(), variant))
                return@flow
            }
            logs.w(TAG, "CACHE_CORRUPT examples; evicting key")
            cache.invalidate(key)
        }

        logs.d(TAG, "CALL  examples ${settings.provider.name}/${settings.activeModel} phrase='${phrase.take(40)}' variant=$variant")
        emit(com.floatingclipboard.ui.ExamplesState.Loading())
        val client = createLlmClient(settings)
        val parser = StreamingArrayParser(json, "examples", ExampleDto.serializer())
        val builder = StringBuilder()
        var lastEmitted = 0
        var deltaCount = 0
        val startedAt = System.currentTimeMillis()

        try {
            client.stream(systemPrompt, userPrompt, EXAMPLES_SCHEMA).collect { delta ->
                deltaCount++
                if (deltaCount == 1) {
                    logs.d(TAG, "TTFT  examples ${System.currentTimeMillis() - startedAt}ms")
                }
                builder.append(delta)
                val partial = parser.extract(builder.toString()).map { it.toDomain() }
                if (partial.size > lastEmitted) {
                    lastEmitted = partial.size
                    emit(com.floatingclipboard.ui.ExamplesState.Loading(partial))
                }
            }
            logs.d(TAG, "DONE  examples ${System.currentTimeMillis() - startedAt}ms, $deltaCount deltas, ${builder.length} chars")
            val full = builder.toString()
            if (full.isBlank()) {
                emit(com.floatingclipboard.ui.ExamplesState.Error("Pusta odpowiedź"))
                return@flow
            }
            parseExamples(phrase, full).fold(
                onSuccess = {
                    cache.put(key, full)
                    emit(com.floatingclipboard.ui.ExamplesState.Success(it, variant))
                },
                onFailure = { err ->
                    logs.e(TAG, "PARSE_ERROR examples: ${err.message}")
                    logs.e(TAG, "RAW(prefix): ${full.take(400)}")
                    logs.saveLastRaw("EXAMPLES @ ${settings.provider.name}/${settings.activeModel}", full)
                    emit(com.floatingclipboard.ui.ExamplesState.Error("Parse error: ${err.message}"))
                },
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: LlmError) {
            logs.e(TAG, "LLM_ERROR examples: ${e.message}")
            emit(com.floatingclipboard.ui.ExamplesState.Error(e.message ?: "Nieznany błąd"))
        } catch (e: Throwable) {
            logs.e(TAG, "UNKNOWN_ERROR examples: ${e.message}")
            emit(com.floatingclipboard.ui.ExamplesState.Error(e.message ?: "Nieznany błąd"))
        }
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
    private fun parseBreakdownItems(rawText: String): List<BreakdownItem>? =
        tryDeserializeArray(rawText, "items", BreakdownItemDto.serializer())?.map { it.toDomain() }

    private fun parseExamples(phrase: String, rawText: String): Result<PhraseExamples> = runCatching {
        val examples = parseExamplesList(rawText)
            ?: throw IllegalStateException("Examples parsowanie nieudane")
        if (examples.isEmpty()) throw IllegalStateException("Examples bez przykładów")
        PhraseExamples(phrase = phrase, examples = examples)
    }

    private fun parseExamplesList(rawText: String): List<Example>? =
        tryDeserializeArray(rawText, "examples", ExampleDto.serializer())?.map { it.toDomain() }

    /**
     * Liberalny parser dla obiektu `{<fieldName>: [...]}` zwracanego przez LLM. Obsługuje:
     *  1. Czysty JSON: `{"items":[...]}`.
     *  2. Markdown-wrapped: ` ```json\n{"items":[...]}\n``` `.
     *  3. Items jako stringified array: `{"items":"[...]"}`.
     *  4. Cały response jako stringified JSON: `"{\"items\":[...]}"`.
     *  5. Items missing — pierwszy znaleziony JsonArray w roocie traktowany jako lista.
     *  6. Items jako pojedynczy obiekt zamiast listy (Claude czasem tak robi).
     */
    private fun <T> tryDeserializeArray(
        rawText: String,
        fieldName: String,
        elementSerializer: kotlinx.serialization.KSerializer<T>,
    ): List<T>? {
        val cleaned = stripMarkdownWrap(rawText).trim()

        // Próba: parse jako JsonElement i znajdź pole.
        runCatching {
            val root = parseLiberal(cleaned)
            val list = extractArrayField(root, fieldName) ?: extractFirstArray(root)
            if (list != null) {
                return list.map { json.decodeFromJsonElement(elementSerializer, it) }
            }
        }.onFailure {
            logs.w(TAG, "PARSE attempt failed: ${it.message?.take(120)}")
        }
        return null
    }

    /** Parsuje raw, jeśli string-as-JSON (otoczony cudzysłowami), odeszczepia. */
    private fun parseLiberal(raw: String): kotlinx.serialization.json.JsonElement {
        val parsed = json.parseToJsonElement(raw)
        if (parsed is JsonPrimitive && parsed.isString) {
            // Cały response był stringified JSON-em
            val inner = parsed.jsonPrimitive.content
            logs.w(TAG, "RECOVERED whole response was stringified JSON")
            return json.parseToJsonElement(inner)
        }
        return parsed
    }

    private fun extractArrayField(root: kotlinx.serialization.json.JsonElement, fieldName: String): JsonArray? {
        if (root !is JsonObject) return null
        val field = root[fieldName] ?: return null
        return when {
            field is JsonArray -> field
            field is JsonPrimitive && field.isString -> {
                // Stringified array
                val inner = json.parseToJsonElement(field.jsonPrimitive.content)
                logs.w(TAG, "RECOVERED $fieldName from stringified array (tool-use quirk)")
                inner as? JsonArray
            }
            field is JsonObject -> {
                // Pojedynczy obiekt zamiast listy — zawiń w listę
                logs.w(TAG, "RECOVERED $fieldName was single object, wrapping in list")
                JsonArray(listOf(field))
            }
            else -> null
        }
    }

    private fun extractFirstArray(root: kotlinx.serialization.json.JsonElement): JsonArray? {
        if (root !is JsonObject) return null
        // Last-resort: weź pierwszy JsonArray jaki znajdziesz w polach roota
        for ((_, value) in root) {
            if (value is JsonArray && value.isNotEmpty()) {
                logs.w(TAG, "RECOVERED items via first-array fallback")
                return value
            }
        }
        return null
    }

    /** Usuwa ` ```json\n{...}\n``` ` opakowanie jeśli model je dodał (markdown formatting). */
    private fun stripMarkdownWrap(raw: String): String {
        val trimmed = raw.trim()
        val withoutFence = trimmed
            .removePrefix("```json").removePrefix("```JSON").removePrefix("```")
            .removeSuffix("```")
            .trim()
        return if (withoutFence != trimmed) {
            logs.w(TAG, "RECOVERED stripped markdown wrap")
            withoutFence
        } else trimmed
    }

    companion object {
        // v5: examples schema dorzucił highlightedSpan field.
        private const val CACHE_VERSION = "v5"
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
    val highlightedSpan: String = "",
    val translation: String = "",
    val usageNote: String = "",
)

private fun ExampleDto.toDomain() = Example(
    english = english,
    translation = translation,
    usageNote = usageNote,
    highlightedSpan = highlightedSpan,
)

/**
 * Progressive parser dla strukturalnego output rosnącego tokenowo. Szuka markera
 * `"<fieldName>":[`, potem liczy klamerki z poprawną obsługą stringów i escape'ów żeby
 * wydobyć KOMPLETNE sub-obiekty z tablicy. Każdy kompletny obiekt próbuje zdeserializować
 * przez podany [elementSerializer]; niepoprawne fragmenty (np. zbyt wcześnie odczytane)
 * są ignorowane — wrócą gdy pełny obiekt zostanie zaakceptowany w kolejnej iteracji.
 */
private class StreamingArrayParser<T>(
    private val json: Json,
    fieldName: String,
    private val elementSerializer: kotlinx.serialization.KSerializer<T>,
) {
    private val marker = "\"$fieldName\":["

    fun extract(accumulated: String): List<T> {
        val markerIndex = accumulated.indexOf(marker)
        if (markerIndex < 0) return emptyList()
        val content = accumulated.substring(markerIndex + marker.length)

        val items = mutableListOf<T>()
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
                            items.add(json.decodeFromString(elementSerializer, objJson))
                        }
                        start = -1
                    }
                }
            }
        }
        return items
    }
}
