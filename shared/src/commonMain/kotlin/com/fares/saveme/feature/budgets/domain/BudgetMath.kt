package com.fares.saveme.feature.budgets.domain

import com.fares.saveme.designsystem.component.budgetPercent
import com.fares.saveme.feature.budgets.domain.model.BudgetFigures
import com.fares.saveme.feature.budgets.domain.model.BudgetPeriod
import com.fares.saveme.feature.budgets.domain.model.FigureSource

/**
 * The integer arithmetic of a budget's figures (research R-07, P-03): `Long` minor units only, the projection rounded
 * up to the minor unit as the server does (ADR-0019 rule 4). No floating point anywhere.
 */
object BudgetMath {
    /** Amounts above this cannot be held exactly by every client and the server (2^53 − 1). */
    const val MAX_MINOR: Long = 9_007_199_254_740_991L

    fun remaining(spentMinor: Long, limitMinor: Long): Long = limitMinor - spentMinor

    fun overLimit(spentMinor: Long, limitMinor: Long): Boolean = spentMinor > limitMinor

    /** Whole percent spent, half up, the design system's own rule so the written share and the bar agree. */
    fun percent(spentMinor: Long, limitMinor: Long): Int = budgetPercent(spentMinor, limitMinor)

    /**
     * `ceil(spent × daysInPeriod ÷ daysElapsed)` for the current period; null when no day has elapsed or today is outside
     * the period, when the product would overflow `Long`, or when the result exceeds 2^53 − 1.
     */
    fun projectedEnd(spentMinor: Long, daysInPeriod: Int, daysElapsed: Int?): Long? {
        if (daysElapsed == null || daysElapsed <= 0 || daysInPeriod <= 0 || spentMinor < 0) return null
        if (spentMinor > Long.MAX_VALUE / daysInPeriod) return null
        val product = spentMinor * daysInPeriod
        val quotient = product / daysElapsed
        val result = if (product % daysElapsed != 0L) quotient + 1 else quotient
        return result.takeIf { it <= MAX_MINOR }
    }

    fun figures(
        spentMinor: Long,
        limitMinor: Long,
        period: BudgetPeriod,
        otherCurrencies: List<String>,
        source: FigureSource,
        includesWaiting: Boolean,
    ) = BudgetFigures(
        spentMinor = spentMinor,
        limitMinor = limitMinor,
        remainingMinor = remaining(spentMinor, limitMinor),
        overLimit = overLimit(spentMinor, limitMinor),
        percent = percent(spentMinor, limitMinor),
        projectedMinor = projectedEnd(spentMinor, period.daysInPeriod, period.daysElapsed),
        otherCurrencies = otherCurrencies,
        source = source,
        includesWaiting = includesWaiting,
    )
}
