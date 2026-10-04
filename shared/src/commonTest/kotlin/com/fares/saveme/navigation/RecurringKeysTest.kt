package com.fares.saveme.navigation

import com.fares.saveme.feature.recurring.domain.model.RulePrefill
import com.fares.saveme.feature.transactions.domain.model.TransactionKind
import kotlinx.datetime.LocalDate
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/** The recurring keys survive a round trip and never print what the person typed (P-09.2). */
class RecurringKeysTest {
    private val prefill = RulePrefill("w1", TransactionKind.Expense, 123_456, "c-secret", null, "Landlord Sara", "memo text", "Sara", LocalDate.parse("2026-11-01"))

    @Test
    fun theEditorKeyRedactsThePrefillInToString() {
        val text = RuleEditorKey(walletId = "w1", prefill = prefill).toString()
        assertFalse("123456" in text || "Landlord" in text || "memo" in text || "Sara" in text || "c-secret" in text, text)
        assertFalse("123456" in prefill.toString() || "Landlord" in prefill.toString())
    }

    @Test
    fun keysSerializeAndComeBack() {
        val key = RuleEditorKey(walletId = "w1", ruleId = null, fromDate = false, prefill = prefill)
        assertEquals(key, Json.decodeFromString(RuleEditorKey.serializer(), Json.encodeToString(RuleEditorKey.serializer(), key)))
        val occurrence = OccurrenceKey("o1", confirm = true)
        assertEquals(occurrence, Json.decodeFromString(OccurrenceKey.serializer(), Json.encodeToString(OccurrenceKey.serializer(), occurrence)))
        assertEquals(RecurringKey(null), Json.decodeFromString(RecurringKey.serializer(), Json.encodeToString(RecurringKey.serializer(), RecurringKey())))
        assertEquals(RuleDetailKey("r1"), Json.decodeFromString(RuleDetailKey.serializer(), Json.encodeToString(RuleDetailKey.serializer(), RuleDetailKey("r1"))))
    }
}
