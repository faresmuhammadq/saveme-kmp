package com.fares.saveme.feature.budgets.domain

import com.fares.saveme.feature.budgets.domain.model.BudgetFieldProblem
import com.fares.saveme.feature.wallets.domain.AmountInput
import com.fares.saveme.feature.wallets.domain.model.WalletFieldProblem

/**
 * The typed limit in minor units, through the one float-free parser (P-03.2): 1 to 2^53 − 1 with no more fraction digits
 * than the currency has (FR-003). Also the duplicate rule of FR-004.
 */
object BudgetValidation {
    sealed interface Result {
        data class Valid(val minor: Long) : Result
        data class Invalid(val problem: BudgetFieldProblem) : Result
    }

    fun parseLimit(text: String, currency: String): Result = when (val parsed = AmountInput.parse(text, currency, liability = false)) {
        is AmountInput.Result.Valid ->
            if (parsed.minor <= 0) Result.Invalid(BudgetFieldProblem.LimitNotPositive) else Result.Valid(parsed.minor)
        is AmountInput.Result.Invalid -> Result.Invalid(
            when (val p = parsed.problem) {
                WalletFieldProblem.AmountEmpty -> BudgetFieldProblem.LimitEmpty
                is WalletFieldProblem.AmountDigits -> BudgetFieldProblem.LimitDigits(p.digits)
                WalletFieldProblem.AmountOutOfRange -> BudgetFieldProblem.LimitOutOfRange
                // A leading minus parses as a negative amount for assets, which is "not positive" here.
                else -> if (text.trim().startsWith("-") || text.trim().startsWith("−")) {
                    BudgetFieldProblem.LimitNotPositive
                } else {
                    BudgetFieldProblem.LimitInvalid
                }
            },
        )
    }

    /** A limit already in minor units (a draft handed to the service): 1 … 2^53 − 1. */
    fun checkLimit(minor: Long): BudgetFieldProblem? = when {
        minor <= 0 -> BudgetFieldProblem.LimitNotPositive
        minor > BudgetMath.MAX_MINOR -> BudgetFieldProblem.LimitOutOfRange
        else -> null
    }

    /** The text the editor shows for a stored limit. */
    fun format(minor: Long, currency: String): String = AmountInput.format(minor, currency, liability = false)

    /** The key a category (or all expenses) has in the "already budgeted" set. */
    fun keyOf(categoryId: String?): String = categoryId ?: ALL_KEY

    const val ALL_KEY = "all"
}
