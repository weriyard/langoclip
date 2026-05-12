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
)

enum class TranslationSource(val tag: String) {
    CACHE("cache"),
    LOCAL("local"),
    HAIKU("haiku"),
    SONNET("sonnet"),
}
