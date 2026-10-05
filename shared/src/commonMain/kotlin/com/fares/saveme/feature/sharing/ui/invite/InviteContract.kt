package com.fares.saveme.feature.sharing.ui.invite

import com.fares.saveme.feature.sharing.domain.model.EmailProblem
import com.fares.saveme.feature.sharing.domain.model.InviteCheck
import com.fares.saveme.feature.sharing.domain.model.InviteNotice
import com.fares.saveme.feature.sharing.domain.model.InvitedRole

/** The wallet being shared and, when the screen opens to replace a lost link, the link invitation to replace (spec 013 US3-S3). */
data class InviteArgs(val walletId: String, val replacing: String? = null)

enum class InviteChannel { Email, Link }

/** Where a link invitation stands: asked for, waiting for the server's answer, or ready to share (spec 013 FR-005). */
sealed interface LinkProgress {
    val invitationId: String?

    data object None : LinkProgress {
        override val invitationId: String? = null
    }

    /** Online: the answer is on its way. */
    data class Waiting(override val invitationId: String) : LinkProgress

    /** Offline: the link will be ready when the connection returns; it can be shared from Members. */
    data class WaitingOffline(override val invitationId: String) : LinkProgress
    data class Ready(override val invitationId: String) : LinkProgress
}

/** What stopped an invitation before anything was queued, other than a gate or a field problem. */
enum class InviteError { NotOwner, WalletArchived }

/** The address is the person's to type; it never prints in logs or in this state's text (P-09.2). */
data class InviteState(
    val walletId: String = "",
    val channel: InviteChannel = InviteChannel.Email,
    /** Least privilege first (Assumptions 3). */
    val role: InvitedRole = InvitedRole.Viewer,
    val email: String = "",
    val emailError: EmailProblem? = null,
    val check: InviteCheck = InviteCheck.Allowed,
    val sending: Boolean = false,
    /** A pending invitation to the same address exists: confirm before the new one replaces it. */
    val confirmSupersede: Boolean = false,
    val link: LinkProgress = LinkProgress.None,
    val error: InviteError? = null,
) {
    /** The role and channel are fixed once the link invitation has been asked for. */
    val locked: Boolean get() = sending || link != LinkProgress.None

    override fun toString() = "InviteState(channel=$channel, role=$role, sending=$sending, link=${link::class.simpleName})"
}

sealed interface InviteIntent {
    data class ChannelChosen(val channel: InviteChannel) : InviteIntent
    data class RoleChosen(val role: InvitedRole) : InviteIntent
    data class EmailChanged(val text: String) : InviteIntent
    data object EmailLeft : InviteIntent
    data object SendClicked : InviteIntent
    data object SupersedeConfirmed : InviteIntent
    data object SupersedeDismissed : InviteIntent
    data object SeePlanClicked : InviteIntent
    data object ShareClicked : InviteIntent
    data object CopyClicked : InviteIntent
    data object DoneClicked : InviteIntent
    data object Back : InviteIntent
}

sealed interface InviteEffect {
    /** Closes the screen; [message] says whether the invitation was sent or saved for later. */
    data class Close(val message: InviteNotice? = null) : InviteEffect
    data object OpenPlan : InviteEffect
    data object ShowVerifyPrompt : InviteEffect

    /** Carry a one-time link: they never print (P-09.2). */
    data class Share(val text: String) : InviteEffect {
        override fun toString() = "Share(<redacted>)"
    }

    data class Copy(val text: String) : InviteEffect {
        override fun toString() = "Copy(<redacted>)"
    }

    data object Copied : InviteEffect
}

sealed interface InviteChange {
    data class Channel(val channel: InviteChannel) : InviteChange
    data class Role(val role: InvitedRole) : InviteChange
    data class Email(val text: String) : InviteChange
    data class EmailError(val problem: EmailProblem?) : InviteChange
    data class Check(val check: InviteCheck) : InviteChange
    data class Sending(val sending: Boolean) : InviteChange
    data class Supersede(val confirm: Boolean) : InviteChange
    data class Link(val progress: LinkProgress) : InviteChange
    data class Failure(val error: InviteError?) : InviteChange
}

fun reduce(state: InviteState, change: InviteChange): InviteState = when (change) {
    is InviteChange.Channel -> if (state.locked) state else state.copy(channel = change.channel, emailError = null, error = null)
    is InviteChange.Role -> if (state.locked) state else state.copy(role = change.role)
    is InviteChange.Email -> state.copy(email = change.text, emailError = null)
    is InviteChange.EmailError -> state.copy(emailError = change.problem)
    is InviteChange.Check -> state.copy(check = change.check)
    is InviteChange.Sending -> state.copy(sending = change.sending)
    is InviteChange.Supersede -> state.copy(confirmSupersede = change.confirm)
    is InviteChange.Link -> state.copy(link = change.progress)
    is InviteChange.Failure -> state.copy(error = change.error)
}
