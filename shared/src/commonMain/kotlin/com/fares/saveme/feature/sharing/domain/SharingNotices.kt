package com.fares.saveme.feature.sharing.domain

import com.fares.saveme.feature.sharing.domain.model.InviteNotice
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * One-line confirmations that must outlive the screen that earned them (spec 013): "Invitation sent" appears on Members after the
 * invite screen closes, "Invitation declined" on whichever screen the invitation screen returns to. Nothing is replayed and
 * nothing carries a secret, an address or a name.
 */
class SharingNotices {
    private val invites = MutableSharedFlow<InviteNotice>(extraBufferCapacity = 4)
    private val declined = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    val inviteNotices: Flow<InviteNotice> = invites.asSharedFlow()
    val declinedNotices: Flow<Unit> = declined.asSharedFlow()

    fun inviteClosed(notice: InviteNotice) {
        invites.tryEmit(notice)
    }

    fun invitationDeclined() {
        declined.tryEmit(Unit)
    }
}
