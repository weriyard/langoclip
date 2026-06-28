package com.langoclip.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Provider defaults and the OpenRouter fallback-chain candidate lists. These encode product
 * decisions (free-first onboarding, Lite for cheap tasks, Flash for structured JSON) that are
 * easy to break accidentally, so they're worth pinning.
 */
class ProviderTest {

    @Test
    fun `parse falls back to OpenRouter for unknown or null input`() {
        assertEquals(Provider.OPENROUTER, Provider.parse(null))
        assertEquals(Provider.OPENROUTER, Provider.parse(""))
        assertEquals(Provider.OPENROUTER, Provider.parse("not-a-provider"))
    }

    @Test
    fun `parse is case-insensitive and matches enum names`() {
        assertEquals(Provider.GEMINI, Provider.parse("gemini"))
        assertEquals(Provider.GEMINI, Provider.parse("GEMINI"))
        assertEquals(Provider.OPENAI, Provider.parse("OpenAi"))
        assertEquals(Provider.ANTHROPIC, Provider.parse("anthropic"))
    }

    @Test
    fun `Gemini default and fast models match the tier intent`() {
        // CAPABLE tier uses the default (Flash); FAST tier uses the cheaper Lite.
        assertEquals("gemini-2.5-flash", Provider.GEMINI.defaultModel)
        assertEquals("gemini-2.5-flash-lite", Provider.GEMINI.fastModel)
    }

    @Test
    fun `paid FAST chain starts with the cheapest fastest model`() {
        assertEquals(
            "google/gemini-2.5-flash-lite",
            Provider.OPENROUTER_PAID_FAST_CANDIDATES.first(),
        )
    }

    @Test
    fun `paid CAPABLE chain skips Lite (it drops objects on long JSON)`() {
        assertFalse(
            "CAPABLE tier must not use Flash-Lite",
            Provider.OPENROUTER_PAID_CAPABLE_CANDIDATES.any { it.contains("flash-lite") },
        )
        assertEquals(
            "google/gemini-2.5-flash",
            Provider.OPENROUTER_PAID_CAPABLE_CANDIDATES.first(),
        )
    }

    @Test
    fun `free candidate list is non-empty and only free models`() {
        assertTrue(Provider.OPENROUTER_FREE_CANDIDATES.isNotEmpty())
        assertTrue(
            "every free-tier candidate must carry the :free suffix",
            Provider.OPENROUTER_FREE_CANDIDATES.all { it.endsWith(":free") },
        )
    }
}
