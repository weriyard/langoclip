package com.langoclip.app.data

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class LogLevel { D, I, W, E }

data class LogEntry(
    val timestampMs: Long,
    val level: LogLevel,
    val tag: String,
    val message: String,
) {
    fun formatted(): String {
        val ts = SimpleDateFormat("HH:mm:ss.SSS", Locale.US).format(Date(timestampMs))
        return "$ts ${level.name} $tag: $message"
    }
}

/**
 * Singleton in-app log store. Keeps the last [MAX_ENTRIES] entries in memory (StateFlow for UI),
 * and also appends to `logs.txt` in filesDir so they survive a process kill (after being pushed
 * out of logcat by system spam — typical on Samsung One UI). Rotated when the file > [MAX_FILE_BYTES].
 *
 * Access to entries is thread-safe; disk I/O runs in a SupervisorJob coroutine scope, fire-and-forget.
 */
class LogStore private constructor(context: Context) {

    private val appContext = context.applicationContext
    private val file = File(appContext.filesDir, "logs.txt")
    private val rotatedFile = File(appContext.filesDir, "logs.prev.txt")
    private val lastRawFile = File(appContext.filesDir, "last_raw.txt")
    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val fileMutex = Mutex()

    private val _entries = MutableStateFlow<List<LogEntry>>(loadFromDisk())
    val entries: StateFlow<List<LogEntry>> = _entries.asStateFlow()

    fun log(level: LogLevel, tag: String, message: String) {
        val entry = LogEntry(System.currentTimeMillis(), level, tag, message)
        _entries.value = (_entries.value + entry).takeLast(MAX_ENTRIES)
        // Mirror to Logcat so Android Studio sees it when attached.
        when (level) {
            LogLevel.D -> Log.d(tag, message)
            LogLevel.I -> Log.i(tag, message)
            LogLevel.W -> Log.w(tag, message)
            LogLevel.E -> Log.e(tag, message)
        }
        appendToFile(entry)
    }

    fun d(tag: String, message: String) = log(LogLevel.D, tag, message)
    fun i(tag: String, message: String) = log(LogLevel.I, tag, message)
    fun w(tag: String, message: String) = log(LogLevel.W, tag, message)
    fun e(tag: String, message: String) = log(LogLevel.E, tag, message)

    fun clear() {
        _entries.value = emptyList()
        ioScope.launch {
            fileMutex.withLock {
                runCatching { file.delete(); rotatedFile.delete() }
            }
        }
    }

    /** The whole buffer as text, e.g. to pass through a share intent. */
    fun snapshot(): String =
        _entries.value.joinToString("\n") { it.formatted() }

    /** Saves the FULL raw response from the LLM to a separate file — for parse error diagnostics. */
    fun saveLastRaw(label: String, content: String) {
        ioScope.launch {
            fileMutex.withLock {
                runCatching {
                    lastRawFile.writeText("[$label]\n$content")
                }
            }
        }
    }

    fun readLastRaw(): String? = runCatching {
        if (lastRawFile.exists()) lastRawFile.readText() else null
    }.getOrNull()

    private fun appendToFile(entry: LogEntry) {
        ioScope.launch {
            fileMutex.withLock {
                runCatching {
                    if (file.exists() && file.length() > MAX_FILE_BYTES) {
                        rotatedFile.delete()
                        file.renameTo(rotatedFile)
                    }
                    file.appendText(entry.formatted() + "\n")
                }
            }
        }
    }

    private fun loadFromDisk(): List<LogEntry> = runCatching {
        if (!file.exists()) return@runCatching emptyList()
        // Parse the last lines of the file; if the format drifts, skip the line.
        file.readLines()
            .takeLast(MAX_ENTRIES)
            .mapNotNull(::parseLine)
    }.getOrDefault(emptyList())

    private fun parseLine(line: String): LogEntry? {
        // Format: "HH:mm:ss.SSS LEVEL TAG: message"
        return runCatching {
            val parts = line.split(" ", limit = 4)
            if (parts.size < 4) return null
            val timePart = parts[0]
            val level = LogLevel.valueOf(parts[1])
            val tag = parts[2].removeSuffix(":")
            val message = parts[3]
            // Time is for display only — we don't know the date, so use "now" as the ordering timestamp;
            // sufficient for display, the real timestamp is already stored in the string.
            val timestampMs = parseTimeMs(timePart) ?: System.currentTimeMillis()
            LogEntry(timestampMs, level, tag, message)
        }.getOrNull()
    }

    private fun parseTimeMs(timePart: String): Long? = runCatching {
        SimpleDateFormat("HH:mm:ss.SSS", Locale.US).parse(timePart)?.time
    }.getOrNull()

    companion object {
        private const val MAX_ENTRIES = 500
        private const val MAX_FILE_BYTES = 512L * 1024  // 512KB → rotate to .prev

        @Volatile
        private var instance: LogStore? = null

        fun getInstance(context: Context): LogStore = instance ?: synchronized(this) {
            instance ?: LogStore(context.applicationContext).also { instance = it }
        }
    }
}
