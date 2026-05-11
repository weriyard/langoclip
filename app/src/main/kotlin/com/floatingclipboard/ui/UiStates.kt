package com.floatingclipboard.ui

import com.floatingclipboard.actions.Action
import com.floatingclipboard.actions.ActionResult
import com.floatingclipboard.actions.BreakdownItem
import com.floatingclipboard.actions.Example
import com.floatingclipboard.actions.PhraseExamples

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

/** State in the Examples tab. */
sealed interface ExamplesState {
    data class Loading(val partial: List<Example> = emptyList()) : ExamplesState
    data class Success(val data: PhraseExamples, val variant: Int) : ExamplesState
    data class Error(val message: String) : ExamplesState
}
