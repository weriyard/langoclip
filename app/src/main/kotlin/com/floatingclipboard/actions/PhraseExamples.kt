package com.floatingclipboard.actions

data class PhraseExamples(
    val phrase: String,
    val examples: List<Example>,
)

data class WordSense(
    val partOfSpeech: PartOfSpeech,
    val meaning: String,
    val example: String,
    val exampleTranslation: String,
)

data class Example(
    val english: String,
    val translation: String,
    val usageNote: String,
    /**
     * Fragment of `english` containing the phrase used in this specific context — may be in a
     * different grammatical form than the original phrase (e.g. "gave up" when phrase is "give up").
     * The UI uses it to highlight the phrase in the sentence. Empty string when the model returns none.
     */
    val highlightedSpan: String,
)
