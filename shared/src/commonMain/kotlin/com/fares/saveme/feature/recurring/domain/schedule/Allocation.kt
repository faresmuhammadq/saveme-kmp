package com.fares.saveme.feature.recurring.domain.schedule

import com.fares.saveme.feature.recurring.domain.model.AmountBasis

/**
 * Amounts of a series in integer minor units (spec 010 R-08, P-03): a total is split with the remainder going to the
 * earliest rounds, and a gam'eya payout is the sum of every round's scheduled amount.
 */
object Allocation {
    /** The largest amount the server and a JSON number hold exactly: 2^53 - 1. */
    const val MAX_MINOR = 9_007_199_254_740_991L

    /** [totalMinor] over [parts] rounds: `base = total / parts`, the first `total % parts` rounds get one more. Null when it cannot give every round at least one minor unit. */
    fun split(totalMinor: Long, parts: Int): List<Long>? {
        if (parts < 1 || totalMinor < parts) return null
        val base = totalMinor / parts
        val extra = (totalMinor % parts).toInt()
        return List(parts) { if (it < extra) base + 1 else base }
    }

    /** The scheduled amount of round [ordinal] (1-based). */
    fun forRound(basis: AmountBasis, amountMinor: Long, count: Int?, ordinal: Int): Long {
        if (basis != AmountBasis.Total || count == null) return amountMinor
        val shares = split(amountMinor, count) ?: return amountMinor
        return shares.getOrElse(ordinal - 1) { shares.last() }
    }

    /** The payout: a total stands as it is, else the per-round amount times the count; null when it passes [MAX_MINOR]. */
    fun payout(basis: AmountBasis, amountMinor: Long, count: Int?): Long? {
        if (basis == AmountBasis.Total || count == null) return amountMinor
        if (count > 0 && amountMinor > MAX_MINOR / count) return null
        return amountMinor * count
    }
}
