package com.fares.saveme.feature.recurring.ui

import com.fares.saveme.feature.recurring.domain.model.RuleFieldProblem
import com.fares.saveme.feature.transactions.domain.model.TransactionFieldProblem

/** The recurring forms parse amounts through the transactions parser; its problems are worded by the recurring copy. */
internal fun TransactionFieldProblem.asRuleProblem(): RuleFieldProblem = when (this) {
    TransactionFieldProblem.AmountEmpty -> RuleFieldProblem.Required
    is TransactionFieldProblem.AmountDigits -> RuleFieldProblem.AmountDigits(digits)
    TransactionFieldProblem.AmountOutOfRange -> RuleFieldProblem.OutOfRange(1, Long.MAX_VALUE)
    else -> RuleFieldProblem.Invalid
}
