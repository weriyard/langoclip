package com.floatingclipboard.llm

import com.floatingclipboard.actions.PartOfSpeech
import com.floatingclipboard.actions.WordSense
import com.floatingclipboard.data.example.ExampleDao
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.http.isSuccess
import kotlinx.serialization.Serializable

/**
 * Free Dictionary API (dictionaryapi.dev) — no key, no cost, Wiktionary-based.
 * Only handles single words; multi-word phrases (phrasal verbs, idioms) return null so
 * the caller falls back to LLM.
 *
 * If [exampleDao] is provided, senses returned without an `example` field are enriched
 * from the local Wiktionary-derived examples database — fills the common gap where
 * dictionaryapi.dev returns a definition but no usage sentence.
 */
class DictionaryClient(private val exampleDao: ExampleDao? = null) {

    suspend fun lookup(word: String): DictionaryResult? {
        val clean = word.trim().lowercase()
        if (clean.isBlank() || clean.contains(' ')) return null
        return runCatching {
            val response = llmHttpClient.get("$BASE_URL/$clean")
            if (!response.status.isSuccess()) return null
            val entries = response.body<List<DictionaryEntry>>()
            if (entries.isEmpty()) return null

            val baseForm = entries.first().word.ifBlank { word }
            val rawSenses = entries
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
            val senses = enrichExamples(baseForm.lowercase(), rawSenses)
            if (senses.isEmpty()) null else DictionaryResult(baseForm, senses)
        }.getOrNull()
    }

    /**
     * For senses that came back without an `example`, pull from the local kaikki-derived
     * examples DB. One DB query per POS bucket: senses sharing a POS share the candidate
     * pool, so distinct senses get distinct sentences (avoids showing the same sentence
     * under two definitions).
     */
    private suspend fun enrichExamples(lemma: String, senses: List<WordSense>): List<WordSense> {
        val dao = exampleDao ?: return senses
        val missingByPos = senses
            .mapIndexedNotNull { idx, s -> if (s.example.isBlank()) idx to s else null }
            .groupBy { it.second.partOfSpeech }
        if (missingByPos.isEmpty()) return senses

        val replacements = HashMap<Int, String>()
        for ((pos, indexed) in missingByPos) {
            val tag = posToKaikkiTag(pos) ?: continue
            val candidates = runCatching {
                dao.byLemmaPos(lemma, tag, limit = indexed.size.coerceAtLeast(1))
            }.getOrDefault(emptyList())
            indexed.forEachIndexed { i, (origIdx, _) ->
                candidates.getOrNull(i)?.let { replacements[origIdx] = it.text }
            }
        }
        if (replacements.isEmpty()) return senses
        return senses.mapIndexed { i, s -> replacements[i]?.let { s.copy(example = it) } ?: s }
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

    // Mirrors ALLOWED_POS in tools/generate_examples.py — keep both in sync.
    private fun posToKaikkiTag(pos: PartOfSpeech): String? = when (pos) {
        PartOfSpeech.NOUN -> "noun"
        PartOfSpeech.VERB -> "verb"
        PartOfSpeech.ADJECTIVE -> "adj"
        PartOfSpeech.ADVERB -> "adv"
        PartOfSpeech.PRONOUN -> "pron"
        PartOfSpeech.PREPOSITION -> "prep"
        PartOfSpeech.IDIOM -> "idiom"
        PartOfSpeech.PHRASAL_VERB -> "phrase"
        PartOfSpeech.OTHER -> null
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
