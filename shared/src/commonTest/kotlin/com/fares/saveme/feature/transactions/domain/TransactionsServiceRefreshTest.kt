package com.fares.saveme.feature.transactions.domain

import com.fares.saveme.feature.auth.domain.model.RefreshOutcome
import com.fares.saveme.feature.auth.jsonResponse
import com.fares.saveme.feature.auth.problemBody
import com.fares.saveme.feature.transactions.TransactionTestEnv
import com.fares.saveme.feature.transactions.transactionJson
import com.fares.saveme.feature.transactions.transactionPage
import com.fares.saveme.feature.transactions.awaitReal
import com.fares.saveme.feature.wallets.pageOf
import com.fares.saveme.feature.wallets.walletJson
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

/** FR-018, FR-019: when a wallet's list is read and what the read triggers. */
class TransactionsServiceRefreshTest {

    private fun txRequests(env: TransactionTestEnv) = env.requests.count { it.url.encodedPath.endsWith("/wallets/w1/transactions") }

    @Test
    fun openingReadsTheListAndAsksForThePartnerCategoriesButASecondOpenWithinAMinuteIsSkipped() = runTest {
        val env = TransactionTestEnv(backgroundScope) { request ->
            // A changed read asks for a wallet read too; it gets a wallet page, not the transactions page.
            if (request.url.encodedPath.endsWith("/wallets")) jsonResponse(200, pageOf(listOf(walletJson(id = "w1"))))
            else jsonResponse(200, transactionPage(listOf(transactionJson())))
        }
        env.signedIn()
        assertEquals(RefreshOutcome.Done, env.service.refresh("w1"))
        assertEquals(RefreshOutcome.Done, env.service.refresh("w1"))
        assertEquals(1, txRequests(env))
        assertEquals(listOf("w1" to true), env.categories.referenceRefreshes)
        env.clock.now = env.clock.now + 61.seconds
        env.service.refresh("w1")
        assertEquals(2, txRequests(env))
    }

    @Test
    fun aPullAlwaysReadsAndAlsoSendsWhatWaits() = runTest {
        val env = TransactionTestEnv(backgroundScope) { request ->
            // A changed read asks for a wallet read too; it gets a wallet page, not the transactions page.
            if (request.url.encodedPath.endsWith("/wallets")) jsonResponse(200, pageOf(listOf(walletJson(id = "w1"))))
            else jsonResponse(200, transactionPage(listOf(transactionJson())))
        }
        env.signedIn()
        env.service.refresh("w1")
        env.service.refresh("w1", force = true)
        assertEquals(2, txRequests(env))
        assertTrue(env.scheduler.enqueued.isNotEmpty())
    }

    @Test
    fun theFloorIsPerWallet() = runTest {
        val env = TransactionTestEnv(backgroundScope) { jsonResponse(200, transactionPage(emptyList())) }
        env.signedIn()
        env.service.refresh("w1")
        env.service.refresh("w2")
        assertEquals(1, txRequests(env))
        assertEquals(1, env.requests.count { it.url.encodedPath.endsWith("/wallets/w2/transactions") })
    }

    @Test
    fun aReadThatChangedRowsAsksForOneWalletRead() = runTest {
        val env = TransactionTestEnv(backgroundScope) { request ->
            if (request.url.encodedPath.endsWith("/transactions")) jsonResponse(200, transactionPage(listOf(transactionJson())))
            else jsonResponse(200, pageOf(listOf(walletJson(id = "w1"))))
        }
        env.signedIn()
        env.service.refresh("w1")
        awaitReal { env.requests.any { it.url.encodedPath.endsWith("/wallets") } }
        assertTrue(env.requests.any { it.url.encodedPath.endsWith("/wallets") })
    }

    @Test
    fun aReadThatChangedNothingDoesNotTouchTheWallets() = runTest {
        val env = TransactionTestEnv(backgroundScope) { request ->
            // A changed read asks for a wallet read too; it gets a wallet page, not the transactions page.
            if (request.url.encodedPath.endsWith("/wallets")) jsonResponse(200, pageOf(listOf(walletJson(id = "w1"))))
            else jsonResponse(200, transactionPage(listOf(transactionJson(version = 2))))
        }
        env.signedIn()
        env.seed(transactionJson(version = 2))
        env.service.refresh("w1")
        testScheduler.runCurrent()
        assertEquals(0, env.requests.count { it.url.encodedPath.endsWith("/wallets") })
    }

    @Test
    fun offlineAndFailedOutcomesAreReportedAndKeepTheLocalData() = runTest {
        var offline = true
        val env = TransactionTestEnv(backgroundScope) {
            if (offline) throw kotlinx.io.IOException("offline") else jsonResponse(503, problemBody(503, "SERVICE_UNAVAILABLE"))
        }
        env.signedIn()
        env.seed(transactionJson(version = 2))
        assertEquals(RefreshOutcome.Offline, env.service.refresh("w1"))
        offline = false
        assertEquals(RefreshOutcome.Failed, env.service.refresh("w1"))
        assertEquals(setOf("t1"), env.transactionDao.rows.value.keys)
        // A failed read does not start the floor: the next open tries again.
        offline = true
        assertEquals(RefreshOutcome.Offline, env.service.refresh("w1"))
    }
}
