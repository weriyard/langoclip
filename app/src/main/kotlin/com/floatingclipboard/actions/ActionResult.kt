package com.floatingclipboard.actions

sealed interface ActionResult {
    data class Text(val text: String) : ActionResult
    data class Breakdown(
        val items: List<BreakdownItem>,
        val fullTranslation: String = "",
    ) : ActionResult
}

enum class PartOfSpeech {
    NOUN, VERB, ADJECTIVE, ADVERB, PRONOUN, PREPOSITION, IDIOM, PHRASAL_VERB, OTHER;

    companion object {
        fun parse(value: String?): PartOfSpeech =
            entries.firstOrNull { it.name == value?.trim()?.uppercase() } ?: OTHER
    }
}

data class BreakdownItem(
    val original: String,
    val translation: String,
    val partOfSpeech: PartOfSpeech,
    val explanation: String,
)
