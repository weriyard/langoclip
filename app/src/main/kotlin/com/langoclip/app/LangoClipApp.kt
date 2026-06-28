package com.langoclip.app

import android.app.Application
import com.langoclip.app.data.LocaleManager
import com.langoclip.app.data.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class LangoClipApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Read persisted locale preference and apply BEFORE any Activity inflates a layout.
        CoroutineScope(Dispatchers.Main).launch {
            val settings = SettingsRepository.getInstance(this@LangoClipApp).settings.first()
            LocaleManager.apply(settings.appLocale)
        }
    }
}
