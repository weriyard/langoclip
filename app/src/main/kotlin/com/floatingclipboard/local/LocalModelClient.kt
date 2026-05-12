package com.floatingclipboard.local

/**
 * Abstraction over on-device LLM inference.
 * Real implementation: LiteRtModelClient (requires downloaded .litertlm model).
 * Default: NoopLocalModelClient — orchestrator skips local inference and goes to API.
 */
interface LocalModelClient {
    val isAvailable: Boolean
    suspend fun translate(prompt: String): String?
    fun close()
}

object NoopLocalModelClient : LocalModelClient {
    override val isAvailable = false
    override suspend fun translate(prompt: String): String? = null
    override fun close() = Unit
}
