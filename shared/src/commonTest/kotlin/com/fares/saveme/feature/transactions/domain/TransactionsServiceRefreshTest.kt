package com.fares.saveme.feature.transactions.domain

import com.fares.saveme.core.sync.ReadOutcome
import com.fares.saveme.core.sync.ReadTarget
import com.fares.saveme.feature.auth.domain.model.RefreshOutcome
import com.fares.saveme.feature.auth.jsonResponse
import com.fares.saveme.feature.transactions.TransactionTestEnv
import com.fares.saveme.feature.transactions.transactionJson
import com.fares.saveme.feature.transactions.transactionPage
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** FR-014, FR-015: a wallet's transactions come from its change feed through the runner (spec 009); outcomes keep their meaning. */
class TransactionsServiceRefreshTest {

    private fun env(scope: kotlinx.coroutines.CoroutineScope) = TransactionTestEnv(scope) { jsonResponse(200, transactionPage(emptyList())) }

    @Test
    fun aRefreshReadsTheWalletsFeedThroughTheRunnerWithTheCallersForce() = runTest {
        val env = env(backgroundScope)
        env.signedIn()
        assertEquals(RefreshOutcome.Done, env.service.refresh("w1"))
        assertEquals(RefreshOutcome.Done, env.service.refresh("w1", force = true))
        assertEquals(listOf<Pair<ReadTarget, Boolean>>(ReadTarget.Wallet("w1") to false, ReadTarget.Wallet("w1") to true), env.runner.refreshes)
        assertEquals(0, env.requests.size, "no whole-list read of the wallet's transactions, no category-reference read")
    }

    @Test
    fun aPullAlsoSendsWhatWaitsAndAnOpenDoesNot() = runTest {
        val env = env(backgroundScope)
        env.signedIn()
        env.service.refresh("w1")
        assertEquals(0, env.scheduler.enqueued.size)
        env.service.refresh("w1", force = true)
        assertTrue(env.scheduler.enqueued.isNotEmpty())
    }

    @Test
    fun eachWalletIsItsOwnTarget() = runTest {
        val env = env(backgroundScope)
        env.service.refresh("w1")
        env.service.refresh("w2")
        assertEquals(listOf<ReadTarget>(ReadTarget.Wallet("w1"), ReadTarget.Wallet("w2")), env.runner.refreshes.map { it.first })
    }

    @Test
    fun offlineAndFailedOutcomesAreReportedAndKeepTheLocalData() = runTest {
        val env = env(backgroundScope)
        env.signedIn()
        env.seed(transactionJson(version = 2))
        env.runner.outcome = ReadOutcome.Offline
        assertEquals(RefreshOutcome.Offline, env.service.refresh("w1"))
        env.runner.outcome = ReadOutcome.Failed
        assertEquals(RefreshOutcome.Failed, env.service.refresh("w1"))
        assertEquals(setOf("t1"), env.transactionDao.rows.value.keys)
    }
}
