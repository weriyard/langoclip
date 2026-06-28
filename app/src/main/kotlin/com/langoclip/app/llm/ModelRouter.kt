package com.langoclip.app.llm

import com.langoclip.app.data.Settings

object ModelRouter {
    fun modelFor(task: LlmTask, settings: Settings): String = when (task.tier) {
        ModelTier.FAST -> settings.provider.fastModel
        ModelTier.CAPABLE -> settings.activeModel
    }
}
