package com.floatingclipboard.translation

data class TranslationResult(
    val lemma: String,
    val translation: String,
    val definitionsPl: List<String>,
    val examplesPl: List<String>,
    val partOfSpeech: String,
    val baseForm: String,
    val source: TranslationSource,
    val score: Float,
    // English source data from dictionaryapi.dev — not cached in Room, empty for cache hits
    val definitionsEn: List<String> = emptyList(),
    val examplesEn: List<String> = emptyList(),
)

enum class TranslationSource(val tag: String) {
    CACHE("cache"),
    LOCAL("local"),
    HAIKU("haiku"),
    SONNET("sonnet"),
}
