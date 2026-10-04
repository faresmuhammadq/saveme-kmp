package com.fares.saveme.feature.recurring.domain

import com.fares.saveme.core.network.AppJson
import com.fares.saveme.core.sync.ScopeKind
import com.fares.saveme.core.sync.SyncScope
import com.fares.saveme.feature.recurring.RecurringTestEnv
import com.fares.saveme.feature.recurring.data.dto.OccurrencePayload
import com.fares.saveme.feature.recurring.occurrenceJson
import com.fares.saveme.feature.recurring.ruleJson
import com.fares.saveme.feature.transactions.domain.OccurrenceTransactions
import com.fares.saveme.feature.transactions.domain.PendingConfirmation
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/** A wallet that leaves the phone takes its rules, occurrences, waiting changes and pending confirmations (FR-033). */
class RecurringWalletCleanupTest {
    @Test
    fun theWalletsRulesOccurrencesQueueRowsAndPendingConfirmationsGo() = runTest {
        val env = RecurringTestEnv(backgroundScope)
        env.seedRule(ruleJson(id = "r1", walletId = "w1"))
        env.seedRule(ruleJson(id = "r2", walletId = "w2"))
        env.seedOccurrence(occurrenceJson(id = "o1", walletId = "w1", ruleId = "r1"))
        env.seedOccurrence(occurrenceJson(id = "o2", walletId = "w2", ruleId = "r2"))
        env.engine.enqueue(
            "occurrence", "o1", SyncScope(ScopeKind.Wallet, "w1"), 1,
            AppJson.encodeToString(OccurrencePayload.serializer(), OccurrencePayload("confirmed", "2026-11-01", 100, null, transactionId = "t1")),
        )
        val dropped = mutableListOf<String>()
        val transactions = object : OccurrenceTransactions {
            override suspend fun placeConfirmation(confirmation: PendingConfirmation) = Unit
            override suspend fun updateConfirmation(confirmation: PendingConfirmation) = Unit
            override suspend fun dropConfirmation(transactionId: String) = Unit
            override suspend fun dropConfirmationOf(occurrenceId: String, exceptTransactionId: String?) { dropped += occurrenceId }
            override suspend fun sweepConfirmations(walletId: String, confirmedTransactionIds: Set<String>) = Unit
        }
        val cleanup = RecurringWalletCleanup(env.rules, env.occurrences, transactions, { env.engine }, backgroundScope)

        cleanup.onWalletDeleted("w1")
        runCurrent()

        assertNull(env.ruleDao.get("r1"))
        assertNull(env.occurrenceDao.get("o1"))
        assertNotNull(env.ruleDao.get("r2"))
        assertNotNull(env.occurrenceDao.get("o2"))
        assertNull(env.engine.get("occurrence", "o1"))
        assertEquals(listOf("o1"), dropped)
    }
}
