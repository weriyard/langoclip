package com.langoclip.app.ui

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.langoclip.app.data.saved.SavedPhrase
import com.langoclip.app.data.saved.SavedPhraseRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Backs the "Saved phrases" overlay screen — observes the notebook and handles deletes. */
class SavedPhrasesViewModel(
    private val repo: SavedPhraseRepository,
) : ViewModel() {

    val phrases: StateFlow<List<SavedPhrase>> = repo.phrases
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun delete(id: String) {
        viewModelScope.launch { repo.delete(id) }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as Application
                SavedPhrasesViewModel(SavedPhraseRepository.getInstance(app))
            }
        }
    }
}
