package com.fares.saveme.feature.transactions.domain

import com.fares.saveme.feature.auth.domain.AuthState
import com.fares.saveme.feature.transactions.TransactionTestEnv
import com.fares.saveme.feature.transactions.dto
import com.fares.saveme.feature.transactions.transactionJson
import com.fares.saveme.feature.wallets.walletJson
import com.fares.saveme.feature.auth.jsonResponse
import com.fares.saveme.feature.wallets.walletBody
import kotlinx.coroutines.test.runCurrent
import com.fares.saveme.feature.transactions.awaitReal
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Foreground and the carry (research R-08, R-09). */
class TransactionsSyncCoordinatorTest {

    @Test
    fun comingBackToTheForegroundSettlesAPendingCarryWithOneReadOfTheWallet() = runTest {
        val env = TransactionTestEnv(backgroundScope) { jsonResponse(200, walletBody(walletJson(id = "w1"))) }
        env.signedIn()
        env.seedWallet(walletJson(id = "w1"))
        env.repository.storeServerCopy(dto(transactionJson(version = 2)), own = true)
        val coordinator = TransactionsSyncCoordinator(env.authState, env.reconciler, backgroundScope)
        coordinator.onForeground()
        awaitReal { env.transactionDao.rows.value.values.none { it.carryConfirmedAt != null } }
        assertEquals(listOf<Pair<com.fares.saveme.core.sync.ReadTarget, Boolean>>(com.fares.saveme.core.sync.ReadTarget.Wallet("w1") to true), env.runner.refreshes)
        assertEquals(emptyList(), env.transactionDao.rows.value.values.filter { it.carryConfirmedAt != null })
    }

    @Test
    fun theReconcilerOnlyStartsWatchingOnceSignedIn() = runTest {
        val env = TransactionTestEnv(backgroundScope) { jsonResponse(200, walletBody(walletJson(id = "w1"))) }
        env.signedIn()
        env.authState.value = AuthState.SignedOut(com.fares.saveme.feature.auth.domain.SignOutReason.None)
        env.seedWallet(walletJson(id = "w1"))
        TransactionsSyncCoordinator(env.authState, env.reconciler, backgroundScope).start()
        runCurrent()
        env.repository.storeServerCopy(dto(transactionJson(version = 2)), own = true)
        runCurrent()
        assertEquals(0, env.runner.refreshes.size)
        env.authState.value = AuthState.SignedIn(com.fares.saveme.feature.transactions.txAccount)
        awaitReal { env.transactionDao.rows.value.values.none { it.carryConfirmedAt != null } }
        assertTrue(env.runner.refreshes.isNotEmpty())
    }

    @Test
    fun signedOutDoesNothing() = runTest {
        val env = TransactionTestEnv(backgroundScope)
        env.authState.value = AuthState.SignedOut(com.fares.saveme.feature.auth.domain.SignOutReason.None)
        TransactionsSyncCoordinator(env.authState, env.reconciler, backgroundScope).onForeground()
        runCurrent()
        assertEquals(0, env.runner.refreshes.size)
    }
}
