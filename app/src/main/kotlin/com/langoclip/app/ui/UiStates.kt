package com.langoclip.app.ui

import com.langoclip.app.actions.Action
import com.langoclip.app.actions.ActionResult
import com.langoclip.app.actions.BreakdownItem
import com.langoclip.app.actions.Example
import com.langoclip.app.actions.PhraseExamples
import com.langoclip.app.actions.WordSense

/** Action state in the Paste tab (Translate) or the Explain tab. */
sealed interface ActionState {
    data object Idle : ActionState
    data class Loading(
        val action: Action,
        val partialText: String? = null,
        val partialBreakdown: List<BreakdownItem>? = null,
        val partialFullTranslation: String? = null,
    ) : ActionState
    data class Success(val action: Action, val result: ActionResult) : ActionState
    data class Error(val action: Action, val message: String) : ActionState
}

/** State of the word-senses section in the Examples tab. */
sealed interface SensesState {
    data object Idle : SensesState
    data class Loading(val partial: List<WordSense> = emptyList(), val baseForm: String? = null) : SensesState
    data class Success(val senses: List<WordSense>, val baseForm: String) : SensesState
    data class Error(val message: String) : SensesState
}

/** State in the Examples tab. */
sealed interface ExamplesState {
    data class Loading(val partial: List<Example> = emptyList()) : ExamplesState
    data class Success(val data: PhraseExamples, val variant: Int) : ExamplesState
    data class Error(val message: String) : ExamplesState
}

/** State for the WordTranslation tab (single-word orchestrator lookup). */
sealed interface WordTranslationState {
    data object Loading : WordTranslationState
    data class Success(val result: com.langoclip.app.translation.TranslationResult) : WordTranslationState
    data class Error(val message: String) : WordTranslationState
}
