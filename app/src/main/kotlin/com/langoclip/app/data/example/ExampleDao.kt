package com.langoclip.app.data.example

import androidx.room.Dao
import androidx.room.Query

@Dao
interface ExampleDao {
    @Query("SELECT * FROM examples WHERE lemma = :lemma AND pos = :pos LIMIT :limit")
    suspend fun byLemmaPos(lemma: String, pos: String, limit: Int = 3): List<ExampleEntry>

    @Query("SELECT COUNT(*) FROM examples")
    suspend fun count(): Int
}
