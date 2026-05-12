package com.floatingclipboard.download

import android.content.Context
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.io.File

class ModelDownloadManager(private val context: Context) {

    fun modelsDir(): File = File(context.filesDir, "models")

    fun modelFile(model: ModelInfo): File = File(modelsDir(), model.fileName)

    fun isDownloaded(model: ModelInfo): Boolean = modelFile(model).exists()

    fun enqueue(model: ModelInfo, hfToken: String? = null) {
        val url = "https://huggingface.co/${model.huggingFaceRepo}/resolve/main/${model.fileName}"
        val destPath = modelFile(model).absolutePath

        val inputData = Data.Builder()
            .putString(ModelDownloadWorker.KEY_URL, url)
            .putString(ModelDownloadWorker.KEY_DEST_PATH, destPath)
            .apply { if (!hfToken.isNullOrBlank()) putString(ModelDownloadWorker.KEY_HF_TOKEN, hfToken) }
            .build()

        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val request = OneTimeWorkRequestBuilder<ModelDownloadWorker>()
            .setInputData(inputData)
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(context)
            .enqueueUniqueWork(model.id, ExistingWorkPolicy.KEEP, request)
    }

    fun progressFlow(model: ModelInfo): Flow<DownloadProgress> =
        WorkManager.getInstance(context)
            .getWorkInfosForUniqueWorkFlow(model.id)
            .map { infos ->
                val info = infos.firstOrNull() ?: return@map DownloadProgress.Idle
                when (info.state) {
                    WorkInfo.State.RUNNING -> {
                        val downloaded = info.progress.getLong(ModelDownloadWorker.KEY_BYTES_DOWNLOADED, 0L)
                        val total = info.progress.getLong(ModelDownloadWorker.KEY_BYTES_TOTAL, 0L)
                        DownloadProgress.Downloading(downloaded, total)
                    }
                    WorkInfo.State.SUCCEEDED -> DownloadProgress.Done
                    WorkInfo.State.FAILED    -> DownloadProgress.Failed(
                        info.outputData.getString(ModelDownloadWorker.KEY_ERROR) ?: "Unknown error"
                    )
                    WorkInfo.State.CANCELLED -> DownloadProgress.Idle
                    else                     -> DownloadProgress.Idle
                }
            }

    fun cancel(model: ModelInfo) {
        WorkManager.getInstance(context).cancelUniqueWork(model.id)
    }
}

sealed interface DownloadProgress {
    data object Idle : DownloadProgress
    data class Downloading(val bytesDownloaded: Long, val bytesTotal: Long) : DownloadProgress {
        val percent: Int get() = if (bytesTotal > 0) (bytesDownloaded * 100 / bytesTotal).toInt() else 0
    }
    data object Done : DownloadProgress
    data class Failed(val error: String) : DownloadProgress
}
