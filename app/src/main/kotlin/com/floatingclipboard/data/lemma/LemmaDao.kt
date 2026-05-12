package com.floatingclipboard.data.lemma

import androidx.room.Dao
import androidx.room.Query

@Dao
interface LemmaDao {
    @Query("SELECT * FROM lemma_forms WHERE surface = :surface LIMIT 1")
    suspend fun lookup(surface: String): LemmaForm?
}
