package com.fares.saveme.feature.sharing.ui.members

import androidx.lifecycle.viewModelScope
import com.fares.saveme.core.presentation.MviViewModel
import com.fares.saveme.feature.sharing.domain.model.InvitationView
import com.fares.saveme.feature.sharing.domain.model.InviteCheck
import com.fares.saveme.feature.sharing.domain.model.InviteNotice
import com.fares.saveme.feature.sharing.domain.model.LeaveCheck
import com.fares.saveme.feature.sharing.domain.model.LeaveResult
import com.fares.saveme.feature.sharing.domain.model.MemberActionResult
import com.fares.saveme.feature.sharing.domain.model.MemberView
import com.fares.saveme.feature.sharing.domain.model.WithdrawResult
import com.fares.saveme.feature.wallets.domain.model.WalletRole
import com.fares.saveme.feature.wallets.domain.model.WalletView
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

/**
 * One wallet's members and pending invitations: always the phone's copy (P-04.1). Member actions are online-only and run one at
 * a time (FR-020). A share link is fetched only when "Share" or "Copy" is tapped and goes straight into an effect that never
 * prints it (P-09.2). Domain lambdas only (P-16.3).
 */
class MembersViewModel(
    private val walletId: String,
    wallet: Flow<WalletView?>,
    members: Flow<List<MemberView>>,
    invitations: Flow<List<InvitationView>>,
    inviteCheck: Flow<InviteCheck>,
    planNote: Flow<Int?>,
    private val changeRole: suspend (userId: String, role: WalletRole) -> MemberActionResult,
    private val remove: suspend (userId: String) -> MemberActionResult,
    private val withdraw: suspend (invitationId: String) -> WithdrawResult,
    private val shareText: suspend (invitationId: String) -> String?,
    private val discard: suspend (invitationId: String) -> Unit,
    private val tryAgain: suspend (invitationId: String) -> Unit,
    private val leaveCheck: suspend () -> LeaveCheck,
    private val leave: suspend () -> LeaveResult,
    timeZoneId: Flow<String> = emptyFlow(),
    inviteNotices: Flow<InviteNotice> = emptyFlow(),
) : MviViewModel<MembersState, MembersIntent, MembersEffect>(MembersState(walletId = walletId)) {

    /** True from a confirmed leave on, so the wallet vanishing is not treated as access lost. */
    private var leaving = false

    init {
        wallet.onEach { view ->
            if (view == null) {
                // The wallet is gone (access lost): close quietly; the wallet's own screen explains (FR-023).
                if (!leaving) sendEffect(MembersEffect.NavigateBack)
            } else {
                setState { reduce(it, MembersChange.Wallet(view)) }
            }
        }.launchIn(viewModelScope)
        members.onEach { list -> setState { reduce(it, MembersChange.Members(list)) } }.launchIn(viewModelScope)
        invitations.onEach { list -> setState { reduce(it, MembersChange.Invitations(list)) } }.launchIn(viewModelScope)
        inviteCheck.onEach { check -> setState { reduce(it, MembersChange.Check(check)) } }.launchIn(viewModelScope)
        planNote.onEach { max -> setState { reduce(it, MembersChange.PlanNote(max)) } }.launchIn(viewModelScope)
        timeZoneId.onEach { id -> setState { reduce(it, MembersChange.TimeZone(id)) } }.launchIn(viewModelScope)
        inviteNotices.onEach { notice ->
            message(if (notice == InviteNotice.Sent) MembersMessage.InviteSent else MembersMessage.InviteSaved)
        }.launchIn(viewModelScope)
    }

    override fun onIntent(intent: MembersIntent) {
        when (intent) {
            MembersIntent.InviteClicked -> if (currentState.canInvite) {
                when (currentState.inviteCheck) {
                    InviteCheck.NotVerified -> sendEffect(MembersEffect.ShowVerifyPrompt)
                    InviteCheck.Allowed -> sendEffect(MembersEffect.OpenInvite(walletId))
                    else -> Unit
                }
            }
            MembersIntent.SeePlanClicked -> sendEffect(MembersEffect.OpenPlan)
            is MembersIntent.MemberClicked -> {
                val member = currentState.members.firstOrNull { it.userId == intent.userId }
                if (member != null && currentState.canManage(member) && !currentState.busy) setState { reduce(it, MembersChange.Sheet(member.userId)) }
            }
            MembersIntent.SheetDismissed -> setState { reduce(it, MembersChange.Sheet(null)) }
            is MembersIntent.ChangeRoleClicked -> {
                setState { reduce(it, MembersChange.Sheet(null)) }
                run { changeRole(intent.userId, intent.role) }
            }
            is MembersIntent.RemoveClicked -> {
                val member = currentState.members.firstOrNull { it.userId == intent.userId } ?: return
                setState { reduce(reduce(it, MembersChange.Sheet(null)), MembersChange.Confirm(MembersConfirm.Remove(member.userId, member.displayName))) }
            }
            is MembersIntent.WithdrawClicked -> setState { reduce(it, MembersChange.Confirm(MembersConfirm.Withdraw(intent.invitationId))) }
            is MembersIntent.ShareClicked -> viewModelScope.launch { shareText(intent.invitationId)?.let { sendEffect(MembersEffect.Share(it)) } }
            is MembersIntent.CopyClicked -> viewModelScope.launch {
                shareText(intent.invitationId)?.let {
                    sendEffect(MembersEffect.Copy(it))
                    sendEffect(MembersEffect.ShowMessage(MembersMessage.Copied))
                }
            }
            is MembersIntent.ReplaceLinkClicked -> if (currentState.isOwner) sendEffect(MembersEffect.OpenInvite(walletId, replacing = intent.invitationId))
            is MembersIntent.DiscardClicked -> viewModelScope.launch { discard(intent.invitationId) }
            is MembersIntent.TryAgainClicked -> viewModelScope.launch { tryAgain(intent.invitationId) }
            MembersIntent.LeaveClicked -> if (currentState.canLeave && !currentState.busy) askToLeave()
            MembersIntent.ConfirmClicked -> confirmed()
            MembersIntent.ConfirmDismissed -> if (!currentState.busy) setState { reduce(it, MembersChange.Confirm(null)) }
            MembersIntent.Back -> sendEffect(MembersEffect.NavigateBack)
        }
    }

    private fun askToLeave() {
        setState { reduce(it, MembersChange.Busy(true)) }
        viewModelScope.launch {
            when (val check = leaveCheck()) {
                is LeaveCheck.Ready -> setState { reduce(it, MembersChange.Confirm(MembersConfirm.Leave(check.waitingCount))) }
                LeaveCheck.Offline -> sendEffect(MembersEffect.ShowMessage(MembersMessage.Offline))
                LeaveCheck.Owner -> sendEffect(MembersEffect.ShowMessage(MembersMessage.OwnerCannotLeave))
            }
            setState { reduce(it, MembersChange.Busy(false)) }
        }
    }

    private fun confirmed() {
        if (currentState.busy) return
        when (val confirm = currentState.confirm ?: return) {
            is MembersConfirm.Remove -> run { remove(confirm.userId) }
            is MembersConfirm.Withdraw -> {
                setState { reduce(it, MembersChange.Confirm(null)) }
                viewModelScope.launch {
                    when (withdraw(confirm.invitationId)) {
                        WithdrawResult.Withdrawn -> Unit
                        WithdrawResult.NotOwner -> sendEffect(MembersEffect.ShowMessage(MembersMessage.OnlyOwner))
                        WithdrawResult.Gone -> Unit
                    }
                }
            }
            is MembersConfirm.Leave -> {
                setState { reduce(reduce(it, MembersChange.Busy(true)), MembersChange.Confirm(null)) }
                leaving = true
                viewModelScope.launch {
                    val result = leave()
                    // A leave that happened is closed by the Left effect; one that did not leaves the screen as it was.
                    if (result !is LeaveResult.Left) leaving = false
                    when (result) {
                        is LeaveResult.Left -> sendEffect(MembersEffect.Left(result.walletName))
                        LeaveResult.Offline -> message(MembersMessage.Offline)
                        LeaveResult.OwnerCannotLeave -> message(MembersMessage.OwnerCannotLeave)
                        LeaveResult.Changed -> message(MembersMessage.Changed)
                        is LeaveResult.Failed -> message(MembersMessage.Failed(result.requestId))
                    }
                    setState { reduce(it, MembersChange.Busy(false)) }
                }
            }
        }
    }

    /** Runs a member action once: the confirmation closes, the screen shows it is busy, and the answer is worded. */
    private fun run(action: suspend () -> MemberActionResult) {
        if (currentState.busy) return
        setState { reduce(reduce(it, MembersChange.Busy(true)), MembersChange.Confirm(null)) }
        viewModelScope.launch {
            val result = action()
            setState { reduce(it, MembersChange.Busy(false)) }
            when (result) {
                MemberActionResult.Done, MemberActionResult.Busy -> Unit
                MemberActionResult.Offline -> message(MembersMessage.Offline)
                MemberActionResult.Changed -> message(MembersMessage.Changed)
                MemberActionResult.OnlyOwner -> message(MembersMessage.OnlyOwner)
                MemberActionResult.Gone -> message(MembersMessage.Gone)
                MemberActionResult.OwnerRole -> message(MembersMessage.OwnerRole)
                MemberActionResult.NotVerified -> sendEffect(MembersEffect.ShowVerifyPrompt)
                is MemberActionResult.NotEntitled -> message(
                    when (result.feature) {
                        FEATURE_SHARING -> MembersMessage.PlanSharing
                        FEATURE_MEMBERS -> MembersMessage.PlanFull
                        else -> MembersMessage.PlanOther
                    },
                )
                is MemberActionResult.Failed -> message(MembersMessage.Failed(result.requestId))
            }
        }
    }

    private fun message(message: MembersMessage) = sendEffect(MembersEffect.ShowMessage(message))

    private companion object {
        const val FEATURE_SHARING = "sharing.enabled"
        const val FEATURE_MEMBERS = "sharing.max_members_per_wallet"
    }
}
