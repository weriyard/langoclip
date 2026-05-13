@file:Suppress("DEPRECATION")

package com.floatingclipboard.local

import android.app.ActivityManager
import android.content.Context
import android.util.Log
import com.floatingclipboard.download.ModelDownloadManager
import com.floatingclipboard.download.TRANSLATION_MODELS
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import java.io.File
import java.util.concurrent.Executors
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.withContext

/**
 * On-device inference via MediaPipe tasks-genai.
 *
 * Thread safety: LlmInference is NOT thread-safe. All calls are serialized through
 * [inferenceDispatcher] (single dedicated thread named "litert-inference").
 *
 * Lazy initialization: model is loaded on the first [translate] call, not in the constructor,
 * because loading blocks for several seconds.
 *
 * [isAvailable] returns true only if the model file exists on disk — if the user hasn't
 * downloaded any model yet, the orchestrator falls through to Haiku/Sonnet automatically.
 */
class LiteRtModelClient(
    private val context: Context,
    private val modelPath: String,
) : LocalModelClient {

    private val inferenceDispatcher = Executors.newSingleThreadExecutor { r ->
        Thread(r, "litert-inference").also { it.isDaemon = true }
    }.asCoroutineDispatcher()

    // Accessed only from inferenceDispatcher — no synchronization needed.
    private var llm: LlmInference? = null

    override val isAvailable: Boolean get() = File(modelPath).exists()

    override suspend fun translate(prompt: String): String? = withContext(inferenceDispatcher) {
        if (!isAvailable) return@withContext null
        Log.d(TAG, "translate() called, model=$modelPath")
        runCatching {
            val result = ensureLlm().generateResponse(prompt)
            Log.d(TAG, "translate() OK, response length=${result.length}")
            result
        }.onFailure { Log.e(TAG, "translate() failed", it) }.getOrNull()
    }

    override fun close() {
        inferenceDispatcher.close()
        llm?.close()
        llm = null
    }

    private fun ensureLlm(): LlmInference = llm ?: run {
        Log.d(TAG, "Loading model from $modelPath …")
        val options = LlmInference.LlmInferenceOptions.builder()
            .setModelPath(modelPath)
            .setMaxTokens(512)
            .build()
        LlmInference.createFromOptions(context, options).also {
            llm = it
            Log.d(TAG, "Model loaded OK")
        }
    }

    companion object {
        private const val TAG = "LiteRtModel"

        /**
         * Returns a [LiteRtModelClient] for the first downloaded translation model,
         * or [NoopLocalModelClient] if none is downloaded yet.
         */
        fun firstAvailableOrNoop(context: Context): LocalModelClient {
            val manager = ModelDownloadManager(context)
            val totalRamGb = totalRamGb(context)
            Log.d(TAG, "firstAvailableOrNoop: device RAM=${totalRamGb}GB")
            val model = TRANSLATION_MODELS.firstOrNull { m ->
                val downloaded = manager.isDownloaded(m)
                val fitsRam = m.requiredRamGb <= totalRamGb
                Log.d(TAG, "  ${m.id}: downloaded=$downloaded requiredRam=${m.requiredRamGb}GB fitsRam=$fitsRam")
                downloaded && fitsRam
            }
            if (model == null) {
                Log.d(TAG, "firstAvailableOrNoop: no model available → Noop")
                return NoopLocalModelClient
            }
            Log.d(TAG, "firstAvailableOrNoop: using ${model.id}")
            return LiteRtModelClient(context, manager.modelFile(model).absolutePath)
        }

        private fun totalRamGb(context: Context): Float {
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val info = ActivityManager.MemoryInfo()
            am.getMemoryInfo(info)
            return info.totalMem / (1024f * 1024 * 1024)
        }
    }
}
