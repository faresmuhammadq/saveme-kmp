package com.fares.saveme.feature.auth.domain

import com.fares.saveme.feature.auth.domain.model.FeatureValue
import com.fares.saveme.feature.billing.BillingTestEnv
import com.fares.saveme.feature.billing.BillingRoutes
import com.fares.saveme.feature.billing.OTHER_USER_ID
import com.fares.saveme.feature.billing.USER_ID
import com.fares.saveme.feature.billing.signIn
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class SessionManagerApplyEntitlementsTest {

    private val features = mapOf("sharing.enabled" to JsonPrimitive(true), "wallets.max" to JsonNull)

    private suspend fun kotlinx.coroutines.test.TestScope.signedIn(): Pair<BillingTestEnv, SessionManager> {
        val routes = BillingRoutes()
        val env = BillingTestEnv { with(routes) { answer(it) } }
        val sessions = env.sessionManager(backgroundScope)
        env.signIn(sessions)
        return env to sessions
    }

    @Test
    fun replacesPlanAndFeaturesInStateAndSnapshotForTheSamePerson() = runTest {
        val (env, sessions) = signedIn()
        assertTrue(sessions.applyEntitlements(USER_ID, "plan-2", "Plus · Yearly", features))

        val account = assertIs<AuthState.SignedIn>(sessions.state.value).account
        assertEquals("Plus · Yearly", account.plan)
        assertEquals("plan-2", account.planId)
        assertEquals(FeatureValue.Flag(true), account.features["sharing.enabled"])
        assertEquals(FeatureValue.Quota(null), account.features["wallets.max"], "a null quota stays unlimited")
        assertEquals("Plus · Yearly", env.auth.repository.readSnapshot()!!.entitlements.plan.name)
    }

    @Test
    fun anotherPersonSignedInWritesNothing() = runTest {
        val (env, sessions) = signedIn()
        assertFalse(sessions.applyEntitlements(OTHER_USER_ID, "plan-2", "Plus · Yearly", features))
        assertEquals("Free", assertIs<AuthState.SignedIn>(sessions.state.value).account.plan)
        assertEquals("Free", env.auth.repository.readSnapshot()!!.entitlements.plan.name)
    }

    @Test
    fun signedOutWritesNothing() = runTest {
        val (env, sessions) = signedIn()
        sessions.signOutLocally(SignOutReason.SessionEnded)
        assertFalse(sessions.applyEntitlements(USER_ID, "plan-2", "Plus · Yearly", features))
        assertEquals(null, env.auth.repository.readSnapshot())
    }
}
