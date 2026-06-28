package com.langoclip.app.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.text.Normalizer

/**
 * LLM response cache (cache-aside). Key is a hash of (version, model, system prompt hash,
 * normalized input). Hit → return the stored raw response; miss → call LLM, persist it.
 *
 * Storage: in-memory map + persistent JSON in `filesDir`. Singleton (two instances would write
 * to the same file). Eviction: LRU once [MAX_ENTRIES] is exceeded.
 *
 * Bulletproof write: serialize to `llm_cache.json.tmp`, then [Files.move] with `ATOMIC_MOVE` —
 * either the file is old or fully new, never truncated JSON. If the process dies mid-write to
 * tmp → the original is untouched, tmp is wiped on next start ([loadFromDisk]).
 */
class LlmCache private constructor(context: Context) {

    private val file = File(context.applicationContext.filesDir, "llm_cache.json")
    private val tmpFile = File(file.parentFile, "${file.name}.tmp")
    private val mutex = Mutex()       // guards the in-memory map
    private val saveMutex = Mutex()   // serializes disk I/O so we don't overwrite tmp in a race
    private val json = Json { ignoreUnknownKeys = true }
    private val memory: MutableMap<String, Entry> by lazy { loadFromDisk() }

    suspend fun get(key: String): String? = mutex.withLock {
        val entry = memory[key] ?: return@withLock null
        memory[key] = entry.copy(accessedAt = System.currentTimeMillis())
        entry.response
    }

    suspend fun put(key: String, response: String) {
        mutex.withLock {
            memory[key] = Entry(response, System.currentTimeMillis())
            if (memory.size > MAX_ENTRIES) evictLruLocked()
        }
        flushToDisk()
    }

    /** Removes the entry for the given key — used when parsing of the cached response failed. */
    suspend fun invalidate(key: String) {
        val removed = mutex.withLock { memory.remove(key) != null }
        if (removed) flushToDisk()
    }

    /** Wipes the entire cache (in-memory + on-disk). User-triggered from Settings. */
    suspend fun clear() {
        mutex.withLock { memory.clear() }
        flushToDisk()
    }

    /** Approximate size — number of cached entries. Useful for "clear X entries" labels. */
    suspend fun size(): Int = mutex.withLock { memory.size }

    private fun evictLruLocked() {
        val keysToRemove = memory.entries
            .sortedBy { it.value.accessedAt }
            .take(memory.size - TARGET_ENTRIES_AFTER_EVICT)
            .map { it.key }
        keysToRemove.forEach { memory.remove(it) }
    }

    /**
     * Flushes the CURRENT in-memory map to disk. Snapshot is taken INSIDE saveMutex so concurrent
     * puts aren't lost — the last snapshot always includes every put made so far.
     */
    private suspend fun flushToDisk() = withContext(Dispatchers.IO) {
        saveMutex.withLock {
            val snapshot = mutex.withLock { memory.toMap() }
            runCatching {
                tmpFile.writeText(json.encodeToString(Storage.serializer(), Storage(snapshot)))
                Files.move(
                    tmpFile.toPath(),
                    file.toPath(),
                    StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE,
                )
            }.onFailure {
                // After a failed write, clean up tmp so it doesn't dangle until next start.
                tmpFile.takeIf { it.exists() }?.delete()
            }
        }
    }

    private fun loadFromDisk(): MutableMap<String, Entry> {
        // Clean up any tmp left over from a crash in the previous session.
        runCatching { if (tmpFile.exists()) tmpFile.delete() }
        return runCatching {
            if (!file.exists()) return@runCatching mutableMapOf<String, Entry>()
            val storage = json.decodeFromString<Storage>(file.readText())
            storage.entries.toMutableMap()
        }.getOrDefault(mutableMapOf())
    }

    @Serializable
    private data class Entry(val response: String, val accessedAt: Long)

    @Serializable
    private data class Storage(val entries: Map<String, Entry> = emptyMap())

    companion object {
        private const val MAX_ENTRIES = 500
        private const val TARGET_ENTRIES_AFTER_EVICT = 400

        @Volatile
        private var instance: LlmCache? = null

        fun getInstance(context: Context): LlmCache = instance ?: synchronized(this) {
            instance ?: LlmCache(context.applicationContext).also { instance = it }
        }

        /**
         * Stable cache key. Bump `version` to invalidate everything (e.g. DTO format change).
         * Editing a .md invalidates entries automatically — the system prompt is part of the hash input.
         */
        fun keyFor(version: String, model: String, systemPrompt: String, input: String): String {
            val normalized = normalize(input)
            val promptHash = sha256(systemPrompt)
            return sha256("$version|$model|$promptHash|$normalized")
        }

        /**
         * Aggressively canonicalizes text for the cache key. Applied ONLY for key generation —
         * the original user input is sent to the LLM. As a result "She runs" ≡ "She runs." ≡
         * "  she runs!  " ≡ "She runs?" → same key, one token call. Tradeoff: we lose the
         * declarative/interrogative distinction. Acceptable for most everyday queries.
         */
        private fun normalize(text: String): String {
            val cleaned = text
                .replace(ZERO_WIDTH_REGEX, "")
                .let { Normalizer.normalize(it, Normalizer.Form.NFC) }
                .lowercase()
                .replace(WHITESPACE_RUN, " ")
                .trim()
            // Strip leading/trailing characters that aren't letters/digits — catches periods,
            // exclamation marks, quotes, brackets etc. Mid-string punctuation stays (it carries meaning).
            return cleaned
                .dropWhile { !it.isLetterOrDigit() }
                .dropLastWhile { !it.isLetterOrDigit() }
        }

        private val ZERO_WIDTH_REGEX = Regex("[​-‏  ﻿]")
        private val WHITESPACE_RUN = Regex("\\s+")

        private fun sha256(input: String): String {
            val bytes = MessageDigest.getInstance("SHA-256")
                .digest(input.toByteArray(Charsets.UTF_8))
            return bytes.joinToString("") { "%02x".format(it) }
        }
    }
}
