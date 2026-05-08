package com.floatingclipboard.llm

import com.floatingclipboard.data.Provider
import com.floatingclipboard.data.Settings

fun createLlmClient(settings: Settings): LlmClient = when (settings.provider) {
    Provider.GEMINI -> GeminiClient(apiKey = settings.geminiApiKey, model = settings.geminiModel)
    Provider.OPENAI -> OpenAiClient(apiKey = settings.openAiApiKey, model = settings.openAiModel)
    Provider.ANTHROPIC -> AnthropicClient(apiKey = settings.anthropicApiKey, model = settings.anthropicModel)
}
