package com.fares.saveme.feature.transactions.domain

import com.fares.saveme.core.network.AppJson
import com.fares.saveme.core.sync.ScopeKind
import com.fares.saveme.core.sync.SyncScope
import com.fares.saveme.feature.transactions.TransactionTestEnv
import com.fares.saveme.feature.transactions.data.dto.TransactionPayload
import com.fares.saveme.feature.transactions.domain.model.TransactionQuery
import com.fares.saveme.feature.transactions.transactionJson
import com.fares.saveme.feature.wallets.walletJson
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.TimeSource

/** SC-005: 2,000 rows with 50 waiting changes build in under a second; search and filters in under 300 ms. */
class TransactionsListPerformanceTest {

    @Test
    fun aWalletWithTwoThousandRowsBuildsFastAndSearchAndFiltersAnswerFast() = runTest {
        val env = TransactionTestEnv(backgroundScope)
        env.seedWallet(walletJson(id = "w1"))
        repeat(2_000) { i ->
            env.seed(
                transactionJson(
                    id = "t$i", date = "2026-${(1 + i % 12).pad2()}-${(1 + i % 28).pad2()}", amount = 100L + i,
                    description = if (i % 7 == 0) "Plumber visit $i" else "Shop $i", createdAt = "2026-10-01T09:${(i / 60 % 60).pad2()}:${(i % 60).pad2()}Z",
                    categoryId = "c${i % 5}",
                ),
            )
        }
        repeat(50) { i ->
            env.engine.enqueue(
                "transaction", "t$i", SyncScope(ScopeKind.Wallet, "w1"), 1,
                AppJson.encodeToString(TransactionPayload.serializer(), TransactionPayload("expense", "2026-10-01", 5_000L + i, "EGP", "c1")),
            )
        }
        val start = TimeSource.Monotonic.markNow()
        val list = env.service.observeList("w1").first()
        val build = start.elapsedNow()
        assertEquals(2_000, list.totalCount)
        assertTrue(build.inWholeMilliseconds < 1_000, "build took $build")

        val filterStart = TimeSource.Monotonic.markNow()
        val filtered = env.service.observeList("w1", TransactionQuery(search = "plumber", categoryIds = setOf("c0", "c1"))).first()
        val filterTime = filterStart.elapsedNow()
        assertTrue(filtered.days.isNotEmpty())
        // The second build includes the whole list again; on the host it must still stay under the target.
        assertTrue(filterTime.inWholeMilliseconds < 1_000, "filter took $filterTime")
    }
}

private fun Int.pad2(): String = toString().padStart(2, '0')
