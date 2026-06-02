package com.floatingclipboard.llm

enum class LlmTask(val tier: ModelTier) {
    TRANSLATE(ModelTier.FAST),
    WORD_SENSES(ModelTier.FAST),
    // Tutor chat is open-ended and nuance-heavy (register, connotation, grammar explained in
    // Polish) — CAPABLE (Flash) handles this better than FAST (Lite). Volume is low + user-paced,
    // so the cost/latency hit is negligible here, unlike the constant FAST translate/senses calls.
    CHAT(ModelTier.CAPABLE),
    EXPLAIN_SENTENCE(ModelTier.CAPABLE),
    PHRASE_EXAMPLES(ModelTier.CAPABLE),
}
