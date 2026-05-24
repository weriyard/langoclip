package com.floatingclipboard

import android.app.Application
import com.floatingclipboard.data.LocaleManager
import com.floatingclipboard.data.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class FloatingClipboardApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Read persisted locale preference and apply BEFORE any Activity inflates a layout.
        CoroutineScope(Dispatchers.Main).launch {
            val settings = SettingsRepository.getInstance(this@FloatingClipboardApp).settings.first()
            LocaleManager.apply(settings.appLocale)
        }
    }
}
