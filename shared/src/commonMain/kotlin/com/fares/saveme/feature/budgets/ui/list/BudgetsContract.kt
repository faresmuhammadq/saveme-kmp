package com.fares.saveme.feature.budgets.ui.list

import com.fares.saveme.feature.budgets.domain.model.BudgetsOverview
import com.fares.saveme.feature.budgets.domain.model.PeriodState
import com.fares.saveme.feature.budgets.domain.model.SyncState
import com.fares.saveme.feature.budgets.ui.BudgetRowUi
import com.fares.saveme.feature.budgets.ui.toRow
import kotlinx.datetime.LocalDate
import kotlin.time.Instant

/** The current Budget Period as the screen words it (FR-016): dates and the days left, or "Last day of the period". */
data class PeriodUi(val start: LocalDate, val end: LocalDate, val daysLeft: Int?) {
    val lastDay: Boolean get() = daysLeft == 0
}

/** "From <date>, periods start on day <n>" while a start day change is pending (FR-010). */
data class PendingDayUi(val day: Int, val from: LocalDate)

data class BudgetsState(
    /** Null while the period is unknown: no line. */
    val period: PeriodUi? = null,
    val pendingDay: PendingDayUi? = null,
    val active: List<BudgetRowUi> = emptyList(),
    val archived: List<BudgetRowUi> = emptyList(),
    val refreshing: Boolean = false,
    val updatedAt: Instant? = null,
    val offline: Boolean = false,
    val refreshFailed: Boolean = false,
    /** False until a refresh has finished or something is on the phone: shows a skeleton, not the empty state. */
    val loaded: Boolean = false,
    val waitingCount: Int = 0,
    val timeZoneId: String = "UTC",
) {
    val isEmpty: Boolean get() = loaded && active.isEmpty() && archived.isEmpty()
}

sealed interface BudgetsIntent {
    /** The screen opened: reads again, at most once a minute. */
    data object Opened : BudgetsIntent

    /** A pull always reads again (FR-018). */
    data object Refresh : BudgetsIntent
    data object AddClicked : BudgetsIntent
    data class BudgetClicked(val id: String) : BudgetsIntent

    /** "Shared budgets": the budgets the person tracks with others. */
    data object SharedClicked : BudgetsIntent
    data object Back : BudgetsIntent
}

sealed interface BudgetsEffect {
    /** A null [id] creates a budget. */
    data class OpenEditor(val id: String?) : BudgetsEffect
    data object RequireVerification : BudgetsEffect
    data object OpenShared : BudgetsEffect
    data object NavigateBack : BudgetsEffect
}

sealed interface BudgetsChange {
    data class Stored(val overview: BudgetsOverview) : BudgetsChange
    data class TimeZone(val id: String) : BudgetsChange
    data class Refreshing(val busy: Boolean) : BudgetsChange
    data class RefreshDone(val offline: Boolean, val failed: Boolean) : BudgetsChange
}

fun reduce(state: BudgetsState, change: BudgetsChange): BudgetsState = when (change) {
    is BudgetsChange.Stored -> {
        val overview = change.overview
        val known = (overview.period as? PeriodState.Known)?.period
        state.copy(
            period = known?.let { PeriodUi(it.start, it.end, overview.daysLeft) },
            pendingDay = overview.pendingStartDay?.let { PendingDayUi(it.day, it.from) },
            active = overview.active.map { it.toRow() },
            archived = overview.archived.map { it.toRow() },
            updatedAt = overview.updatedAt,
            loaded = state.loaded || overview.loaded,
            waitingCount = (overview.active + overview.archived).count { it.syncState is SyncState.Waiting },
        )
    }
    is BudgetsChange.TimeZone -> state.copy(timeZoneId = change.id)
    is BudgetsChange.Refreshing -> state.copy(refreshing = change.busy)
    is BudgetsChange.RefreshDone -> state.copy(refreshing = false, loaded = true, offline = change.offline, refreshFailed = change.failed)
}
