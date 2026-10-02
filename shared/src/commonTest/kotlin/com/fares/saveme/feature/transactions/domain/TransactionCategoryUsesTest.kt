package com.fares.saveme.feature.transactions.domain

import com.fares.saveme.core.network.AppJson
import com.fares.saveme.core.sync.ScopeKind
import com.fares.saveme.core.sync.SyncScope
import com.fares.saveme.feature.transactions.TransactionTestEnv
import com.fares.saveme.feature.transactions.data.dto.TransactionPayload
import com.fares.saveme.feature.transactions.transactionJson
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

/** C5 must not offer Delete for a category an unsent change uses (FR-030). */
class TransactionCategoryUsesTest {
    private val w1 = SyncScope(ScopeKind.Wallet, "w1")

    private fun payload(category: String?) =
        AppJson.encodeToString(TransactionPayload.serializer(), TransactionPayload("expense", "2026-10-01", 100, "EGP", category))

    @Test
    fun itReportsTheCategoriesOfNonDeletionChangesOnly() = runTest {
        val env = TransactionTestEnv(backgroundScope)
        val uses = TransactionCategoryUses { env.engine }
        assertEquals(emptySet(), uses.usedLocally().first())
        env.engine.enqueue("transaction", "t1", w1, 0, payload("c1"))
        env.engine.enqueue("transaction", "t2", w1, 0, payload("c2"))
        env.engine.enqueue("transaction", "t3", w1, 0, payload(null))
        env.seed(transactionJson(id = "t4", categoryId = "c9", version = 2))
        env.engine.enqueueDelete("transaction", "t4", w1, 2)
        assertEquals(setOf("c1", "c2"), uses.usedLocally().first())
        env.engine.discard("transaction", "t1")
        assertEquals(setOf("c2"), uses.usedLocally().first())
    }
}
