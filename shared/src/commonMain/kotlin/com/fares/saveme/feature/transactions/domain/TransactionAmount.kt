package com.fares.saveme.feature.transactions.domain

import com.fares.saveme.feature.transactions.domain.model.TransactionFieldProblem
import com.fares.saveme.feature.wallets.domain.AmountInput
import com.fares.saveme.feature.wallets.domain.model.WalletFieldProblem

/**
 * The typed amount in minor units, through the one float-free parser (research R-15, P-03.2). A
 * result at or below zero, or a leading minus, is refused: the direction comes from the kind (FR-003).
 */
object TransactionAmount {
    sealed interface Result {
        data class Valid(val minor: Long) : Result
        data class Invalid(val problem: TransactionFieldProblem) : Result
    }

    fun parse(text: String, currency: String): Result = when (val parsed = AmountInput.parse(text, currency, liability = false)) {
        is AmountInput.Result.Valid ->
            if (parsed.minor <= 0) Result.Invalid(TransactionFieldProblem.AmountNotPositive) else Result.Valid(parsed.minor)
        is AmountInput.Result.Invalid -> Result.Invalid(
            when (val p = parsed.problem) {
                WalletFieldProblem.AmountEmpty -> TransactionFieldProblem.AmountEmpty
                is WalletFieldProblem.AmountDigits -> TransactionFieldProblem.AmountDigits(p.digits)
                WalletFieldProblem.AmountOutOfRange -> TransactionFieldProblem.AmountOutOfRange
                // A leading minus parses as a negative amount for assets, which is "not positive" here.
                else -> if (text.trim().startsWith("-") || text.trim().startsWith("−")) {
                    TransactionFieldProblem.AmountNotPositive
                } else {
                    TransactionFieldProblem.AmountInvalid
                }
            },
        )
    }

    /** The text the editor shows for a stored amount. */
    fun format(minor: Long, currency: String): String = AmountInput.format(minor, currency, liability = false)
}
