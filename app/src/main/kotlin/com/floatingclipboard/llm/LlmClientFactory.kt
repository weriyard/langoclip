package com.floatingclipboard.llm

import com.floatingclipboard.data.LogStore
import com.floatingclipboard.data.Provider
import com.floatingclipboard.data.Settings

fun createLlmClient(
    settings: Settings,
    model: String = settings.activeModel,
    logs: LogStore? = null,
): LlmClient = when (settings.provider) {
    Provider.GEMINI -> GeminiClient(apiKey = settings.geminiApiKey, model = model)
    Provider.OPENAI -> OpenAiClient(apiKey = settings.openAiApiKey, model = model)
    Provider.ANTHROPIC -> AnthropicClient(apiKey = settings.anthropicApiKey, model = model)
    // OpenRouter goes through the fallback-chain wrapper. Candidate list comes from the
    // "only free" toggle in Settings; OpenRouterClient walks it on 402 / 429 from upstream
    // providers (each :free model is hosted by a backend with its own per-day quota).
    Provider.OPENROUTER -> OpenRouterClient(
        apiKey = settings.openRouterApiKey,
        candidates = if (settings.onlyFreeOpenRouter)
            Provider.OPENROUTER_FREE_CANDIDATES
        else
            Provider.OPENROUTER_PAID_CANDIDATES,
        logs = logs,
    )
}
