package com.langoclip.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [LlmCache.keyFor] is the cache's correctness contract: inputs that should hit the same cached
 * response must hash to the same key, and anything that changes the response (version, model,
 * system prompt) must change the key. Normalization is deliberately aggressive — see assertions.
 */
class LlmCacheKeyTest {

    private val v = "v1"
    private val model = "gemini-2.5-flash"
    private val prompt = "system prompt"

    @Test
    fun `same inputs produce the same key`() {
        assertEquals(
            LlmCache.keyFor(v, model, prompt, "She runs every day"),
            LlmCache.keyFor(v, model, prompt, "She runs every day"),
        )
    }

    @Test
    fun `key is a 64-char hex sha256`() {
        val key = LlmCache.keyFor(v, model, prompt, "hello")
        assertEquals(64, key.length)
        assertTrue(key.all { it in "0123456789abcdef" })
    }

    @Test
    fun `normalization ignores case, surrounding whitespace and edge punctuation`() {
        val base = LlmCache.keyFor(v, model, prompt, "She runs.")
        assertEquals(base, LlmCache.keyFor(v, model, prompt, "  she runs!  "))
        assertEquals(base, LlmCache.keyFor(v, model, prompt, "SHE RUNS?"))
        assertEquals(base, LlmCache.keyFor(v, model, prompt, "she   runs"))
    }

    @Test
    fun `mid-string punctuation is significant`() {
        assertNotEquals(
            LlmCache.keyFor(v, model, prompt, "well, done"),
            LlmCache.keyFor(v, model, prompt, "well done"),
        )
    }

    @Test
    fun `version, model and system prompt all change the key`() {
        val base = LlmCache.keyFor(v, model, prompt, "hello")
        assertNotEquals(base, LlmCache.keyFor("v2", model, prompt, "hello"))
        assertNotEquals(base, LlmCache.keyFor(v, "other-model", prompt, "hello"))
        assertNotEquals(base, LlmCache.keyFor(v, model, "different prompt", "hello"))
    }
}
