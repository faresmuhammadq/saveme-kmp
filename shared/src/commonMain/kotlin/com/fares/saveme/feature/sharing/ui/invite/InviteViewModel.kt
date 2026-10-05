package com.fares.saveme.feature.sharing.ui.invite

import androidx.lifecycle.viewModelScope
import com.fares.saveme.core.presentation.MviViewModel
import com.fares.saveme.feature.sharing.domain.InviteValidation
import com.fares.saveme.feature.sharing.domain.model.Delivery
import com.fares.saveme.feature.sharing.domain.model.EmailProblem
import com.fares.saveme.feature.sharing.domain.model.InvitationDraft
import com.fares.saveme.feature.sharing.domain.model.InviteCheck
import com.fares.saveme.feature.sharing.domain.model.InviteNotice
import com.fares.saveme.feature.sharing.domain.model.InviteResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

/**
 * Invites someone to a wallet by email or by a link (spec 013 US1, US3). The invitation is written on the phone and queued
 * (P-04.2): the screen closes with "sent" or "saved" once the queue has had a moment. A link invitation's one-time link arrives
 * with the server's answer and is held in memory; the screen only learns that it is ready and asks for the text to share when
 * the person taps (P-09.2). With [replacing] the screen starts by replacing that link invitation. Domain lambdas only (P-16.3).
 */
class InviteViewModel(
    private val walletId: String,
    replacing: String?,
    inviteCheck: Flow<InviteCheck>,
    private val invite: suspend (InvitationDraft) -> InviteResult,
    private val inviteConfirmed: suspend (InvitationDraft) -> InviteResult,
    private val replaceLink: suspend (invitationId: String) -> InviteResult,
    private val awaitDelivery: suspend (invitationId: String) -> Delivery,
    private val linkHeld: (invitationId: String) -> Flow<Boolean>,
    private val shareText: suspend (invitationId: String) -> String?,
    /** Leaves the confirmation for the screen that comes back (Members says "sent" or "saved"). */
    private val announce: (InviteNotice) -> Unit = {},
) : MviViewModel<InviteState, InviteIntent, InviteEffect>(
    InviteState(walletId = walletId, channel = if (replacing != null) InviteChannel.Link else InviteChannel.Email),
) {

    init {
        inviteCheck.onEach { check -> setState { reduce(it, InviteChange.Check(check)) } }.launchIn(viewModelScope)
        if (replacing != null) {
            setState { reduce(it, InviteChange.Sending(true)) }
            viewModelScope.launch { handle(replaceLink(replacing), draft = null) }
        }
    }

    override fun onIntent(intent: InviteIntent) {
        when (intent) {
            is InviteIntent.ChannelChosen -> setState { reduce(it, InviteChange.Channel(intent.channel)) }
            is InviteIntent.RoleChosen -> setState { reduce(it, InviteChange.Role(intent.role)) }
            is InviteIntent.EmailChanged -> setState { reduce(it, InviteChange.Email(intent.text)) }
            InviteIntent.EmailLeft -> if (currentState.email.isNotBlank()) {
                setState { reduce(it, InviteChange.EmailError(fieldProblem(currentState.email))) }
            }
            InviteIntent.SendClicked -> send(confirmed = false)
            InviteIntent.SupersedeConfirmed -> {
                setState { reduce(it, InviteChange.Supersede(false)) }
                send(confirmed = true)
            }
            InviteIntent.SupersedeDismissed -> setState { reduce(it, InviteChange.Supersede(false)) }
            InviteIntent.SeePlanClicked -> sendEffect(InviteEffect.OpenPlan)
            InviteIntent.ShareClicked -> withText { sendEffect(InviteEffect.Share(it)) }
            InviteIntent.CopyClicked -> withText {
                sendEffect(InviteEffect.Copy(it))
                sendEffect(InviteEffect.Copied)
            }
            InviteIntent.DoneClicked, InviteIntent.Back -> sendEffect(InviteEffect.Close())
        }
    }

    /** Local format checks need no connection; the server's rules are the authority (FR-003). */
    private fun fieldProblem(email: String): EmailProblem? =
        InviteValidation.validate(email, ownEmail = null)

    private fun draft(): InvitationDraft = InvitationDraft(
        role = currentState.role,
        email = if (currentState.channel == InviteChannel.Email) InviteValidation.clean(currentState.email) else null,
    )

    private fun send(confirmed: Boolean) {
        val state = currentState
        if (state.sending || state.link != LinkProgress.None) return
        if (state.channel == InviteChannel.Email && state.email.isBlank()) {
            setState { reduce(it, InviteChange.EmailError(EmailProblem.Invalid)) }
            return
        }
        val draft = draft()
        setState { reduce(reduce(it, InviteChange.Sending(true)), InviteChange.Failure(null)) }
        viewModelScope.launch { handle(if (confirmed) inviteConfirmed(draft) else invite(draft), draft) }
    }

    private suspend fun handle(result: InviteResult, draft: InvitationDraft?) {
        setState { reduce(it, InviteChange.Sending(false)) }
        when (result) {
            is InviteResult.Queued -> afterQueued(result.invitationId, isLink = draft == null || draft.email == null)
            InviteResult.NotVerified -> {
                setState { reduce(it, InviteChange.Check(InviteCheck.NotVerified)) }
                sendEffect(InviteEffect.ShowVerifyPrompt)
            }
            InviteResult.SharingNotIncluded -> setState { reduce(it, InviteChange.Check(InviteCheck.SharingNotIncluded)) }
            is InviteResult.Full -> setState { reduce(it, InviteChange.Check(InviteCheck.Full(result.max))) }
            InviteResult.Invalid -> setState { reduce(it, InviteChange.EmailError(EmailProblem.Invalid)) }
            InviteResult.OwnAddress -> setState { reduce(it, InviteChange.EmailError(EmailProblem.OwnAddress)) }
            InviteResult.SupersedeNeeded -> setState { reduce(it, InviteChange.Supersede(true)) }
            InviteResult.NotOwner -> setState { reduce(it, InviteChange.Failure(InviteError.NotOwner)) }
            InviteResult.WalletArchived -> setState { reduce(it, InviteChange.Failure(InviteError.WalletArchived)) }
        }
    }

    private suspend fun afterQueued(invitationId: String, isLink: Boolean) {
        if (isLink) {
            setState { reduce(it, InviteChange.Link(LinkProgress.Waiting(invitationId))) }
            // The link arrives with the server's answer; offline it waits for the connection and can be shared from Members.
            viewModelScope.launch {
                linkHeld(invitationId).first { it }
                setState { reduce(it, InviteChange.Link(LinkProgress.Ready(invitationId))) }
            }
            when (awaitDelivery(invitationId)) {
                Delivery.Waiting -> if (currentState.link !is LinkProgress.Ready) {
                    setState { reduce(it, InviteChange.Link(LinkProgress.WaitingOffline(invitationId))) }
                }
                Delivery.Refused -> sendEffect(InviteEffect.Close())
                Delivery.Sent -> Unit
            }
        } else {
            setState { reduce(it, InviteChange.Sending(true)) }
            val message = when (awaitDelivery(invitationId)) {
                Delivery.Sent -> InviteNotice.Sent
                Delivery.Waiting -> InviteNotice.SavedOffline
                Delivery.Refused -> null
            }
            setState { reduce(it, InviteChange.Sending(false)) }
            message?.let(announce)
            sendEffect(InviteEffect.Close(message))
        }
    }

    private fun withText(use: (String) -> Unit) {
        val id = currentState.link.invitationId ?: return
        viewModelScope.launch { shareText(id)?.let(use) }
    }
}
