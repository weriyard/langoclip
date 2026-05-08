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
import com.floatingclipboard.actions.BreakdownItem
import com.floatingclipboard.actions.PromptLoader
import com.floatingclipboard.data.LlmCache
import com.floatingclipboard.data.SettingsRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface ActionState {
    data object Idle : ActionState
    data class Loading(
        val action: Action,
        val partialText: String? = null,
        val partialBreakdown: List<BreakdownItem>? = null,
    ) : ActionState
    data class Success(val action: Action, val result: ActionResult) : ActionState
    data class Error(val action: Action, val message: String) : ActionState
}

class PasteViewModel(private val runner: ActionRunner) : ViewModel() {

    private val _text = MutableStateFlow("")
    val text: StateFlow<String> = _text.asStateFlow()

    private val _state = MutableStateFlow<ActionState>(ActionState.Idle)
    val state: StateFlow<ActionState> = _state.asStateFlow()

    /** "Provider · Model" do wyświetlenia w UI — żeby było widać jaka backend obecnie obsługuje. */
    val providerLabel: StateFlow<String> = runner.settings
        .map { "${it.provider.displayName} · ${it.activeModel}" }
        .stateIn(viewModelScope, SharingStarted.Eagerly, "")

    // Para (akcja, tekst) ostatniego *uruchomienia* — nie ostatniego sukcesu, żeby retry po błędzie
    // też dedupował poprawnie po sprawdzeniu state.
    private var lastInput: Pair<Action, String>? = null
    private var currentJob: Job? = null

    fun setText(value: String) {
        _text.value = value
    }

    fun runAction(action: Action) {
        val currentText = _text.value
        if (currentText.isBlank()) return

        // Mamy już Success dla tej samej akcji + tekstu — nie pal tokenów ponownie.
        if (lastInput == (action to currentText) && _state.value is ActionState.Success) return

        currentJob?.cancel()
        lastInput = action to currentText
        currentJob = viewModelScope.launch {
            runner.runStreaming(action, currentText).collect { _state.value = it }
        }
    }

    fun clear() {
        currentJob?.cancel()
        lastInput = null
        _state.value = ActionState.Idle
    }

    /** Czyści również pole tekstu — pełny reset pod nowy prompt. Cache pozostaje nietknięty. */
    fun clearAll() {
        clear()
        _text.value = ""
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as Application
                PasteViewModel(
                    ActionRunner(
                        SettingsRepository(app),
                        PromptLoader(app),
                        LlmCache.getInstance(app),
                    )
                )
            }
        }
    }
}
