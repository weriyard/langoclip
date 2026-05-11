package com.floatingclipboard.ui

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.floatingclipboard.data.Provider
import com.floatingclipboard.data.Settings
import com.floatingclipboard.data.SettingsRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(private val repo: SettingsRepository) : ViewModel() {

    val state: StateFlow<Settings> = repo.settings.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = Settings(
            provider = Provider.GEMINI,
            geminiApiKey = "",
            isUsingDefaultGeminiKey = true,
            geminiModel = Provider.GEMINI.defaultModel,
            openAiApiKey = "",
            isUsingDefaultOpenAiKey = true,
            openAiModel = Provider.OPENAI.defaultModel,
            anthropicApiKey = "",
            isUsingDefaultAnthropicKey = true,
            anthropicModel = Provider.ANTHROPIC.defaultModel,
            targetLanguage = SettingsRepository.DEFAULT_TARGET_LANGUAGE,
            autoStartBubble = true,
        ),
    )

    fun save(
        provider: Provider,
        geminiApiKey: String,
        openAiApiKey: String,
        anthropicApiKey: String,
        geminiModel: String,
        openAiModel: String,
        anthropicModel: String,
        targetLanguage: String,
    ) {
        viewModelScope.launch {
            val current = state.value
            if (provider != current.provider) repo.setProvider(provider)
            if (geminiApiKey != current.geminiApiKey ||
                (geminiApiKey.isBlank() && !current.isUsingDefaultGeminiKey)
            ) repo.setGeminiApiKey(geminiApiKey)
            if (openAiApiKey != current.openAiApiKey ||
                (openAiApiKey.isBlank() && !current.isUsingDefaultOpenAiKey)
            ) repo.setOpenAiApiKey(openAiApiKey)
            if (anthropicApiKey != current.anthropicApiKey ||
                (anthropicApiKey.isBlank() && !current.isUsingDefaultAnthropicKey)
            ) repo.setAnthropicApiKey(anthropicApiKey)
            if (geminiModel != current.geminiModel) repo.setGeminiModel(geminiModel)
            if (openAiModel != current.openAiModel) repo.setOpenAiModel(openAiModel)
            if (anthropicModel != current.anthropicModel) repo.setAnthropicModel(anthropicModel)
            if (targetLanguage != current.targetLanguage) repo.setTargetLanguage(targetLanguage)
        }
    }

    fun resetGeminiApiKey() {
        viewModelScope.launch { repo.setGeminiApiKey("") }
    }

    fun resetOpenAiApiKey() {
        viewModelScope.launch { repo.setOpenAiApiKey("") }
    }

    fun resetAnthropicApiKey() {
        viewModelScope.launch { repo.setAnthropicApiKey("") }
    }

    fun setAutoStartBubble(enabled: Boolean) {
        viewModelScope.launch { repo.setAutoStartBubble(enabled) }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as Application
                SettingsViewModel(SettingsRepository(app))
            }
        }
    }
}
