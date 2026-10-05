package com.fares.saveme.feature.auth.domain

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf

/**
 * Whether an invitation link is held in memory for this app session, for Home's "You have an invitation to answer" card
 * (spec 013 FR-013, R-16). Auth owns the interface so Home never imports sharing (P-10.4); the sharing module rebinds
 * [NoHeldInvitation]. It says nothing about the link itself.
 */
interface HeldInvitationGlimpse {
    fun observeHeld(): Flow<Boolean>

    /** Emits once each time the person declines an invitation, so the screen it returns to can say so. */
    fun observeDeclined(): Flow<Unit> = emptyFlow()
}

object NoHeldInvitation : HeldInvitationGlimpse {
    override fun observeHeld(): Flow<Boolean> = flowOf(false)
}
