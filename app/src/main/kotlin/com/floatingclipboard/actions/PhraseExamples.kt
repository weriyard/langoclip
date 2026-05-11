package com.floatingclipboard.actions

data class PhraseExamples(
    val phrase: String,
    val examples: List<Example>,
)

data class Example(
    val english: String,
    val translation: String,
    val usageNote: String,
    /**
     * Fragment z `english` zawierający frazę użytą w tym konkretnym kontekście — może być w
     * innej formie gramatycznej niż original phrase (np. "gave up" gdy phrase to "give up").
     * UI używa do podświetlenia frazy w zdaniu. Pusty string gdy model nie zwrócił.
     */
    val highlightedSpan: String,
)
