package com.fares.saveme.feature.sharing.ui.members

import com.fares.saveme.feature.sharing.domain.model.InvitationView
import com.fares.saveme.feature.sharing.domain.model.InviteCheck
import com.fares.saveme.feature.sharing.domain.model.MemberView
import com.fares.saveme.feature.wallets.domain.model.WalletRole
import com.fares.saveme.feature.wallets.domain.model.WalletView

/** Which confirmation, if any, is open. */
sealed interface MembersConfirm {
    data class Remove(val userId: String, val name: String?) : MembersConfirm
    data class Withdraw(val invitationId: String) : MembersConfirm
    data class Leave(val waitingCount: Int) : MembersConfirm
}

/** One-line answers in the app's own words; the screen picks the copy (P-06.5). */
sealed interface MembersMessage {
    data object Changed : MembersMessage
    data object OnlyOwner : MembersMessage
    data object Gone : MembersMessage
    data object OwnerRole : MembersMessage
    data object Offline : MembersMessage
    data object PlanSharing : MembersMessage
    data object PlanFull : MembersMessage
    data object PlanOther : MembersMessage
    data object Copied : MembersMessage
    data object OwnerCannotLeave : MembersMessage
    data object InviteSent : MembersMessage
    data object InviteSaved : MembersMessage
    data class Failed(val requestId: String?) : MembersMessage
}

data class MembersState(
    val walletId: String = "",
    val walletName: String = "",
    val loaded: Boolean = false,
    val myRole: WalletRole? = null,
    val walletArchived: Boolean = false,
    val members: List<MemberView> = emptyList(),
    val invitations: List<InvitationView> = emptyList(),
    val inviteCheck: InviteCheck = InviteCheck.Allowed,
    /** The plan's number of people when the wallet holds more (FR-028). */
    val planNote: Int? = null,
    /** The member whose action sheet is open. */
    val sheetFor: String? = null,
    val confirm: MembersConfirm? = null,
    val busy: Boolean = false,
    val timeZoneId: String = "UTC",
) {
    val isOwner: Boolean get() = myRole == WalletRole.Owner

    /** "Invite" is offered to the owner of an active wallet only (FR-001). */
    val canInvite: Boolean get() = isOwner && !walletArchived

    val inviteEnabled: Boolean get() = canInvite && inviteCheck == InviteCheck.Allowed

    /** Another member exists: the owner may transfer, a non-owner may leave. */
    val hasOtherMembers: Boolean get() = members.any { !it.isYou }

    val canLeave: Boolean get() = loaded && myRole != null && !isOwner

    /** Owner controls on a member: never on the owner's own row (FR-021). */
    fun canManage(member: MemberView): Boolean = isOwner && !member.isOwner && !member.isYou
}

sealed interface MembersIntent {
    data object InviteClicked : MembersIntent
    data object SeePlanClicked : MembersIntent
    data class MemberClicked(val userId: String) : MembersIntent
    data object SheetDismissed : MembersIntent
    data class ChangeRoleClicked(val userId: String, val role: WalletRole) : MembersIntent
    data class RemoveClicked(val userId: String) : MembersIntent
    data class WithdrawClicked(val invitationId: String) : MembersIntent
    data class ShareClicked(val invitationId: String) : MembersIntent
    data class CopyClicked(val invitationId: String) : MembersIntent
    data class ReplaceLinkClicked(val invitationId: String) : MembersIntent
    data class DiscardClicked(val invitationId: String) : MembersIntent
    data class TryAgainClicked(val invitationId: String) : MembersIntent
    data object LeaveClicked : MembersIntent
    data object ConfirmClicked : MembersIntent
    data object ConfirmDismissed : MembersIntent
    data object Back : MembersIntent
}

sealed interface MembersEffect {
    data class OpenInvite(val walletId: String, val replacing: String? = null) : MembersEffect

    /** Opens the Plan screen and nothing else: the feature never offers a purchase (FR-036). */
    data object OpenPlan : MembersEffect

    /** Carries a one-time link: it never prints (P-09.2). */
    data class Share(val text: String) : MembersEffect {
        override fun toString() = "Share(<redacted>)"
    }

    data class Copy(val text: String) : MembersEffect {
        override fun toString() = "Copy(<redacted>)"
    }

    data class ShowMessage(val message: MembersMessage) : MembersEffect
    data class Left(val walletName: String) : MembersEffect
    data object ShowVerifyPrompt : MembersEffect
    data object NavigateBack : MembersEffect
}

sealed interface MembersChange {
    data class Wallet(val view: WalletView?) : MembersChange
    data class Members(val members: List<MemberView>) : MembersChange
    data class Invitations(val invitations: List<InvitationView>) : MembersChange
    data class Check(val check: InviteCheck) : MembersChange
    data class PlanNote(val max: Int?) : MembersChange
    data class Sheet(val userId: String?) : MembersChange
    data class Confirm(val confirm: MembersConfirm?) : MembersChange
    data class Busy(val busy: Boolean) : MembersChange
    data class TimeZone(val id: String) : MembersChange
}

fun reduce(state: MembersState, change: MembersChange): MembersState = when (change) {
    is MembersChange.Wallet -> change.view?.let { view ->
        state.copy(
            loaded = true,
            walletName = view.shown.name,
            myRole = view.server.myRole,
            walletArchived = view.archived,
        )
    } ?: state
    is MembersChange.Members -> state.copy(members = change.members)
    is MembersChange.Invitations -> state.copy(invitations = change.invitations)
    is MembersChange.Check -> state.copy(inviteCheck = change.check)
    is MembersChange.PlanNote -> state.copy(planNote = change.max)
    is MembersChange.Sheet -> state.copy(sheetFor = change.userId)
    is MembersChange.Confirm -> state.copy(confirm = change.confirm)
    is MembersChange.Busy -> state.copy(busy = change.busy)
    is MembersChange.TimeZone -> state.copy(timeZoneId = change.id)
}
