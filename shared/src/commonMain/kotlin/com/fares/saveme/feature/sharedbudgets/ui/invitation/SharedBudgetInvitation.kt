package com.fares.saveme.feature.sharedbudgets.ui.invitation

import androidx.lifecycle.viewModelScope
import com.fares.saveme.core.presentation.MviViewModel
import com.fares.saveme.feature.sharedbudgets.data.dto.SharedBudgetInvitationPreviewDto
import com.fares.saveme.feature.sharedbudgets.domain.ActionResult
import com.fares.saveme.feature.sharedbudgets.domain.JoinResult
import com.fares.saveme.feature.sharedbudgets.domain.PreviewResult
import com.fares.saveme.feature.sharedbudgets.domain.SharedBudgetsManager
import kotlinx.coroutines.launch

/** What the invitation screen shows. [Preview] is exactly what joining lets the person see (backend ADR-0027). */
sealed interface InvitationStage {
    data object Loading : InvitationStage
    data object SignedOut : InvitationStage
    data class Preview(val preview: SharedBudgetInvitationPreviewDto) : InvitationStage
    data object InvalidLink : InvitationStage
    data object Unavailable : InvitationStage
    data object Offline : InvitationStage
    data class TooMany(val minutes: Int) : InvitationStage
    data object PlanFull : InvitationStage
    data object Failed : InvitationStage
}

data class SharedBudgetInvitationState(val stage: InvitationStage = InvitationStage.Loading, val busy: Boolean = false)

sealed interface SharedBudgetInvitationIntent {
    data object Retry : SharedBudgetInvitationIntent
    data object JoinClicked : SharedBudgetInvitationIntent
    data object NotNowClicked : SharedBudgetInvitationIntent
    data object DeclineClicked : SharedBudgetInvitationIntent
    data object SignInClicked : SharedBudgetInvitationIntent
    data object RegisterClicked : SharedBudgetInvitationIntent
    data object Close : SharedBudgetInvitationIntent
}

sealed interface SharedBudgetInvitationEffect {
    data class OpenBudget(val budgetId: String) : SharedBudgetInvitationEffect
    data object NavigateSignIn : SharedBudgetInvitationEffect
    data object NavigateRegister : SharedBudgetInvitationEffect
    data object Close : SharedBudgetInvitationEffect
}

/**
 * A Shared Budget invitation opened from a link. The person sees the budget, who made it and how many are in it, and what
 * joining shows them, and only then chooses. The secret is never logged or shown (P-09.2); an empty [token] is a link that was
 * not valid and no call is made.
 */
class SharedBudgetInvitationViewModel(
    private val token: String,
    private val signedIn: () -> Boolean,
    private val manager: SharedBudgetsManager,
    private val onAnswered: () -> Unit = {},
) : MviViewModel<SharedBudgetInvitationState, SharedBudgetInvitationIntent, SharedBudgetInvitationEffect>(SharedBudgetInvitationState()) {

    init {
        load()
    }

    private fun load() {
        when {
            token.isEmpty() -> setStage(InvitationStage.InvalidLink)
            !signedIn() -> setStage(InvitationStage.SignedOut)
            else -> {
                setStage(InvitationStage.Loading)
                viewModelScope.launch {
                    setStage(
                        when (val result = manager.preview(token)) {
                            is PreviewResult.Ready -> InvitationStage.Preview(result.preview)
                            PreviewResult.Invalid -> InvitationStage.InvalidLink
                            PreviewResult.Unavailable -> InvitationStage.Unavailable
                            PreviewResult.Offline -> InvitationStage.Offline
                            is PreviewResult.TooMany -> InvitationStage.TooMany(result.minutes)
                            is PreviewResult.Failed -> InvitationStage.Failed
                        },
                    )
                }
            }
        }
    }

    private fun setStage(stage: InvitationStage) = setState { it.copy(stage = stage) }

    override fun onIntent(intent: SharedBudgetInvitationIntent) {
        when (intent) {
            SharedBudgetInvitationIntent.Retry -> load()
            SharedBudgetInvitationIntent.JoinClicked -> join()
            SharedBudgetInvitationIntent.DeclineClicked -> decline()
            SharedBudgetInvitationIntent.NotNowClicked, SharedBudgetInvitationIntent.Close -> sendEffect(SharedBudgetInvitationEffect.Close)
            SharedBudgetInvitationIntent.SignInClicked -> sendEffect(SharedBudgetInvitationEffect.NavigateSignIn)
            SharedBudgetInvitationIntent.RegisterClicked -> sendEffect(SharedBudgetInvitationEffect.NavigateRegister)
        }
    }

    private fun join() {
        if (currentState.busy || currentState.stage !is InvitationStage.Preview) return
        setState { it.copy(busy = true) }
        viewModelScope.launch {
            val result = manager.join(token)
            setState { it.copy(busy = false) }
            when (result) {
                is JoinResult.Joined -> {
                    onAnswered()
                    sendEffect(SharedBudgetInvitationEffect.OpenBudget(result.budgetId))
                }
                JoinResult.Invalid -> setStage(InvitationStage.InvalidLink)
                JoinResult.Unavailable -> setStage(InvitationStage.Unavailable)
                JoinResult.PlanFull -> setStage(InvitationStage.PlanFull)
                JoinResult.Offline -> setStage(InvitationStage.Offline)
                is JoinResult.TooMany -> setStage(InvitationStage.TooMany(result.minutes))
                is JoinResult.Failed -> setStage(InvitationStage.Failed)
            }
        }
    }

    private fun decline() {
        if (currentState.busy || currentState.stage !is InvitationStage.Preview) return
        setState { it.copy(busy = true) }
        viewModelScope.launch {
            val result = manager.decline(token)
            setState { it.copy(busy = false) }
            if (result == ActionResult.Done) {
                onAnswered()
                sendEffect(SharedBudgetInvitationEffect.Close)
            } else {
                setStage(if (result == ActionResult.Offline) InvitationStage.Offline else InvitationStage.Failed)
            }
        }
    }
}
