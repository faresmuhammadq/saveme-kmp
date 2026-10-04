package com.fares.saveme.feature.recurring.data

import com.fares.saveme.core.network.AppJson
import com.fares.saveme.core.sync.ScopeKind
import com.fares.saveme.core.sync.SyncScope
import com.fares.saveme.feature.recurring.RecurringTestEnv
import com.fares.saveme.feature.recurring.occurrenceJson
import com.fares.saveme.feature.recurring.ruleJson
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class RecurringFeedAppliersTest {
    private val wallet = SyncScope(ScopeKind.Wallet, "w1")

    private fun env(scope: kotlinx.coroutines.CoroutineScope) = RecurringTestEnv(scope)

    private fun RecurringTestEnv.ruleApplier() = RecurringRuleFeedApplier(rules)
    private fun RecurringTestEnv.occurrenceApplier() = OccurrenceFeedApplier(occurrences, dropped.drop)
    private fun rows(vararg json: String) = json.map { AppJson.parseToJsonElement(it) }

    @Test
    fun theyConsumeTheWalletScopesRecurringRowTypes() = runTest {
        val env = env(backgroundScope)
        assertEquals(ScopeKind.Wallet, env.ruleApplier().scopeKind)
        assertEquals("recurring_rule", env.ruleApplier().rowType)
        assertEquals(ScopeKind.Wallet, env.occurrenceApplier().scopeKind)
        assertEquals("occurrence", env.occurrenceApplier().rowType)
    }

    @Test
    fun anOlderVersionIsIgnoredAndAnEqualOneIsApplied() = runTest {
        val env = env(backgroundScope)
        env.ruleApplier().apply(wallet, rows(ruleJson(version = 3, description = "Three")))
        env.ruleApplier().apply(wallet, rows(ruleJson(version = 2, description = "Two")))
        assertEquals("Three", env.ruleDao.get("r1")!!.description)
        env.ruleApplier().apply(wallet, rows(ruleJson(version = 3, description = "Again")))
        assertEquals("Again", env.ruleDao.get("r1")!!.description)

        env.occurrenceApplier().apply(wallet, rows(occurrenceJson(version = 3, description = "Three")))
        env.occurrenceApplier().apply(wallet, rows(occurrenceJson(version = 2, description = "Two")))
        assertEquals("Three", env.occurrenceDao.get("o1")!!.description)
        env.occurrenceApplier().apply(wallet, rows(occurrenceJson(version = 3, description = "Again")))
        assertEquals("Again", env.occurrenceDao.get("o1")!!.description)
    }

    @Test
    fun aTombstoneRemovesUnlessAChangeWaits() = runTest {
        val env = env(backgroundScope)
        env.seedRule(ruleJson(id = "ra"))
        env.seedRule(ruleJson(id = "rb"))
        env.seedOccurrence(occurrenceJson(id = "oa", ruleId = "ra"))
        env.seedOccurrence(occurrenceJson(id = "ob", ordinal = 2))
        env.engine.enqueue("recurring_rule", "rb", wallet, 1, "{}")
        env.engine.enqueue("occurrence", "ob", wallet, 1, "{}")
        val gone = "2026-10-03T00:00:00Z"
        env.ruleApplier().apply(wallet, rows(ruleJson(id = "ra", version = 2, deletedAt = gone), ruleJson(id = "rb", version = 2, deletedAt = gone)))
        env.occurrenceApplier().apply(wallet, rows(occurrenceJson(id = "ob", version = 2, deletedAt = gone)))
        assertNull(env.ruleDao.get("ra"))
        assertNotNull(env.ruleDao.get("rb"), "a change waits")
        assertNotNull(env.occurrenceDao.get("ob"), "a change waits")
        assertNull(env.occurrenceDao.get("oa"), "removed with its rule, which had no change waiting")
    }

    @Test
    fun aRuleTombstoneRemovesItsOccurrencesWithoutWaitingChanges() = runTest {
        val env = env(backgroundScope)
        env.seedRule()
        env.seedOccurrence(occurrenceJson(id = "o1"))
        env.seedOccurrence(occurrenceJson(id = "o9", ruleId = "other"))
        env.ruleApplier().apply(wallet, rows(ruleJson(version = 2, deletedAt = "2026-10-03T00:00:00Z")))
        assertNull(env.occurrenceDao.get("o1"))
        assertNotNull(env.occurrenceDao.get("o9"))
    }

    @Test
    fun aSweepRemovesUnseenSyncedRowsExceptWaitingOnesAndPlaceholders() = runTest {
        val env = env(backgroundScope)
        env.seedRule(ruleJson(id = "seen"))
        env.seedRule(ruleJson(id = "gone"))
        env.seedRule(ruleJson(id = "waiting"))
        env.rules.putPlaceholder(
            NewRule(
                "placeholder", "u1", "w1", "custom", "active", "expense", 1, "per_occurrence", "EGP", "c1", null, null, null, null, false,
                "monthly", 1, 1, "2026-11-01", null, null, null, null, null,
            ),
        )
        env.seedOccurrence(occurrenceJson(id = "os"))
        env.seedOccurrence(occurrenceJson(id = "og", ordinal = 2))
        env.seedOccurrence(occurrenceJson(id = "ow", ordinal = 3))
        env.engine.enqueue("recurring_rule", "waiting", wallet, 1, "{}")
        env.engine.enqueue("occurrence", "ow", wallet, 1, "{}")
        env.ruleApplier().sweep(wallet, setOf("seen"))
        env.occurrenceApplier().sweep(wallet, setOf("os"))
        assertEquals(setOf("seen", "waiting", "placeholder"), env.ruleDao.rows.value.keys)
        assertEquals(setOf("os", "ow"), env.occurrenceDao.rows.value.keys)
    }

    @Test
    fun aCopyConfirmedUnderAnotherTransactionIdDropsThePendingConfirmation() = runTest {
        val env = env(backgroundScope)
        env.seedOccurrence()
        env.occurrenceApplier().apply(wallet, rows(occurrenceJson(version = 2, status = "confirmed", transactionId = "server-tx")))
        assertEquals(listOf<Pair<String, String?>>("o1" to "server-tx"), env.dropped.calls)
        env.dropped.calls.clear()
        env.occurrenceApplier().apply(wallet, rows(occurrenceJson(version = 3, status = "confirmed", transactionId = "server-tx")))
        assertEquals(emptyList(), env.dropped.calls, "already confirmed: nothing to drop again")
    }

    @Test
    fun theFeedKeepsThePhoneOnlyNotice() = runTest {
        val env = env(backgroundScope)
        env.seedOccurrence()
        env.occurrences.storeCopy(com.fares.saveme.feature.recurring.occurrenceDto(occurrenceJson(version = 2, status = "confirmed", transactionId = "x")), false, noticeAlreadyConfirmed = true)
        env.occurrenceApplier().apply(wallet, rows(occurrenceJson(version = 2, status = "confirmed", transactionId = "x", description = "Later")))
        assertEquals("already_confirmed", env.occurrenceDao.get("o1")!!.localNotice)
    }
}
