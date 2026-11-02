package com.fares.saveme.feature.sharedbudgets.ui.detail

import androidx.lifecycle.viewModelScope
import com.fares.saveme.core.presentation.MviViewModel
import com.fares.saveme.feature.sharedbudgets.domain.ActionResult
import com.fares.saveme.feature.sharedbudgets.domain.InviteResult
import com.fares.saveme.feature.sharedbudgets.domain.SharedBudgetsManager
import com.fares.saveme.feature.sharedbudgets.domain.model.SharedBudgetProgress
import com.fares.saveme.feature.sharedbudgets.ui.editor.EditorMessage
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

/** A step that asks first, because it cannot be taken back. */
sealed interface DetailConfirm {
    data object Leave : DetailConfirm
    data class Remove(val userId: String, val name: String?) : DetailConfirm
    data class Transfer(val userId: String, val name: String?) : DetailConfirm
}

data class SharedBudgetDetailState(
    val progress: SharedBudgetProgress? = null,
    val loaded: Boolean = false,
    val confirm: DetailConfirm? = null,
    val busy: Boolean = false,
    val refreshing: Boolean = false,
    /** 0 is the current period; each step back shows the period before. */
    val periodsBack: Int = 0,
) {
    val isOwner: Boolean get() = progress?.isOwner == true
}

sealed interface SharedBudgetDetailIntent {
    data object Back : SharedBudgetDetailIntent
    data object Opened : SharedBudgetDetailIntent
    data object Refresh : SharedBudgetDetailIntent
    data object EarlierClicked : SharedBudgetDetailIntent
    data object LaterClicked : SharedBudgetDetailIntent
    data object EditClicked : SharedBudgetDetailIntent
    data object InviteClicked : SharedBudgetDetailIntent
    data object LeaveClicked : SharedBudgetDetailIntent
    data class RemoveClicked(val userId: String) : SharedBudgetDetailIntent
    data class TransferClicked(val userId: String) : SharedBudgetDetailIntent
    data object ConfirmYes : SharedBudgetDetailIntent
    data object ConfirmNo : SharedBudgetDetailIntent
}

sealed interface SharedBudgetDetailEffect {
    data object NavigateBack : SharedBudgetDetailEffect
    data class OpenEditor(val id: String) : SharedBudgetDetailEffect
    data object RequireVerification : SharedBudgetDetailEffect

    /** The one-time link of a new invitation: straight to the platform's share sheet, never stored, logged or shown (P-09.2). */
    class ShareLink(val url: String) : SharedBudgetDetailEffect {
        override fun toString() = "ShareLink(url=<redacted>)"
    }
    data class ShowMessage(val message: EditorMessage) : SharedBudgetDetailEffect

    /** An invitation was made but the server gave no link (a replay): the person asks again. */
    data object LinkUnavailable : SharedBudgetDetailEffect
}

/**
 * One Shared Budget: overall and per-category progress, who spent what, and when the phone last heard from the server. Members
 * can leave; the owner invites, removes people and hands the budget over. A budget that leaves the phone (the person left or
 * was removed) closes the screen.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SharedBudgetDetailViewModel(
    private val budgetId: String,
    /** The budget's figures for the period [it] periods back (0 = the current one). */
    detail: (Int) -> Flow<SharedBudgetProgress?>,
    private val manager: SharedBudgetsManager,
) : MviViewModel<SharedBudgetDetailState, SharedBudgetDetailIntent, SharedBudgetDetailEffect>(SharedBudgetDetailState()) {
    private val back = MutableStateFlow(0)

    init {
        back.flatMapLatest { detail(it) }.onEach { progress ->
            if (progress == null && currentState.loaded) {
                sendEffect(SharedBudgetDetailEffect.NavigateBack)
            } else {
                setState { it.copy(progress = progress, loaded = true) }
            }
        }.launchIn(viewModelScope)
    }

    override fun onIntent(intent: SharedBudgetDetailIntent) {
        when (intent) {
            SharedBudgetDetailIntent.Back -> sendEffect(SharedBudgetDetailEffect.NavigateBack)
            SharedBudgetDetailIntent.Opened -> read(force = false, spinner = false)
            SharedBudgetDetailIntent.Refresh -> read(force = true, spinner = true)
            SharedBudgetDetailIntent.EarlierClicked -> if (currentState.progress?.canGoBack == true) step(+1)
            SharedBudgetDetailIntent.LaterClicked -> if (currentState.periodsBack > 0) step(-1)
            SharedBudgetDetailIntent.EditClicked -> if (currentState.progress != null) sendEffect(SharedBudgetDetailEffect.OpenEditor(budgetId))
            SharedBudgetDetailIntent.InviteClicked -> if (currentState.isOwner) invite()
            SharedBudgetDetailIntent.LeaveClicked -> if (currentState.progress?.isOwner == false) setState { it.copy(confirm = DetailConfirm.Leave) }
            is SharedBudgetDetailIntent.RemoveClicked -> member(intent.userId)?.let { m -> if (currentState.isOwner && !m.isMe) setState { it.copy(confirm = DetailConfirm.Remove(m.userId, m.name)) } }
            is SharedBudgetDetailIntent.TransferClicked -> member(intent.userId)?.let { m -> if (currentState.isOwner && !m.isMe) setState { it.copy(confirm = DetailConfirm.Transfer(m.userId, m.name)) } }
            SharedBudgetDetailIntent.ConfirmNo -> if (!currentState.busy) setState { it.copy(confirm = null) }
            SharedBudgetDetailIntent.ConfirmYes -> confirmed()
        }
    }

    private fun step(by: Int) {
        val next = (currentState.periodsBack + by).coerceAtLeast(0)
        setState { it.copy(periodsBack = next) }
        back.value = next
    }

    private fun member(userId: String) = currentState.progress?.members?.firstOrNull { it.userId == userId && it.isMember }

    private fun read(force: Boolean, spinner: Boolean) {
        if (currentState.refreshing) return
        if (spinner) setState { it.copy(refreshing = true) }
        viewModelScope.launch {
            manager.refresh(budgetId, force)
            setState { it.copy(refreshing = false) }
        }
    }

    private fun invite() {
        if (currentState.busy) return
        setState { it.copy(busy = true) }
        viewModelScope.launch {
            val result = manager.invite(budgetId)
            setState { it.copy(busy = false) }
            when (result) {
                is InviteResult.Created -> sendEffect(result.link?.let { SharedBudgetDetailEffect.ShareLink(it) } ?: SharedBudgetDetailEffect.LinkUnavailable)
                InviteResult.NotVerified -> sendEffect(SharedBudgetDetailEffect.RequireVerification)
                InviteResult.Offline -> sendEffect(SharedBudgetDetailEffect.ShowMessage(EditorMessage.Offline))
                InviteResult.NotAllowed -> sendEffect(SharedBudgetDetailEffect.ShowMessage(EditorMessage.NotAllowed))
                InviteResult.PlanFull -> sendEffect(SharedBudgetDetailEffect.ShowMessage(EditorMessage.PlanFull))
                is InviteResult.TooMany, is InviteResult.Failed -> sendEffect(SharedBudgetDetailEffect.ShowMessage(EditorMessage.Failed))
            }
        }
    }

    private fun confirmed() {
        val step = currentState.confirm ?: return
        if (currentState.busy) return
        setState { it.copy(busy = true) }
        viewModelScope.launch {
            val result = when (step) {
                DetailConfirm.Leave -> manager.leave(budgetId)
                is DetailConfirm.Remove -> manager.removeMember(budgetId, step.userId)
                is DetailConfirm.Transfer -> manager.transferOwnership(budgetId, step.userId)
            }
            setState { it.copy(busy = false, confirm = null) }
            when (result) {
                ActionResult.Done -> if (step == DetailConfirm.Leave) sendEffect(SharedBudgetDetailEffect.NavigateBack)
                ActionResult.NotVerified -> sendEffect(SharedBudgetDetailEffect.RequireVerification)
                ActionResult.Offline -> sendEffect(SharedBudgetDetailEffect.ShowMessage(EditorMessage.Offline))
                ActionResult.NotAllowed -> sendEffect(SharedBudgetDetailEffect.ShowMessage(EditorMessage.NotAllowed))
                ActionResult.PlanFull -> sendEffect(SharedBudgetDetailEffect.ShowMessage(EditorMessage.PlanFull))
                ActionResult.Stale -> sendEffect(SharedBudgetDetailEffect.ShowMessage(EditorMessage.Stale))
                is ActionResult.Failed -> sendEffect(SharedBudgetDetailEffect.ShowMessage(EditorMessage.Failed))
            }
        }
    }
}
