package com.floatingclipboard.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import com.floatingclipboard.BuildConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine

private val Context.settingsDataStore by preferencesDataStore(name = "settings")

enum class AppLocale(val tag: String) {
    SYSTEM(""),
    POLISH("pl"),
    ENGLISH("en");

    companion object {
        fun parse(value: String?): AppLocale =
            entries.firstOrNull { it.name == value?.uppercase() } ?: SYSTEM
    }
}

data class Settings(
    val provider: Provider,
    val geminiApiKey: String,
    val isUsingDefaultGeminiKey: Boolean,
    val geminiModel: String,
    val openAiApiKey: String,
    val isUsingDefaultOpenAiKey: Boolean,
    val openAiModel: String,
    val anthropicApiKey: String,
    val isUsingDefaultAnthropicKey: Boolean,
    val anthropicModel: String,
    val targetLanguage: String,
    val autoStartBubble: Boolean,
    val appLocale: AppLocale,
    val huggingFaceToken: String = "",
) {
    val activeApiKey: String
        get() = when (provider) {
            Provider.GEMINI -> geminiApiKey
            Provider.OPENAI -> openAiApiKey
            Provider.ANTHROPIC -> anthropicApiKey
        }

    val activeModel: String
        get() = when (provider) {
            Provider.GEMINI -> geminiModel
            Provider.OPENAI -> openAiModel
            Provider.ANTHROPIC -> anthropicModel
        }
}

class SettingsRepository(context: Context) {

    private val appContext = context.applicationContext

    private val secure by lazy {
        val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
        EncryptedSharedPreferences.create(
            SECURE_PREFS_NAME,
            masterKeyAlias,
            appContext,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    private val geminiApiOverride = MutableStateFlow(readSecureKey(KEY_GEMINI_API))
    private val openAiApiOverride = MutableStateFlow(readSecureKey(KEY_OPENAI_API))
    private val anthropicApiOverride = MutableStateFlow(readSecureKey(KEY_ANTHROPIC_API))
    private val hfTokenOverride = MutableStateFlow(readSecureKey(KEY_HF_TOKEN))

    private fun readSecureKey(key: String): String? =
        secure.getString(key, null)?.takeIf { it.isNotBlank() }

    val settings: Flow<Settings> = combine(
        geminiApiOverride.asStateFlow(),
        openAiApiOverride.asStateFlow(),
        anthropicApiOverride.asStateFlow(),
        hfTokenOverride.asStateFlow(),
        appContext.settingsDataStore.data,
    ) { geminiOverride, openAiOverride, anthropicOverride, hfToken, prefs ->
        Settings(
            provider = Provider.parse(prefs[PREF_PROVIDER]),
            geminiApiKey = geminiOverride ?: BuildConfig.DEFAULT_GEMINI_API_KEY,
            isUsingDefaultGeminiKey = geminiOverride == null,
            geminiModel = prefs[PREF_GEMINI_MODEL] ?: Provider.GEMINI.defaultModel,
            openAiApiKey = openAiOverride ?: BuildConfig.DEFAULT_OPENAI_API_KEY,
            isUsingDefaultOpenAiKey = openAiOverride == null,
            openAiModel = prefs[PREF_OPENAI_MODEL] ?: Provider.OPENAI.defaultModel,
            anthropicApiKey = anthropicOverride ?: BuildConfig.DEFAULT_ANTHROPIC_API_KEY,
            isUsingDefaultAnthropicKey = anthropicOverride == null,
            anthropicModel = prefs[PREF_ANTHROPIC_MODEL] ?: Provider.ANTHROPIC.defaultModel,
            targetLanguage = prefs[PREF_TARGET_LANG] ?: DEFAULT_TARGET_LANGUAGE,
            autoStartBubble = prefs[PREF_AUTO_START_BUBBLE] ?: true,
            appLocale = AppLocale.parse(prefs[PREF_APP_LOCALE]),
            huggingFaceToken = hfToken ?: "",
        )
    }

    suspend fun setProvider(provider: Provider) {
        appContext.settingsDataStore.edit { it[PREF_PROVIDER] = provider.name }
    }

    suspend fun setGeminiApiKey(key: String) = writeSecureKey(KEY_GEMINI_API, key, geminiApiOverride)
    suspend fun setOpenAiApiKey(key: String) = writeSecureKey(KEY_OPENAI_API, key, openAiApiOverride)
    suspend fun setAnthropicApiKey(key: String) = writeSecureKey(KEY_ANTHROPIC_API, key, anthropicApiOverride)
    suspend fun setHuggingFaceToken(token: String) = writeSecureKey(KEY_HF_TOKEN, token, hfTokenOverride)

    private fun writeSecureKey(prefKey: String, value: String, flow: MutableStateFlow<String?>) {
        val trimmed = value.trim()
        if (trimmed.isBlank()) {
            secure.edit().remove(prefKey).apply()
            flow.value = null
        } else {
            secure.edit().putString(prefKey, trimmed).apply()
            flow.value = trimmed
        }
    }

    suspend fun setGeminiModel(model: String) {
        appContext.settingsDataStore.edit { it[PREF_GEMINI_MODEL] = model }
    }

    suspend fun setOpenAiModel(model: String) {
        appContext.settingsDataStore.edit { it[PREF_OPENAI_MODEL] = model }
    }

    suspend fun setAnthropicModel(model: String) {
        appContext.settingsDataStore.edit { it[PREF_ANTHROPIC_MODEL] = model }
    }

    suspend fun setTargetLanguage(lang: String) {
        appContext.settingsDataStore.edit { it[PREF_TARGET_LANG] = lang }
    }

    suspend fun setAutoStartBubble(enabled: Boolean) {
        appContext.settingsDataStore.edit { it[PREF_AUTO_START_BUBBLE] = enabled }
    }

    suspend fun setAppLocale(locale: AppLocale) {
        appContext.settingsDataStore.edit { it[PREF_APP_LOCALE] = locale.name }
    }

    companion object {
        const val DEFAULT_TARGET_LANGUAGE = "polski"

        private const val SECURE_PREFS_NAME = "secure_prefs"
        private const val KEY_GEMINI_API = "gemini_api_key"
        private const val KEY_OPENAI_API = "openai_api_key"
        private const val KEY_ANTHROPIC_API = "anthropic_api_key"
        private const val KEY_HF_TOKEN = "hf_token"
        private val PREF_PROVIDER = stringPreferencesKey("provider")
        private val PREF_GEMINI_MODEL = stringPreferencesKey("model")  // legacy name, was Gemini-only
        private val PREF_OPENAI_MODEL = stringPreferencesKey("openai_model")
        private val PREF_ANTHROPIC_MODEL = stringPreferencesKey("anthropic_model")
        private val PREF_TARGET_LANG = stringPreferencesKey("target_language")
        private val PREF_AUTO_START_BUBBLE = booleanPreferencesKey("auto_start_bubble")
        private val PREF_APP_LOCALE = stringPreferencesKey("app_locale")
    }
}
