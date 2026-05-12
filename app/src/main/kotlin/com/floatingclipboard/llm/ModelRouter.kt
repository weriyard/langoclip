package com.floatingclipboard.llm

import com.floatingclipboard.data.Settings

object ModelRouter {
    fun modelFor(task: LlmTask, settings: Settings): String = when (task.tier) {
        ModelTier.FAST -> settings.provider.fastModel
        ModelTier.CAPABLE -> settings.activeModel
    }
}
