package com.fares.saveme.feature.sharing.domain

import com.fares.saveme.core.session.UserDataCleaner
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map

/**
 * The invitation secret from a link opened before anyone signed in, or after "Not now" (spec 013 R-09, FR-011). Memory only
 * (P-09.2): never stored, never logged; a new link replaces it and sign-out drops it.
 */
class HeldInvitation : UserDataCleaner {
    private val state = MutableStateFlow<InvitationLink.Valid?>(null)
    val held: StateFlow<InvitationLink.Valid?> = state.asStateFlow()

    fun hold(link: InvitationLink.Valid) {
        state.value = link
    }

    /** Forgets the held secret from code that cannot suspend. */
    fun drop() {
        state.value = null
    }

    fun isHeld(): Flow<Boolean> = state.map { it != null }

    /** Sign-out. */
    override suspend fun clear() = drop()

    override fun toString() = "HeldInvitation(held=${if (state.value == null) "none" else "<redacted>"})"
}
