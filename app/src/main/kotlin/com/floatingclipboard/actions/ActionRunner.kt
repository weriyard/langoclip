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
        val showPartial = action == Action.TRANSLATE
        val builder = StringBuilder()
        var deltaCount = 0
        val startedAt = System.currentTimeMillis()

        try {
            client.stream(systemPrompt, userText, schema).collect { delta ->
                deltaCount++
                if (deltaCount == 1) {
                    Log.d(TAG, "TTFT  ${System.currentTimeMillis() - startedAt}ms (first delta arrived)")
                }
                builder.append(delta)
                if (showPartial) {
                    emit(ActionState.Loading(action, partialText = builder.toString()))
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
