package com.fares.saveme.feature.sharedbudgets.domain

import com.fares.saveme.core.session.UserDataCleaner
import com.fares.saveme.feature.sharing.domain.InvitationLink
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The secret of a Shared Budget link opened before anyone signed in. Memory only (P-09.2): never stored, never logged; a new link
 * replaces it and sign-out drops it.
 */
class HeldSharedBudgetInvitation : UserDataCleaner {
    private val state = MutableStateFlow<InvitationLink.Valid?>(null)
    val held: StateFlow<InvitationLink.Valid?> = state.asStateFlow()

    fun hold(link: InvitationLink.Valid) {
        state.value = link
    }

    fun drop() {
        state.value = null
    }

    override suspend fun clear() = drop()

    override fun toString() = "HeldSharedBudgetInvitation(held=${if (state.value == null) "none" else "<redacted>"})"
}
