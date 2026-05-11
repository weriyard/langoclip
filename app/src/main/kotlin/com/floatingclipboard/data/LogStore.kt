package com.floatingclipboard.data

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
 * Singleton in-app log store. Trzyma ostatnie [MAX_ENTRIES] wpisów w pamięci (StateFlow dla UI),
 * dodatkowo appenduje do `logs.txt` w filesDir żeby przeżyło killa procesu (po wypchnięciu z
 * logcata przez systemowy spam — typowe na Samsung One UI). Rotacja gdy plik > [MAX_FILE_BYTES].
 *
 * Dostęp do entries jest thread-safe; I/O na disk w SupervisorJob coroutine scope, fire-and-forget.
 */
class LogStore private constructor(context: Context) {

    private val appContext = context.applicationContext
    private val file = File(appContext.filesDir, "logs.txt")
    private val rotatedFile = File(appContext.filesDir, "logs.prev.txt")
    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val fileMutex = Mutex()

    private val _entries = MutableStateFlow<List<LogEntry>>(loadFromDisk())
    val entries: StateFlow<List<LogEntry>> = _entries.asStateFlow()

    fun log(level: LogLevel, tag: String, message: String) {
        val entry = LogEntry(System.currentTimeMillis(), level, tag, message)
        _entries.value = (_entries.value + entry).takeLast(MAX_ENTRIES)
        // Mirror do Logcata żeby Android Studio też widział, gdy podpięte.
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

    /** Cały bufor jako tekst, np. do przekazania przez share intent. */
    fun snapshot(): String =
        _entries.value.joinToString("\n") { it.formatted() }

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
        // Parsujemy ostatnie linie z pliku; jeśli format się rozjedzie, ignorujemy linijkę.
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
            // Czas tylko dla wyświetlenia — nie znamy daty, więc używamy "teraz" dla timestamp porządkowania;
            // wystarczy do display, real timestamp już zapisany w stringu.
            val timestampMs = parseTimeMs(timePart) ?: System.currentTimeMillis()
            LogEntry(timestampMs, level, tag, message)
        }.getOrNull()
    }

    private fun parseTimeMs(timePart: String): Long? = runCatching {
        SimpleDateFormat("HH:mm:ss.SSS", Locale.US).parse(timePart)?.time
    }.getOrNull()

    companion object {
        private const val MAX_ENTRIES = 500
        private const val MAX_FILE_BYTES = 512L * 1024  // 512KB → rotacja do .prev

        @Volatile
        private var instance: LogStore? = null

        fun getInstance(context: Context): LogStore = instance ?: synchronized(this) {
            instance ?: LogStore(context.applicationContext).also { instance = it }
        }
    }
}
