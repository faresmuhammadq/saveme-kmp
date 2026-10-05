package com.fares.saveme.feature.sharing.domain

import com.fares.saveme.feature.billing.domain.QuotaLimit
import com.fares.saveme.feature.sharing.domain.model.InviteCheck
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** The invite gate: verified, then sharing included, then room for one more (spec 013 R-06, FR-002). */
class InviteGateTest {

    private fun check(verified: Boolean = true, sharing: Boolean = true, quota: QuotaLimit, people: Int) =
        InviteGate.check(verified, sharing, quota, people)

    @Test
    fun theOrderIsVerifiedThenSharingThenCount() {
        assertEquals(InviteCheck.NotVerified, check(verified = false, sharing = false, quota = QuotaLimit.Limited(1), people = 9))
        assertEquals(InviteCheck.SharingNotIncluded, check(sharing = false, quota = QuotaLimit.Limited(1), people = 9))
        assertEquals(InviteCheck.Full(1), check(quota = QuotaLimit.Limited(1), people = 9))
    }

    @Test
    fun anUnlimitedQuotaIsAlwaysAllowed() {
        assertEquals(InviteCheck.Allowed, check(quota = QuotaLimit.Unlimited, people = 10_000))
    }

    @Test
    fun aLimitOfOneWithOnlyTheOwnerIsFull() {
        assertEquals(InviteCheck.Full(1), check(quota = QuotaLimit.Limited(1), people = 1))
    }

    @Test
    fun theOwnerTwoMembersAnUnexpiredPendingAndAnUnsentPlaceholderAllowOneMoreOfFiveThenFull() {
        // owner + 2 members + 1 pending + 1 unsent placeholder = 5 people counted before the new one: no room.
        assertEquals(InviteCheck.Allowed, check(quota = QuotaLimit.Limited(5), people = 4))
        assertEquals(InviteCheck.Full(5), check(quota = QuotaLimit.Limited(5), people = 5))
        assertEquals(InviteCheck.Full(5), check(quota = QuotaLimit.Limited(5), people = 6))
    }

    @Test
    fun aQuotaOfZeroIsFull() {
        assertEquals(InviteCheck.Full(0), check(quota = QuotaLimit.Limited(0), people = 1))
    }

    @Test
    fun noSharingIsNotIncludedWhateverTheQuota() {
        assertEquals(InviteCheck.SharingNotIncluded, check(sharing = false, quota = QuotaLimit.Unlimited, people = 1))
    }

    @Test
    fun theAllowedPeopleCountSharingAsOneWhenNotIncluded() {
        assertEquals(1, InviteGate.allowedPeople(false, QuotaLimit.Limited(5)))
        assertEquals(5, InviteGate.allowedPeople(true, QuotaLimit.Limited(5)))
        assertNull(InviteGate.allowedPeople(true, QuotaLimit.Unlimited))
    }
}
