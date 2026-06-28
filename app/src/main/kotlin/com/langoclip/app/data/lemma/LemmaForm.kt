package com.langoclip.app.data.lemma

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

// idx_surface comes from tools/generate_lemmas.py — the @Index has to match so Room's strict
// schema validation doesn't fail when opening the bundled SQLite asset on first use.
@Entity(
    tableName = "lemma_forms",
    indices = [Index(value = ["surface"], name = "idx_surface")],
)
data class LemmaForm(
    @PrimaryKey val surface: String,
    val lemma: String,
)
