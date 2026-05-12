package com.floatingclipboard.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.floatingclipboard.data.SettingsRepository
import com.floatingclipboard.download.DownloadProgress
import com.floatingclipboard.download.ModelDownloadManager
import com.floatingclipboard.download.ModelInfo
import com.floatingclipboard.download.TRANSLATION_MODELS
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ModelDownloadUiState(
    val model: ModelInfo,
    val progress: DownloadProgress,
    val isDownloaded: Boolean,
)

class ModelDownloadViewModel(app: Application) : AndroidViewModel(app) {

    private val manager = ModelDownloadManager(app)
    private val settingsRepo = SettingsRepository(app)

    val models: StateFlow<List<ModelDownloadUiState>> =
        combine(TRANSLATION_MODELS.map { model ->
            manager.progressFlow(model).map { progress ->
                ModelDownloadUiState(
                    model = model,
                    progress = progress,
                    isDownloaded = manager.isDownloaded(model),
                )
            }
        }) { it.toList() }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            TRANSLATION_MODELS.map {
                ModelDownloadUiState(it, DownloadProgress.Idle, manager.isDownloaded(it))
            },
        )

    fun download(model: ModelInfo) {
        viewModelScope.launch {
            val hfToken = settingsRepo.settings.first().huggingFaceToken.takeIf { it.isNotBlank() }
            manager.enqueue(model, hfToken)
        }
    }
    fun cancel(model: ModelInfo) = manager.cancel(model)

    fun delete(model: ModelInfo) {
        manager.cancel(model)
        manager.modelFile(model).delete()
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as Application
                ModelDownloadViewModel(app)
            }
        }
    }
}
