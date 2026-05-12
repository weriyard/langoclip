package com.floatingclipboard.llm

import com.floatingclipboard.actions.PartOfSpeech
import com.floatingclipboard.actions.WordSense
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.http.isSuccess
import kotlinx.serialization.Serializable

/**
 * Free Dictionary API (dictionaryapi.dev) — no key, no cost, Wiktionary-based.
 * Only handles single words; multi-word phrases (phrasal verbs, idioms) return null so
 * the caller falls back to LLM.
 */
class DictionaryClient {

    suspend fun lookup(word: String): DictionaryResult? {
        val clean = word.trim().lowercase()
        if (clean.isBlank() || clean.contains(' ')) return null
        return runCatching {
            val response = llmHttpClient.get("$BASE_URL/$clean")
            if (!response.status.isSuccess()) return null
            val entries = response.body<List<DictionaryEntry>>()
            if (entries.isEmpty()) return null

            val baseForm = entries.first().word.ifBlank { word }
            val senses = entries
                .flatMap { it.meanings }
                .flatMap { meaning ->
                    meaning.definitions.take(MAX_DEFS_PER_POS).map { def ->
                        WordSense(
                            partOfSpeech = mapPos(meaning.partOfSpeech),
                            meaning = def.definition,
                            example = def.example.orEmpty(),
                            exampleTranslation = "",
                        )
                    }
                }
                .distinctBy { it.partOfSpeech to it.meaning }
                .take(MAX_TOTAL_SENSES)

            if (senses.isEmpty()) null else DictionaryResult(baseForm, senses)
        }.getOrNull()
    }

    private fun mapPos(pos: String): PartOfSpeech = when (pos.lowercase().trim()) {
        "noun" -> PartOfSpeech.NOUN
        "verb" -> PartOfSpeech.VERB
        "adjective", "adjective satellite" -> PartOfSpeech.ADJECTIVE
        "adverb" -> PartOfSpeech.ADVERB
        "pronoun" -> PartOfSpeech.PRONOUN
        "preposition" -> PartOfSpeech.PREPOSITION
        "idiom" -> PartOfSpeech.IDIOM
        "phrasal verb" -> PartOfSpeech.PHRASAL_VERB
        else -> PartOfSpeech.OTHER
    }

    companion object {
        private const val BASE_URL = "https://api.dictionaryapi.dev/api/v2/entries/en"
        private const val MAX_DEFS_PER_POS = 2
        private const val MAX_TOTAL_SENSES = 8
    }
}

data class DictionaryResult(
    val baseForm: String,
    val senses: List<WordSense>,
)

@Serializable
internal data class DictionaryEntry(
    val word: String = "",
    val meanings: List<DictionaryMeaning> = emptyList(),
)

@Serializable
internal data class DictionaryMeaning(
    val partOfSpeech: String = "",
    val definitions: List<DictionaryDefinition> = emptyList(),
)

@Serializable
internal data class DictionaryDefinition(
    val definition: String = "",
    val example: String? = null,
)
