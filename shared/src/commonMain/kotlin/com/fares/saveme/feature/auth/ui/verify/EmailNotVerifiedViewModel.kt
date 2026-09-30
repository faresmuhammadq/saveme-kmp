package com.fares.saveme.feature.auth.ui.verify

import androidx.lifecycle.viewModelScope
import com.fares.saveme.core.presentation.MviViewModel
import com.fares.saveme.feature.auth.domain.model.ResendOutcome

/** Backs the sheet shown for any EMAIL_NOT_VERIFIED refusal (FR-014): resend only. */
class EmailNotVerifiedViewModel(
    private val resend: suspend () -> ResendOutcome,
) : MviViewModel<ResendState, EmailNotVerifiedIntent, Nothing>(ResendState()) {

    override fun onIntent(intent: EmailNotVerifiedIntent) {
        when (intent) {
            EmailNotVerifiedIntent.ResendClicked -> viewModelScope.launchResend(
                current = { currentState },
                apply = { change -> setState { reduceResend(it, change) } },
                resend = resend,
            )
        }
    }
}

sealed interface EmailNotVerifiedIntent {
    data object ResendClicked : EmailNotVerifiedIntent
}
