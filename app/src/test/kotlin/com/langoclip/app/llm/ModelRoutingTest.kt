package com.langoclip.app.llm

import com.langoclip.app.data.AppLocale
import com.langoclip.app.data.Provider
import com.langoclip.app.data.Settings
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Tier mapping ([LlmTask] -> [ModelTier]) and [ModelRouter] selection. The routing rule:
 * FAST tasks use the provider's cheap fast model, CAPABLE tasks use the active (default) model.
 */
class ModelRoutingTest {

    private fun settings(
        provider: Provider = Provider.GEMINI,
        geminiModel: String = "gemini-2.5-flash",
    ) = Settings(
        provider = provider,
        geminiApiKey = "", isUsingDefaultGeminiKey = true, geminiModel = geminiModel,
        openAiApiKey = "", isUsingDefaultOpenAiKey = true, openAiModel = "gpt-5-nano",
        anthropicApiKey = "", isUsingDefaultAnthropicKey = true, anthropicModel = "claude-haiku-4-5-20251001",
        openRouterApiKey = "", isUsingDefaultOpenRouterKey = true, openRouterModel = "deepseek/deepseek-v4-flash:free",
        onlyFreeOpenRouter = true, openRouterTtftTimeoutSec = 30,
        targetLanguage = "polski", autoStartBubble = false, appLocale = AppLocale.SYSTEM,
    )

    @Test
    fun `task tiers are assigned as designed`() {
        assertEquals(ModelTier.FAST, LlmTask.TRANSLATE.tier)
        assertEquals(ModelTier.FAST, LlmTask.WORD_SENSES.tier)
        assertEquals(ModelTier.CAPABLE, LlmTask.CHAT.tier)
        assertEquals(ModelTier.CAPABLE, LlmTask.EXPLAIN_SENTENCE.tier)
        assertEquals(ModelTier.CAPABLE, LlmTask.PHRASE_EXAMPLES.tier)
    }

    @Test
    fun `FAST tasks route to the provider fast model`() {
        val s = settings(provider = Provider.GEMINI)
        assertEquals(Provider.GEMINI.fastModel, ModelRouter.modelFor(LlmTask.TRANSLATE, s))
        assertEquals(Provider.GEMINI.fastModel, ModelRouter.modelFor(LlmTask.WORD_SENSES, s))
    }

    @Test
    fun `CAPABLE tasks route to the active model`() {
        val s = settings(provider = Provider.GEMINI, geminiModel = "gemini-2.5-pro")
        assertEquals("gemini-2.5-pro", ModelRouter.modelFor(LlmTask.CHAT, s))
        assertEquals("gemini-2.5-pro", ModelRouter.modelFor(LlmTask.EXPLAIN_SENTENCE, s))
    }

    @Test
    fun `routing follows the configured provider`() {
        val s = settings(provider = Provider.OPENAI)
        assertEquals(Provider.OPENAI.fastModel, ModelRouter.modelFor(LlmTask.TRANSLATE, s))
        assertEquals("gpt-5-nano", ModelRouter.modelFor(LlmTask.CHAT, s))
    }
}
