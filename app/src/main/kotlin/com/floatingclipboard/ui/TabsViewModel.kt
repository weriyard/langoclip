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
import com.floatingclipboard.data.lemma.LemmaDatabase
import com.floatingclipboard.data.translation.TranslationDatabase
import com.floatingclipboard.translation.Lemmatizer
import com.floatingclipboard.translation.TranslationOrchestrator
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Master ViewModel managing all tabs. Replaces the earlier PasteViewModel and
 * PhraseExamplesViewModel — those screens now read state from the corresponding tab in
 * [TabsRepository].
 */
class TabsViewModel(
    private val tabs: TabsRepository,
    private val runner: ActionRunner,
    private val orchestrator: TranslationOrchestrator? = null,
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

    // === Paste tab operations ===

    fun setPasteText(text: String) {
        tabs.updatePaste { it.copy(text = text) }
    }

    /**
     * Accepts text from the system Share intent / Process Text. Switches to the Paste tab,
     * overwrites the text + resets the result. Empty input is ignored (no-op).
     */
    fun receiveSharedText(text: String) {
        val trimmed = text.trim()
        if (trimmed.isBlank()) return
        tabs.select(Tab.PASTE_ID)
        tabs.updatePaste { it.copy(text = trimmed, actionState = ActionState.Idle) }
    }

    /** Translate as an inline action in the Paste tab (does NOT create a new tab). */
    fun translateInPaste() {
        val paste = tabs.tabs.value.firstOrNull { it is Tab.Paste } as? Tab.Paste ?: return
        val text = paste.text
        if (text.isBlank()) return
        // Dedup: same text + Translate in Success → do nothing.
        val current = paste.actionState
        if (current is ActionState.Success && current.action == Action.TRANSLATE) return

        val job = viewModelScope.launch {
            runner.runStreaming(Action.TRANSLATE, text).collect { newState ->
                tabs.updatePaste { it.copy(actionState = newState) }
            }
        }
        tabs.putJob(Tab.PASTE_ID, job)
    }

    /** Explain creates a NEW tab (snapshot) and streams the result into its state. */
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

    /** Opens a new Examples tab for the given phrase + auto-fetches examples and senses. */
    fun showExamplesAsNewTab(phrase: String, translation: String) {
        val tabId = tabs.openExamples(phrase, translation, ExamplesState.Loading())
        fetchExamples(tabId, phrase, variant = 0)
        fetchSenses(tabId, phrase, translation)
    }

    /** Pull-to-refresh / regenerate for an existing Examples tab — bumps the variant counter. */
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

    /** Translates a single selected word using TranslationOrchestrator. Opens a new WordTranslation tab. */
    fun translateWord(token: String, sentence: String) {
        val orc = orchestrator ?: return
        val trimmed = token.trim()
        if (trimmed.isBlank()) return
        val tabId = tabs.openWordTranslation(trimmed, sentence, WordTranslationState.Loading)
        val job = viewModelScope.launch {
            runCatching { orc.translate(trimmed, sentence) }
                .onSuccess { result ->
                    tabs.updateWordTranslation(tabId) { it.copy(state = WordTranslationState.Success(result)) }
                }
                .onFailure { e ->
                    tabs.updateWordTranslation(tabId) { it.copy(state = WordTranslationState.Error(e.message ?: "Error")) }
                }
        }
        tabs.putJob(tabId, job)
    }

    private fun fetchSenses(tabId: TabId, phrase: String, context: String) {
        viewModelScope.launch {
            runner.runSensesStreaming(phrase, context).collect { newState ->
                tabs.updateExamples(tabId) { it.copy(sensesState = newState) }
            }
        }
        // Not stored in jobs — senses aren't cancelled on swipe-to-refresh
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as Application
                val settingsRepo = SettingsRepository(app)
                val lemmaDb = LemmaDatabase.getOptional(app)
                val translationDb = TranslationDatabase.getInstance(app)
                TabsViewModel(
                    tabs = TabsRepository.getInstance(),
                    runner = ActionRunner(
                        settingsRepo,
                        PromptLoader(app),
                        LlmCache.getInstance(app),
                        LogStore.getInstance(app),
                    ),
                    orchestrator = TranslationOrchestrator(
                        lemmatizer = Lemmatizer(lemmaDb),
                        translationDao = translationDb.translationDao(),
                        settingsRepo = settingsRepo,
                    ),
                )
            }
        }
    }
}
