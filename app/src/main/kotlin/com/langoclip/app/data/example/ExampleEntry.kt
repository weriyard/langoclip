package com.langoclip.app.data.example

import androidx.room.Entity
import androidx.room.Index

// idx_examples_lookup comes from tools/generate_examples.py — the @Index has to match so Room's
// strict schema validation doesn't fail when opening the bundled SQLite asset on first use.
@Entity(
    tableName = "examples",
    primaryKeys = ["lemma", "pos", "text"],
    indices = [Index(value = ["lemma", "pos"], name = "idx_examples_lookup")],
)
data class ExampleEntry(
    val lemma: String,
    val pos: String,
    val text: String,
)
