package com.fares.saveme.feature.wallets.domain

import com.fares.saveme.feature.wallets.domain.model.WalletFieldProblem
import kotlin.test.Test
import kotlin.test.assertEquals

class AmountInputTest {
    private fun minor(text: String, currency: String = "EGP", liability: Boolean = false): Long =
        (AmountInput.parse(text, currency, liability) as AmountInput.Result.Valid).minor

    private fun problem(text: String, currency: String = "EGP", liability: Boolean = false): WalletFieldProblem =
        (AmountInput.parse(text, currency, liability) as AmountInput.Result.Invalid).problem

    @Test
    fun parsesWholeAndFractionDigitsPerCurrency() {
        assertEquals(25_000, minor("250"))
        assertEquals(25_050, minor("250.5"))
        assertEquals(25_050, minor("250.50"))
        assertEquals(250, minor("250", "JPY"))
        assertEquals(250_500, minor("250.5", "KWD"))
        assertEquals(0, minor("0"))
        assertEquals(5, minor(".05"))
    }

    @Test
    fun tooManyFractionDigitsReportTheAllowedCount() {
        assertEquals(WalletFieldProblem.AmountDigits(2), problem("1.234"))
        assertEquals(WalletFieldProblem.AmountDigits(0), problem("1.5", "JPY"))
        assertEquals(WalletFieldProblem.AmountDigits(3), problem("1.2345", "KWD"))
    }

    @Test
    fun theArabicDecimalSeparatorAndDigitsAreAccepted() {
        assertEquals(25_050, minor("250٫5"))
        assertEquals(12_345, minor("١٢٣٫٤٥"))
    }

    @Test
    fun assetsMayBeNegativeLiabilitiesTakeWhatIsOwed() {
        assertEquals(-1_200, minor("−12.00"))
        assertEquals(-1_200, minor("-12"))
        assertEquals(-100_000, minor("1000", liability = true))
        assertEquals(0, minor("0", liability = true))
        assertEquals(WalletFieldProblem.AmountInvalid, problem("-1000", liability = true))
    }

    @Test
    fun boundsAreExactlyTheSafeIntegerRange() {
        assertEquals(AmountInput.MAX_MINOR, minor("90071992547409.91"))
        assertEquals(-AmountInput.MAX_MINOR, minor("-90071992547409.91"))
        assertEquals(WalletFieldProblem.AmountOutOfRange, problem("90071992547409.92"))
        assertEquals(WalletFieldProblem.AmountOutOfRange, problem("99999999999999999999"))
        assertEquals(-AmountInput.MAX_MINOR, minor("90071992547409.91", liability = true))
    }

    @Test
    fun emptyAndMalformedTextIsAnError() {
        assertEquals(WalletFieldProblem.AmountEmpty, problem(""))
        assertEquals(WalletFieldProblem.AmountEmpty, problem("   "))
        assertEquals(WalletFieldProblem.AmountInvalid, problem("abc"))
        assertEquals(WalletFieldProblem.AmountInvalid, problem("1.2.3"))
        assertEquals(WalletFieldProblem.AmountInvalid, problem("."))
        assertEquals(WalletFieldProblem.AmountInvalid, problem("1e5"))
    }

    @Test
    fun formatShowsTheStoredAmountAsTyped() {
        assertEquals("250.50", AmountInput.format(25_050, "EGP", liability = false))
        assertEquals("-12.00", AmountInput.format(-1_200, "EGP", liability = false))
        assertEquals("1000.00", AmountInput.format(-100_000, "EGP", liability = true))
        assertEquals("250", AmountInput.format(250, "JPY", liability = false))
    }
}
