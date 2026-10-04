package com.fares.saveme.feature.recurring.domain

import com.fares.saveme.feature.recurring.RecurringServiceEnv
import com.fares.saveme.feature.recurring.occurrenceJson
import com.fares.saveme.feature.recurring.ruleJson
import com.fares.saveme.feature.wallets.domain.model.WalletRole
import com.fares.saveme.feature.wallets.walletJson
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Spec section 6: roles come from the wallet; a viewer reads, an editor may change another member's rule. */
class RecurringRolesTest {

    private suspend fun env(scope: kotlinx.coroutines.CoroutineScope, role: String): RecurringServiceEnv {
        val env = RecurringServiceEnv(scope)
        env.seedWallet(walletJson(id = "w1", role = role))
        env.seedRule(ruleJson(id = "r1").replace("\"created_by\":\"u1\"", "\"created_by\":\"u2\""))
        env.seedOccurrence(occurrenceJson(id = "o1", scheduledDate = "2026-10-01"))
        env.seedOccurrence(occurrenceJson(id = "o2", ordinal = 2, scheduledDate = "2026-11-01"))
        return env
    }

    @Test
    fun aViewerSeesEverythingReadOnly() = runTest {
        val env = env(backgroundScope, "viewer")
        val rule = env.service.observeRule("r1").first()!!
        assertEquals(WalletRole.Viewer, rule.myRole)
        assertTrue(rule.readOnly)
        assertFalse(rule.canWrite)
        val lists = env.service.observeOccurrences("w1").first()
        assertEquals(listOf("o1"), lists.due.map { it.id })
        assertEquals(listOf("o2"), lists.upcoming.map { it.id })
        assertTrue((lists.due + lists.upcoming).none { it.canWrite })
    }

    @Test
    fun anEditorWritesAnotherMembersRuleAndItsOccurrences() = runTest {
        val env = env(backgroundScope, "editor")
        val rule = env.service.observeRule("r1").first()!!
        assertFalse(rule.readOnly)
        assertTrue(rule.canWrite)
        assertTrue(env.service.observeOccurrences("w1").first().due.single().canWrite)
    }
}
