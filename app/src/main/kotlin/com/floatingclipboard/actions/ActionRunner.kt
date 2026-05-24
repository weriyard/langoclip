package com.floatingclipboard.actions

import com.floatingclipboard.data.LlmCache
import com.floatingclipboard.data.LogStore
import com.floatingclipboard.data.Provider
import com.floatingclipboard.data.Settings
import com.floatingclipboard.data.SettingsRepository
import com.floatingclipboard.data.example.ExampleDao
import com.floatingclipboard.llm.BREAKDOWN_SCHEMA
import com.floatingclipboard.llm.DictionaryClient
import com.floatingclipboard.llm.EXAMPLES_SCHEMA
import com.floatingclipboard.llm.LlmError
import com.floatingclipboard.llm.LlmTask
import com.floatingclipboard.llm.ModelRouter
import com.floatingclipboard.llm.WORD_SENSES_SCHEMA
import com.floatingclipboard.llm.createLlmClient
import com.floatingclipboard.ui.ActionState
import com.floatingclipboard.ui.SensesState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.decodeFromJsonElement
import com.floatingclipboard.local.LocalModelClient
import com.floatingclipboard.local.NoopLocalModelClient

class ActionRunner(
    private val settingsRepository: SettingsRepository,
    private val prompts: PromptLoader,
    private val cache: LlmCache,
    private val logs: LogStore,
    private val localModel: LocalModelClient = NoopLocalModelClient,
    exampleDao: ExampleDao? = null,
) {

    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }
    private val dictionary = DictionaryClient(exampleDao, logs)

    val settings: Flow<Settings> get() = settingsRepository.settings

    fun runStreaming(action: Action, userText: String): Flow<ActionState> = flow {
        if (userText.isBlank()) {
            emit(ActionState.Error(action, "Tekst jest pusty"))
            return@flow
        }
        val settings = settingsRepository.settings.first()
        val model = ModelRouter.modelFor(action.task, settings)
        val systemPrompt = prompts.render(
            action.promptFile,
            mapOf("targetLanguage" to settings.targetLanguage),
        )
        val key = LlmCache.keyFor(CACHE_VERSION, "${settings.provider.name}:$model", systemPrompt, userText)

        val cached = cache.get(key)
        if (cached != null) {
            val parsed = parseAction(action, cached)
            if (parsed.isSuccess) {
                logs.d(TAG, "CACHE HIT ${settings.provider.name}/$model action=$action")
                emit(ActionState.Success(action, parsed.getOrThrow()))
                return@flow
            }
            // Cache held bad JSON — invalidate and fall through to a live call.
            logs.w(TAG, "CACHE_CORRUPT evicting; reason=${parsed.exceptionOrNull()?.message?.take(120)}")
            cache.invalidate(key)
        }

        logs.d(TAG, "CALL  ${settings.provider.name}/$model action=$action textLen=${userText.length}")
        emit(ActionState.Loading(action))
        val client = createLlmClient(settings, model, logs)
        val schema = if (action == Action.EXPLAIN_SENTENCE) BREAKDOWN_SCHEMA else null
        val showPartialText = action == Action.TRANSLATE
        val streamBreakdown = action == Action.EXPLAIN_SENTENCE
        val builder = StringBuilder()
        val breakdownParser = if (streamBreakdown) {
            StreamingArrayParser(json, "items", BreakdownItemDto.serializer())
        } else null
        var lastBreakdownEmitted = 0
        var lastFullTranslation: String? = null
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
                    val accumulator = builder.toString()
                    val items = breakdownParser.extract(accumulator).map { it.toDomain() }
                    val translation = extractStringField(accumulator, "fullTranslation")
                    val itemsChanged = items.size > lastBreakdownEmitted
                    val translationChanged = translation != null && translation != lastFullTranslation
                    if (itemsChanged || translationChanged) {
                        lastBreakdownEmitted = items.size
                        if (translation != null) lastFullTranslation = translation
                        emit(ActionState.Loading(
                            action,
                            partialBreakdown = items.takeIf { it.isNotEmpty() },
                            partialFullTranslation = lastFullTranslation,
                        ))
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
                    // CACHE WRITE ONLY AFTER SUCCESSFUL PARSE — bad JSON never reaches the cache.
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
        val model = ModelRouter.modelFor(LlmTask.PHRASE_EXAMPLES, settings)
        val systemPrompt = prompts.render(
            "phrase_examples.md",
            mapOf("phrase" to phrase, "targetLanguage" to settings.targetLanguage),
        )
        val userPrompt = if (variant == 0) phrase else {
            val theme = CONTEXT_THEMES[(variant - 1) % CONTEXT_THEMES.size]
            "$phrase\n\n(Set ${variant + 1} — generate COMPLETELY DIFFERENT sentences from any previous sets. $theme. Vary tenses and sentence structures.)"
        }
        val key = LlmCache.keyFor(
            CACHE_VERSION,
            "${settings.provider.name}:$model:v$variant",
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

        logs.d(TAG, "CALL  examples ${settings.provider.name}/$model phrase='${phrase.take(40)}' variant=$variant")
        emit(com.floatingclipboard.ui.ExamplesState.Loading())
        val client = createLlmClient(settings, model, logs)
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

    fun runSensesStreaming(phrase: String, context: String): Flow<SensesState> = flow {
        if (phrase.isBlank()) {
            emit(SensesState.Error("Fraza jest pusta"))
            return@flow
        }
        emit(SensesState.Loading())
        val settings = settingsRepository.settings.first()

        // Free Dictionary API — single words only, no API key, Wiktionary-backed.
        val dictKey = LlmCache.keyFor(CACHE_VERSION, "DICTIONARY", "", phrase.trim().lowercase())
        val dictCached = cache.get(dictKey)
        if (dictCached != null) {
            val parsed = parseSenses(dictCached)
            if (parsed.isSuccess) {
                logs.d(TAG, "CACHE HIT dict senses phrase='${phrase.take(40)}'")
                val (baseForm, senses) = parsed.getOrThrow()
                emit(SensesState.Success(senses, baseForm))
                return@flow
            }
            cache.invalidate(dictKey)
        }
        val dictResult = dictionary.lookup(phrase)
        if (dictResult != null) {
            logs.d(TAG_SENSES, "phrase=\"$phrase\" → dictionaryapi ${dictResult.senses.size} senses (baseForm=${dictResult.baseForm})")
            // Show all senses immediately with empty translations — UI shows English right away.
            emit(SensesState.Success(dictResult.senses, dictResult.baseForm))
            // Translate sequentially so the UI can emit a progressive update after each sense.
            val enriched = mutableListOf<com.floatingclipboard.actions.WordSense>()
            val total = dictResult.senses.size
            val startedAll = System.currentTimeMillis()
            var generated = 0
            for ((idx, s) in dictResult.senses.withIndex()) {
                val updated = completeSense(dictResult.baseForm, s, settings, idx, total)
                if (s.example.isBlank() && updated.example.isNotBlank()) generated++
                enriched += updated
                val current = enriched + dictResult.senses.drop(idx + 1)
                emit(SensesState.Success(current, dictResult.baseForm))
            }
            val elapsedAll = System.currentTimeMillis() - startedAll
            logs.d(TAG_SENSES, "done $total/$total, ${elapsedAll}ms, $generated generated, ${total - generated} translated only")
            val dto = SensesResponseDto(
                baseForm = dictResult.baseForm,
                senses = enriched.map { s ->
                    SenseDto(
                        partOfSpeech = s.partOfSpeech.name,
                        meaning = s.meaning,
                        meaningTranslation = s.meaningTranslation,
                        example = s.example,
                        exampleTranslation = s.exampleTranslation,
                        exampleSource = s.exampleSource.name,
                    )
                },
            )
            cache.put(dictKey, json.encodeToString(dto))
            return@flow
        }

        // Dictionary miss (multi-word phrase or not in Wiktionary) → fall through to LLM.
        val sensesModel = ModelRouter.modelFor(LlmTask.WORD_SENSES, settings)
        val systemPrompt = prompts.render(
            "word_senses.md",
            mapOf(
                "phrase" to phrase,
                "context" to context.ifBlank { phrase },
                "targetLanguage" to settings.targetLanguage,
            ),
        )
        val key = LlmCache.keyFor(CACHE_VERSION, "${settings.provider.name}:$sensesModel", systemPrompt, phrase)
        val cached = cache.get(key)
        if (cached != null) {
            val parsed = parseSenses(cached)
            if (parsed.isSuccess) {
                logs.d(TAG, "CACHE HIT senses phrase='${phrase.take(40)}'")
                val (baseForm, senses) = parsed.getOrThrow()
                emit(SensesState.Success(senses, baseForm))
                return@flow
            }
            cache.invalidate(key)
        }

        logs.d(TAG, "CALL  senses ${settings.provider.name}/$sensesModel phrase='${phrase.take(40)}'")
        emit(SensesState.Loading())
        val client = createLlmClient(settings, sensesModel, logs)
        val parser = StreamingArrayParser(json, "senses", SenseDto.serializer())
        val builder = StringBuilder()
        var lastEmitted = 0
        var lastBaseForm: String? = null

        try {
            client.stream(systemPrompt, phrase, WORD_SENSES_SCHEMA).collect { delta ->
                builder.append(delta)
                val accumulator = builder.toString()
                val partial = parser.extract(accumulator).map { it.toDomain() }
                val baseForm = extractStringField(accumulator, "baseForm")
                val changed = partial.size > lastEmitted || (baseForm != null && baseForm != lastBaseForm)
                if (changed) {
                    lastEmitted = partial.size
                    if (baseForm != null) lastBaseForm = baseForm
                    emit(SensesState.Loading(partial, lastBaseForm))
                }
            }
            val full = builder.toString()
            if (full.isBlank()) { emit(SensesState.Error("Pusta odpowiedź")); return@flow }
            parseSenses(full).fold(
                onSuccess = { (baseForm, senses) ->
                    cache.put(key, full)
                    emit(SensesState.Success(senses, baseForm))
                },
                onFailure = { err ->
                    logs.e(TAG, "PARSE_ERROR senses: ${err.message}")
                    emit(SensesState.Error("Parse error: ${err.message}"))
                },
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: LlmError) {
            logs.e(TAG, "LLM_ERROR senses: ${e.message}")
            emit(SensesState.Error(e.message ?: "Nieznany błąd"))
        } catch (e: Throwable) {
            logs.e(TAG, "UNKNOWN_ERROR senses: ${e.message}")
            emit(SensesState.Error(e.message ?: "Nieznany błąd"))
        }
    }

    private fun parseSenses(rawText: String): Result<Pair<String, List<WordSense>>> = runCatching {
        val cleaned = stripMarkdownWrap(rawText).trim()
        val root = json.parseToJsonElement(cleaned)
        val dto = json.decodeFromJsonElement(SensesResponseDto.serializer(), root)
        if (dto.senses.isEmpty()) throw IllegalStateException("Senses: brak wyników")
        Pair(dto.baseForm, dto.senses.map { it.toDomain() })
    }

    private fun parseAction(action: Action, rawText: String): Result<ActionResult> = runCatching {
        when (action) {
            Action.TRANSLATE -> ActionResult.Text(rawText)
            Action.EXPLAIN_SENTENCE -> {
                val items = parseBreakdownItems(rawText)
                    ?: throw IllegalStateException("Breakdown parsowanie nieudane")
                if (items.isEmpty()) throw IllegalStateException("Breakdown bez itemów")
                // Prefer proper JSON parse for fullTranslation; fall back to the streaming
                // heuristic so partial-but-complete-enough responses still surface PL.
                val fullTranslation = parseFullTranslation(rawText)
                    ?: extractStringField(rawText, "fullTranslation").orEmpty()
                ActionResult.Breakdown(items = items, fullTranslation = fullTranslation)
            }
        }
    }

    /**
     * Proper JSON parse for the `fullTranslation` field — only viable after the whole response is
     * in. Strips markdown fences and handles the "whole-response-as-stringified-JSON" recovery
     * case that some providers ship in tool use. Returns null when the JSON isn't well-formed
     * yet so the caller can fall through to the streaming-time heuristic.
     */
    private fun parseFullTranslation(rawText: String): String? = runCatching {
        val root = parseLiberal(stripMarkdownWrap(rawText).trim())
        (root as? JsonObject)?.get("fullTranslation")?.let { field ->
            (field as? JsonPrimitive)?.takeIf { it.isString }?.content
        }
    }.getOrNull()

    /**
     * Heuristic extractor for `"<fieldName>":"..."` value from a JSON string under construction.
     * Used to surface root-level string fields (e.g. fullTranslation) during streaming, before the
     * whole JSON is closed. Returns null if field not found or value not yet terminated.
     * Handles JSON escapes (\", \\, \n) on the way out.
     */
    private fun extractStringField(accumulated: String, fieldName: String): String? {
        val marker = "\"$fieldName\":\""
        val start = accumulated.indexOf(marker)
        if (start < 0) return null
        val contentStart = start + marker.length
        var i = contentStart
        while (i < accumulated.length) {
            val c = accumulated[i]
            if (c == '\\' && i + 1 < accumulated.length) {
                i += 2
                continue
            }
            if (c == '"') {
                val raw = accumulated.substring(contentStart, i)
                return raw
                    .replace("\\\"", "\"")
                    .replace("\\n", "\n")
                    .replace("\\t", "\t")
                    .replace("\\\\", "\\")
            }
            i++
        }
        return null
    }

    /**
     * Attempt 1: standard decodeFromString — works when items is a real JSON array.
     * Attempt 2 (recovery): sometimes Claude in tool use wraps the array as stringified JSON,
     *   i.e. `{"items": "[\n  {\n    \"original\": ...]"}`. Then items.jsonPrimitive.content
     *   is escaped JSON that has to be unwrapped and parsed as an array. Logs "RECOVERED".
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
     * Liberal parser for the `{<fieldName>: [...]}` object returned by the LLM. Handles:
     *  1. Plain JSON: `{"items":[...]}`.
     *  2. Markdown-wrapped: ` ```json\n{"items":[...]}\n``` `.
     *  3. Items as a stringified array: `{"items":"[...]"}`.
     *  4. The whole response as stringified JSON: `"{\"items\":[...]}"`.
     *  5. Items missing — the first JsonArray found in the root is treated as the list.
     *  6. Items as a single object instead of a list (Claude does this sometimes).
     */
    private fun <T> tryDeserializeArray(
        rawText: String,
        fieldName: String,
        elementSerializer: kotlinx.serialization.KSerializer<T>,
    ): List<T>? {
        val cleaned = stripMarkdownWrap(rawText).trim()

        // Attempt: parse as JsonElement and find the field.
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

    /** Parses raw; if string-as-JSON (wrapped in quotes), unwraps it. */
    private fun parseLiberal(raw: String): kotlinx.serialization.json.JsonElement {
        val parsed = json.parseToJsonElement(raw)
        if (parsed is JsonPrimitive && parsed.isString) {
            // The whole response was stringified JSON
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
                // Single object instead of a list — wrap it in a list
                logs.w(TAG, "RECOVERED $fieldName was single object, wrapping in list")
                JsonArray(listOf(field))
            }
            else -> null
        }
    }

    private fun extractFirstArray(root: kotlinx.serialization.json.JsonElement): JsonArray? {
        if (root !is JsonObject) return null
        // Last-resort: take the first JsonArray found among the root's fields
        for ((_, value) in root) {
            if (value is JsonArray && value.isNotEmpty()) {
                logs.w(TAG, "RECOVERED items via first-array fallback")
                return value
            }
        }
        return null
    }

    /**
     * One Haiku JSON call per sense covering both translation (`meaning` + `example` → PL) and
     * — when [WordSense.example] is blank — generation of a fresh English example sentence.
     *
     * Always uses Anthropic Haiku regardless of the configured provider, since the orchestration
     * chain (dictionaryapi.dev → en_examples.db → here) is structured around Anthropic latency/cost.
     * Returns the original sense unchanged when the call fails or parses garbage — UI degrades to
     * the EN-only state, no exception thrown to the caller.
     */
    private suspend fun completeSense(
        lemma: String,
        sense: com.floatingclipboard.actions.WordSense,
        settings: Settings,
        idx: Int,
        total: Int,
    ): com.floatingclipboard.actions.WordSense {
        if (sense.meaning.isBlank()) return sense
        val pos = sense.partOfSpeech.name.lowercase()
        val hasExample = sense.example.isNotBlank()
        val mode = if (hasExample) "translate(meaning+example)" else "generate(example)+translate(meaning+example)"
        val source = if (hasExample) "API or kaikki" else "no example → generate"
        logs.d(TAG_SENSES, "[${idx + 1}/$total] $lemma ($pos) \"${sense.meaning.take(60)}\" → $source")
        val started = System.currentTimeMillis()

        val userPrompt = if (hasExample) """
For the English word "$lemma":
EN meaning: "${sense.meaning}"
EN example: "${sense.example}"

Translate both to Polish. Respond ONLY with this JSON object, nothing else:
{
  "meaningPl": "Polish translation of the meaning",
  "example": "${sense.example}",
  "examplePl": "Polish translation of the example sentence"
}
""".trimIndent()
        else """
For the English word "$lemma":
EN meaning: "${sense.meaning}"

There is no example sentence. Produce ONE short English sentence (max 15 words) using "$lemma" in this exact sense, then translate everything to Polish.

Respond ONLY with this JSON object, nothing else:
{
  "meaningPl": "Polish translation of the meaning",
  "example": "the new English example sentence using $lemma",
  "examplePl": "Polish translation of the example sentence"
}
""".trimIndent()

        val raw = runCatching {
            // Always Anthropic Haiku — provider override keeps this independent of user settings.
            val client = createLlmClient(settings.copy(provider = Provider.ANTHROPIC), HAIKU_MODEL)
            val builder = StringBuilder()
            client.stream(
                "You are a translation API. Respond with valid JSON only — no prose, no markdown fences.",
                userPrompt,
                null,
            ).collect { delta -> builder.append(delta) }
            builder.toString().trim()
        }.onFailure { logs.w(TAG_SENSES, "[${idx + 1}/$total] Haiku FAILED: ${it.message}") }
            .getOrNull()

        if (raw.isNullOrBlank()) {
            logs.w(TAG_SENSES, "[${idx + 1}/$total] empty response")
            return sense
        }
        val cleaned = stripMarkdownWrap(raw).trim()
        val dto = runCatching { json.decodeFromString<SenseCompletionDto>(cleaned) }
            .onFailure { logs.w(TAG_SENSES, "[${idx + 1}/$total] parse FAILED: ${it.message?.take(80)} raw='${cleaned.take(120)}'") }
            .getOrNull() ?: return sense

        val elapsed = System.currentTimeMillis() - started
        logs.d(TAG_SENSES, "[${idx + 1}/$total] Haiku $mode ${elapsed}ms")
        val finalExample = dto.example.ifBlank { sense.example }
        val finalSource = when {
            // Keep upstream attribution (API or KAIKKI) — completeSense only translates in that case.
            sense.exampleSource != ExampleSource.NONE -> sense.exampleSource
            // Was blank, Haiku produced one — mark as generated.
            finalExample.isNotBlank() -> ExampleSource.GENERATED
            else -> ExampleSource.NONE
        }
        return sense.copy(
            meaningTranslation = dto.meaningPl.ifBlank { sense.meaningTranslation },
            example = finalExample,
            exampleTranslation = dto.examplePl.ifBlank { sense.exampleTranslation },
            exampleSource = finalSource,
        )
    }

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
        // v10: explain_sentence prompt tightened (no non-adjacent correlatives, subject pronoun
        // grouped with tense). Old v9 EXPLAIN cache entries would still show over-grouped items,
        // so we bump even though the prompt hash already keys part of the cache.
        private const val CACHE_VERSION = "v10"
        private const val TAG = "LLM"
        private const val TAG_SENSES = "Senses"
        private const val HAIKU_MODEL = "claude-haiku-4-5-20251001"

        private val CONTEXT_THEMES = listOf(
            "Focus on formal/academic contexts (essays, lectures, official documents)",
            "Focus on casual everyday conversation (friends, texting, social media)",
            "Focus on business and professional settings (emails, meetings, negotiations)",
            "Focus on literature and creative writing (novels, poetry, storytelling)",
            "Focus on news media and journalism (headlines, reports, commentary)",
            "Focus on travel, culture, and lifestyle (blogs, travel writing, food)",
            "Focus on historical or classical language (formal, old-fashioned usage)",
            "Focus on technical or scientific language (research, documentation, manuals)",
        )
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

@Serializable
private data class SensesResponseDto(
    val baseForm: String = "",
    val senses: List<SenseDto> = emptyList(),
)

@Serializable
private data class SenseDto(
    val partOfSpeech: String = "OTHER",
    val meaning: String = "",
    val example: String = "",
    val exampleTranslation: String = "",
    val meaningTranslation: String = "",
    val exampleSource: String = "",
)

private fun SenseDto.toDomain() = WordSense(
    partOfSpeech = PartOfSpeech.parse(partOfSpeech),
    meaning = meaning,
    example = example,
    exampleTranslation = exampleTranslation,
    meaningTranslation = meaningTranslation,
    exampleSource = runCatching {
        com.floatingclipboard.actions.ExampleSource.valueOf(exampleSource)
    }.getOrDefault(com.floatingclipboard.actions.ExampleSource.NONE),
)

@Serializable
private data class SenseCompletionDto(
    val meaningPl: String = "",
    val example: String = "",
    val examplePl: String = "",
)

/**
 * Progressive parser for structured output that grows token-by-token. Looks for the marker
 * `"<fieldName>":[`, then counts braces with correct string and escape handling to extract
 * COMPLETE sub-objects from the array. Each complete object is tried against the provided
 * [elementSerializer]; invalid fragments (e.g. read too early) are ignored — they will be
 * picked up when a full object is accepted in the next iteration.
 */
private class StreamingArrayParser<T>(
    private val json: Json,
    fieldName: String,
    private val elementSerializer: kotlinx.serialization.KSerializer<T>,
) {
    private val keyMarker = "\"$fieldName\""

    fun extract(accumulated: String): List<T> {
        // Find the JSON key, then scan for '[' tolerating optional whitespace after ':'
        val keyIdx = accumulated.indexOf(keyMarker)
        if (keyIdx < 0) return emptyList()
        var i = keyIdx + keyMarker.length
        while (i < accumulated.length && accumulated[i] != ':') i++
        if (i >= accumulated.length) return emptyList()
        i++ // skip ':'
        while (i < accumulated.length && (accumulated[i] == ' ' || accumulated[i] == '\n' || accumulated[i] == '\r')) i++
        if (i >= accumulated.length || accumulated[i] != '[') return emptyList()
        val content = accumulated.substring(i + 1)

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
