package com.floatingclipboard.local

/**
 * Abstraction over on-device LLM inference. Reserved hook — no implementation is wired up
 * right now; orchestrator and ActionRunner pass [NoopLocalModelClient] and rely on the
 * remote API. Plug a concrete implementation in via the orchestrator/runner ctor when
 * on-device inference comes back.
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
