package com.fares.saveme.feature.budgets.domain.model

import com.fares.saveme.feature.categories.domain.model.CategoryDisplay
import kotlinx.datetime.LocalDate
import kotlin.time.Instant

/** Why the server refused a change for good, in the app's own terms (chosen by code and pointer only, P-02.4). */
enum class RefusalKind {
    NotVerified,
    NoLongerAvailable,
    CategoryUnusable,
    LimitInvalid,
    CurrencyInvalid,
    Immutable,
    Invalid,
    Other,
}

/** [requestId] is the server's reference for support (P-13.2). */
data class RefusalReason(val kind: RefusalKind, val requestId: String? = null) {
    /** A refusal that resending unchanged can cure, so "Try again" is offered. */
    val canRetryUnchanged: Boolean get() = kind == RefusalKind.NotVerified
}

/** What the sync queue says about a budget's change (data-model §7). */
sealed interface SyncState {
    data object None : SyncState

    /** Waiting or being sent. */
    data object Waiting : SyncState

    /** [theirs] is the other device's copy; null with [deletedElsewhere] true when it deleted the budget. */
    data class Clash(val theirs: Budget?, val deletedElsewhere: Boolean, val mineIsDeletion: Boolean) : SyncState

    /** [fields] maps the fields the server named to its error code. */
    data class Refused(val reason: RefusalReason, val fields: Map<BudgetField, String>) : SyncState
}

/**
 * What the phone shows for one budget: the row, any change waiting on top of it, the queue's state, the category and the
 * figures of the current period. [figures] is null while the period is unknown or the period type is not known here.
 */
data class BudgetView(
    val server: Budget,
    val pending: BudgetDraft?,
    val pendingDelete: Boolean,
    val syncState: SyncState,
    /** Null = all expenses. An empty name means the category cannot be resolved. */
    val category: CategoryDisplay?,
    val figures: BudgetFigures?,
) {
    val id: String get() = server.id

    /** What the person sees: their waiting change if there is one, else the server copy. */
    val limitMinor: Long get() = pending?.limitMinor ?: server.limitMinor

    val currency: String get() = server.currency

    val archived: Boolean get() = pending?.archive == true || server.archivedAt != null

    /** A delete that is still only on this phone hides the budget everywhere. */
    val hidden: Boolean get() = pendingDelete && syncState is SyncState.Waiting

    val waiting: Boolean get() = syncState is SyncState.Waiting

    /** A period type this version does not know: shown with its limit, never edited (research R-15). */
    val readOnly: Boolean get() = !server.isMonthly
}

/** The budgets screen's data (FR-016). */
data class BudgetsOverview(
    val period: PeriodState,
    /** Today in the profile's time zone, for the "days left" line. */
    val today: LocalDate?,
    val pendingStartDay: PendingStartDay?,
    /** The server's start day, for the editor's "Monthly, starting on day n" line. */
    val startDay: Int,
    val active: List<BudgetView>,
    val archived: List<BudgetView>,
    val updatedAt: Instant?,
    /** False until the first budgets read or any budget is on the phone: shows a skeleton, not the empty state. */
    val loaded: Boolean,
) {
    val daysLeft: Int? get() {
        val known = (period as? PeriodState.Known)?.period ?: return null
        val now = today ?: return null
        return (known.end.toEpochDays() - now.toEpochDays()).toInt().takeIf { it >= 0 }
    }
}

/** What the budget editor offers (FR-003, FR-004). */
data class BudgetChoices(
    /** The budget's current category when it is not in the lists below (archived, retired). */
    val current: CategoryDisplay?,
    val own: List<CategoryDisplay>,
    val defaults: List<CategoryDisplay>,
    /** The currency [alreadyBudgeted] was computed for. */
    val forCurrency: String,
    /** Wallets' currencies and the profile default, the default first. */
    val currencies: List<String>,
    val defaultCurrency: String,
    /** Keys ([com.fares.saveme.feature.budgets.domain.BudgetValidation.keyOf]) of the categories already budgeted in the chosen currency. */
    val alreadyBudgeted: Set<String>,
    val startDay: Int,
) {
    fun isAlreadyBudgeted(categoryId: String?): Boolean = com.fares.saveme.feature.budgets.domain.BudgetValidation.keyOf(categoryId) in alreadyBudgeted
}
