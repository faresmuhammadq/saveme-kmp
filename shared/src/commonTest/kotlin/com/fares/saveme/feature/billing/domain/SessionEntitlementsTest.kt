package com.fares.saveme.feature.billing.domain

import com.fares.saveme.feature.billing.BillingRoutes
import com.fares.saveme.feature.billing.BillingTestEnv
import com.fares.saveme.feature.billing.signIn
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SessionEntitlementsTest {

    @Test
    fun signedOutAnswersOffAndZeroAndSignedInReadsTheCachedFeatures() = runTest {
        val routes = BillingRoutes()
        val env = BillingTestEnv { with(routes) { answer(it) } }
        val sessions = env.sessionManager(backgroundScope)
        val refresher = EntitlementsRefresher(sessions, env.subscriptions, backgroundScope)
        val gate = SessionEntitlements(sessions, refresher, backgroundScope)

        assertFalse(gate.can(FlagFeature.SharingEnabled))
        assertEquals(QuotaLimit.Limited(0), gate.quota(QuotaFeature.Wallets))
        assertFalse(gate.allowsAnother(QuotaFeature.Wallets, 0))
        assertNull(gate.state.value)

        env.signIn(sessions)
        assertFalse(gate.can(FlagFeature.SharingEnabled), "the sign-in answer is Free")

        gate.refreshAfterBillingAction()
        assertTrue(gate.can(FlagFeature.SharingEnabled), "Plus after the refresh, without signing in again")
        assertEquals(QuotaLimit.Limited(5), gate.quota(QuotaFeature.MembersPerWallet))
        assertEquals(QuotaLimit.Unlimited, gate.quota(QuotaFeature.Wallets))
    }
}
