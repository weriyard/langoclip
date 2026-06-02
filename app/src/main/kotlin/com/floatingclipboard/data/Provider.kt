package com.floatingclipboard.data

enum class Provider(
    val displayName: String,
    val defaultModel: String,
    val fastModel: String,
    val models: List<String>,
    val apiKeyConsoleUrl: String,
) {
    GEMINI(
        displayName = "Gemini",
        // CAPABLE tier (Explain / Examples) — Flash handles the long structured JSON.
        defaultModel = "gemini-2.5-flash",
        // FAST tier (Translate / Word senses / Chat) — Lite is ~2× cheaper & faster, JSON is
        // shallow here. Mirrors the OpenRouter paid split (see OPENROUTER_PAID_*_CANDIDATES).
        fastModel = "gemini-2.5-flash-lite",
        models = listOf("gemini-2.5-flash-lite", "gemini-2.5-flash", "gemini-2.5-pro", "gemini-2.0-flash"),
        apiKeyConsoleUrl = "https://aistudio.google.com/apikey",
    ),
    OPENAI(
        displayName = "ChatGPT (OpenAI)",
        defaultModel = "gpt-5-nano",
        fastModel = "gpt-5-nano",
        models = listOf("gpt-5-nano", "gpt-5-mini", "gpt-5", "gpt-4o-mini"),
        apiKeyConsoleUrl = "https://platform.openai.com/api-keys",
    ),
    ANTHROPIC(
        displayName = "Claude (Anthropic)",
        defaultModel = "claude-haiku-4-5-20251001",
        fastModel = "claude-haiku-4-5-20251001",
        models = listOf("claude-haiku-4-5-20251001", "claude-sonnet-4-6", "claude-opus-4-7"),
        apiKeyConsoleUrl = "https://console.anthropic.com/settings/keys",
    ),
    OPENROUTER(
        displayName = "OpenRouter (free)",
        defaultModel = "deepseek/deepseek-v4-flash:free",
        fastModel = "deepseek/deepseek-v4-flash:free",
        // Curated subset of openrouter.ai/api/v1/models filtered to ':free' — verified against the
        // current catalogue. Slugs change occasionally as providers rotate hosted versions; if a
        // 404 comes back from OpenRouter, refresh this list (and pin a known-good default).
        models = listOf(
            "deepseek/deepseek-v4-flash:free",
            "qwen/qwen3-next-80b-a3b-instruct:free",
            "meta-llama/llama-3.3-70b-instruct:free",
            "google/gemma-4-31b-it:free",
            "openai/gpt-oss-120b:free",
            "nvidia/nemotron-3-super-120b-a12b:free",
            "z-ai/glm-4.5-air:free",
            "nousresearch/hermes-3-llama-3.1-405b:free",
        ),
        apiKeyConsoleUrl = "https://openrouter.ai/keys",
    );

    companion object {
        // Default falls back to OpenRouter because its free tier covers all our tasks at $0/mo
        // — users without any API key still get a usable app out of the box.
        fun parse(value: String?): Provider =
            entries.firstOrNull { it.name == value?.uppercase() } ?: OPENROUTER

        // Ordered candidate list when "only free" mode is ON in Settings. Best Polish + JSON
        // discipline first — OpenRouterClient walks this until one returns content (some upstream
        // providers run out of their per-model daily free allocations independently of our
        // account's OpenRouter quota).
        val OPENROUTER_FREE_CANDIDATES: List<String> = listOf(
            "deepseek/deepseek-v4-flash:free",
            "qwen/qwen3-next-80b-a3b-instruct:free",
            "meta-llama/llama-3.3-70b-instruct:free",
            "openai/gpt-oss-120b:free",
            "google/gemma-4-31b-it:free",
            // nvidia/nemotron-3-super-120b removed — observed to hang indefinitely on the SSE
            // stream (no chunks, no error) which blocks the fallback walker. Re-add if upstream
            // routing stabilises.
            "z-ai/glm-4.5-air:free",
            "nousresearch/hermes-3-llama-3.1-405b:free",
        )

        // "Only free" toggle OFF → paid routing, split by LlmTask.tier so word translations and
        // chat use the cheap fast Lite while breakdowns/examples get Flash. All upstream Gemini
        // pricing is text non-thinking (no reasoning surcharge).
        //
        // FAST tier — TRANSLATE / WORD_SENSES / chat. Single-word or short outputs, JSON shape
        // is shallow. Lite is ~2× cheaper and ~2× faster than Flash; if it stumbles we fall
        // back to Flash → Llama → DeepSeek.
        val OPENROUTER_PAID_FAST_CANDIDATES: List<String> = listOf(
            "google/gemini-2.5-flash-lite",          // ~$0.04 / $0.15 per M, fastest
            "google/gemini-2.5-flash",               // ~$0.075 / $0.30 per M
            "meta-llama/llama-3.3-70b-instruct",     // ~$0.05–0.30 / M, well-rounded
            "deepseek/deepseek-v4-flash",            // strongest Polish but slow + pricier
        )

        // CAPABLE tier — EXPLAIN_SENTENCE / PHRASE_EXAMPLES. Longer structured JSON with 10+
        // items where Lite has historically dropped objects. Skip Lite entirely; start with Flash.
        val OPENROUTER_PAID_CAPABLE_CANDIDATES: List<String> = listOf(
            "google/gemini-2.5-flash",               // ~$0.075 / $0.30 per M, good JSON discipline
            "meta-llama/llama-3.3-70b-instruct",     // ~$0.05–0.30 / M, well-rounded fallback
            "deepseek/deepseek-v4-flash",            // strongest Polish, ~2-3× slower
        )
    }
}
