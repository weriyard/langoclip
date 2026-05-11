package com.floatingclipboard.data

import com.floatingclipboard.actions.Action
import com.floatingclipboard.actions.BreakdownItem
import com.floatingclipboard.actions.Example
import com.floatingclipboard.ui.ActionState
import com.floatingclipboard.ui.ExamplesState
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicLong

/**
 * Identyfikator zakładki. Long zamiast UUID — w UI używamy do `key()` w Compose, mniejszy hit
 * memory niż String UUID.
 */
@JvmInline
value class TabId(val value: Long)

/**
 * Stan pojedynczej zakładki. Każdy wariant nosi swój własny state, niezależnie od pozostałych.
 *
 * - [Paste] — jedyna stała zakładka (`id == PASTE_ID`), bez X, edytowalne pole tekstowe + wynik
 *   inline (Translate). Wytłumacz w Schowku NIE zmienia jej stanu na breakdown, tylko tworzy
 *   nowy [Explain] tab (snapshot).
 * - [Explain] — snapshot wyniku Wytłumacz dla konkretnego tekstu. Streamuje w Loading, finalnie
 *   Success/Error. Tytuł = pierwsze ~25 znaków tekstu.
 * - [Examples] — snapshot 5 przykładów dla frazy. Pull-to-refresh / regenerate tworzą nowy
 *   wariant w tej samej zakładce (variant counter trzymany w stanie).
 */
sealed interface Tab {
    val id: TabId
    val label: String
    val isCloseable: Boolean

    data class Paste(
        val text: String = "",
        val actionState: ActionState = ActionState.Idle,
    ) : Tab {
        override val id: TabId = PASTE_ID
        override val label: String = "Schowek"
        override val isCloseable: Boolean = false
    }

    data class Explain(
        override val id: TabId,
        val sourceText: String,
        val state: ActionState,
    ) : Tab {
        override val label: String = sourceText.take(LABEL_MAX).let {
            if (sourceText.length > LABEL_MAX) "$it…" else it
        }
        override val isCloseable: Boolean = true
    }

    data class Examples(
        override val id: TabId,
        val phrase: String,
        val translation: String,
        val variant: Int,
        val state: ExamplesState,
    ) : Tab {
        override val label: String = phrase
        override val isCloseable: Boolean = true
    }

    companion object {
        val PASTE_ID = TabId(0)
        private const val LABEL_MAX = 25
    }
}

/**
 * Singleton in-memory store dla zakładek. Operacje:
 *  - [updatePaste] modyfikuje pierwszą zakładkę (Schowek).
 *  - [openExplain] / [openExamples] tworzą nową zakładkę i auto-przełączają na nią.
 *  - [updateTab] aktualizuje konkretną zakładkę (np. streaming progress).
 *  - [close] zamyka zakładkę; jeśli była aktywna, przełącza na poprzednią (lub Schowek).
 *  - [putJob] / [getJob] przechowują streamingowe Job per tab id — dzięki temu zamknięcie
 *    zakładki w trakcie streamingu anuluje request.
 *
 * NIE persistujemy. Restart apki = pusty stan (tylko Schowek).
 */
class TabsRepository private constructor() {

    private val idGen = AtomicLong(1)  // 0 zarezerwowane dla Schowka

    private val _tabs = MutableStateFlow<List<Tab>>(listOf(Tab.Paste()))
    val tabs: StateFlow<List<Tab>> = _tabs.asStateFlow()

    private val _selectedId = MutableStateFlow(Tab.PASTE_ID)
    val selectedId: StateFlow<TabId> = _selectedId.asStateFlow()

    private val jobs = mutableMapOf<TabId, Job>()

    fun select(id: TabId) {
        if (_tabs.value.any { it.id == id }) {
            _selectedId.value = id
        }
    }

    fun updatePaste(transform: (Tab.Paste) -> Tab.Paste) {
        _tabs.update { list ->
            list.map { if (it is Tab.Paste) transform(it) else it }
        }
    }

    fun openExplain(sourceText: String, initial: ActionState): TabId {
        val id = TabId(idGen.getAndIncrement())
        val tab = Tab.Explain(id = id, sourceText = sourceText, state = initial)
        _tabs.update { it + tab }
        _selectedId.value = id
        return id
    }

    fun openExamples(phrase: String, translation: String, initial: ExamplesState): TabId {
        val id = TabId(idGen.getAndIncrement())
        val tab = Tab.Examples(
            id = id, phrase = phrase, translation = translation, variant = 0, state = initial,
        )
        _tabs.update { it + tab }
        _selectedId.value = id
        return id
    }

    fun updateExplain(id: TabId, transform: (Tab.Explain) -> Tab.Explain) {
        _tabs.update { list ->
            list.map { if (it is Tab.Explain && it.id == id) transform(it) else it }
        }
    }

    fun updateExamples(id: TabId, transform: (Tab.Examples) -> Tab.Examples) {
        _tabs.update { list ->
            list.map { if (it is Tab.Examples && it.id == id) transform(it) else it }
        }
    }

    fun close(id: TabId) {
        if (id == Tab.PASTE_ID) return  // Schowek nie ma X
        jobs.remove(id)?.cancel()
        val currentList = _tabs.value
        val idx = currentList.indexOfFirst { it.id == id }
        if (idx < 0) return
        val newList = currentList.filterIndexed { i, _ -> i != idx }
        _tabs.value = newList
        // Jeśli zamknięta była aktywna, przełącz na poprzednią lub Schowek.
        if (_selectedId.value == id) {
            val fallback = newList.getOrNull(idx - 1)?.id ?: Tab.PASTE_ID
            _selectedId.value = fallback
        }
    }

    fun putJob(id: TabId, job: Job) {
        jobs[id]?.cancel()
        jobs[id] = job
    }

    /** Pomocnicze rozszerzenie żeby update nie potrzebował copy(...) na zewnątrz. */
    private fun <T> MutableStateFlow<T>.update(transform: (T) -> T) {
        value = transform(value)
    }

    companion object {
        @Volatile
        private var instance: TabsRepository? = null

        fun getInstance(): TabsRepository = instance ?: synchronized(this) {
            instance ?: TabsRepository().also { instance = it }
        }
    }
}
