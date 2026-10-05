package com.fares.saveme.feature.billing.domain

import com.fares.saveme.feature.billing.redeemResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

class RedeemConfirmationTest {

    private val now = Instant.parse("2026-10-05T13:00:00Z")

    @Test
    fun theRedeemedSubscriptionGoverningHasNoPlayNote() {
        val confirmation = RedeemConfirmation.from(redeemResult(), now)
        assertTrue(confirmation.governingIsRedeemed)
        assertFalse(confirmation.playRenews)
        assertEquals(Instant.parse("2027-10-05T12:00:00Z"), confirmation.governingEnd)
    }

    @Test
    fun aRenewingPlaySubscriptionThatKeepsGoverningIsNamedWithItsPlayNote() {
        val confirmation = RedeemConfirmation.from(
            redeemResult(governingProvider = "google_play", governingEnd = "2027-01-10T08:00:00Z", autoRenew = true, redeemedEnd = "2026-11-05T12:00:00Z"),
            now,
        )
        assertFalse(confirmation.governingIsRedeemed)
        assertTrue(confirmation.playRenews)
        assertEquals("Governing plan", confirmation.governingPlanName)
        assertEquals("Redeemed plan", confirmation.redeemedPlanName)
        assertEquals(Instant.parse("2026-11-05T12:00:00Z"), confirmation.redeemedEnd)
    }

    @Test
    fun playWithoutAutoRenewHasNoNote() {
        val confirmation = RedeemConfirmation.from(redeemResult(governingProvider = "google_play", governingEnd = "2027-01-10T08:00:00Z", autoRenew = false, redeemedEnd = "2026-11-05T12:00:00Z"), now)
        assertFalse(confirmation.playRenews)
    }

    @Test
    fun aManualLifetimeGoverningHasNoEndAndIsNotTheRedeemedOne() {
        val confirmation = RedeemConfirmation.from(redeemResult(governingProvider = "manual", governingEnd = null, redeemedEnd = "2026-11-05T12:00:00Z"), now)
        assertNull(confirmation.governingEnd)
        assertFalse(confirmation.governingIsRedeemed)
        assertFalse(confirmation.playRenews)
    }

    @Test
    fun graceGoverningShowsTheGraceEnd() {
        val confirmation = RedeemConfirmation.from(
            redeemResult(governingProvider = "google_play", governingStatus = "grace", governingEnd = "2026-10-01T00:00:00Z", graceUntil = "2026-10-08T00:00:00Z", autoRenew = true, redeemedEnd = "2026-11-05T12:00:00Z"),
            now,
        )
        assertEquals(Instant.parse("2026-10-08T00:00:00Z"), confirmation.governingEnd)
    }

    @Test
    fun aRedeemedStartAfterNowIsReportedOtherwiseNull() {
        assertEquals(Instant.parse("2026-11-05T12:00:00Z"), RedeemConfirmation.from(redeemResult(redeemedStart = "2026-11-05T12:00:00Z"), now).redeemedStartsAt)
        assertNull(RedeemConfirmation.from(redeemResult(redeemedStart = "2026-10-05T12:00:00Z"), now).redeemedStartsAt)
    }

    @Test
    fun anUnknownProviderIsAnotherSubscriptionWithoutAPlayNote() {
        val confirmation = RedeemConfirmation.from(redeemResult(governingProvider = "future_store", governingEnd = "2027-01-10T08:00:00Z", autoRenew = true, redeemedEnd = "2026-11-05T12:00:00Z"), now)
        assertFalse(confirmation.governingIsRedeemed)
        assertFalse(confirmation.playRenews)
    }

    @Test
    fun bothEndsNullCountAsEqualForTheRedeemedPlan() {
        assertTrue(RedeemConfirmation.from(redeemResult(governingEnd = null, redeemedEnd = null), now).governingIsRedeemed)
    }
}
