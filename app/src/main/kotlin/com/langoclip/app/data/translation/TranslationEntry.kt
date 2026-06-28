package com.langoclip.app.data.translation

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "translations",
    indices = [Index(value = ["lemma"], unique = true)],
)
data class TranslationEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val lemma: String,
    val translation: String,
    val definitionsPl: String,   // JSON array serialized as string
    val examplesPl: String,      // JSON array serialized as string
    val partOfSpeech: String,
    val baseForm: String,
    val source: String,          // "local", "haiku", "sonnet"
    val score: Float,
    val createdAt: Long = System.currentTimeMillis(),
)
