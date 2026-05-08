package com.floatingclipboard.actions

import android.content.Context

/**
 * Ładuje system prompty z plików .md w `assets/prompts/`. Każdy prompt jest cachowany po pierwszym
 * odczycie (pliki są małe i się nie zmieniają w runtime). Templating: `{var}` w treści jest
 * podmieniane przez wartości z mapy w [render].
 */
class PromptLoader(context: Context) {

    private val appContext = context.applicationContext
    private val cache = mutableMapOf<String, String>()

    fun load(fileName: String): String = cache.getOrPut(fileName) {
        appContext.assets.open("prompts/$fileName").bufferedReader().use { it.readText() }
    }

    fun render(fileName: String, vars: Map<String, String>): String =
        vars.entries.fold(load(fileName)) { acc, (k, v) -> acc.replace("{$k}", v) }
}
