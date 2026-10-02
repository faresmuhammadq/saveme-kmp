package com.fares.saveme.feature.auth.domain

import com.fares.saveme.core.network.AppJson
import com.fares.saveme.core.network.Envelope
import com.fares.saveme.feature.auth.data.dto.SignInResultDto
import com.fares.saveme.feature.auth.domain.model.FeatureValue
import com.fares.saveme.feature.auth.domain.model.RefreshOutcome
import com.fares.saveme.feature.auth.signInSuccessBody
import com.fares.saveme.feature.billing.BillingRoutes
import com.fares.saveme.feature.billing.BillingTestEnv
import com.fares.saveme.feature.billing.OTHER_USER_ID
import com.fares.saveme.feature.billing.PLUS_ID
import com.fares.saveme.feature.billing.USER_ID
import com.fares.saveme.feature.billing.signIn
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class RefreshEntitlementsTest {

    @Test
    fun successReplacesFeaturesAndPlanInStateAndSnapshot() = runTest {
        val routes = BillingRoutes()
        val env = BillingTestEnv { with(routes) { answer(it) } }
        val manager = env.sessionManager(backgroundScope)
        env.signIn(manager)

        assertEquals(RefreshOutcome.Done, manager.refreshEntitlements())

        val account = assertIs<AuthState.SignedIn>(manager.state.value).account
        assertEquals(PLUS_ID, account.planId)
        assertEquals("Plus · Monthly", account.plan)
        assertEquals(FeatureValue.Flag(true), account.features["sharing.enabled"])
        assertEquals(FeatureValue.Quota(null), account.features["wallets.max"])
        assertEquals(PLUS_ID, env.auth.snapshots.read()!!.entitlements.plan.id)
    }

    @Test
    fun signedOutBeforeTheAnswerWritesNothing() = runTest {
        val routes = BillingRoutes(gatedPaths = setOf("/me/entitlements"))
        val env = BillingTestEnv { with(routes) { answer(it) } }
        val manager = env.sessionManager(backgroundScope)
        env.signIn(manager)

        val outcome = async { manager.refreshEntitlements() }
        routes.started.await()
        manager.signOutLocally(SignOutReason.UserSignedOut)
        routes.gate.complete(Unit)

        assertEquals(RefreshOutcome.Failed, outcome.await())
        assertEquals(AuthState.SignedOut(SignOutReason.UserSignedOut), manager.state.value)
        assertNull(env.auth.snapshots.read())
    }

    @Test
    fun anotherPersonSignedInBeforeTheAnswerWritesNothing() = runTest {
        val routes = BillingRoutes(gatedPaths = setOf("/me/entitlements"))
        val env = BillingTestEnv { with(routes) { answer(it) } }
        val manager = env.sessionManager(backgroundScope)
        env.signIn(manager)

        val outcome = async { manager.refreshEntitlements() }
        routes.started.await()
        manager.establish(AppJson.decodeFromString(Envelope.serializer(SignInResultDto.serializer()), signInSuccessBody().replace(USER_ID, OTHER_USER_ID)).data)
        routes.gate.complete(Unit)

        assertEquals(RefreshOutcome.Failed, outcome.await())
        val account = assertIs<AuthState.SignedIn>(manager.state.value).account
        assertEquals(OTHER_USER_ID, account.userId)
        assertEquals("Free", account.plan, "the new person's entitlements are untouched")
        assertEquals("Free", env.auth.snapshots.read()!!.entitlements.plan.name)
    }

    @Test
    fun offlineLeavesTheStateAndKeepsThePersonSignedIn() = runTest {
        var offline = false
        val routes = BillingRoutes()
        val env = BillingTestEnv { if (offline) throw kotlinx.io.IOException("no route") else with(routes) { answer(it) } }
        val manager = env.sessionManager(backgroundScope)
        env.signIn(manager)
        val before = manager.state.value
        offline = true

        assertEquals(RefreshOutcome.Offline, manager.refreshEntitlements())
        assertEquals(before, manager.state.value)
    }
}
