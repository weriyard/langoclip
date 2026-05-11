package com.floatingclipboard.ui

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.floatingclipboard.actions.Action
import com.floatingclipboard.actions.ActionResult
import com.floatingclipboard.actions.ActionRunner
import com.floatingclipboard.actions.PromptLoader
import com.floatingclipboard.data.LlmCache
import com.floatingclipboard.data.LogStore
import com.floatingclipboard.data.SettingsRepository
import com.floatingclipboard.data.Tab
import com.floatingclipboard.data.Tab.Companion.PASTE_ID
import com.floatingclipboard.data.TabId
import com.floatingclipboard.data.TabsRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Master ViewModel zarządzający wszystkimi zakładkami. Zastępuje wcześniejsze PasteViewModel
 * i PhraseExamplesViewModel — te ekrany teraz biorą state z odpowiedniej zakładki w
 * [TabsRepository].
 */
class TabsViewModel(
    private val tabs: TabsRepository,
    private val runner: ActionRunner,
) : ViewModel() {

    val tabsList: StateFlow<List<Tab>> = tabs.tabs
    val selectedId: StateFlow<TabId> = tabs.selectedId
    val selectedTab: StateFlow<Tab?> = combine(tabs.tabs, tabs.selectedId) { list, id ->
        list.firstOrNull { it.id == id }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val providerLabel: StateFlow<String> = runner.settings
        .map { "${it.provider.displayName} · ${it.activeModel}" }
        .stateIn(viewModelScope, SharingStarted.Eagerly, "")

    fun select(id: TabId) = tabs.select(id)
    fun close(id: TabId) = tabs.close(id)
    fun closeAllExceptPaste() = tabs.closeAllExceptPaste()

    // === Paste (Schowek) operations ===

    fun setPasteText(text: String) {
        tabs.updatePaste { it.copy(text = text) }
    }

    /**
     * Przyjmuje tekst od systemowego Share intent / Process Text. Przełącza na Schowek,
     * nadpisuje text + resetuje wynik. Pusty input ignorowany (no-op).
     */
    fun receiveSharedText(text: String) {
        val trimmed = text.trim()
        if (trimmed.isBlank()) return
        tabs.select(Tab.PASTE_ID)
        tabs.updatePaste { it.copy(text = trimmed, actionState = ActionState.Idle) }
    }

    /** Translate jako akcja inline w Schowku (NIE tworzy nowej zakładki). */
    fun translateInPaste() {
        val paste = tabs.tabs.value.firstOrNull { it is Tab.Paste } as? Tab.Paste ?: return
        val text = paste.text
        if (text.isBlank()) return
        // Dedup: ten sam tekst + Translate w Success → nic nie rób.
        val current = paste.actionState
        if (current is ActionState.Success && current.action == Action.TRANSLATE) return

        val job = viewModelScope.launch {
            runner.runStreaming(Action.TRANSLATE, text).collect { newState ->
                tabs.updatePaste { it.copy(actionState = newState) }
            }
        }
        tabs.putJob(Tab.PASTE_ID, job)
    }

    /** Wytłumacz tworzy NOWĄ zakładkę (snapshot) i streamuje wynik do jej state. */
    fun explainAsNewTab() {
        val paste = tabs.tabs.value.firstOrNull { it is Tab.Paste } as? Tab.Paste ?: return
        val text = paste.text
        if (text.isBlank()) return
        val tabId = tabs.openExplain(text, ActionState.Loading(Action.EXPLAIN_SENTENCE))
        val job = viewModelScope.launch {
            runner.runStreaming(Action.EXPLAIN_SENTENCE, text).collect { newState ->
                tabs.updateExplain(tabId) { it.copy(state = newState) }
            }
        }
        tabs.putJob(tabId, job)
    }

    fun clearPaste() {
        tabs.updatePaste { Tab.Paste() }
    }

    fun clearPasteResult() {
        tabs.updatePaste { it.copy(actionState = ActionState.Idle) }
    }

    // === Examples operations ===

    /** Otwiera nową zakładkę Examples dla danej frazy + auto-fetch pierwszego zestawu. */
    fun showExamplesAsNewTab(phrase: String, translation: String) {
        val tabId = tabs.openExamples(phrase, translation, ExamplesState.Loading())
        fetchExamples(tabId, phrase, variant = 0)
    }

    /** Pull-to-refresh / regenerate dla istniejącej zakładki Examples — bump variant counter. */
    fun regenerateExamples(tabId: TabId) {
        val tab = tabs.tabs.value.firstOrNull { it.id == tabId } as? Tab.Examples ?: return
        val nextVariant = tab.variant + 1
        tabs.updateExamples(tabId) { it.copy(variant = nextVariant, state = ExamplesState.Loading()) }
        fetchExamples(tabId, tab.phrase, nextVariant)
    }

    private fun fetchExamples(tabId: TabId, phrase: String, variant: Int) {
        val job = viewModelScope.launch {
            runner.runExamplesStreaming(phrase, variant).collect { newState ->
                tabs.updateExamples(tabId) { it.copy(state = newState) }
            }
        }
        tabs.putJob(tabId, job)
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as Application
                TabsViewModel(
                    tabs = TabsRepository.getInstance(),
                    runner = ActionRunner(
                        SettingsRepository(app),
                        PromptLoader(app),
                        LlmCache.getInstance(app),
                        LogStore.getInstance(app),
                    ),
                )
            }
        }
    }
}
