package com.fares.saveme.feature.notifications.domain

import com.fares.saveme.feature.notifications.data.PromptFlags

/** The explanation sheet is offered at most once per installation, only where the permission can still be asked (FR-011). */
class NotificationPromptGate(
    private val provider: PushTokenProvider,
    private val flags: PromptFlags,
) {
    suspend fun shouldOffer(permissionAskable: Boolean): Boolean = provider.isAvailable && permissionAskable && !flags.isOffered()

    /**
     * True when the sheet should be shown now, and counts it as offered. Another sheet or dialog already showing suppresses it
     * without counting, so the next moment may offer it (FR-011).
     */
    suspend fun offerIfDue(permissionAskable: Boolean, otherSheetShowing: Boolean): Boolean {
        if (otherSheetShowing || !shouldOffer(permissionAskable)) return false
        markOffered()
        return true
    }

    /** Called when the sheet is actually shown; permanent across sign-out (it is about the phone, not the person). */
    suspend fun markOffered() = flags.markOffered()

    suspend fun markPermissionAsked() = flags.markPermissionAsked()

    suspend fun permissionAsked(): Boolean = flags.permissionAsked()
}
