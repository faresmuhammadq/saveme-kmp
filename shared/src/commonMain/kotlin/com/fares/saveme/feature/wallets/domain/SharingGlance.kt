package com.fares.saveme.feature.wallets.domain

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/**
 * What the wallet screens need to know about sharing (spec 013 R-15). Wallets owns the interface so it never imports sharing
 * (P-10.4); the sharing module rebinds [NoSharingGlance].
 */
interface SharingGlance {
    /** Pending, unexpired invitations of the wallet; 0 when there is none. */
    fun observePendingCount(walletId: String): Flow<Int>

    /**
     * True when the latest role change about the person that an activity page read in this app session showed was the owner's
     * plan lowering them (`role_limited_by_plan`).
     */
    fun observePlanLimited(walletId: String): Flow<Boolean>
}

object NoSharingGlance : SharingGlance {
    override fun observePendingCount(walletId: String): Flow<Int> = flowOf(0)
    override fun observePlanLimited(walletId: String): Flow<Boolean> = flowOf(false)
}
