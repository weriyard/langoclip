package com.floatingclipboard.local

import java.util.concurrent.Executors
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.withContext

/**
 * LiteRT-LM inference client for on-device translation.
 *
 * Thread safety: LiteRT interpreters are NOT thread-safe. All inference is
 * serialized through a single dedicated thread via [inferenceDispatcher].
 *
 * TODO: add dependency to build.gradle.kts when ready to wire up:
 *   implementation("com.google.ai.edge.litert:litert-lm:<version>")
 * Then replace the stub body with real LiteRT-LM SessionRunner calls.
 */
class LiteRtModelClient(private val modelPath: String) : LocalModelClient {

    private val inferenceDispatcher = Executors.newSingleThreadExecutor { r ->
        Thread(r, "litert-inference").also { it.isDaemon = true }
    }.asCoroutineDispatcher()

    // TODO: initialize LiteRT-LM SessionRunner here
    // private val session: SessionRunner = ...

    override val isAvailable: Boolean = true

    override suspend fun translate(prompt: String): String? = withContext(inferenceDispatcher) {
        // TODO: replace with real inference
        // session.run(prompt)
        throw NotImplementedError("LiteRT-LM not yet wired up — add litert-lm dependency")
    }

    override fun close() {
        // TODO: session.close()
        inferenceDispatcher.close()
    }
}
