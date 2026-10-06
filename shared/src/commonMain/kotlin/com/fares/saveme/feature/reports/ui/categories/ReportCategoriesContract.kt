package com.fares.saveme.feature.reports.ui.categories

import com.fares.saveme.feature.categories.domain.model.ReportLabels
import com.fares.saveme.feature.reports.domain.model.CategoryGroup
import com.fares.saveme.feature.reports.domain.model.Report
import com.fares.saveme.feature.reports.domain.model.ReportState
import com.fares.saveme.feature.reports.ui.CategoryRowUi
import com.fares.saveme.feature.reports.ui.categoryRows
import com.fares.saveme.feature.reports.ui.reports.CategoryGroupKind
import com.fares.saveme.feature.reports.ui.reports.RangeUi

enum class ReportCategoriesStatus { Loading, Ready, Gone }

/** One group of the breakdown as the screen lists it: the total, the scheduled sum (null while the switch is off) and the rows. */
data class GroupUi(val totalMinor: Long = 0, val plannedMinor: Long? = null, val rows: List<CategoryRowUi> = emptyList())

/** Spending by category for the range the Reports screen showed: every item of the chosen group with amount and share (FR-022). */
data class ReportCategoriesState(
    val status: ReportCategoriesStatus = ReportCategoriesStatus.Loading,
    val title: String? = null,
    val range: RangeUi? = null,
    val thisYear: Int = 1970,
    val currency: String = "",
    val kind: CategoryGroupKind = CategoryGroupKind.Expenses,
    val expenses: GroupUi = GroupUi(),
    val income: GroupUi = GroupUi(),
    val includesWaiting: Boolean = false,
    val tooLarge: Boolean = false,
) {
    /** The group the person chose; switching only changes which one is read. */
    val group: GroupUi get() = if (kind == CategoryGroupKind.Expenses) expenses else income
    val totalMinor: Long get() = group.totalMinor
    val plannedMinor: Long? get() = group.plannedMinor
    val rows: List<CategoryRowUi> get() = group.rows
}

sealed interface ReportCategoriesIntent {
    data class KindPicked(val kind: CategoryGroupKind) : ReportCategoriesIntent
    data object BackClicked : ReportCategoriesIntent
}

sealed interface ReportCategoriesEffect {
    data object Close : ReportCategoriesEffect
}

sealed interface ReportCategoriesChange {
    data class Stored(val state: ReportState, val labels: ReportLabels) : ReportCategoriesChange
    data class Kind(val kind: CategoryGroupKind) : ReportCategoriesChange
}

fun reduce(state: ReportCategoriesState, change: ReportCategoriesChange): ReportCategoriesState = when (change) {
    is ReportCategoriesChange.Kind -> state.copy(kind = change.kind)
    is ReportCategoriesChange.Stored -> when (val stored = change.state) {
        ReportState.Loading, ReportState.NoWallets -> state.copy(status = ReportCategoriesStatus.Loading)
        ReportState.WalletGone -> state.copy(status = ReportCategoriesStatus.Gone)
        is ReportState.Ready -> ready(state, stored.report, change.labels)
    }
}

private fun ready(state: ReportCategoriesState, report: Report, labels: ReportLabels): ReportCategoriesState {
    fun group(group: CategoryGroup?) = group?.let { GroupUi(it.totalMinor, it.plannedMinor, categoryRows(it, labels, report.currency)) } ?: GroupUi()
    return state.copy(
        status = ReportCategoriesStatus.Ready,
        title = report.title,
        range = report.range?.let { RangeUi(it.from, it.to, custom = it.kind == com.fares.saveme.feature.reports.domain.model.RangeKind.Custom) },
        thisYear = report.today.year,
        currency = report.currency,
        expenses = group(report.breakdown?.expenses),
        income = group(report.breakdown?.income),
        includesWaiting = report.includesWaiting,
        tooLarge = report.tooLarge,
    )
}
