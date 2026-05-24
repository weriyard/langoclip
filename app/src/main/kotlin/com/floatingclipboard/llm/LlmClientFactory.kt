package com.floatingclipboard.llm

import com.floatingclipboard.data.Provider
import com.floatingclipboard.data.Settings

fun createLlmClient(settings: Settings, model: String = settings.activeModel): LlmClient =
    when (settings.provider) {
        Provider.GEMINI -> GeminiClient(apiKey = settings.geminiApiKey, model = model)
        Provider.OPENAI -> OpenAiClient(apiKey = settings.openAiApiKey, model = model)
        Provider.ANTHROPIC -> AnthropicClient(apiKey = settings.anthropicApiKey, model = model)
        // OpenRouter speaks the OpenAI Chat Completions protocol → reuse OpenAiClient with their URL.
        Provider.OPENROUTER -> OpenAiClient(
            apiKey = settings.openRouterApiKey,
            model = model,
            baseUrl = OpenAiClient.OPENROUTER_BASE_URL,
        )
    }
