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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface ExamplesState {
    data object Loading : ExamplesState
    data class Success(val data: PhraseExamples) : ExamplesState
    data class Error(val message: String) : ExamplesState
}

class PhraseExamplesViewModel(private val runner: ActionRunner) : ViewModel() {

    private val _state = MutableStateFlow<ExamplesState>(ExamplesState.Loading)
    val state: StateFlow<ExamplesState> = _state.asStateFlow()

    private var lastPhrase: String? = null

    fun load(phrase: String) {
        // Już załadowane dla tej frazy — nie wołamy ponownie (oszczędność tokenów).
        if (lastPhrase == phrase && _state.value is ExamplesState.Success) return
        lastPhrase = phrase
        _state.value = ExamplesState.Loading
        viewModelScope.launch {
            val result = runner.runExamples(phrase)
            _state.value = result.fold(
                onSuccess = { ExamplesState.Success(it) },
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
