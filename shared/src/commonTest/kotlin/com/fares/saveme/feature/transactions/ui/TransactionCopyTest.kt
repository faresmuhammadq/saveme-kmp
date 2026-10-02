package com.fares.saveme.feature.transactions.ui

import com.fares.saveme.feature.transactions.domain.model.RefusalKind
import com.fares.saveme.feature.transactions.domain.model.TransactionField
import com.fares.saveme.feature.transactions.domain.model.TransactionFieldProblem
import com.fares.saveme.feature.transactions.domain.model.TransactionKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import saveme.shared.generated.resources.Res
import saveme.shared.generated.resources.error_generic
import saveme.shared.generated.resources.error_generic_reference
import saveme.shared.generated.resources.tx_reason_no_right
import saveme.shared.generated.resources.tx_reason_no_right_other

/** Refusal and field copy is chosen by kind and field, never by the server's text, and is always the transaction's own (P-02.4, P-06.5, FR-025). */
class TransactionCopyTest {

    @Test
    fun everyRefusalKindMapsToATransactionKeyNotAGenericMessage() {
        RefusalKind.entries.forEach { kind ->
            val res = kind.messageRes()
            assertNotEquals(Res.string.error_generic, res, kind.name)
            assertNotEquals(Res.string.error_generic_reference, res, kind.name)
        }
        // Every kind has its own wording.
        assertEquals(RefusalKind.entries.size, RefusalKind.entries.map { it.messageRes() }.toSet().size)
    }

    @Test
    fun aForbiddenTransferReadsAsNoRightInTheOtherWalletAndOthersAsNoRightHere() {
        assertEquals(Res.string.tx_reason_no_right_other, RefusalKind.NoRightInOtherWallet.messageRes())
        assertEquals(Res.string.tx_reason_no_right, RefusalKind.NoRightHere.messageRes())
        assertNotEquals(RefusalKind.NoRightHere.messageRes(), RefusalKind.NoRightInOtherWallet.messageRes())
    }

    @Test
    fun everyFieldProblemMapsToAKey() {
        val problems = listOf(
            TransactionFieldProblem.AmountEmpty, TransactionFieldProblem.AmountNotPositive, TransactionFieldProblem.AmountInvalid,
            TransactionFieldProblem.AmountOutOfRange, TransactionFieldProblem.AmountDigits(2), TransactionFieldProblem.DateOutOfRange,
            TransactionFieldProblem.CategoryRequired, TransactionFieldProblem.DestinationRequired, TransactionFieldProblem.KindUnknown,
            TransactionFieldProblem.TooLong(100), TransactionFieldProblem.Server("OUT_OF_RANGE"),
        )
        problems.forEach { it.messageRes(TransactionField.Amount) }
        assertEquals(problems.size, problems.map { it.messageRes(TransactionField.Amount) }.size)
    }

    @Test
    fun serverFieldCodesDependOnTheirField() {
        val outOfRange = TransactionFieldProblem.Server("OUT_OF_RANGE")
        assertEquals(TransactionFieldProblem.AmountOutOfRange.messageRes(), outOfRange.messageRes(TransactionField.Amount))
        assertEquals(TransactionFieldProblem.DateOutOfRange.messageRes(), outOfRange.messageRes(TransactionField.Date))
        assertEquals(TransactionFieldProblem.CategoryRequired.messageRes(), TransactionFieldProblem.Server("REQUIRED").messageRes(TransactionField.Category))
        assertEquals(TransactionFieldProblem.DestinationRequired.messageRes(), TransactionFieldProblem.Server("REQUIRED").messageRes(TransactionField.Destination))
        assertNotEquals(TransactionFieldProblem.Server("IMMUTABLE_FIELD").messageRes(), outOfRange.messageRes(TransactionField.Note))
    }

    @Test
    fun everyKindHasALabel() {
        assertEquals(TransactionKind.entries.size, TransactionKind.entries.map { it.labelRes() }.toSet().size)
    }

    @Test
    fun refusedFieldNamesBecomeServerProblems() {
        val problems = mapOf(TransactionField.Amount to "OUT_OF_RANGE").asProblems()
        assertEquals(TransactionFieldProblem.Server("OUT_OF_RANGE"), problems[TransactionField.Amount])
    }
}
