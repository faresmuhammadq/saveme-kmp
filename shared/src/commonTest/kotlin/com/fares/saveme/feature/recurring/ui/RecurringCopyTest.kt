package com.fares.saveme.feature.recurring.ui

import com.fares.saveme.feature.recurring.domain.model.AutoConfirmRefusal
import com.fares.saveme.feature.recurring.domain.model.ConfirmField
import com.fares.saveme.feature.recurring.domain.model.Frequency
import com.fares.saveme.feature.recurring.domain.model.OccurrenceState
import com.fares.saveme.feature.recurring.domain.model.RuleField
import com.fares.saveme.feature.recurring.domain.model.RuleFieldProblem
import com.fares.saveme.feature.recurring.domain.model.RuleStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import saveme.shared.generated.resources.Res
import saveme.shared.generated.resources.error_generic
import saveme.shared.generated.resources.error_generic_reference
import saveme.shared.generated.resources.recurring_error_amount_empty
import saveme.shared.generated.resources.recurring_error_category_required
import saveme.shared.generated.resources.recurring_error_immutable
import saveme.shared.generated.resources.recurring_error_invalid
import saveme.shared.generated.resources.recurring_error_out_of_range
import saveme.shared.generated.resources.recurring_error_required
import saveme.shared.generated.resources.recurring_reason_no_right_other
import saveme.shared.generated.resources.refusal_now_viewer
import saveme.shared.generated.resources.recurring_reason_occurrence_gone
import saveme.shared.generated.resources.recurring_reason_gone
import saveme.shared.generated.resources.tx_error_amount_range

/** Refusal and field copy is chosen by code and pointer, never by the server's text, and is always the rule's own (P-02.4, P-06.5, FR-035). */
class RecurringCopyTest {

    @Test
    fun everyCodeOfSpecSection5MapsToItsKind() {
        assertEquals(RecurringRefusalKind.NotVerified, recurringRefusalKind("EMAIL_NOT_VERIFIED", emptyMap()))
        assertEquals(RecurringRefusalKind.NoLongerAvailable, recurringRefusalKind("NOT_FOUND", emptyMap()))
        assertEquals(RecurringRefusalKind.NoRight, recurringRefusalKind("FORBIDDEN", emptyMap()))
        assertEquals(RecurringRefusalKind.NoRightInOtherWallet, recurringRefusalKind("FORBIDDEN", emptyMap(), isTransfer = true))
        assertEquals(RecurringRefusalKind.CategoryUnusable, recurringRefusalKind("VALIDATION_FAILED", mapOf("category" to "INVALID_ENUM_VALUE")))
        assertEquals(RecurringRefusalKind.CategoryUnusable, recurringRefusalKind("VALIDATION_FAILED", mapOf("payoutCategory" to "INVALID_ENUM_VALUE")))
        assertEquals(RecurringRefusalKind.CurrenciesDiffer, recurringRefusalKind("VALIDATION_FAILED", mapOf("destination" to "CURRENCY_MISMATCH")))
        assertEquals(RecurringRefusalKind.DestinationUnavailable, recurringRefusalKind("VALIDATION_FAILED", mapOf("destination" to "INVALID_ENUM_VALUE")))
        assertEquals(RecurringRefusalKind.CurrencyChanged, recurringRefusalKind("VALIDATION_FAILED", mapOf("currency" to "CURRENCY_MISMATCH")))
        assertEquals(RecurringRefusalKind.CurrenciesDiffer, recurringRefusalKind("VALIDATION_FAILED", mapOf("status" to "CURRENCY_MISMATCH")))
        assertEquals(RecurringRefusalKind.DestinationUnavailable, recurringRefusalKind("VALIDATION_FAILED", mapOf("status" to "INVALID_ENUM_VALUE"), isTransfer = true))
        assertEquals(RecurringRefusalKind.ConfirmationRefused, recurringRefusalKind("VALIDATION_FAILED", mapOf("status" to "REQUIRED")))
        assertEquals(RecurringRefusalKind.Immutable, recurringRefusalKind("VALIDATION_FAILED", mapOf("frequency" to "IMMUTABLE_FIELD")))
        assertEquals(RecurringRefusalKind.OutOfRange, recurringRefusalKind("VALIDATION_FAILED", mapOf("amount" to "OUT_OF_RANGE")))
        assertEquals(RecurringRefusalKind.Invalid, recurringRefusalKind("VALIDATION_FAILED", mapOf("frequency" to "INVALID_ENUM_VALUE")))
        assertEquals(RecurringRefusalKind.Invalid, recurringRefusalKind("INVALID_ID", emptyMap()))
        assertEquals(RecurringRefusalKind.Other, recurringRefusalKind("SOMETHING_NEW", emptyMap()))
        assertEquals(RecurringRefusalKind.Other, recurringRefusalKind(null, emptyMap()))
    }

    @Test
    fun everyRefusalKindHasItsOwnWordingAndNeverTheGenericOne() {
        RecurringRefusalKind.entries.forEach { kind ->
            val res = kind.messageRes()
            assertNotEquals(Res.string.error_generic, res, kind.name)
            assertNotEquals(Res.string.error_generic_reference, res, kind.name)
        }
        assertEquals(RecurringRefusalKind.entries.size, RecurringRefusalKind.entries.map { it.messageRes() }.toSet().size)
    }

    @Test
    fun aForbiddenTransferNamesTheOtherWalletAndAGoneOccurrenceSaysSo() {
        assertEquals(Res.string.recurring_reason_no_right_other, RecurringRefusalKind.NoRightInOtherWallet.messageRes())
        // A write refused because the role changed to viewer says so (spec 013 FR-027).
        assertEquals(Res.string.refusal_now_viewer, RecurringRefusalKind.NoRight.messageRes())
        assertEquals(Res.string.recurring_reason_gone, RecurringRefusalKind.NoLongerAvailable.messageRes(isOccurrence = false))
        assertEquals(Res.string.recurring_reason_occurrence_gone, RecurringRefusalKind.NoLongerAvailable.messageRes(isOccurrence = true))
    }

    @Test
    fun onlyAnUnverifiedAddressCanBeRetriedUnchanged() {
        assertEquals(listOf(RecurringRefusalKind.NotVerified), RecurringRefusalKind.entries.filter { it.canRetryUnchanged })
    }

    @Test
    fun everyAutoConfirmRefusalMapsToARecurringKey() {
        val all = AutoConfirmRefusal.entries.map { it.messageRes() }
        assertEquals(AutoConfirmRefusal.entries.size, all.toSet().size)
    }

    @Test
    fun everyFieldProblemMapsToAKey() {
        val problems = listOf(
            RuleFieldProblem.Required, RuleFieldProblem.Invalid, RuleFieldProblem.OutOfRange(1, 365), RuleFieldProblem.TooLong(100),
            RuleFieldProblem.AmountDigits(2), RuleFieldProblem.TotalTooSmall, RuleFieldProblem.PayoutTooLarge, RuleFieldProblem.Frozen,
            RuleFieldProblem.CutsResolvedRound, RuleFieldProblem.FromOutOfRange,
        )
        problems.forEach { assertNotNull(it.messageRes(RuleField.Interval)) }
        assertEquals(problems.size, problems.map { it.messageRes(RuleField.Interval) }.size)
    }

    @Test
    fun problemsDependOnTheirField() {
        assertEquals(Res.string.recurring_error_amount_empty, RuleFieldProblem.Required.messageRes(RuleField.Amount))
        assertEquals(Res.string.recurring_error_category_required, RuleFieldProblem.Required.messageRes(RuleField.Category))
        assertEquals(Res.string.recurring_error_required, RuleFieldProblem.Required.messageRes(RuleField.Note))
        assertEquals(Res.string.tx_error_amount_range, RuleFieldProblem.OutOfRange(0, 1).messageRes(RuleField.Amount))
        assertEquals(Res.string.recurring_error_out_of_range, RuleFieldProblem.OutOfRange(1, 365).messageRes(RuleField.Interval))
    }

    @Test
    fun serverCodesAtAFieldReadAsThatField() {
        assertEquals(Res.string.recurring_error_immutable, serverFieldMessageRes("IMMUTABLE_FIELD", RuleField.Frequency))
        assertEquals(Res.string.recurring_error_out_of_range, serverFieldMessageRes("OUT_OF_RANGE", RuleField.Interval))
        assertEquals(Res.string.recurring_error_required, serverFieldMessageRes("REQUIRED", RuleField.Note))
        assertEquals(Res.string.recurring_error_invalid, serverFieldMessageRes("INVALID_ENUM_VALUE", RuleField.Frequency))
    }

    @Test
    fun everyStateStatusAndFrequencyHasALabel() {
        assertEquals(OccurrenceState.entries.size, OccurrenceState.entries.map { it.labelRes() }.toSet().size)
        assertEquals(RuleStatus.entries.size, RuleStatus.entries.map { it.labelRes() }.toSet().size)
        assertEquals(Frequency.entries.size, Frequency.entries.map { it.labelRes() }.toSet().size)
        ConfirmField.entries.forEach { assertNotNull(it.asRuleField()) }
    }

    @Test
    fun everyKnownFrequencyHasCadenceWordsAndAnUnknownOneHasNone() {
        val known = Frequency.entries.filter { it != Frequency.Unknown }
        assertEquals(known.size, known.map { it.cadenceRes() }.toSet().size)
        known.forEach { assertNotNull(it.cadenceRes()) }
        assertNull(Frequency.Unknown.cadenceRes())
    }
}
