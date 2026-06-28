package com.langoclip.app.actions

import android.content.Context

/**
 * Loads system prompts from .md files in `assets/prompts/`. Each prompt is cached after the first
 * read (files are small and don't change at runtime). Templating: `{var}` in the body is replaced
 * with values from the map in [render].
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
