package com.floatingclipboard.translation

import com.floatingclipboard.data.LogStore
import com.floatingclipboard.data.Settings
import com.floatingclipboard.data.SettingsRepository
import com.floatingclipboard.data.translation.TranslationDao
import com.floatingclipboard.data.translation.TranslationEntry
import com.floatingclipboard.llm.DictionaryClient
import com.floatingclipboard.local.LocalModelClient
import com.floatingclipboard.local.NoopLocalModelClient
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.android.Android
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Orchestration chain for single-word translation:
 * lemmatize → cache → dictionary → API (Haiku/Sonnet) → cache write
 *
 * [localModel] is a forward-looking hook — currently [NoopLocalModelClient] and unused.
 * When on-device inference comes back, plug an implementation in via the ctor and reinsert
 * the local-first branch between dictionary lookup and API call.
 */
class TranslationOrchestrator(
    private val lemmatizer: Lemmatizer,
    private val translationDao: TranslationDao,
    private val settingsRepo: SettingsRepository,
    private val localModel: LocalModelClient = NoopLocalModelClient,
    private val logStore: LogStore? = null,
) {
    private val dictionary = DictionaryClient()
    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }

    suspend fun translate(token: String, sentence: String): TranslationResult {
        val lemma = lemmatizer.lemmatize(token)
        logStore?.d(TAG, "translate: token='$token' lemma='$lemma'")

        translationDao.get(lemma)?.let {
            logStore?.d(TAG, "translate: HIT cache → returning cached result")
            return it.toDomain()
        }

        val dictSenses = dictionary.lookup(lemma)
        logStore?.d(TAG, "translate: dictionary senses=${dictSenses?.senses?.size ?: 0}")
        val sensesForPrompt = dictSenses?.senses.orEmpty()
        val definitionsEn = sensesForPrompt.map { it.meaning }
        val examplesEn = sensesForPrompt.mapNotNull { it.example.ifBlank { null } }

        val prompt = buildPrompt(token, sensesForPrompt, sentence)
        val settings = settingsRepo.settings.first()
        val useHaiku = !isComplexSentence(sentence)
        logStore?.d(TAG, "translate: calling API useHaiku=$useHaiku")
        val result = callApi(prompt, lemma, settings, useHaiku)
            .copy(definitionsEn = definitionsEn, examplesEn = examplesEn)
        cacheResult(result)
        return result
    }

    private fun buildPrompt(
        token: String,
        senses: List<com.floatingclipboard.actions.WordSense>,
        sentence: String,
    ): String {
        val defsText = senses.take(2)
            .joinToString("\n") { "${it.partOfSpeech.name.lowercase()}: ${it.meaning}" }
        val exampleText = senses.mapNotNull { it.example.ifBlank { null } }
            .take(2)
            .joinToString("\n") { "Example: $it" }
            .let { if (it.isBlank()) "" else "$it\n" }
        return """
Translate to Polish. Respond only in JSON.

Word: "$token"
English definitions:
$defsText
${exampleText}Used in sentence: "$sentence"

JSON response:
{
  "translation": "główne tłumaczenie",
  "definitions": ["def 1 po polsku", "def 2 po polsku"],
  "examples": ["przykład po polsku"],
  "partOfSpeech": "rzeczownik/czasownik/etc",
  "baseForm": "base form or infinitive (e.g. draw on for drew on, run for running)"
}
        """.trimIndent()
    }

    private suspend fun callApi(
        prompt: String,
        lemma: String,
        settings: Settings,
        useHaiku: Boolean,
    ): TranslationResult {
        val model = if (useHaiku) HAIKU_MODEL else SONNET_MODEL
        val source = if (useHaiku) TranslationSource.HAIKU else TranslationSource.SONNET
        val raw = runCatching {
            callAnthropicRaw(prompt, settings.anthropicApiKey, model)
        }.getOrNull() ?: return fallbackResult(lemma)

        val dto = runCatching { json.decodeFromString<TranslationDto>(raw) }.getOrNull()
            ?: return fallbackResult(lemma)
        return dto.toResult(lemma, score = 0f, source = source)
    }

    private suspend fun callAnthropicRaw(prompt: String, apiKey: String, model: String): String {
        val client = HttpClient(Android) {
            install(ContentNegotiation) { json(json) }
        }
        return client.use { http ->
            val resp = http.post("https://api.anthropic.com/v1/messages") {
                header("x-api-key", apiKey)
                header("anthropic-version", "2023-06-01")
                contentType(ContentType.Application.Json)
                setBody(AnthropicRequest(model = model, messages = listOf(Message("user", prompt))))
            }
            if (!resp.status.isSuccess()) error("Anthropic error: ${resp.status}")
            resp.body<AnthropicResponse>().content.firstOrNull()?.text ?: error("Empty response")
        }
    }

    private fun isComplexSentence(sentence: String): Boolean {
        val clauseCount = sentence.count { it == ',' || it == ';' }
        val hasNegation = Regex("\\b(not|no|never|neither)\\b", RegexOption.IGNORE_CASE).containsMatchIn(sentence)
        val wordCount = sentence.split(Regex("\\s+")).size
        return clauseCount > 3 || (hasNegation && wordCount > 15)
    }

    private suspend fun cacheResult(result: TranslationResult) {
        translationDao.insert(result.toEntry())
        // Multi-word phrase: also cache under baseForm so future inflections hit the cache.
        val baseForm = result.baseForm.trim().lowercase()
        if (baseForm.isNotEmpty() && baseForm != result.lemma) {
            translationDao.insert(result.copy(lemma = baseForm).toEntry())
            logStore?.d(TAG, "cacheResult: also cached under baseForm='$baseForm' (was lemma='${result.lemma}')")
        }
    }

    private fun fallbackResult(lemma: String) = TranslationResult(
        lemma = lemma,
        translation = "—",
        definitionsPl = emptyList(),
        examplesPl = emptyList(),
        partOfSpeech = "",
        baseForm = lemma,
        source = TranslationSource.SONNET,
        score = 0f,
    )

    companion object {
        private const val TAG = "TranslationOrchestrator"
        private const val HAIKU_MODEL = "claude-haiku-4-5-20251001"
        private const val SONNET_MODEL = "claude-sonnet-4-6"
    }
}

@Serializable
private data class TranslationDto(
    val translation: String = "",
    val definitions: List<String> = emptyList(),
    val examples: List<String> = emptyList(),
    val partOfSpeech: String = "",
    val baseForm: String = "",
)

private fun TranslationDto.toResult(lemma: String, score: Float, source: TranslationSource) =
    TranslationResult(
        lemma = lemma,
        translation = translation,
        definitionsPl = definitions,
        examplesPl = examples,
        partOfSpeech = partOfSpeech,
        baseForm = baseForm.ifBlank { lemma },
        source = source,
        score = score,
    )

private val resultJson = Json { ignoreUnknownKeys = true }

private fun TranslationResult.toEntry() = TranslationEntry(
    lemma = lemma,
    translation = translation,
    definitionsPl = resultJson.encodeToString(definitionsPl),
    examplesPl = resultJson.encodeToString(examplesPl),
    partOfSpeech = partOfSpeech,
    baseForm = baseForm,
    source = source.tag,
    score = score,
)

private fun TranslationEntry.toDomain() = TranslationResult(
    lemma = lemma,
    translation = translation,
    definitionsPl = runCatching { resultJson.decodeFromString<List<String>>(definitionsPl) }.getOrDefault(emptyList()),
    examplesPl = runCatching { resultJson.decodeFromString<List<String>>(examplesPl) }.getOrDefault(emptyList()),
    partOfSpeech = partOfSpeech,
    baseForm = baseForm,
    source = TranslationSource.CACHE,
    score = score,
)

@Serializable
private data class AnthropicRequest(
    val model: String,
    val messages: List<Message>,
    val max_tokens: Int = 512,
)

@Serializable
private data class Message(val role: String, val content: String)

@Serializable
private data class AnthropicResponse(val content: List<ContentBlock> = emptyList())

@Serializable
private data class ContentBlock(val type: String = "", val text: String = "")
