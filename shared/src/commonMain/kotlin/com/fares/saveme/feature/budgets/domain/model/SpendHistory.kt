package com.fares.saveme.feature.budgets.domain.model

/** What a category (or all expenses) was spent in earlier periods, as quick limits for the editor; null when nothing was spent. */
data class SpendHistory(
    val lastPeriodMinor: Long?,
    val averageMinor: Long?,
) {
    override fun toString() = "SpendHistory"

    companion object {
        val None = SpendHistory(null, null)
    }
}
