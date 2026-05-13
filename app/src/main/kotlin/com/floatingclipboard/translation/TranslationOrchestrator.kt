package com.floatingclipboard.translation

import android.util.Log
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
 * Main orchestration chain (CLAUDE-6.md):
 * lemmatize → cache → dictionary → local model → score → decide → api fallback → cache write
 */
class TranslationOrchestrator(
    private val lemmatizer: Lemmatizer,
    private val translationDao: TranslationDao,
    private val settingsRepo: SettingsRepository,
    private val localModel: LocalModelClient = NoopLocalModelClient,
    private val embeddingScorer: EmbeddingScorer? = null,
) {
    private val dictionary = DictionaryClient()
    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }

    suspend fun translate(token: String, sentence: String): TranslationResult {
        val lemma = lemmatizer.lemmatize(token)
        Log.d(TAG, "translate: token='$token' lemma='$lemma' localModel.isAvailable=${localModel.isAvailable}")

        // 1. Cache lookup
        translationDao.get(lemma)?.let {
            Log.d(TAG, "translate: HIT cache → returning cached result")
            return it.toDomain()
        }

        // 2. Dictionary (EN definitions + examples)
        val dictSenses = dictionary.lookup(lemma)
        Log.d(TAG, "translate: dictionary senses=${dictSenses?.senses?.size ?: 0}")
        val definitions = dictSenses?.senses?.map { it.meaning } ?: emptyList()
        val examples = dictSenses?.senses?.mapNotNull { it.example.ifBlank { null } } ?: emptyList()

        // 3. Build translation prompt
        val prompt = buildPrompt(token, definitions, examples, sentence)

        // 4. Local model (if available)
        if (localModel.isAvailable) {
            Log.d(TAG, "translate: trying local model…")
            val localRaw = runCatching { localModel.translate(prompt) }
                .onFailure { Log.e(TAG, "translate: local model threw", it) }
                .getOrNull()
            Log.d(TAG, "translate: local raw=${localRaw?.take(120)}")
            if (localRaw != null) {
                val localDto = runCatching { json.decodeFromString<TranslationDto>(localRaw) }
                    .onFailure { Log.w(TAG, "translate: failed to parse local JSON: ${localRaw.take(200)}") }
                    .getOrNull()
                if (localDto != null) {
                    val score = TranslationScorer.score(token, localDto.translation, embeddingScorer)
                    val decision = TranslationScorer.decide(score)
                    Log.d(TAG, "translate: local translation='${localDto.translation}' score=$score decision=$decision")

                    val settings = settingsRepo.settings.first()
                    val result = when (decision) {
                        RoutingDecision.USE_LOCAL -> {
                            Log.d(TAG, "translate: → LOCAL")
                            localDto.toResult(lemma, score, TranslationSource.LOCAL)
                        }
                        RoutingDecision.HAIKU_JUDGE -> {
                            Log.d(TAG, "translate: → HAIKU_JUDGE")
                            val judgeScore = judgeWithHaiku(token, sentence, localDto.translation, settings)
                            Log.d(TAG, "translate: judgeScore=$judgeScore")
                            if (judgeScore >= TranslationScorer.HAIKU_JUDGE_ACCEPT)
                                localDto.toResult(lemma, score, TranslationSource.LOCAL)
                            else
                                callApi(prompt, lemma, score, settings, useHaiku = true)
                        }
                        RoutingDecision.HAIKU_DIRECT -> {
                            Log.d(TAG, "translate: → HAIKU_DIRECT")
                            callApi(prompt, lemma, score, settings, useHaiku = true)
                        }
                        RoutingDecision.SONNET -> {
                            Log.d(TAG, "translate: → SONNET")
                            callApi(prompt, lemma, score, settings, useHaiku = false)
                        }
                    }
                    cacheResult(result)
                    return result
                }
            }
        }

        // 5. No local model — go directly to API based on sentence complexity
        Log.d(TAG, "translate: no local model result → API fallback")
        val settings = settingsRepo.settings.first()
        val useHaiku = !isComplexSentence(sentence)
        Log.d(TAG, "translate: calling API useHaiku=$useHaiku")
        val result = callApi(prompt, lemma, score = 0f, settings, useHaiku)
        cacheResult(result)
        return result
    }

    private fun buildPrompt(
        token: String,
        definitions: List<String>,
        examples: List<String>,
        sentence: String,
    ): String {
        val defsText = definitions.take(2).mapIndexed { i, d -> "${i + 1}. $d" }.joinToString("\n")
        val exampleText = examples.firstOrNull()?.let { "Example: $it\n" } ?: ""
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

    private suspend fun judgeWithHaiku(
        token: String,
        sentence: String,
        translation: String,
        settings: Settings,
    ): Float {
        val judgePrompt = """
Rate the quality of this English→Polish translation from 0.0 to 1.0.
Respond with ONLY a decimal number, nothing else.

English word: "$token"
Used in sentence: "$sentence"
Polish translation: "$translation"
        """.trimIndent()
        val response = runCatching {
            callAnthropicRaw(judgePrompt, settings.anthropicApiKey, HAIKU_MODEL)
        }.getOrNull() ?: return 0f
        return response.trim().toFloatOrNull() ?: 0f
    }

    private suspend fun callApi(
        prompt: String,
        lemma: String,
        score: Float,
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
        return dto.toResult(lemma, score, source)
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

// ── DTOs ─────────────────────────────────────────────────────────────────────

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

// ── Anthropic API request/response shapes ────────────────────────────────────

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
