package com.floatingclipboard.actions

enum class Action(val displayName: String, val promptFile: String) {
    TRANSLATE("Przetłumacz", "translate.md"),
    EXPLAIN_SENTENCE("Wytłumacz konstrukcję", "explain_sentence.md"),
}
