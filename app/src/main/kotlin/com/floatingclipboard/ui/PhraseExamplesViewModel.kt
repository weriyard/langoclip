package com.floatingclipboard.ui

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.floatingclipboard.actions.ActionRunner
import com.floatingclipboard.actions.PhraseExamples
import com.floatingclipboard.actions.PromptLoader
import com.floatingclipboard.data.LlmCache
import com.floatingclipboard.data.LogStore
import com.floatingclipboard.data.SettingsRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface ExamplesState {
    data object Loading : ExamplesState
    data class Success(val data: PhraseExamples, val variant: Int) : ExamplesState
    data class Error(val message: String) : ExamplesState
}

class PhraseExamplesViewModel(private val runner: ActionRunner) : ViewModel() {

    private val _state = MutableStateFlow<ExamplesState>(ExamplesState.Loading)
    val state: StateFlow<ExamplesState> = _state.asStateFlow()

    private var lastPhrase: String? = null
    // Najwyższy widziany variant per fraza — pozwala kontynuować numerację po wejściach.
    private val variantCounters = mutableMapOf<String, Int>()
    private var currentJob: Job? = null

    fun load(phrase: String) {
        // Już mamy dane dla tej frazy — nie wołamy ponownie (oszczędność tokenów).
        if (lastPhrase == phrase && _state.value is ExamplesState.Success) return
        lastPhrase = phrase
        fetchVariant(phrase, variantCounters[phrase] ?: 0)
    }

    /** Pull-to-refresh: nowy set przykładów. */
    fun regenerate() {
        val phrase = lastPhrase ?: return
        val next = (variantCounters[phrase] ?: 0) + 1
        variantCounters[phrase] = next
        fetchVariant(phrase, next)
    }

    private fun fetchVariant(phrase: String, variant: Int) {
        currentJob?.cancel()
        _state.value = ExamplesState.Loading
        currentJob = viewModelScope.launch {
            val result = runner.runExamples(phrase, variant)
            _state.value = result.fold(
                onSuccess = { ExamplesState.Success(it, variant) },
                onFailure = { ExamplesState.Error(it.message ?: "Nieznany błąd") },
            )
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as Application
                PhraseExamplesViewModel(
                    ActionRunner(
                        SettingsRepository(app),
                        PromptLoader(app),
                        LlmCache.getInstance(app),
                        LogStore.getInstance(app),
                    )
                )
            }
        }
    }
}
