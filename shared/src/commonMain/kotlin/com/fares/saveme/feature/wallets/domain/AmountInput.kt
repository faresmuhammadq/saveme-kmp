package com.fares.saveme.feature.wallets.domain

import com.fares.saveme.designsystem.money.MoneyFormatter
import com.fares.saveme.feature.wallets.domain.model.WalletFieldProblem

/**
 * Turns the typed opening balance into minor units without ever going through a floating-point
 * number (P-03.2, research R-12). Western or Arabic-Indic digits, one decimal separator (`.` or the
 * Arabic `٫`), no more fraction digits than the currency has, a minus only for assets, and the
 * result inside the range every client and the server can hold exactly.
 */
object AmountInput {
    const val MAX_MINOR: Long = 9_007_199_254_740_991L

    sealed interface Result {
        data class Valid(val minor: Long) : Result
        data class Invalid(val problem: WalletFieldProblem) : Result
    }

    /** @param liability true for credit cards and loans: the person types what is owed, stored negative. */
    fun parse(text: String, currency: String, liability: Boolean): Result {
        val normalised = normalise(text.trim())
        if (normalised.isEmpty()) return Result.Invalid(WalletFieldProblem.AmountEmpty)
        val negative = normalised.startsWith("-")
        if (negative && liability) return Result.Invalid(WalletFieldProblem.AmountInvalid)
        val body = if (negative) normalised.drop(1) else normalised
        val parts = body.split('.')
        if (parts.size > 2) return Result.Invalid(WalletFieldProblem.AmountInvalid)
        val whole = parts[0]
        val fraction = parts.getOrElse(1) { "" }
        if ((whole.isEmpty() && fraction.isEmpty()) || !whole.all { it in '0'..'9' } || !fraction.all { it in '0'..'9' }) {
            return Result.Invalid(WalletFieldProblem.AmountInvalid)
        }
        val digits = MoneyFormatter.digitsFor(currency)
        if (fraction.length > digits) return Result.Invalid(WalletFieldProblem.AmountDigits(digits))
        val magnitudeText = (whole + fraction.padEnd(digits, '0')).trimStart('0').ifEmpty { "0" }
        if (magnitudeText.length > MAX_MINOR.toString().length) return Result.Invalid(WalletFieldProblem.AmountOutOfRange)
        val magnitude = magnitudeText.toLong()
        if (magnitude > MAX_MINOR) return Result.Invalid(WalletFieldProblem.AmountOutOfRange)
        val signed = if (negative || liability) -magnitude else magnitude
        return Result.Valid(signed)
    }

    /** The text the editor shows for a stored amount: the owed amount unsigned for liabilities. */
    fun format(minor: Long, currency: String, liability: Boolean): String {
        val shown = if (liability) -minor else minor
        val digits = MoneyFormatter.digitsFor(currency)
        val sign = if (shown < 0) "-" else ""
        val plain = MoneyFormatter.number(kotlin.math.abs(shown), digits).replace(",", "")
        return sign + plain
    }

    private fun normalise(text: String): String = buildString {
        for (c in text) {
            append(
                when (c) {
                    '٫', '،' -> '.'
                    '−', '–' -> '-'
                    in '٠'..'٩' -> '0' + (c - '٠')
                    in '۰'..'۹' -> '0' + (c - '۰')
                    ' ', ' ' -> continue
                    else -> c
                },
            )
        }
    }
}
