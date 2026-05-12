package com.floatingclipboard.data.lemma

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "lemma_forms")
data class LemmaForm(
    @PrimaryKey val surface: String,
    val lemma: String,
)
