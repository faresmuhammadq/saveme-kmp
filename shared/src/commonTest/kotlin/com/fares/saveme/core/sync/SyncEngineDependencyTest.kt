package com.fares.saveme.core.sync

import com.fares.saveme.core.network.ApiErrorCode
import com.fares.saveme.core.network.ApiResult
import com.fares.saveme.core.sync.FakeSyncApi.Companion.rejected
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** A change never goes before the create it needs; it waits without a try (spec 009 FR-022, SC-002). */
class SyncEngineDependencyTest {

    private val wallet = SyncScope(ScopeKind.Wallet, "w1")

    private fun BatchHarness.dependsOnWallet(id: String) {
        handlers[1].prerequisitesOf[id] = listOf(RecordKey("wallet", "w1"))
    }

    private suspend fun BatchHarness.newWallet() = save("w1", base = 0, type = "wallet", scope = wallet)

    private suspend fun BatchHarness.transaction(id: String, base: Long = 0) = save(id, base = base, type = "transaction", scope = wallet)

    @Test
    fun aTransactionWhoseWalletCreateIsEarlierInTheSameRequestIsSentAfterIt() = runTest {
        val h = BatchHarness("wallet", "transaction")
        h.newWallet()
        h.dependsOnWallet("t1")
        h.transaction("t1")
        h.engine.drain()
        assertEquals(1, h.api.batches.size)
        assertEquals(listOf("w1", "t1"), h.api.batches.single().map { it.id })
        assertTrue(h.dao.rows.value.isEmpty())
    }

    @Test
    fun aTransactionWhoseWalletCreateClashedWasRefusedOrIsBackingOffIsHeldWithoutATry() = runTest {
        for (state in listOf("Clash", "Refused")) {
            val h = BatchHarness("wallet", "transaction")
            h.newWallet()
            h.dao.upsert(h.row("w1", "wallet")!!.copy(state = state))
            h.dependsOnWallet("t1")
            h.transaction("t1")
            h.engine.drain()
            val held = assertNotNull(h.row("t1", "transaction"), state)
            assertEquals("Waiting", held.state)
            assertEquals(0, held.attempts)
            assertNull(held.nextAttemptAt)
            assertEquals(emptyList(), h.api.batches, "$state: nothing is sent")
        }

        val backing = BatchHarness("wallet", "transaction")
        backing.newWallet()
        backing.dao.upsert(backing.row("w1", "wallet")!!.copy(nextAttemptAt = "2099-01-01T00:00:00.000Z", attempts = 2))
        backing.dependsOnWallet("t1")
        backing.transaction("t1")
        backing.engine.drain()
        assertEquals(0, backing.row("t1", "transaction")!!.attempts)
        assertEquals(emptyList(), backing.api.batches)
    }

    @Test
    fun aTransactionWhoseWalletCreateIsLaterInTheQueueIsHeldForThisRun() = runTest {
        val h = BatchHarness("wallet", "transaction")
        h.dependsOnWallet("t1")
        h.transaction("t1") // created before the wallet's create
        h.newWallet()
        h.engine.drain()
        assertEquals(listOf(listOf("w1")), h.api.batches.map { b -> b.map { it.id } })
        assertEquals("Waiting", h.row("t1", "transaction")!!.state)
        assertEquals(0, h.row("t1", "transaction")!!.attempts)
    }

    @Test
    fun whenThePrerequisiteIsAcceptedARunIsScheduledAndTheHeldChangeGoesNext() = runTest {
        val h = BatchHarness("wallet", "transaction")
        h.dependsOnWallet("t1")
        h.transaction("t1")
        h.newWallet()
        h.engine.drain()
        val scheduledBefore = h.scheduler.enqueued.size
        assertTrue(scheduledBefore >= 1, "a run is scheduled for what was held")
        h.engine.drain()
        assertEquals(listOf(listOf("w1"), listOf("t1")), h.api.batches.map { b -> b.map { it.id } })
        assertTrue(h.dao.rows.value.isEmpty())
    }

    @Test
    fun aDependentWhosePrerequisiteInTheSameRequestWasNotAcceptedGoesBackToWaitingWithItsAnswerIgnored() = runTest {
        val h = BatchHarness("wallet", "transaction")
        h.newWallet()
        h.dependsOnWallet("t1")
        h.transaction("t1")
        h.api.batchAnswer = { items ->
            ApiResult.Success(
                items.map { if (it.id == "w1") rejected(it, ApiErrorCode.FEATURE_NOT_ENTITLED, 403) else FakeSyncApi.appliedResult(it) },
            )
        }
        h.engine.drain()
        assertEquals("Refused", h.row("w1", "wallet")!!.state)
        val dependent = assertNotNull(h.row("t1", "transaction"))
        assertEquals("Waiting", dependent.state)
        assertEquals(0, dependent.attempts)
        assertEquals(emptyList(), h.handlers[1].applied, "the answer of the dependent was ignored")
    }

    @Test
    fun aDependencyOnAnUnacceptedCategoryInAnotherScopeHoldsAndOneEarlierInTheRequestDoesNot() = runTest {
        val h = BatchHarness("category", "transaction")
        h.handlers[1].prerequisitesOf["t1"] = listOf(RecordKey("wallet", "w1"), RecordKey("category", "c1"))
        h.save("c1", base = 0, type = "category", scope = h.user)
        h.transaction("t1")
        h.engine.drain()
        assertEquals(listOf(listOf("c1", "t1")), h.api.batches.map { b -> b.map { it.id } }, "the category is earlier in the same request")
    }

    @Test
    fun aTransactionEditedToUseACategoryCreatedLaterIsHeldOneRun() = runTest {
        val h = BatchHarness("category", "transaction")
        h.handlers[1].prerequisitesOf["t1"] = listOf(RecordKey("category", "c1"))
        h.transaction("t1", base = 3) // an existing transaction, edited first
        h.save("c1", base = 0, type = "category", scope = h.user)
        h.engine.drain()
        assertEquals(listOf(listOf("c1")), h.api.batches.map { b -> b.map { it.id } })
        h.engine.drain()
        assertEquals(listOf("t1"), h.api.batches.last().map { it.id })
    }

    @Test
    fun aPerRecordPrerequisiteThatWasJustAcceptedDoesNotHoldItsDependent() = runTest {
        val h = BatchHarness("wallet", "transaction")
        h.handlers[0].perRecordOnly += "w1" // a wallet created with an archive goes per record
        h.handlers[0].answer = { EngineHarness.ok(1) }
        h.newWallet()
        h.dependsOnWallet("t1")
        h.transaction("t1")
        h.engine.drain()
        assertEquals(listOf("send w1", "batch 1: t1"), h.log)
    }
}
