package com.langoclip.app.data

import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat

/**
 * Applies a per-app locale override. On Android 13+ this is hardware-backed by the system;
 * on older versions AppCompatDelegate handles configuration overrides itself. Calling with
 * [AppLocale.SYSTEM] clears the override so the OS-level locale takes over again.
 */
object LocaleManager {
    fun apply(locale: AppLocale) {
        val list = if (locale == AppLocale.SYSTEM) {
            LocaleListCompat.getEmptyLocaleList()
        } else {
            LocaleListCompat.forLanguageTags(locale.tag)
        }
        AppCompatDelegate.setApplicationLocales(list)
    }
}
