package com.langoclip.app.data.saved

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface SavedPhraseDao {

    /** Live list of non-deleted phrases, newest edit first. */
    @Query("SELECT * FROM saved_phrases WHERE deletedAt IS NULL ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<SavedPhraseEntity>>

    /** Live count of non-deleted phrases — drives the badge in the top bar. */
    @Query("SELECT COUNT(*) FROM saved_phrases WHERE deletedAt IS NULL")
    fun observeCount(): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entry: SavedPhraseEntity)

    /** Soft delete — keep the row as a tombstone so a future sync peer can see the deletion. */
    @Query("UPDATE saved_phrases SET deletedAt = :ts, updatedAt = :ts WHERE id = :id")
    suspend fun softDelete(id: String, ts: Long)

    /** True if a live (non-deleted) phrase with this English text already exists. */
    @Query("SELECT EXISTS(SELECT 1 FROM saved_phrases WHERE phraseEn = :phraseEn AND deletedAt IS NULL)")
    suspend fun exists(phraseEn: String): Boolean
}
