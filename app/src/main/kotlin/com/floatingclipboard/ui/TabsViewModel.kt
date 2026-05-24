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
import com.floatingclipboard.chat.ChatMessage
import com.floatingclipboard.data.LlmCache
import com.floatingclipboard.data.LogStore
import com.floatingclipboard.data.SettingsRepository
import com.floatingclipboard.llm.AnthropicClient
import com.floatingclipboard.llm.ChatTurn
import com.floatingclipboard.data.Tab
import com.floatingclipboard.data.Tab.Companion.PASTE_ID
import com.floatingclipboard.data.TabId
import com.floatingclipboard.data.TabsRepository
import com.floatingclipboard.data.example.ExampleDatabase
import com.floatingclipboard.data.lemma.LemmaDatabase
import com.floatingclipboard.data.translation.TranslationDatabase
import com.floatingclipboard.local.NoopLocalModelClient
import com.floatingclipboard.translation.Lemmatizer
import com.floatingclipboard.translation.TranslationOrchestrator
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
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
    private val logStore: LogStore? = null,
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
        logStore?.d("TabsViewModel", "translateWord called: '$token'")
        val orc = orchestrator ?: run {
            logStore?.e("TabsViewModel", "translateWord: orchestrator is null!")
            return
        }
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

    // === Chat operations ===

    /**
     * Opens a vocabulary-tutor chat tab anchored to one sense of a word. Seeds the conversation
     * with a Polish greeting so the user sees what they can ask, then waits for their input.
     */
    fun openChatForWord(word: String, meaningEn: String, meaningPl: String) {
        val cleanWord = word.trim()
        if (cleanWord.isBlank()) return
        val greeting = ChatMessage(
            role = ChatMessage.Role.ASSISTANT,
            content = buildString {
                append("Pomogę Ci lepiej zrozumieć słowo **").append(cleanWord).append("** ")
                if (meaningEn.isNotBlank()) append("w znaczeniu „").append(meaningEn).append("\" ")
                if (meaningPl.isNotBlank()) append("(").append(meaningPl).append(") ")
                append("\n\nSpytaj o synonimy, kolokacje, idiomy, przykłady w różnych kontekstach albo o cokolwiek związanego z tym słowem.")
            },
        )
        tabs.openChat(
            word = cleanWord,
            meaningEn = meaningEn,
            meaningPl = meaningPl,
            initialMessages = listOf(greeting),
        )
    }

    fun setChatInput(tabId: TabId, text: String) {
        tabs.updateChat(tabId) { it.copy(input = text) }
    }

    /**
     * Appends the current input as a user turn and streams the assistant reply. Anthropic is
     * always used (Haiku) regardless of the configured provider, to keep chat latency/cost
     * predictable and aligned with the rest of the tutor pipeline.
     */
    fun sendChatMessage(tabId: TabId) {
        val chat = tabs.tabs.value.firstOrNull { it.id == tabId } as? Tab.Chat ?: return
        val text = chat.input.trim()
        if (text.isBlank() || chat.streamingAssistant != null) return

        val userMsg = ChatMessage(ChatMessage.Role.USER, text)
        tabs.updateChat(tabId) {
            it.copy(
                messages = it.messages + userMsg,
                input = "",
                streamingAssistant = "",
                error = null,
            )
        }

        val job = viewModelScope.launch {
            val settings = runner.settings.first()
            val systemPrompt = buildChatSystemPrompt(chat.word, chat.meaningEn, chat.meaningPl)
            val turns = (chat.messages + userMsg).map { m ->
                ChatTurn(
                    role = if (m.role == ChatMessage.Role.USER) "user" else "assistant",
                    content = m.content,
                )
            }

            val builder = StringBuilder()
            val result = runCatching {
                val client = AnthropicClient(settings.anthropicApiKey, CHAT_MODEL)
                client.streamChat(systemPrompt, turns).collect { delta ->
                    builder.append(delta)
                    tabs.updateChat(tabId) { it.copy(streamingAssistant = builder.toString()) }
                }
            }
            tabs.updateChat(tabId) { cur ->
                val finalText = builder.toString()
                val withAssistant =
                    if (finalText.isNotBlank()) {
                        cur.messages + ChatMessage(ChatMessage.Role.ASSISTANT, finalText)
                    } else cur.messages
                cur.copy(
                    messages = withAssistant,
                    streamingAssistant = null,
                    error = result.exceptionOrNull()?.message,
                )
            }
        }
        tabs.putJob(tabId, job)
    }

    private fun buildChatSystemPrompt(word: String, meaningEn: String, meaningPl: String): String =
        """
You are an English tutor helping a Polish-speaking learner explore vocabulary in depth.

The user is studying the English word: "$word"
${if (meaningEn.isNotBlank()) "Sense (EN): \"$meaningEn\"" else ""}
${if (meaningPl.isNotBlank()) "Polish translation: \"$meaningPl\"" else ""}

Help the user understand this word in depth. They may ask for more example sentences, related expressions, common collocations, usage notes, register, etymology, or synonyms/antonyms. If the user writes in Polish, reply in Polish — but always show English usage examples with Polish translations directly beneath them. Keep replies concise and concrete; prefer bullet points and short paragraphs over long prose.
        """.trimIndent()

    companion object {
        private const val CHAT_MODEL = "claude-haiku-4-5-20251001"

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as Application
                val settingsRepo = SettingsRepository.getInstance(app)
                val lemmaDb = LemmaDatabase.getOptional(app)
                val translationDb = TranslationDatabase.getInstance(app)
                val exampleDao = ExampleDatabase.getOptional(app)?.exampleDao()
                TabsViewModel(
                    tabs = TabsRepository.getInstance(),
                    runner = ActionRunner(
                        settingsRepo,
                        PromptLoader(app),
                        LlmCache.getInstance(app),
                        LogStore.getInstance(app),
                        localModel = NoopLocalModelClient,
                        exampleDao = exampleDao,
                    ),
                    orchestrator = TranslationOrchestrator(
                        lemmatizer = Lemmatizer(lemmaDb),
                        translationDao = translationDb.translationDao(),
                        settingsRepo = settingsRepo,
                        localModel = NoopLocalModelClient,
                        exampleDao = exampleDao,
                        logStore = LogStore.getInstance(app),
                    ),
                    logStore = LogStore.getInstance(app),
                )
            }
        }
    }
}
