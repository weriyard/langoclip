package com.langoclip.app.actions

import com.langoclip.app.llm.LlmTask

enum class Action(val displayName: String, val promptFile: String, val task: LlmTask) {
    TRANSLATE("Translate", "translate.md", LlmTask.TRANSLATE),
    EXPLAIN_SENTENCE("Explain construction", "explain_sentence.md", LlmTask.EXPLAIN_SENTENCE),
}
