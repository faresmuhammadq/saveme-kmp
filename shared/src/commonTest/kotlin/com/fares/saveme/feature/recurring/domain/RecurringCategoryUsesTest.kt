package com.fares.saveme.feature.recurring.domain

import com.fares.saveme.core.network.AppJson
import com.fares.saveme.core.sync.ScopeKind
import com.fares.saveme.core.sync.SyncScope
import com.fares.saveme.feature.recurring.RecurringTestEnv
import com.fares.saveme.feature.recurring.data.dto.OccurrencePayload
import com.fares.saveme.feature.recurring.data.dto.RulePayload
import com.fares.saveme.feature.recurring.ruleJson
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

/** C5 must not offer Delete for a category an unsent rule or occurrence change uses, payout category included (FR-032). */
class RecurringCategoryUsesTest {
    private val w1 = SyncScope(ScopeKind.Wallet, "w1")

    private fun rule(category: String?, payout: String? = null) = AppJson.encodeToString(
        RulePayload.serializer(),
        RulePayload("custom", "active", "expense", 100, "per_occurrence", "EGP", category, null, null, null, null, false, "monthly", 1, 1, "2026-11-01", null, null, null, payout),
    )

    private fun occurrence(category: String?) =
        AppJson.encodeToString(OccurrencePayload.serializer(), OccurrencePayload("scheduled", "2026-11-01", 100, category))

    @Test
    fun itReportsRuleAndOccurrenceCategoriesOfNonDeletionChanges() = runTest {
        val env = RecurringTestEnv(backgroundScope)
        val uses = RecurringCategoryUses { env.engine }
        assertEquals(emptySet(), uses.usedLocally().first())
        env.engine.enqueue("recurring_rule", "r1", w1, 0, rule("c1", payout = "ci"))
        env.engine.enqueue("recurring_rule", "r2", w1, 0, rule(null))
        env.engine.enqueue("occurrence", "o1", w1, 1, occurrence("c2"))
        env.seedRule(ruleJson(id = "r3", categoryId = "c9", version = 2))
        env.engine.enqueueDelete("recurring_rule", "r3", w1, 2)
        assertEquals(setOf("c1", "ci", "c2"), uses.usedLocally().first())
        env.engine.discard("recurring_rule", "r1")
        assertEquals(setOf("c2"), uses.usedLocally().first())
    }
}
