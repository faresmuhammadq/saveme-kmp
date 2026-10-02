package com.fares.saveme.feature.transactions.domain

import com.fares.saveme.feature.transactions.dto
import com.fares.saveme.feature.transactions.domain.model.TransactionField
import com.fares.saveme.feature.transactions.domain.model.TransactionKind
import com.fares.saveme.feature.transactions.transactionJson
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TransactionModelTest {

    @Test
    fun kindsRoundTripAndAnUnknownOneStaysAsReceived() {
        TransactionKind.selectable.forEach { assertEquals(it, TransactionKind.fromWire(it.wire)) }
        assertEquals(TransactionKind.Unknown, TransactionKind.fromWire("refund"))
        val tx = dto(transactionJson(kind = "refund")).toTransaction()
        assertEquals("refund", tx.kindWire)
        assertEquals(TransactionKind.Unknown, tx.kind)
        assertEquals("refund", tx.toDraft().kindWire)
    }

    @Test
    fun aDraftRoundTripsThroughThePayloadWithItsNulls() {
        val draft = dto(transactionJson(kind = "transfer", categoryId = null, counterWalletId = "w2", description = "Rent", memo = null)).toTransaction().toDraft()
        val payload = draft.toPayload()
        assertNull(payload.categoryId)
        assertEquals("w2", payload.counterWalletId)
        assertEquals(draft, payload.toDraft(draft.walletId))
        assertEquals(draft, decodePayload(draft.toPayloadJson())!!.toDraft(draft.walletId))
    }

    @Test
    fun theRecurringLinksAreKeptAsReceived() {
        val tx = dto(transactionJson(recurringRuleId = "r1", occurrenceId = "o1")).toTransaction()
        assertEquals("r1", tx.recurringRuleId)
        assertEquals("o1", tx.occurrenceId)
        assertEquals(LocalDate(2026, 10, 1), tx.date)
    }

    @Test
    fun fieldsComeFromThePlainNamesTheHandlerReturns() {
        assertEquals(TransactionField.Amount, TransactionField.fromName("amount"))
        assertEquals(TransactionField.Destination, TransactionField.fromName("destination"))
        assertNull(TransactionField.fromName("version"))
    }

    @Test
    fun theAmountParserRefusesZeroNegativeAndTooManyDigitsPerCurrency() {
        fun problem(text: String, currency: String) = (TransactionAmount.parse(text, currency) as? TransactionAmount.Result.Invalid)?.problem
        assertTrue(TransactionAmount.parse("45.50", "EGP") is TransactionAmount.Result.Valid)
        assertEquals(4_550, (TransactionAmount.parse("45.50", "EGP") as TransactionAmount.Result.Valid).minor)
        assertEquals(45_500, (TransactionAmount.parse("45.500", "KWD") as TransactionAmount.Result.Valid).minor)
        assertEquals(45, (TransactionAmount.parse("45", "JPY") as TransactionAmount.Result.Valid).minor)
        assertEquals(com.fares.saveme.feature.transactions.domain.model.TransactionFieldProblem.AmountNotPositive, problem("0", "EGP"))
        assertEquals(com.fares.saveme.feature.transactions.domain.model.TransactionFieldProblem.AmountNotPositive, problem("-5", "EGP"))
        assertEquals(com.fares.saveme.feature.transactions.domain.model.TransactionFieldProblem.AmountDigits(2), problem("1.234", "EGP"))
        assertEquals(com.fares.saveme.feature.transactions.domain.model.TransactionFieldProblem.AmountDigits(0), problem("1.5", "JPY"))
        assertEquals(com.fares.saveme.feature.transactions.domain.model.TransactionFieldProblem.AmountEmpty, problem("", "EGP"))
        assertEquals(com.fares.saveme.feature.transactions.domain.model.TransactionFieldProblem.AmountOutOfRange, problem("90071992547409.92", "EGP"))
        assertEquals(9_007_199_254_740_991L, (TransactionAmount.parse("90071992547409.91", "EGP") as TransactionAmount.Result.Valid).minor)
    }

    @Test
    fun arabicIndicDigitsAndTheArabicDecimalMarkAreAccepted() {
        assertEquals(4_550, (TransactionAmount.parse("٤٥٫٥٠", "EGP") as TransactionAmount.Result.Valid).minor)
        assertFalse(TransactionAmount.parse("abc", "EGP") is TransactionAmount.Result.Valid)
    }
}
