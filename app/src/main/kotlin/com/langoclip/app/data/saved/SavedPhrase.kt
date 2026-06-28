package com.langoclip.app.data.saved

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

/**
 * One English example sentence with its Polish translation. Stored inside [SavedPhraseEntity] as a
 * serialized JSON array (Room has no first-class list column).
 */
@Serializable
data class SavedExample(
    val en: String,
    val pl: String,
)

/**
 * A phrase/word the user chose to save to their notebook. Schema is sync-ready from day one so a
 * future shared backend (and the planned macOS app) can reconcile records without a migration:
 *  - [id] is a stable client-generated UUID, never an autoincrement.
 *  - [updatedAt] is a last-write-wins clock for conflict resolution.
 *  - [deletedAt] is a soft-delete tombstone — rows are hidden, never physically dropped, so a sync
 *    peer can learn about the deletion.
 */
@Entity(
    tableName = "saved_phrases",
    indices = [
        Index(value = ["phraseEn"]),
        Index(value = ["updatedAt"]),
        Index(value = ["deletedAt"]),
    ],
)
data class SavedPhraseEntity(
    @PrimaryKey val id: String,
    val phraseEn: String,
    val phrasePl: String,
    val partOfSpeech: String = "",
    val note: String = "",
    /** JSON-serialized List<[SavedExample]>. */
    val examplesJson: String = "[]",
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
)

/** UI/domain view of a saved phrase — [SavedPhraseEntity] with [examplesJson] parsed. */
data class SavedPhrase(
    val id: String,
    val phraseEn: String,
    val phrasePl: String,
    val partOfSpeech: String,
    val note: String,
    val examples: List<SavedExample>,
    val createdAt: Long,
    val updatedAt: Long,
)
