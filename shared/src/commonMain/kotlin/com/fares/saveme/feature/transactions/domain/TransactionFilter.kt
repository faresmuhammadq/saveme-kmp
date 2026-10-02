package com.fares.saveme.feature.transactions.domain

import com.fares.saveme.feature.transactions.domain.model.TransactionQuery
import com.fares.saveme.feature.transactions.domain.model.TransactionView
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlinx.datetime.plus

/**
 * Search and filters over the shown (waiting-change) values of a wallet's transactions (research
 * R-12, FR-027, FR-028): search is a literal, case-insensitive substring of the note, details or
 * person, so `%` and `_` match themselves; values of one filter combine with "or", filters and
 * search with "and".
 */
object TransactionFilter {
    const val MAX_SEARCH = 100

    fun apply(views: List<TransactionView>, query: TransactionQuery): List<TransactionView> {
        val needle = query.search.trim().take(MAX_SEARCH)
        return views.filter { view ->
            val shown = view.shown
            (needle.isEmpty() || listOf(shown.description, shown.memo, shown.counterparty).any { it?.contains(needle, ignoreCase = true) == true }) &&
                (query.from == null || shown.date >= query.from) &&
                (query.to == null || shown.date <= query.to) &&
                (query.categoryIds.isEmpty() || shown.categoryId in query.categoryIds) &&
                (query.kinds.isEmpty() || shown.kind in query.kinds)
        }
    }
}

/** The month presets of the date filter, from "today" in the profile's time zone (research R-12). */
object DatePresets {
    fun thisMonth(today: LocalDate): Pair<LocalDate, LocalDate> {
        val first = LocalDate(today.year, today.month, 1)
        return first to first.plus(1, DateTimeUnit.MONTH).minus(1, DateTimeUnit.DAY)
    }

    fun lastMonth(today: LocalDate): Pair<LocalDate, LocalDate> {
        val firstOfThis = LocalDate(today.year, today.month, 1)
        return firstOfThis.minus(1, DateTimeUnit.MONTH) to firstOfThis.minus(1, DateTimeUnit.DAY)
    }
}
