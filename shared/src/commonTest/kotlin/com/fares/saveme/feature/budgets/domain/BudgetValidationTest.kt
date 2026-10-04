package com.fares.saveme.feature.budgets.domain

import com.fares.saveme.feature.budgets.domain.model.BudgetFieldProblem
import kotlin.test.Test
import kotlin.test.assertEquals

/** The limit is 1 to 2^53 - 1 with the currency's fraction digits (FR-003). */
class BudgetValidationTest {

    private fun valid(text: String, currency: String = "EGP") =
        assertEquals(true, BudgetValidation.parseLimit(text, currency) is BudgetValidation.Result.Valid, text)

    private fun problem(text: String, currency: String = "EGP") =
        (BudgetValidation.parseLimit(text, currency) as BudgetValidation.Result.Invalid).problem

    @Test
    fun aPositiveAmountWithinTheCurrencysDigitsIsParsedWithoutFloats() {
        assertEquals(BudgetValidation.Result.Valid(600_000), BudgetValidation.parseLimit("6000", "EGP"))
        assertEquals(BudgetValidation.Result.Valid(600_050), BudgetValidation.parseLimit("6000.50", "EGP"))
        assertEquals(BudgetValidation.Result.Valid(6_000_500), BudgetValidation.parseLimit("6000.500", "KWD"))
        assertEquals(BudgetValidation.Result.Valid(6_000), BudgetValidation.parseLimit("6000", "JPY"))
        assertEquals(BudgetValidation.Result.Valid(600_050), BudgetValidation.parseLimit("٦٠٠٠٫٥٠", "EGP"), "Arabic-Indic digits and mark")
        assertEquals(BudgetValidation.Result.Valid(1), BudgetValidation.parseLimit("0.01", "EGP"))
        valid("90071992547409.91")
    }

    @Test
    fun zeroNegativeEmptyAndGarbageAreRefusedWithAFieldError() {
        assertEquals(BudgetFieldProblem.LimitNotPositive, problem("0"))
        assertEquals(BudgetFieldProblem.LimitNotPositive, problem("0.00"))
        assertEquals(BudgetFieldProblem.LimitNotPositive, problem("-5"))
        assertEquals(BudgetFieldProblem.LimitNotPositive, problem("−5"))
        assertEquals(BudgetFieldProblem.LimitEmpty, problem("  "))
        assertEquals(BudgetFieldProblem.LimitInvalid, problem("12a"))
        assertEquals(BudgetFieldProblem.LimitInvalid, problem("1.2.3"))
    }

    @Test
    fun tooManyFractionDigitsAndTooLargeAmountsAreRefused() {
        assertEquals(BudgetFieldProblem.LimitDigits(2), problem("1.234", "EGP"))
        assertEquals(BudgetFieldProblem.LimitDigits(0), problem("1.5", "JPY"))
        assertEquals(BudgetFieldProblem.LimitOutOfRange, problem("90071992547409.92"))
        assertEquals(BudgetFieldProblem.LimitOutOfRange, problem("99999999999999999999"))
    }

    @Test
    fun aLimitInMinorUnitsIsCheckedAgainstTheRange() {
        assertEquals(null, BudgetValidation.checkLimit(1))
        assertEquals(null, BudgetValidation.checkLimit(BudgetMath.MAX_MINOR))
        assertEquals(BudgetFieldProblem.LimitNotPositive, BudgetValidation.checkLimit(0))
        assertEquals(BudgetFieldProblem.LimitNotPositive, BudgetValidation.checkLimit(-1))
        assertEquals(BudgetFieldProblem.LimitOutOfRange, BudgetValidation.checkLimit(BudgetMath.MAX_MINOR + 1))
    }

    @Test
    fun theKeyOfAllExpensesIsNotACategoryId() {
        assertEquals("c1", BudgetValidation.keyOf("c1"))
        assertEquals(BudgetValidation.ALL_KEY, BudgetValidation.keyOf(null))
    }

    @Test
    fun theEditorShowsAStoredLimitAsTypedText() {
        assertEquals("6000.00", BudgetValidation.format(600_000, "EGP"))
        assertEquals("6000", BudgetValidation.format(6_000, "JPY"))
    }
}
