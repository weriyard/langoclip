package com.floatingclipboard.data.example

import androidx.room.Entity

@Entity(tableName = "examples", primaryKeys = ["lemma", "pos", "text"])
data class ExampleEntry(
    val lemma: String,
    val pos: String,
    val text: String,
)
