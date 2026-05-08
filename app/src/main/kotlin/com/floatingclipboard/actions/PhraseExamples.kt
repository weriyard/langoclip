package com.floatingclipboard.actions

data class PhraseExamples(
    val phrase: String,
    val examples: List<Example>,
)

data class Example(
    val english: String,
    val translation: String,
    val usageNote: String,
)
