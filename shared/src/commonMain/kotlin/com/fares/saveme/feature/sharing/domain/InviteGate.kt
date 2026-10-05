package com.fares.saveme.feature.sharing.domain

import com.fares.saveme.feature.billing.domain.QuotaLimit
import com.fares.saveme.feature.sharing.domain.model.InviteCheck

/**
 * Whether the owner may invite another person (spec 013 R-06, FR-002): a verified address, then the plan including sharing,
 * then room for one more person. The gates read the entitlement features, never the plan's name (P-12.1); the server stays
 * the authority.
 */
object InviteGate {

    /**
     * @param peopleCount the owner, the active members and the unexpired pending invitations (waiting ones included,
     *   waiting withdrawals not).
     */
    fun check(verified: Boolean, sharingEnabled: Boolean, quota: QuotaLimit, peopleCount: Int): InviteCheck = when {
        !verified -> InviteCheck.NotVerified
        !sharingEnabled -> InviteCheck.SharingNotIncluded
        quota is QuotaLimit.Limited && peopleCount + 1 > quota.max -> InviteCheck.Full(quota.max.coerceIn(0, Int.MAX_VALUE.toLong()).toInt())
        else -> InviteCheck.Allowed
    }

    /**
     * The people count the plan allows in a wallet: the quota, counted as 1 when sharing is not included (the backend's
     * `EnforceMemberQuota`). Null = unlimited.
     */
    fun allowedPeople(sharingEnabled: Boolean, quota: QuotaLimit): Int? = when {
        !sharingEnabled -> 1
        quota is QuotaLimit.Limited -> quota.max.coerceIn(0, Int.MAX_VALUE.toLong()).toInt()
        else -> null
    }
}
