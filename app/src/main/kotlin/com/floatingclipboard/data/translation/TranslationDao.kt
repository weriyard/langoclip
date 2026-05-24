package com.floatingclipboard.data.translation

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface TranslationDao {
    @Query("SELECT * FROM translations WHERE lemma = :lemma LIMIT 1")
    suspend fun get(lemma: String): TranslationEntry?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: TranslationEntry)

    @Query("DELETE FROM translations")
    suspend fun clearAll()

    @Query("SELECT COUNT(*) FROM translations")
    suspend fun count(): Int
}
