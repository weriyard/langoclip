package com.floatingclipboard.download

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import java.io.File
import java.io.RandomAccessFile
import java.net.HttpURLConnection
import java.net.URL

/**
 * Downloads a model file from HuggingFace CDN with resume support.
 * Runs as a regular background task (no foreground service) — avoids
 * the Android 14 foregroundServiceType manifest declaration requirement.
 * Progress is reported via WorkManager's setProgress API.
 *
 * Input keys: KEY_URL, KEY_DEST_PATH, KEY_HF_TOKEN (optional)
 * Output keys: KEY_DEST_PATH (on success)
 * Progress: KEY_BYTES_DOWNLOADED, KEY_BYTES_TOTAL
 */
class ModelDownloadWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

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
            dest.delete()
            Result.failure(workDataOf(KEY_ERROR to (e.message ?: "Unknown error")))
        }
    }

    private suspend fun downloadWithResume(url: String, dest: File, hfToken: String?) {
        val resumeFrom = if (dest.exists()) dest.length() else 0L

        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 30_000
            readTimeout = 60_000
            if (hfToken?.isNotBlank() == true) setRequestProperty("Authorization", "Bearer $hfToken")
            if (resumeFrom > 0) setRequestProperty("Range", "bytes=$resumeFrom-")
        }

        val responseCode = conn.responseCode
        if (responseCode != HttpURLConnection.HTTP_OK && responseCode != 206) {
            error("HTTP $responseCode for $url")
        }

        val totalSize = conn.contentLengthLong + resumeFrom

        conn.inputStream.use { input ->
            RandomAccessFile(dest, "rw").use { raf ->
                raf.seek(resumeFrom)
                val buffer = ByteArray(BUFFER_SIZE)
                var downloaded = resumeFrom
                var read: Int
                while (input.read(buffer).also { read = it } != -1) {
                    raf.write(buffer, 0, read)
                    downloaded += read
                    setProgress(workDataOf(
                        KEY_BYTES_DOWNLOADED to downloaded,
                        KEY_BYTES_TOTAL to totalSize,
                    ))
                }
            }
        }
    }

    companion object {
        const val KEY_URL = "url"
        const val KEY_DEST_PATH = "dest_path"
        const val KEY_HF_TOKEN = "hf_token"
        const val KEY_BYTES_DOWNLOADED = "bytes_downloaded"
        const val KEY_BYTES_TOTAL = "bytes_total"
        const val KEY_ERROR = "error"

        private const val BUFFER_SIZE = 128 * 1024
    }
}
