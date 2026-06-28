package com.langoclip.app.actions

data class PhraseExamples(
    val phrase: String,
    val examples: List<Example>,
)

/** Where the EN example came from — drives the small provenance chip in the UI. */
enum class ExampleSource {
    NONE,       // dictionaryapi.dev had no example and kaikki DB had no match
    API,        // dictionaryapi.dev returned an example
    KAIKKI,     // pulled from local en_examples.db (Wiktionary-derived)
    GENERATED,  // Haiku produced a fresh sentence
}

data class WordSense(
    val partOfSpeech: PartOfSpeech,
    val meaning: String,
    val example: String,
    val exampleTranslation: String,
    val meaningTranslation: String = "",
    val exampleSource: ExampleSource = ExampleSource.NONE,
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
