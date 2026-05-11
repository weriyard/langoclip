package com.floatingclipboard.ui

import com.floatingclipboard.actions.Action
import com.floatingclipboard.actions.ActionResult
import com.floatingclipboard.actions.BreakdownItem
import com.floatingclipboard.actions.Example
import com.floatingclipboard.actions.PhraseExamples

/** Stan akcji w Schowku (Translate) lub zakładce Explain. */
sealed interface ActionState {
    data object Idle : ActionState
    data class Loading(
        val action: Action,
        val partialText: String? = null,
        val partialBreakdown: List<BreakdownItem>? = null,
    ) : ActionState
    data class Success(val action: Action, val result: ActionResult) : ActionState
    data class Error(val action: Action, val message: String) : ActionState
}

/** Stan w zakładce Examples. */
sealed interface ExamplesState {
    data class Loading(val partial: List<Example> = emptyList()) : ExamplesState
    data class Success(val data: PhraseExamples, val variant: Int) : ExamplesState
    data class Error(val message: String) : ExamplesState
}
