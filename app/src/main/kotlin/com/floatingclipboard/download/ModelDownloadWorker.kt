package com.floatingclipboard.download

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import java.io.File
import java.io.RandomAccessFile
import java.net.HttpURLConnection
import java.net.URL

/**
 * Downloads a model file from HuggingFace CDN with resume support.
 * Run via ModelDownloadManager.enqueue().
 *
 * Input keys: KEY_URL, KEY_DEST_PATH, KEY_HF_TOKEN (optional)
 * Output keys: KEY_DEST_PATH (on success)
 * Progress: KEY_BYTES_DOWNLOADED, KEY_BYTES_TOTAL
 */
class ModelDownloadWorker(
    private val context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun getForegroundInfo(): ForegroundInfo = buildForegroundInfo(0, 0)

    override suspend fun doWork(): Result {
        val url = inputData.getString(KEY_URL) ?: return Result.failure()
        val destPath = inputData.getString(KEY_DEST_PATH) ?: return Result.failure()
        val hfToken = inputData.getString(KEY_HF_TOKEN)

        val dest = File(destPath)
        dest.parentFile?.mkdirs()

        return runCatching {
            downloadWithResume(url, dest, hfToken)
            Result.success(workDataOf(KEY_DEST_PATH to destPath))
        }.getOrElse { e ->
            Result.failure(workDataOf(KEY_ERROR to (e.message ?: "Unknown error")))
        }
    }

    private suspend fun downloadWithResume(url: String, dest: File, hfToken: String?) {
        val resumeFrom = if (dest.exists()) dest.length() else 0L

        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 30_000
            readTimeout = 30_000
            if (hfToken?.isNotBlank() == true) setRequestProperty("Authorization", "Bearer $hfToken")
            if (resumeFrom > 0) setRequestProperty("Range", "bytes=$resumeFrom-")
        }

        val totalSize = conn.contentLengthLong + resumeFrom
        val responseCode = conn.responseCode
        if (responseCode != HttpURLConnection.HTTP_OK && responseCode != 206) {
            error("HTTP $responseCode for $url")
        }

        conn.inputStream.use { input ->
            RandomAccessFile(dest, "rw").use { raf ->
                raf.seek(resumeFrom)
                val buffer = ByteArray(BUFFER_SIZE)
                var downloaded = resumeFrom
                var read: Int
                while (input.read(buffer).also { read = it } != -1) {
                    raf.write(buffer, 0, read)
                    downloaded += read
                    setForeground(buildForegroundInfo(downloaded, totalSize))
                    setProgress(workDataOf(
                        KEY_BYTES_DOWNLOADED to downloaded,
                        KEY_BYTES_TOTAL to totalSize,
                    ))
                }
            }
        }
    }

    private fun buildForegroundInfo(downloaded: Long, total: Long): ForegroundInfo {
        val channel = NotificationChannel(CHANNEL_ID, "Model download", NotificationManager.IMPORTANCE_LOW)
        (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
            .createNotificationChannel(channel)

        val percent = if (total > 0) (downloaded * 100 / total).toInt() else 0
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle("Pobieranie modelu")
            .setContentText("$percent% (${downloaded / MB}/${total / MB} MB)")
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setProgress(100, percent, total == 0L)
            .setOngoing(true)
            .build()

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ForegroundInfo(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(NOTIFICATION_ID, notification)
        }
    }

    companion object {
        const val KEY_URL = "url"
        const val KEY_DEST_PATH = "dest_path"
        const val KEY_HF_TOKEN = "hf_token"
        const val KEY_BYTES_DOWNLOADED = "bytes_downloaded"
        const val KEY_BYTES_TOTAL = "bytes_total"
        const val KEY_ERROR = "error"

        private const val CHANNEL_ID = "model_download"
        private const val NOTIFICATION_ID = 42
        private const val BUFFER_SIZE = 128 * 1024
        private const val MB = 1_048_576L
    }
}
