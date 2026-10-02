package com.fares.saveme.feature.transactions.data

import com.fares.saveme.core.sync.ChangeState
import com.fares.saveme.feature.auth.jsonResponse
import com.fares.saveme.feature.auth.problemBody
import com.fares.saveme.feature.transactions.TransactionTestEnv
import com.fares.saveme.feature.transactions.domain.model.SaveResult
import com.fares.saveme.feature.transactions.domain.model.TransactionDraft
import com.fares.saveme.feature.transactions.transactionBody
import com.fares.saveme.feature.transactions.transactionJson
import com.fares.saveme.feature.wallets.walletJson
import io.ktor.http.HttpMethod
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/** "Restore with my change" through the real engine and handler (FR-024). */
class TransactionRestoreFlowTest {

    @Test
    fun aWaitingEditMeetsATombstoneThenKeepMineSendsThePatchAndThePut() = runTest {
        val tombstone = transactionJson(version = 6, deletedAt = "2026-10-02T09:00:00Z")
        var phase = 0
        val env = TransactionTestEnv(backgroundScope) { request ->
            when {
                phase == 0 -> jsonResponse(409, problemBody(409, "VERSION_CONFLICT", ""","current":$tombstone"""))
                request.method == HttpMethod.Patch -> jsonResponse(200, transactionBody(transactionJson(version = 7, amount = 4_550)))
                else -> jsonResponse(200, transactionBody(transactionJson(version = 8, amount = 6_000, description = "Taxi")))
            }
        }
        env.signedIn()
        env.seedWallet(walletJson(id = "w1"))
        env.seed(transactionJson(version = 5, amount = 4_550))
        env.service.save("t1", TransactionDraft("w1", "expense", LocalDate(2026, 10, 1), 6_000, "EGP", "c1", null, "Taxi", null, null, false), baseVersion = 5)

        env.engine.drain()
        val clashed = env.pendingDao.get("transaction", "t1")!!
        assertEquals(ChangeState.Clash.name, clashed.state)

        phase = 1
        env.service.keepMine("t1")
        assertEquals(true, env.pendingDao.get("transaction", "t1")!!.restore)
        env.engine.drain()

        val methods = env.requests.map { it.method }
        assertEquals(listOf(HttpMethod.Put, HttpMethod.Patch, HttpMethod.Put), methods)
        assertNull(env.pendingDao.get("transaction", "t1"))
        val row = env.transactionDao.get("t1")!!
        assertEquals(8, row.version)
        assertEquals(6_000, row.amountMinor)
        assertNull(row.deletedAt)
        // The restore and the edit together moved the wallet by 15.00 more than before: that waits as carry for a wallet read.
        assertNotNull(row.carryConfirmedAt)
        assertEquals(-1_450, row.carrySourceMinor)
    }

    @Test
    fun aDeleteClashOffersDeleteAnywayWhichSendsTheDeleteOnTopOfTheirVersion() = runTest {
        var phase = 0
        val env = TransactionTestEnv(backgroundScope) {
            if (phase == 0) jsonResponse(409, problemBody(409, "VERSION_CONFLICT", ""","current":${transactionJson(version = 6, amount = 999)}"""))
            else jsonResponse(200, transactionBody(transactionJson(version = 7, deletedAt = "2026-10-02T10:00:00Z")))
        }
        env.signedIn()
        env.seedWallet(walletJson(id = "w1"))
        env.seed(transactionJson(version = 5))
        assertEquals(SaveResult.Saved, env.service.delete("t1"))
        env.engine.drain()
        assertEquals(ChangeState.Clash.name, env.pendingDao.get("transaction", "t1")!!.state)
        phase = 1
        env.service.keepMine("t1")
        assertEquals(6, env.pendingDao.get("transaction", "t1")!!.baseVersion)
        env.engine.drain()
        assertNull(env.pendingDao.get("transaction", "t1"))
        // The deletion is kept only until its carry settles.
        assertNotNull(env.transactionDao.get("t1")!!.deletedAt)
    }
}
