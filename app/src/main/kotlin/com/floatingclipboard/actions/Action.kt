package com.floatingclipboard.actions

import com.floatingclipboard.llm.LlmTask

enum class Action(val displayName: String, val promptFile: String, val task: LlmTask) {
    TRANSLATE("Przetłumacz", "translate.md", LlmTask.TRANSLATE),
    EXPLAIN_SENTENCE("Wytłumacz konstrukcję", "explain_sentence.md", LlmTask.EXPLAIN_SENTENCE),
}
