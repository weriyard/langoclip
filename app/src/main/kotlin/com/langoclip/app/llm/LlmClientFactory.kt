package com.langoclip.app.llm

import com.langoclip.app.data.LogStore
import com.langoclip.app.data.Provider
import com.langoclip.app.data.Settings

fun createLlmClient(
    settings: Settings,
    model: String = settings.activeModel,
    logs: LogStore? = null,
    /** Selects the OpenRouter paid candidate list (FAST → Lite-first, CAPABLE → Flash-first).
     *  Ignored by other providers. Defaults to CAPABLE so older callers stay safe. */
    tier: ModelTier = ModelTier.CAPABLE,
): LlmClient = when (settings.provider) {
    Provider.GEMINI -> GeminiClient(apiKey = settings.geminiApiKey, model = model)
    Provider.OPENAI -> OpenAiClient(apiKey = settings.openAiApiKey, model = model)
    Provider.ANTHROPIC -> AnthropicClient(apiKey = settings.anthropicApiKey, model = model)
    // OpenRouter goes through the fallback-chain wrapper. Candidate list comes from the
    // "only free" toggle in Settings; OpenRouterClient walks it on 402 / 429 from upstream
    // providers (each :free model is hosted by a backend with its own per-day quota).
    Provider.OPENROUTER -> OpenRouterClient(
        apiKey = settings.openRouterApiKey,
        candidates = if (settings.onlyFreeOpenRouter) {
            Provider.OPENROUTER_FREE_CANDIDATES
        } else when (tier) {
            ModelTier.FAST -> Provider.OPENROUTER_PAID_FAST_CANDIDATES
            ModelTier.CAPABLE -> Provider.OPENROUTER_PAID_CAPABLE_CANDIDATES
        },
        logs = logs,
        ttftTimeoutMs = settings.openRouterTtftTimeoutSec.toLong() * 1000L,
    )
}
