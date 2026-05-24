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
        defaultModel = "gemini-2.5-flash",
        fastModel = "gemini-2.5-flash",
        models = listOf("gemini-2.5-flash", "gemini-2.5-pro", "gemini-2.0-flash"),
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
    }
}
