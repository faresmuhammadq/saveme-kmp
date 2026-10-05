package com.fares.saveme.feature.reports.domain

import com.fares.saveme.feature.reports.domain.model.Share

/**
 * Shares are computed here and never read from the server (FR-007, P-03): `value x 100 / total` rounded half up with
 * integer arithmetic. They need not add up to 100 and are never forced to.
 */
object Shares {
    private const val LIMIT = Long.MAX_VALUE / 200

    fun of(valueMinor: Long, totalMinor: Long): Share {
        if (totalMinor <= 0L) return Share.None
        if (valueMinor < 0L) return Share.None
        // (value x 200 + total) / (2 x total) is value x 100 / total rounded half up, without a fraction.
        if (valueMinor > LIMIT || totalMinor > LIMIT) return Share.None
        val percent = ((valueMinor * 200 + totalMinor) / (2 * totalMinor)).toInt()
        return if (percent == 0 && valueMinor > 0L) Share.UnderOne else Share.Percent(percent)
    }

    /** The bar's fill, 0..1; drawing only, never shown as a number. */
    fun fraction(valueMinor: Long, totalMinor: Long): Float {
        if (totalMinor <= 0L || valueMinor <= 0L) return 0f
        val ratio = valueMinor.toDouble() / totalMinor.toDouble()
        return ratio.coerceIn(0.0, 1.0).toFloat()
    }
}
