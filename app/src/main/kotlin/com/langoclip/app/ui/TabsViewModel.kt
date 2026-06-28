package com.langoclip.app.ui

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.langoclip.app.actions.Action
import com.langoclip.app.actions.ActionResult
import com.langoclip.app.actions.ActionRunner
import com.langoclip.app.actions.PromptLoader
import com.langoclip.app.chat.ChatMessage
import com.langoclip.app.data.LlmCache
import com.langoclip.app.data.LogStore
import com.langoclip.app.data.Provider
import com.langoclip.app.data.SettingsRepository
import com.langoclip.app.llm.ChatTurn
import com.langoclip.app.llm.LlmTask
import com.langoclip.app.llm.ModelRouter
import com.langoclip.app.llm.OpenRouterModelHint
import com.langoclip.app.llm.createLlmClient
import com.langoclip.app.data.Tab
import com.langoclip.app.data.Tab.Companion.PASTE_ID
import com.langoclip.app.data.TabId
import com.langoclip.app.data.TabsRepository
import com.langoclip.app.data.example.ExampleDatabase
import com.langoclip.app.data.lemma.LemmaDatabase
import com.langoclip.app.data.saved.SavedExample
import com.langoclip.app.data.saved.SavedPhraseRepository
import com.langoclip.app.data.translation.TranslationDatabase
import com.langoclip.app.actions.WordSense
import com.langoclip.app.local.NoopLocalModelClient
import com.langoclip.app.translation.Lemmatizer
import com.langoclip.app.translation.TranslationOrchestrator
import com.langoclip.app.translation.TranslationResult
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
    private val savedRepo: SavedPhraseRepository? = null,
) : ViewModel() {

    val tabsList: StateFlow<List<Tab>> = tabs.tabs
    val selectedId: StateFlow<TabId> = tabs.selectedId
    val selectedTab: StateFlow<Tab?> = combine(tabs.tabs, tabs.selectedId) { list, id ->
        list.firstOrNull { it.id == id }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val providerLabel: StateFlow<String> = combine(
        runner.settings,
        OpenRouterModelHint.trying,
        OpenRouterModelHint.current,
    ) { settings, trying, current ->
        val provider = settings.provider.displayName
        if (settings.provider != Provider.OPENROUTER) {
            return@combine "$provider · ${settings.activeModel}"
        }
        // Display compactly: drop the "vendor/" prefix and ":free" suffix that bloat the bar.
        val suffix = when {
            trying != null -> "↻ ${shortModel(trying.model)} (${trying.attempt}/${trying.total})"
            current != null -> shortModel(current)
            else -> shortModel(settings.activeModel)
        }
        "$provider · $suffix"
    }.stateIn(viewModelScope, SharingStarted.Eagerly, "")

    private fun shortModel(model: String): String =
        model.substringAfter('/').removeSuffix(":free")

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

    /**
     * Re-run an existing Explain tab in place. Reuses the tab's sourceText, switches state back
     * to Loading and streams a fresh result with whatever provider/settings are current — so
     * after the user flips e.g. "Tylko darmowe" off in Settings, this picks the new path up.
     */
    fun retryExplain(tabId: TabId) {
        val tab = tabs.tabs.value.firstOrNull { it.id == tabId } as? Tab.Explain ?: return
        val text = tab.sourceText
        if (text.isBlank()) return
        tabs.updateExplain(tabId) { it.copy(state = ActionState.Loading(Action.EXPLAIN_SENTENCE)) }
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

    // === Saved phrases (notebook) ===

    /**
     * Saves one word sense (meaning + its single example pair) to the notebook. [onResult] reports
     * whether a new row was actually written (false = blank or already saved), so the UI can show a
     * "saved" / "already saved" toast.
     */
    fun saveSense(phrase: String, sense: WordSense, onResult: (Boolean) -> Unit = {}) {
        val repo = savedRepo ?: return onResult(false)
        viewModelScope.launch {
            val saved = repo.save(
                phraseEn = phrase,
                phrasePl = sense.meaningTranslation,
                partOfSpeech = sense.partOfSpeech.label,
                note = sense.meaning,
                examples = if (sense.example.isNotBlank()) {
                    listOf(SavedExample(en = sense.example, pl = sense.exampleTranslation))
                } else emptyList(),
            )
            onResult(saved)
        }
    }

    /** Saves a single-word translation result (lemma + PL translation + EN/PL example pairs). */
    fun saveWord(token: String, result: TranslationResult, onResult: (Boolean) -> Unit = {}) {
        val repo = savedRepo ?: return onResult(false)
        viewModelScope.launch {
            // Zip EN/PL example lists positionally; fall back to whichever side exists.
            val examples = result.examplesEn.mapIndexed { i, en ->
                SavedExample(en = en, pl = result.examplesPl.getOrElse(i) { "" })
            }.ifEmpty {
                result.examplesPl.map { SavedExample(en = "", pl = it) }
            }
            val saved = repo.save(
                phraseEn = result.baseForm.ifBlank { result.lemma.ifBlank { token } },
                phrasePl = result.translation,
                partOfSpeech = result.partOfSpeech,
                note = result.definitionsPl.firstOrNull().orEmpty(),
                examples = examples.filter { it.en.isNotBlank() },
            )
            onResult(saved)
        }
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

    /**
     * Opens a general English-tutor chat — a free-form conversation window for grading sentences,
     * getting idiomatic alternatives and nuance explanations. Seeds a Polish greeting so the user
     * sees what they can do, then waits for input.
     */
    fun openTutorChat() {
        val greeting = ChatMessage(
            role = ChatMessage.Role.ASSISTANT,
            content = buildString {
                append("Cześć! Jestem Twoim korepetytorem angielskiego. 🇬🇧\n\n")
                append("Napisz zdanie, a sprawdzę je i zaproponuję bardziej naturalne wersje. ")
                append("Możesz też zapytać o różnicę między słowami, idiomy, rejestr (casual vs business) albo o cokolwiek z angielskiego.")
            },
        )
        tabs.openTutorChat(initialMessages = listOf(greeting))
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
            val systemPrompt = if (chat.isTutor) {
                TUTOR_SYSTEM_PROMPT
            } else {
                buildChatSystemPrompt(chat.word, chat.meaningEn, chat.meaningPl)
            }
            val turns = (chat.messages + userMsg).map { m ->
                ChatTurn(
                    role = if (m.role == ChatMessage.Role.USER) "user" else "assistant",
                    content = m.content,
                )
            }

            val builder = StringBuilder()
            val result = runCatching {
                // Chat follows the configured provider on the FAST tier (Gemini Flash, OpenRouter
                // Flash-Lite, …) — same routing as Translate, not a hardcoded model.
                val model = ModelRouter.modelFor(LlmTask.CHAT, settings)
                val client = createLlmClient(settings, model, logStore, LlmTask.CHAT.tier)
                client.streamChat(
                    systemPrompt = systemPrompt,
                    turns = turns,
                    onUsage = { u ->
                        logStore?.d("Chat", "TOKENS in=${u.inputTokens} out=${u.outputTokens}")
                    },
                ).collect { delta ->
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
        /**
         * System prompt for the general tutor chat. This is an INTERACTIVE English teacher, not a
         * translation engine: it corrects, explains and teaches. Hard rules on output language —
         * every English example carries a Polish translation, all commentary is in Polish.
         */
        private val TUTOR_SYSTEM_PROMPT = """
You are an elite, friendly English tutor and native speaker, teaching a Polish-speaking learner
INTERACTIVELY. You are a teacher, not just a translator — you correct, explain, give examples and
keep the learner practising.

## OUTPUT LANGUAGE — NON-NEGOTIABLE
- ALL explanatory text — corrections, explanations, comments, grammar notes, questions to the
  learner — MUST be in POLISH. Never explain in English.
- Every English example, sentence or phrase you give MUST be IMMEDIATELY followed by its Polish
  translation. Format: the English line, then its Polish translation directly beneath it (e.g. in
  italics or after "→"). Never leave an English sentence without a Polish translation.
- The ONLY English in your reply is the example material itself (words, phrases, sentences). Every
  other word is Polish.

## HOW TO TEACH
1. IDIOMATIC OVER DICTIONARY. Never map words 1:1 from Polish. If the learner uses a word that is
   dictionary-correct but stiff or unnatural in context (e.g. "compound" instead of "accumulate"
   for dust), flag it and explain — in Polish — why a native wouldn't say it.
2. EVERYDAY, NATURAL LANGUAGE. Give examples Americans/Brits actually use. When relevant, clearly
   separate *casual* from *business/formal* register (label them in Polish).
3. THE NATIVE-SPEAKER TEST. For every sentence ask: would a real person in the US/UK actually say
   this? If not, fix it and show the natural version (+ Polish translation).
4. EXPLAIN THE NUANCE. Don't just say "źle" — explain the connotation of the learner's choice
   (too academic, archaic, comical, wrong register…) in Polish.
5. CORRECT & COMPLETE. Fix grammar and clipped endings (e.g. "if you don't clean" →
   "if you don't clean up"). Briefly name the rule in Polish so the learner understands.
6. KEEP IT INTERACTIVE. End most replies with a short follow-up — a question, a mini-task, or an
   invitation to try a sentence — to keep the learner engaged. Stay encouraging.

Keep replies concise and concrete: bullet points and short paragraphs, not essays.
        """.trimIndent()

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
                        lemmatizer = Lemmatizer(lemmaDb, LogStore.getInstance(app)),
                        translationDao = translationDb.translationDao(),
                        settingsRepo = settingsRepo,
                        localModel = NoopLocalModelClient,
                        exampleDao = exampleDao,
                        logStore = LogStore.getInstance(app),
                    ),
                    logStore = LogStore.getInstance(app),
                    savedRepo = SavedPhraseRepository.getInstance(app),
                )
            }
        }
    }
}
