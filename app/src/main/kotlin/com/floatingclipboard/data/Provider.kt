package com.floatingclipboard.data

enum class Provider(
    val displayName: String,
    val defaultModel: String,
    val models: List<String>,
    val apiKeyConsoleUrl: String,
) {
    GEMINI(
        displayName = "Gemini",
        defaultModel = "gemini-2.5-flash",
        models = listOf("gemini-2.5-flash", "gemini-2.5-pro", "gemini-2.0-flash"),
        apiKeyConsoleUrl = "https://aistudio.google.com/apikey",
    ),
    OPENAI(
        displayName = "ChatGPT (OpenAI)",
        defaultModel = "gpt-5-nano",
        models = listOf("gpt-5-nano", "gpt-5-mini", "gpt-5", "gpt-4o-mini"),
        apiKeyConsoleUrl = "https://platform.openai.com/api-keys",
    ),
    ANTHROPIC(
        displayName = "Claude (Anthropic)",
        defaultModel = "claude-haiku-4-5-20251001",
        models = listOf("claude-haiku-4-5-20251001", "claude-sonnet-4-6", "claude-opus-4-7"),
        apiKeyConsoleUrl = "https://console.anthropic.com/settings/keys",
    );

    companion object {
        fun parse(value: String?): Provider =
            entries.firstOrNull { it.name == value?.uppercase() } ?: GEMINI
    }
}
