package com.fares.saveme.core.sync

import com.fares.saveme.core.network.AppJson
import com.fares.saveme.core.sync.CapturedHeadsResponses.CATEGORY_A
import com.fares.saveme.core.sync.CapturedHeadsResponses.CATEGORY_B
import com.fares.saveme.core.sync.CapturedHeadsResponses.PAIR_APPLIED
import com.fares.saveme.core.sync.CapturedHeadsResponses.PAIR_APPLIED_REQUEST
import com.fares.saveme.core.sync.CapturedHeadsResponses.PAIR_ITEM_2_REJECTED
import com.fares.saveme.core.sync.CapturedHeadsResponses.PAIR_ITEM_2_REJECTED_REQUEST
import com.fares.saveme.core.sync.CapturedHeadsResponses.PAIR_REPLAY
import com.fares.saveme.core.sync.CapturedHeadsResponses.PAIR_REPLAY_AFTER_LATER_EDIT
import com.fares.saveme.core.sync.CapturedHeadsResponses.PAIR_REPLAY_AFTER_LATER_EDIT_REQUEST
import com.fares.saveme.core.sync.CapturedHeadsResponses.PAIR_REPLAY_AFTER_REDELETE
import com.fares.saveme.core.sync.CapturedHeadsResponses.PAIR_REPLAY_AFTER_REDELETE_REQUEST
import com.fares.saveme.core.sync.CapturedHeadsResponses.PAIR_REPLAY_REQUEST
import com.fares.saveme.core.sync.CapturedHeadsResponses.PAIR_STALE_LIVE
import com.fares.saveme.core.sync.CapturedHeadsResponses.PAIR_STALE_LIVE_REQUEST
import com.fares.saveme.core.sync.CapturedHeadsResponses.PAIR_STALE_TOMBSTONE
import com.fares.saveme.core.sync.CapturedHeadsResponses.PAIR_STALE_TOMBSTONE_REQUEST
import com.fares.saveme.core.sync.CapturedHeadsResponses.PAIR_VIEWER
import com.fares.saveme.core.sync.CapturedHeadsResponses.PAIR_VIEWER_REQUEST
import com.fares.saveme.core.sync.CapturedHeadsResponses.TX_BAD
import com.fares.saveme.core.sync.CapturedHeadsResponses.TX_OK
import com.fares.saveme.core.sync.CapturedHeadsResponses.TX_STALE_LIVE
import com.fares.saveme.core.sync.CapturedHeadsResponses.TX_STALE_TOMBSTONE
import com.fares.saveme.core.sync.CapturedHeadsResponses.TX_VIEWER
import com.fares.saveme.core.sync.CapturedHeadsResponses.W1
import com.fares.saveme.core.sync.CapturedSyncResponses.Answer
import com.fares.saveme.core.sync.CapturedSyncServer.Companion.itemsOf
import com.fares.saveme.feature.transactions.TransactionTestEnv
import com.fares.saveme.feature.transactions.data.TransactionSyncHandler
import com.fares.saveme.feature.transactions.data.dto.TransactionPayload
import com.fares.saveme.feature.transactions.transactionJson
import com.fares.saveme.feature.wallets.walletJson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.runTest
import kotlinx.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * "Restore with my change" as the pair the live backend answered (contract QA T030, [CapturedHeadsResponses]): the
 * items the engine builds equal the ones the server was sent, and every real answer (applied, replayed after a lost
 * answer, stale, refused for a viewer, refused for the content) settles the one change the way the per-record path
 * settles the save's answer (spec 011 FR-014 - FR-017, R-09, R-10).
 */
class SyncRestorePairCapturedTest {

    private class Pair(scope: CoroutineScope, val id: String, base: Long, storedAmount: Long, payload: TransactionPayload) {
        val server = CapturedSyncServer()
        val env = TransactionTestEnv(scope)
        val pending = FakePendingChangeDao()
        val engine = SyncEngine(
            pending, env.scopeDao, listOf(env.walletHandler, env.syncHandler), FakeSyncScheduler(),
            currentAccountId = { "u1" }, clock = env.clock, syncApi = server.api,
        )

        /** The phone holds the deletion it saw (version [base], a tombstone) and the person chose "Restore with my change". */
        suspend fun prepare(base: Long, storedAmount: Long, payload: TransactionPayload) {
            server.signedIn()
            env.signedIn()
            env.seedWallet(walletJson(id = W1))
            env.seed(transactionJson(id = id, walletId = W1, version = base, amount = storedAmount, categoryId = CATEGORY_A, date = "2026-10-04", deletedAt = "2026-10-04T21:32:40Z"))
            engine.enqueue(TransactionSyncHandler.RECORD_TYPE, id, SyncScope(ScopeKind.Wallet, W1), base, AppJson.encodeToString(TransactionPayload.serializer(), payload))
            pending.upsert(pending.rows.value.getValue("transaction" to id).copy(restore = true))
        }

        fun row() = pending.rows.value["transaction" to id]
        fun local() = env.transactionDao.rows.value[id]
    }

    private fun payload(amount: Long, description: String, category: String = CATEGORY_A) =
        TransactionPayload("expense", "2026-10-04", amount, "EGP", categoryId = category, description = description)

    private suspend fun kotlinx.coroutines.test.TestScope.pair(id: String, base: Long, storedAmount: Long, payload: TransactionPayload, answer: Answer): Pair {
        val p = Pair(backgroundScope, id, base, storedAmount, payload)
        p.prepare(base, storedAmount, payload)
        p.server.batch = { _, _ -> answer }
        return p
    }

    @Test
    fun theLiveServerAcceptedTheItemsTheEngineBuildsAndBothAppliedSettleTheChangeAtVersionFour() = runTest {
        val p = pair(TX_OK, 2, 5_000, payload(5_500, "pair ok edited"), PAIR_APPLIED)

        p.engine.drain()

        assertEquals(1, p.server.uploads.size, "the restore and the save in one request")
        assertEquals(itemsOf(PAIR_APPLIED_REQUEST), p.server.uploads.single(), "restore at 2, upsert at 3: what the live server accepted")
        assertNull(p.row(), "settled as one change")
        assertEquals(4L, p.local()?.version)
        assertEquals(5_500L, p.local()?.amountMinor)
        assertNull(p.local()?.deletedAt, "live again")
    }

    @Test
    fun aPairReplayedAfterALostAnswerIsAnsweredAppliedTwiceWithTheUnchangedRecordAndSettlesOnce() = runTest {
        val p = Pair(backgroundScope, TX_OK, 2, 5_000, payload(5_500, "pair ok edited"))
        p.prepare(2, 5_000, payload(5_500, "pair ok edited"))
        // The first answer never arrives although the server applied the pair (the record is at n + 2 = 4); the retry is the same items.
        p.server.batch = { n, _ -> if (n == 0) throw IOException("the answer never arrived") else PAIR_REPLAY }

        assertTrue(p.engine.drain().offline)
        assertEquals("Waiting", p.row()?.state, "nothing lost, nothing settled")
        p.engine.drain()

        assertEquals(p.server.uploads[0], p.server.uploads[1], "the same items again")
        assertEquals(itemsOf(PAIR_REPLAY_REQUEST), p.server.uploads[1])
        assertNull(p.row(), "accepted: no clash, no duplicate, no second bump")
        assertEquals(4L, p.local()?.version, "the replay bumped nothing")
        assertEquals(5_500L, p.local()?.amountMinor)
        assertEquals(1, p.env.transactionDao.rows.value.size)
    }

    @Test
    fun aReplayAfterALaterEditIsAClashOnTheLiveCopyAndNothingIsAppliedToAVersionThePersonDidNotSee() = runTest {
        val p = pair(TX_OK, 2, 5_000, payload(5_500, "pair ok edited"), PAIR_REPLAY_AFTER_LATER_EDIT)

        p.engine.drain()

        assertEquals(itemsOf(PAIR_REPLAY_AFTER_LATER_EDIT_REQUEST), p.server.uploads.single())
        val row = assertNotNull(p.row())
        assertEquals("Clash", row.state, "the restore was a no-op on a live record; the save met v5")
        assertTrue("\"version\":5" in row.clashJson.orEmpty() && "\"amount_minor\":5600" in row.clashJson.orEmpty())
        assertNull(p.local(), "the phone stores nothing until the person chooses")
    }

    @Test
    fun aReplayAfterTheRecordWasDeletedAgainIsAClashOnTheTombstone() = runTest {
        val p = pair(TX_OK, 2, 5_000, payload(5_500, "pair ok edited"), PAIR_REPLAY_AFTER_REDELETE)

        p.engine.drain()

        assertEquals(itemsOf(PAIR_REPLAY_AFTER_REDELETE_REQUEST), p.server.uploads.single())
        val row = assertNotNull(p.row())
        assertEquals("Clash", row.state, "item 1 conflicted; item 2 is ignored")
        assertTrue("\"version\":6" in row.clashJson.orEmpty() && "\"deleted_at\":\"2026" in row.clashJson.orEmpty())
    }

    @Test
    fun aStalePairAgainstARecordRestoredAndEditedByAnotherDeviceIsTheSameClashAsToday() = runTest {
        val p = pair(TX_STALE_LIVE, 2, 3_300, payload(3_500, "A pair vs live"), PAIR_STALE_LIVE)

        p.engine.drain()

        assertEquals(itemsOf(PAIR_STALE_LIVE_REQUEST), p.server.uploads.single())
        val row = assertNotNull(p.row())
        assertEquals("Clash", row.state)
        assertTrue("\"version\":4" in row.clashJson.orEmpty() && "\"amount_minor\":3400" in row.clashJson.orEmpty(), "the other device's live copy")
    }

    @Test
    fun aStalePairAgainstARecordDeletedAgainIsAClashOnItsTombstone() = runTest {
        val p = pair(TX_STALE_TOMBSTONE, 2, 8_000, payload(8_500, "A pair stale3"), PAIR_STALE_TOMBSTONE)

        p.engine.drain()

        assertEquals(itemsOf(PAIR_STALE_TOMBSTONE_REQUEST), p.server.uploads.single())
        val row = assertNotNull(p.row())
        assertEquals("Clash", row.state)
        assertTrue("\"version\":5" in row.clashJson.orEmpty())
    }

    @Test
    fun aViewersPairIsRefusedForbiddenOnBothItemsAndTheChangeIsNotSavedWithThatReason() = runTest {
        val p = pair(TX_VIEWER, 2, 9_000, payload(9_500, "C pair", CATEGORY_B), PAIR_VIEWER)

        p.engine.drain()

        assertEquals(itemsOf(PAIR_VIEWER_REQUEST), p.server.uploads.single())
        val row = assertNotNull(p.row())
        assertEquals("Refused", row.state)
        assertEquals("FORBIDDEN", AppJson.decodeFromString(Refusal.serializer(), row.refusalJson!!).code)
        assertNull(p.local(), "nothing stored from a refused pair")
    }

    @Test
    fun aRefusalOfTheSecondItemsContentIsNotSavedWithItsFieldAndTheRestoredRecordArrivesWithTheNextRead() = runTest {
        val p = pair(TX_BAD, 2, 1_100, payload(0, "zero"), PAIR_ITEM_2_REJECTED)

        p.engine.drain()

        assertEquals(itemsOf(PAIR_ITEM_2_REJECTED_REQUEST), p.server.uploads.single())
        val row = assertNotNull(p.row())
        assertEquals("Refused", row.state)
        val refusal = AppJson.decodeFromString(Refusal.serializer(), row.refusalJson!!)
        assertEquals("VALIDATION_FAILED", refusal.code)
        assertEquals(listOf("OUT_OF_RANGE"), refusal.fields.values.toList(), "the same field error the single save gives")
        assertNull(p.local(), "the restored copy is not stored from the pair's first answer (spec Q3)")
    }
}
