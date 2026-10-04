package com.fares.saveme.feature.budgets.domain

import com.fares.saveme.feature.budgets.domain.model.BudgetPeriod
import com.fares.saveme.feature.budgets.domain.model.FigureSource
import com.fares.saveme.feature.budgets.domain.model.Selection
import com.fares.saveme.feature.budgets.domain.model.SpendingSums
import com.fares.saveme.feature.budgets.domain.model.StoredProgress

/**
 * Which spent figure a budget shows (research R-06, FR-014): the server's, only while it can still be true, else the
 * phone's own with waiting changes. Because the comparison uses the phone's synced-only sums, accepting a waiting change
 * never makes the shown figure jump back to an older server figure.
 */
object ProgressSelection {
    /**
     * @param phone the phone's figures with waiting changes ([SpendingSums.includesWaiting] = (b) fails)
     * @param syncedNow the phone's synced-only figures right now
     */
    fun select(
        stored: StoredProgress?,
        period: BudgetPeriod,
        currency: String,
        limitMinor: Long,
        phone: SpendingSums,
        syncedNow: SpendingSums,
    ): Selection {
        if (stored == null ||
            stored.period.start != period.start || stored.period.end != period.end || // (a) the stored period is the current one
            phone.includesWaiting || // (b) a waiting change matches
            stored.localSpentMinor != syncedNow.spentMinor || stored.localOtherCurrencies != syncedNow.otherCurrencies || // (c) something synced since
            stored.limitMinor != limitMinor || stored.currency != currency // (d) the limit changed
        ) {
            return Selection(phone.spentMinor, phone.otherCurrencies, FigureSource.Phone, phone.includesWaiting)
        }
        return Selection(stored.spentMinor, stored.otherCurrencies, FigureSource.Server, includesWaiting = false)
    }
}
