package com.floatingclipboard.actions

import android.util.Log
import com.floatingclipboard.data.LlmCache
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

class ActionRunner(
    private val settingsRepository: SettingsRepository,
    private val prompts: PromptLoader,
    private val cache: LlmCache,
) {

    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }

    /** Wystawia aktualne ustawienia dla VM (np. żeby pokazać aktywny provider w UI). */
    val settings: Flow<Settings> get() = settingsRepository.settings

    /**
     * Streaming wariant głównych akcji (TRANSLATE, EXPLAIN_SENTENCE). Emituje [ActionState] od
     * Loading (z partialText dla TRANSLATE) do Success/Error. Cache hit → emituje od razu Success.
     * Cache miss → odpala stream, akumuluje, parsuje całość, kachuje, emituje Success.
     */
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

        cache.get(key)?.let { rawCached ->
            Log.d(TAG, "CACHE HIT ${settings.provider.name}/${settings.activeModel} action=$action")
            parseAction(action, rawCached).fold(
                onSuccess = { emit(ActionState.Success(action, it)) },
                onFailure = { emit(ActionState.Error(action, "Cache parse error: ${it.message}")) },
            )
            return@flow
        }

        Log.d(TAG, "CALL  ${settings.provider.name}/${settings.activeModel} action=$action textLen=${userText.length}")
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
                    Log.d(TAG, "TTFT  ${System.currentTimeMillis() - startedAt}ms (first delta arrived)")
                }
                builder.append(delta)
                if (showPartialText) {
                    emit(ActionState.Loading(action, partialText = builder.toString()))
                } else if (breakdownParser != null) {
                    val items = breakdownParser.extractItems(builder.toString())
                    if (items.size > lastBreakdownEmitted) {
                        lastBreakdownEmitted = items.size
                        Log.d(TAG, "PART  ${System.currentTimeMillis() - startedAt}ms — ${items.size} items parsed so far")
                        emit(ActionState.Loading(action, partialBreakdown = items))
                    }
                }
            }
            Log.d(TAG, "DONE  ${System.currentTimeMillis() - startedAt}ms total, $deltaCount deltas, ${builder.length} chars")
            val full = builder.toString()
            if (full.isBlank()) {
                emit(ActionState.Error(action, "Pusta odpowiedź"))
                return@flow
            }
            cache.put(key, full)
            parseAction(action, full).fold(
                onSuccess = { emit(ActionState.Success(action, it)) },
                onFailure = { emit(ActionState.Error(action, "Parse error: ${it.message}")) },
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: LlmError) {
            emit(ActionState.Error(action, e.message ?: "Nieznany błąd"))
        } catch (e: Throwable) {
            emit(ActionState.Error(action, e.message ?: "Nieznany błąd"))
        }
    }

    /** Examples — non-streaming, structured JSON. Stream nie daje benefitu (nie da się parsować częściowo). */
    suspend fun runExamples(phrase: String): Result<PhraseExamples> {
        if (phrase.isBlank()) {
            return Result.failure(IllegalArgumentException("Fraza jest pusta"))
        }
        val settings = settingsRepository.settings.first()
        val systemPrompt = prompts.render(
            "phrase_examples.md",
            mapOf("phrase" to phrase, "targetLanguage" to settings.targetLanguage),
        )
        val key = LlmCache.keyFor(CACHE_VERSION, "${settings.provider.name}:${settings.activeModel}", systemPrompt, phrase)

        cache.get(key)?.let { rawCached ->
            return parseExamples(phrase, rawCached)
        }

        val client = createLlmClient(settings)
        return client.complete(systemPrompt, phrase, EXAMPLES_SCHEMA)
            .fold(
                onSuccess = { rawText ->
                    cache.put(key, rawText)
                    parseExamples(phrase, rawText)
                },
                onFailure = { Result.failure(it) },
            )
    }

    private fun parseAction(action: Action, rawText: String): Result<ActionResult> = runCatching {
        when (action) {
            Action.TRANSLATE -> ActionResult.Text(rawText)
            Action.EXPLAIN_SENTENCE -> {
                val dto = json.decodeFromString<BreakdownDto>(rawText)
                ActionResult.Breakdown(items = dto.items.map { it.toDomain() })
            }
        }
    }

    private fun parseExamples(phrase: String, rawText: String): Result<PhraseExamples> = runCatching {
        val dto = json.decodeFromString<ExamplesDto>(rawText)
        PhraseExamples(phrase = phrase, examples = dto.examples.map { it.toDomain() })
    }

    companion object {
        // Bump przy zmianach formatu odpowiedzi / DTO żeby invalidate cały cache.
        private const val CACHE_VERSION = "v1"
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

/**
 * Parsuje akumulator stringa zawierający częściowy JSON `{"items":[{...},{...},...]}` i wyciąga
 * tylko KOMPLETNE sub-obiekty z tablicy items. Pozwala renderować pojedyncze BreakdownItem'y
 * w UI gdy tylko model skończy je generować, zamiast czekać na całe zdanie.
 *
 * Algorytm: licznik klamerek z poprawną obsługą stringów (cudzysłowy + escape'y) — `{` i `}`
 * wewnątrz stringów nie liczymy. Każdy zamknięty obiekt na głębokości 0 → próba deserializacji.
 * Niepowodzenie (np. Gemini wstawi pole którego nie znamy) → ignorujemy ten item, zostaje w
 * akumulatorze do końcowego pełnego parse'u.
 */
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
