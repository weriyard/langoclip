package com.floatingclipboard.llm

enum class LlmTask(val tier: ModelTier) {
    TRANSLATE(ModelTier.FAST),
    WORD_SENSES(ModelTier.FAST),
    CHAT(ModelTier.FAST),
    EXPLAIN_SENTENCE(ModelTier.CAPABLE),
    PHRASE_EXAMPLES(ModelTier.CAPABLE),
}
