package com.floatingclipboard.data

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
 * Cache odpowiedzi LLM (cache-aside). Klucz to hash z (wersja, model, hash system prompta,
 * znormalizowany input). Hit → zwracamy zachowaną surową odpowiedź; miss → wołamy LLM, zapisujemy.
 *
 * Storage: in-memory mapa + persistent JSON w `filesDir`. Singleton (dwie instancje pisałyby do
 * tego samego pliku). Eviction: LRU po przekroczeniu [MAX_ENTRIES].
 *
 * Bulletproof zapis: serializujemy do `llm_cache.json.tmp`, potem [Files.move] z `ATOMIC_MOVE` —
 * albo plik jest stary, albo cały nowy, nigdy uciętym JSON-em. Jeśli proces zginie w trakcie
 * write'a do tmp → oryginał jest nietknięty, tmp znika przy następnym starcie ([loadFromDisk]).
 */
class LlmCache private constructor(context: Context) {

    private val file = File(context.applicationContext.filesDir, "llm_cache.json")
    private val tmpFile = File(file.parentFile, "${file.name}.tmp")
    private val mutex = Mutex()       // chroni in-memory mapę
    private val saveMutex = Mutex()   // serializuje I/O na disk żeby nie nadpisywać tmp w wyścigu
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

    /** Usuwa wpis o danym kluczu — używane gdy parsowanie cached responseu się wywaliło. */
    suspend fun invalidate(key: String) {
        val removed = mutex.withLock { memory.remove(key) != null }
        if (removed) flushToDisk()
    }

    private fun evictLruLocked() {
        val keysToRemove = memory.entries
            .sortedBy { it.value.accessedAt }
            .take(memory.size - TARGET_ENTRIES_AFTER_EVICT)
            .map { it.key }
        keysToRemove.forEach { memory.remove(it) }
    }

    /**
     * Zrzuca AKTUALNY stan in-memory mapy na dysk. Snapshot brany WEWNĄTRZ saveMutex żeby
     * concurrent puty nie ginęły — ostatni snapshot zawsze zawiera wszystkie dotychczasowe puty.
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
                // Po nieudanym zapisie sprzątamy tmp żeby nie został zwisem do następnego startu.
                tmpFile.takeIf { it.exists() }?.delete()
            }
        }
    }

    private fun loadFromDisk(): MutableMap<String, Entry> {
        // Sprzątamy ewentualny tmp po crash'u z poprzedniej sesji.
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
         * Stabilny klucz cache. Bumpnij `version` żeby invalidate wszystko (np. zmiana formatu DTO).
         * Edycja .md unieważnia wpisy automatycznie — system prompt jest częścią hash inputu.
         */
        fun keyFor(version: String, model: String, systemPrompt: String, input: String): String {
            val normalized = normalize(input)
            val promptHash = sha256(systemPrompt)
            return sha256("$version|$model|$promptHash|$normalized")
        }

        /**
         * Agresywnie kanonicznizuje tekst pod kątem klucza cache. Stosujemy TYLKO do generowania
         * klucza — do LLM idzie oryginalny user input. Dzięki temu "She runs" ≡ "She runs." ≡
         * "  she runs!  " ≡ "She runs?" → ten sam klucz, jeden token-call. Tradeoff: tracimy
         * rozróżnienie deklaratywne/pytajne. Akceptowalne dla większości codziennych zapytań.
         */
        private fun normalize(text: String): String {
            val cleaned = text
                .replace(ZERO_WIDTH_REGEX, "")
                .let { Normalizer.normalize(it, Normalizer.Form.NFC) }
                .lowercase()
                .replace(WHITESPACE_RUN, " ")
                .trim()
            // Skubnięcie leading/trailing wszystkiego co nie jest literą/cyfrą — łapie kropki,
            // wykrzykniki, cudzysłowy, nawiasy itd. Punktuacja w środku zostaje (zmienia sens).
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
