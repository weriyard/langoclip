package com.langoclip.app.data

import com.langoclip.app.actions.Action
import com.langoclip.app.actions.BreakdownItem
import com.langoclip.app.actions.Example
import com.langoclip.app.chat.ChatMessage
import com.langoclip.app.ui.ActionState
import com.langoclip.app.ui.ExamplesState
import com.langoclip.app.ui.SensesState
import com.langoclip.app.ui.WordTranslationState
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicLong

/**
 * Tab identifier. Long instead of UUID — used as Compose `key()` in the UI, smaller memory hit
 * than a String UUID.
 */
@JvmInline
value class TabId(val value: Long)

/**
 * State of a single tab. Each variant carries its own state, independent of the others.
 *
 * - [Paste] — the only persistent tab (`id == PASTE_ID`), no X, editable text field + inline
 *   result (Translate). Explain inside the Paste tab does NOT switch it to breakdown, it only
 *   creates a new [Explain] tab (snapshot).
 * - [Explain] — snapshot of an Explain result for a specific text. Streams in Loading, finally
 *   Success/Error. Title = first ~25 characters of the text.
 * - [Examples] — snapshot of 5 examples for a phrase. Pull-to-refresh / regenerate create a new
 *   variant in the same tab (variant counter is kept in state).
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
        val sensesState: SensesState = SensesState.Idle,
    ) : Tab {
        override val label: String = phrase
        override val isCloseable: Boolean = true
    }

    data class WordTranslation(
        override val id: TabId,
        val token: String,
        val sentence: String,
        val state: WordTranslationState,
    ) : Tab {
        override val label: String = token.take(LABEL_MAX)
        override val isCloseable: Boolean = true
    }

    /**
     * Tutor chat. Two flavours, distinguished by [isTutor]:
     *  - word mode (`isTutor == false`) — anchored to a specific sense of a word (header shows the
     *    word + EN/PL meaning, prompt is vocabulary-focused).
     *  - general mode (`isTutor == true`) — open conversation about English: grading sentences,
     *    idiomatic alternatives, nuances. [word]/[meaning*] are blank.
     *
     * [messages] is the full exchange; [streamingAssistant] is non-null while a response is being
     * streamed in (rendered as a final-position assistant bubble that grows). [input] is held here
     * so the field survives tab switches and process restarts the same way Paste.text does.
     */
    data class Chat(
        override val id: TabId,
        val word: String,
        val meaningEn: String,
        val meaningPl: String,
        val messages: List<ChatMessage>,
        val streamingAssistant: String? = null,
        val input: String = "",
        val error: String? = null,
        val isTutor: Boolean = false,
    ) : Tab {
        override val label: String = if (isTutor) "Korepetytor" else word.take(LABEL_MAX)
        override val isCloseable: Boolean = true
    }

    companion object {
        val PASTE_ID = TabId(0)
        private const val LABEL_MAX = 25
    }
}

/**
 * Singleton in-memory store for tabs. Operations:
 *  - [updatePaste] modifies the first tab (Paste tab).
 *  - [openExplain] / [openExamples] create a new tab and auto-switch to it.
 *  - [updateTab] updates a specific tab (e.g. streaming progress).
 *  - [close] closes a tab; if it was active, switches to the previous one (or the Paste tab).
 *  - [putJob] / [getJob] store the streaming Job per tab id — so closing a tab during streaming
 *    cancels the request.
 *
 * NOT persisted. App restart = empty state (only the Paste tab).
 */
class TabsRepository private constructor() {

    private val idGen = AtomicLong(1)  // 0 is reserved for the Paste tab

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
            id = id, phrase = phrase, translation = translation, variant = 0,
            state = initial, sensesState = SensesState.Loading(),
        )
        _tabs.update { it + tab }
        _selectedId.value = id
        return id
    }

    fun openWordTranslation(token: String, sentence: String, initial: WordTranslationState): TabId {
        val id = TabId(idGen.getAndIncrement())
        _tabs.update { it + Tab.WordTranslation(id = id, token = token, sentence = sentence, state = initial) }
        _selectedId.value = id
        return id
    }

    fun updateWordTranslation(id: TabId, transform: (Tab.WordTranslation) -> Tab.WordTranslation) {
        _tabs.update { list ->
            list.map { if (it is Tab.WordTranslation && it.id == id) transform(it) else it }
        }
    }

    fun openChat(
        word: String,
        meaningEn: String,
        meaningPl: String,
        initialMessages: List<ChatMessage>,
    ): TabId {
        val id = TabId(idGen.getAndIncrement())
        _tabs.update {
            it + Tab.Chat(
                id = id,
                word = word,
                meaningEn = meaningEn,
                meaningPl = meaningPl,
                messages = initialMessages,
            )
        }
        _selectedId.value = id
        return id
    }

    /** Opens a general English-tutor chat (not anchored to any word). Auto-switches to it. */
    fun openTutorChat(initialMessages: List<ChatMessage>): TabId {
        val id = TabId(idGen.getAndIncrement())
        _tabs.update {
            it + Tab.Chat(
                id = id,
                word = "",
                meaningEn = "",
                meaningPl = "",
                messages = initialMessages,
                isTutor = true,
            )
        }
        _selectedId.value = id
        return id
    }

    fun updateChat(id: TabId, transform: (Tab.Chat) -> Tab.Chat) {
        _tabs.update { list ->
            list.map { if (it is Tab.Chat && it.id == id) transform(it) else it }
        }
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
        if (id == Tab.PASTE_ID) return  // Paste tab has no X
        jobs.remove(id)?.cancel()
        val currentList = _tabs.value
        val idx = currentList.indexOfFirst { it.id == id }
        if (idx < 0) return
        val newList = currentList.filterIndexed { i, _ -> i != idx }
        _tabs.value = newList
        // If the closed tab was active, switch to the previous one or the Paste tab.
        if (_selectedId.value == id) {
            val fallback = newList.getOrNull(idx - 1)?.id ?: Tab.PASTE_ID
            _selectedId.value = fallback
        }
    }

    /** Closes all tabs except the Paste tab. Cancels all streaming jobs. */
    fun closeAllExceptPaste() {
        jobs.keys
            .filter { it != Tab.PASTE_ID }
            .forEach { jobs.remove(it)?.cancel() }
        _tabs.value = _tabs.value.filter { it.id == Tab.PASTE_ID }
        _selectedId.value = Tab.PASTE_ID
    }

    fun putJob(id: TabId, job: Job) {
        jobs[id]?.cancel()
        jobs[id] = job
    }

    /** Helper extension so update doesn't require copy(...) on the outside. */
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
