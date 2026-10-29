package com.fares.saveme.feature.recurring.ui.list

import com.fares.saveme.feature.auth.domain.model.RefreshOutcome
import com.fares.saveme.feature.recurring.domain.model.OccurrenceLists
import com.fares.saveme.feature.recurring.domain.model.OccurrenceState
import com.fares.saveme.feature.recurring.domain.model.OccurrenceView
import com.fares.saveme.feature.recurring.domain.model.RuleStatus
import com.fares.saveme.feature.recurring.domain.model.RuleView

/** Which wallet to show; one object so Koin never confuses a null wallet with a missing parameter. */
data class RecurringArgs(val walletId: String?)

/**
 * The recurring screen of one wallet, or of all the person's wallets when [walletId] is null: what is due, what is
 * coming and the rules, always from the phone's copy (P-04.1).
 */
data class RecurringState(
    val walletId: String? = null,
    val walletName: String? = null,
    val loading: Boolean = true,
    /** Missed first, then by date. */
    val due: List<OccurrenceView> = emptyList(),
    val upcoming: List<OccurrenceView> = emptyList(),
    /** Confirmed, skipped and unknown ones: the rules' progress counts the confirmed. */
    val history: List<OccurrenceView> = emptyList(),
    /** Occurrences whose confirm or skip from the list is under way. */
    val busyIds: Set<String> = emptySet(),
    val rules: List<RuleView> = emptyList(),
    val canAdd: Boolean = false,
    val refreshing: Boolean = false,
    val offline: Boolean = false,
    val refreshFailed: Boolean = false,
) {
    val allWallets: Boolean get() = walletId == null

    /** How many rounds of [ruleId] are confirmed. */
    fun paidCount(ruleId: String): Int = history.count { it.occurrence.ruleId == ruleId && it.state == OccurrenceState.Confirmed }
    val isEmpty: Boolean get() = !loading && rules.isEmpty() && due.isEmpty() && upcoming.isEmpty()

    /** Changes still waiting to sync, for the offline banner. */
    val waitingCount: Int get() = rules.count { it.waiting } + (due + upcoming).count { it.waiting }

    val activeRules: List<RuleView> get() = rules.filter { it.rule.status == RuleStatus.Active || it.rule.status == RuleStatus.Unknown }
    val pausedRules: List<RuleView> get() = rules.filter { it.rule.status == RuleStatus.Paused }
    val endedRules: List<RuleView> get() = rules.filter { it.rule.status == RuleStatus.Ended }
}

sealed interface RecurringIntent {
    data object Opened : RecurringIntent
    data object Foregrounded : RecurringIntent
    data object PullToRefresh : RecurringIntent
    data object AddClicked : RecurringIntent
    data class RuleClicked(val id: String) : RecurringIntent
    data class OccurrenceClicked(val id: String) : RecurringIntent
    data class ConfirmOccurrence(val id: String) : RecurringIntent
    data class SkipOccurrence(val id: String) : RecurringIntent
    data object Back : RecurringIntent
}

sealed interface RecurringEffect {
    data class OpenRule(val id: String) : RecurringEffect
    data class OpenOccurrence(val id: String) : RecurringEffect

    /** [walletId] is the shown wallet, null in the all-wallets mode (the editor then picks one). */
    data class OpenEditor(val walletId: String?) : RecurringEffect
    data object Back : RecurringEffect
    data object RequireVerification : RecurringEffect
}

sealed interface RecurringChange {
    data class WalletName(val name: String?) : RecurringChange
    data class Occurrences(val lists: OccurrenceLists) : RecurringChange
    data class Rules(val rules: List<RuleView>) : RecurringChange
    data class CanAdd(val canAdd: Boolean) : RecurringChange
    data class Busy(val id: String, val busy: Boolean) : RecurringChange
    data class Refreshing(val refreshing: Boolean) : RecurringChange
    data class RefreshDone(val outcome: RefreshOutcome) : RecurringChange
}

fun reduce(state: RecurringState, change: RecurringChange): RecurringState = when (change) {
    is RecurringChange.WalletName -> state.copy(walletName = change.name)
    is RecurringChange.Occurrences -> state.copy(due = change.lists.due, upcoming = change.lists.upcoming, history = change.lists.history, loading = false)
    is RecurringChange.Rules -> state.copy(rules = change.rules)
    is RecurringChange.CanAdd -> state.copy(canAdd = change.canAdd)
    is RecurringChange.Busy -> state.copy(busyIds = if (change.busy) state.busyIds + change.id else state.busyIds - change.id)
    is RecurringChange.Refreshing -> state.copy(refreshing = change.refreshing)
    is RecurringChange.RefreshDone -> state.copy(
        refreshing = false,
        loading = false,
        offline = change.outcome == RefreshOutcome.Offline,
        refreshFailed = change.outcome == RefreshOutcome.Failed,
    )
}
