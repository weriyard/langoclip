package com.langoclip.app.data.saved

import android.content.Context
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID

/**
 * Single entry point for the saved-phrases notebook. Owns JSON (de)serialization of the example
 * list and stamps the sync clocks ([SavedPhraseEntity.createdAt] / [updatedAt]), so callers work
 * only with the parsed [SavedPhrase] domain model.
 */
class SavedPhraseRepository private constructor(private val dao: SavedPhraseDao) {

    private val json = Json { ignoreUnknownKeys = true }

    val phrases: Flow<List<SavedPhrase>> = dao.observeAll().map { rows -> rows.map(::toDomain) }
    val count: Flow<Int> = dao.observeCount()

    /**
     * Inserts a new saved phrase. Blank examples are dropped. Returns false (no-op) when [phraseEn]
     * is blank or already saved — dedup is by English text so the same word isn't stored twice.
     */
    suspend fun save(
        phraseEn: String,
        phrasePl: String,
        partOfSpeech: String = "",
        note: String = "",
        examples: List<SavedExample> = emptyList(),
    ): Boolean {
        val en = phraseEn.trim()
        if (en.isBlank()) return false
        if (dao.exists(en)) return false
        val now = System.currentTimeMillis()
        val cleanExamples = examples
            .map { SavedExample(it.en.trim(), it.pl.trim()) }
            .filter { it.en.isNotBlank() }
        dao.upsert(
            SavedPhraseEntity(
                id = UUID.randomUUID().toString(),
                phraseEn = en,
                phrasePl = phrasePl.trim(),
                partOfSpeech = partOfSpeech.trim(),
                note = note.trim(),
                examplesJson = json.encodeToString(cleanExamples),
                createdAt = now,
                updatedAt = now,
            ),
        )
        return true
    }

    suspend fun delete(id: String) = dao.softDelete(id, System.currentTimeMillis())

    suspend fun isSaved(phraseEn: String): Boolean = dao.exists(phraseEn.trim())

    private fun toDomain(e: SavedPhraseEntity) = SavedPhrase(
        id = e.id,
        phraseEn = e.phraseEn,
        phrasePl = e.phrasePl,
        partOfSpeech = e.partOfSpeech,
        note = e.note,
        examples = runCatching { json.decodeFromString<List<SavedExample>>(e.examplesJson) }
            .getOrDefault(emptyList()),
        createdAt = e.createdAt,
        updatedAt = e.updatedAt,
    )

    companion object {
        @Volatile private var instance: SavedPhraseRepository? = null

        fun getInstance(context: Context): SavedPhraseRepository = instance ?: synchronized(this) {
            instance ?: SavedPhraseRepository(
                SavedPhraseDatabase.getInstance(context).savedPhraseDao(),
            ).also { instance = it }
        }
    }
}
