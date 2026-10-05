package com.fares.saveme.feature.recurring.domain

import com.fares.saveme.core.network.AppJson
import com.fares.saveme.core.sync.FakeClock
import com.fares.saveme.core.sync.FakeHandler
import com.fares.saveme.core.sync.FakePendingChangeDao
import com.fares.saveme.core.sync.FakeSyncScheduler
import com.fares.saveme.core.sync.ScopeKind
import com.fares.saveme.core.sync.SyncEngine
import com.fares.saveme.core.sync.SyncScope
import com.fares.saveme.feature.account.FakeSyncScopeDao
import com.fares.saveme.feature.recurring.FakeOccurrenceDao
import com.fares.saveme.feature.recurring.data.OccurrenceRepository
import com.fares.saveme.feature.recurring.data.dto.OccurrencePayload
import com.fares.saveme.feature.recurring.data.local.OccurrenceEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Spec 014 research R-05: the occurrences still scheduled in some wallets and a range, the queue laid over them. */
class ScheduledMoneyTest {

    private val from = LocalDate(2026, 10, 1)
    private val to = LocalDate(2026, 10, 31)
    private val ids = setOf("w1", "w2")

    private class Rig {
        val dao = FakeOccurrenceDao()
        val clock = FakeClock()
        val engine = SyncEngine(FakePendingChangeDao(), FakeSyncScopeDao(), listOf(FakeHandler("occurrence")), FakeSyncScheduler(), currentAccountId = { "u1" }, clock = clock)
        val repository = OccurrenceRepository(dao, { "u1" }, { engine.get("occurrence", it) != null }, clock)
        val scheduled = OccurrenceScheduledMoney({ engine }, repository, { "u1" })

        suspend fun putAll(rows: List<OccurrenceEntity>) = rows.forEach { dao.upsert(it) }
    }

    private fun occurrence(
        id: String,
        wallet: String = "w1",
        date: String = "2026-10-28",
        status: String = "scheduled",
        kind: String = "expense",
        amount: Long = 500_000,
        counter: String? = null,
        category: String? = "rent",
        account: String = "u1",
    ) = OccurrenceEntity(
        id, account, wallet, "r1", 1, false, date, date, status, kind, amount, "EGP", category, counter, null, null, null, false, null, null,
        1, 5, "2026-09-01T00:00:00Z", "2026-09-01T00:00:00Z", "2026-09-01T00:00:00Z",
    )

    private fun payload(status: String = "scheduled", date: String = "2026-10-28", amount: Long = 500_000, category: String? = "rent") =
        AppJson.encodeToString(OccurrencePayload.serializer(), OccurrencePayload(status, date, amount, category))

    private suspend fun Rig.lines() = scheduled.observe(ids, from, to).first()

    @Test
    fun onlyScheduledOccurrencesInTheRangeForTheWalletsOrAsTheirDestination() = runTest {
        val rig = Rig()
        rig.putAll(
            listOf(
                occurrence("rent"), occurrence("confirmed", status = "confirmed"), occurrence("skipped", status = "skipped"),
                occurrence("late", date = "2026-11-02"), occurrence("early", date = "2026-09-30"),
                occurrence("elsewhere", wallet = "w9"), occurrence("intoMine", wallet = "w9", kind = "transfer", counter = "w2", category = null),
                occurrence("someoneElse", account = "u9"),
            ),
        )
        assertEquals(setOf("rent", "intoMine"), rig.lines().map { it.occurrenceId }.toSet())
        assertEquals(500_000L, rig.lines().first { it.occurrenceId == "rent" }.amountMinor)
    }

    private suspend fun OccurrenceEntity.save(rig: Rig) = rig.dao.upsert(this)

    @Test
    fun aWaitingConfirmOrSkipRemovesTheOccurrence() = runTest {
        val rig = Rig()
        occurrence("a").save(rig)
        occurrence("b").save(rig)
        rig.engine.enqueue("occurrence", "a", SyncScope(ScopeKind.Wallet, "w1"), 1, payload(status = "confirmed"))
        rig.engine.enqueue("occurrence", "b", SyncScope(ScopeKind.Wallet, "w1"), 1, payload(status = "skipped"))
        assertEquals(emptyList(), rig.lines())
    }

    @Test
    fun aWaitingEditsValuesApplyAndItCanMoveInOrOutOfTheRange() = runTest {
        val rig = Rig()
        occurrence("edited").save(rig)
        occurrence("movedOut", date = "2026-10-20").save(rig)
        occurrence("movedIn", date = "2026-11-20").save(rig)
        rig.engine.enqueue("occurrence", "edited", SyncScope(ScopeKind.Wallet, "w1"), 1, payload(amount = 480_000, category = "other"))
        rig.engine.enqueue("occurrence", "movedOut", SyncScope(ScopeKind.Wallet, "w1"), 1, payload(date = "2026-12-01"))
        rig.engine.enqueue("occurrence", "movedIn", SyncScope(ScopeKind.Wallet, "w1"), 1, payload(date = "2026-10-29"))
        val lines = rig.lines().associateBy { it.occurrenceId }
        assertEquals(setOf("edited", "movedIn"), lines.keys)
        assertEquals(480_000, lines.getValue("edited").amountMinor)
        assertEquals("other", lines.getValue("edited").categoryId)
        assertEquals(LocalDate(2026, 10, 29), lines.getValue("movedIn").date)
    }

    @Test
    fun anUnSkipWaitingMakesASkippedOccurrenceScheduledAgain() = runTest {
        val rig = Rig()
        occurrence("s", status = "skipped").save(rig)
        rig.engine.enqueue("occurrence", "s", SyncScope(ScopeKind.Wallet, "w1"), 1, payload(status = "scheduled"))
        assertEquals(listOf("s"), rig.lines().map { it.occurrenceId })
    }

    @Test
    fun theWaitingFlagTellsWhenAnOccurrenceChangeWaitsInTheRange() = runTest {
        val rig = Rig()
        occurrence("a").save(rig)
        occurrence("far", date = "2027-03-01").save(rig)
        assertFalse(rig.scheduled.observeWaiting(ids, from, to).first())
        rig.engine.enqueue("occurrence", "far", SyncScope(ScopeKind.Wallet, "w1"), 1, payload(date = "2027-03-01", status = "skipped"))
        assertFalse(rig.scheduled.observeWaiting(ids, from, to).first(), "a change entirely outside the range does not count")
        rig.engine.enqueue("occurrence", "a", SyncScope(ScopeKind.Wallet, "w1"), 1, payload(status = "skipped"))
        assertTrue(rig.scheduled.observeWaiting(ids, from, to).first())
    }

    @Test
    fun nothingWithoutAnAccountOrWallets() = runTest {
        val rig = Rig()
        occurrence("a").save(rig)
        assertEquals(emptyList(), OccurrenceScheduledMoney({ rig.engine }, rig.repository, { null }).observe(ids, from, to).first())
        assertEquals(emptyList(), rig.scheduled.observe(emptySet(), from, to).first())
        assertEquals(emptyList(), NoScheduledMoney.observe(ids, from, to).first())
        assertFalse(NoScheduledMoney.observeWaiting(ids, from, to).first())
    }
}
