package com.fares.saveme.designsystem.money

/**
 * Formats integer minor units (P-03, ADR-0003). Fraction digits come from ISO 4217, the minus sign
 * is U+2212, digits are always Western, and Arabic output is wrapped in an LTR isolate so the
 * amount reads left-to-right inside right-to-left text (P-06.2).
 */
object MoneyFormatter {

    private const val MINUS = "−"
    private const val LTR_ISOLATE = "⁦"
    private const val POP_ISOLATE = "⁩"

    private val fractionDigits = mapOf(
        "KWD" to 3, "BHD" to 3, "OMR" to 3, "JOD" to 3, "TND" to 3, "IQD" to 3, "LYD" to 3,
        "JPY" to 0, "KRW" to 0,
    )

    private val arabicSymbols = mapOf("EGP" to "ج.م")

    fun digitsFor(currency: String): Int = fractionDigits[currency.uppercase()] ?: 2

    fun format(amountMinor: Long, currency: String, languageTag: String, showPlus: Boolean = false): String {
        val code = currency.uppercase()
        val sign = when {
            amountMinor < 0 -> MINUS
            showPlus && amountMinor > 0 -> "+"
            else -> ""
        }
        val number = number(if (amountMinor < 0) -amountMinor else amountMinor, digitsFor(code))
        if (languageTag != "ar") return "$sign$code $number"
        val symbol = arabicSymbols[code]
        val body = if (symbol != null) "$sign$number $symbol" else "$sign$code $number"
        return "$LTR_ISOLATE$body$POP_ISOLATE"
    }

    /** Grouped number without currency, e.g. for budget captions. */
    fun number(amountMinor: Long, digits: Int): String {
        var divisor = 1L
        repeat(digits) { divisor *= 10 }
        val whole = (amountMinor / divisor).toString()
        val grouped = whole.reversed().chunked(3).joinToString(",").reversed()
        if (digits == 0) return grouped
        val fraction = (amountMinor % divisor).toString().padStart(digits, '0')
        return "$grouped.$fraction"
    }
}
