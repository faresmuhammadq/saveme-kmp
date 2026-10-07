package com.fares.saveme.feature.billing.domain

import com.fares.saveme.feature.auth.domain.AuthState
import com.fares.saveme.feature.auth.domain.SignOutReason
import com.fares.saveme.feature.billing.BillingRoutes
import com.fares.saveme.feature.billing.BillingTestEnv
import com.fares.saveme.feature.billing.OTHER_USER_ID
import com.fares.saveme.feature.billing.RecordingEntitlements
import com.fares.saveme.feature.billing.USER_ID
import com.fares.saveme.feature.billing.data.dto.MySubscriptionDto
import com.fares.saveme.feature.billing.data.dto.PlanRefDto
import com.fares.saveme.feature.billing.data.dto.RedeemEntitlementsDto
import com.fares.saveme.feature.billing.signIn
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BillingAnswerApplierTest {

    private val entitlementsAnswer = RedeemEntitlementsDto(PlanRefDto("plan-plus", "Plus · Monthly"), mapOf("sharing.enabled" to JsonPrimitive(true)))
    private val subscriptionAnswer = MySubscriptionDto(PlanRefDto("plan-plus", "Plus · Monthly"), provider = "google_play", status = "active")

    @Test
    fun appliesEntitlementsAndSubscriptionForTheSamePersonThenRefreshes() = runTest {
        val routes = BillingRoutes()
        val env = BillingTestEnv { with(routes) { answer(it) } }
        val sessions = env.sessionManager(backgroundScope)
        env.signIn(sessions)
        val entitlements = RecordingEntitlements()
        val applier = BillingAnswerApplier(sessions, env.subscriptions, entitlements, backgroundScope)

        assertTrue(applier.apply(USER_ID, entitlementsAnswer, subscriptionAnswer))
        runCurrent()

        assertEquals("Plus · Monthly", (sessions.state.value as AuthState.SignedIn).account.plan)
        assertEquals("google_play", env.subscriptions.stored.value?.provider)
        assertEquals(1, entitlements.refreshes)
    }

    @Test
    fun refusesForAnotherPersonAndWritesNothing() = runTest {
        val routes = BillingRoutes()
        val env = BillingTestEnv { with(routes) { answer(it) } }
        val sessions = env.sessionManager(backgroundScope)
        env.signIn(sessions)
        val before = (sessions.state.value as AuthState.SignedIn).account.plan
        val entitlements = RecordingEntitlements()
        val applier = BillingAnswerApplier(sessions, env.subscriptions, entitlements, backgroundScope)

        assertFalse(applier.apply(OTHER_USER_ID, entitlementsAnswer, subscriptionAnswer))
        runCurrent()

        assertEquals(before, (sessions.state.value as AuthState.SignedIn).account.plan)
        assertNull(env.subscriptions.stored.value)
        assertEquals(0, entitlements.refreshes)
    }

    @Test
    fun refusesWhenSignedOut() = runTest {
        val routes = BillingRoutes()
        val env = BillingTestEnv { with(routes) { answer(it) } }
        val sessions = env.sessionManager(backgroundScope)
        env.signIn(sessions)
        sessions.signOutLocally(SignOutReason.SessionEnded)
        val entitlements = RecordingEntitlements()
        val applier = BillingAnswerApplier(sessions, env.subscriptions, entitlements, backgroundScope)

        assertFalse(applier.apply(USER_ID, entitlementsAnswer, subscriptionAnswer))
        runCurrent()

        assertNull(env.subscriptions.stored.value)
        assertEquals(0, entitlements.refreshes)
    }
}
