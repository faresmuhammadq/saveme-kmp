package com.fares.saveme.feature.recurring.ui.rule

import com.fares.saveme.feature.categories.domain.model.CategoryDisplay
import com.fares.saveme.feature.recurring.domain.model.AmountBasis
import com.fares.saveme.feature.recurring.domain.model.OccurrenceLists
import com.fares.saveme.feature.recurring.domain.model.Preset
import com.fares.saveme.feature.recurring.domain.model.RuleStatus
import com.fares.saveme.feature.recurring.domain.model.RuleView
import com.fares.saveme.feature.recurring.domain.model.SchedulePreview

/** Which rule to show; one object so Koin never confuses two string parameters. */
data class RuleDetailArgs(val ruleId: String)

/** The confirmations the screen asks before a rule ends or goes (FR-015, FR-016). */
enum class RuleDialog { None, End, Delete }

enum class RuleDetailMessage { Gone, NotAllowed, ReadOnly }

/** Why nothing (or only a delete) can change, in the person's words (FR-015, R-15). */
enum class ReadOnlyReason { None, Unknown, Ended, Viewer }

/** What "Check for new dates" last answered (FR-028). */
sealed interface CatchUpNote {
    data object Offline : CatchUpNote
    data object Done : CatchUpNote
    data object NotAllowed : CatchUpNote
    data class Failed(val requestId: String?) : CatchUpNote
}

/**
 * One rule: its summary, its dates, the sync status with choices, and the actions the person's role and the rule's
 * status allow. Everything comes from the phone's copy (P-04.1).
 */
data class RuleDetailState(
    val ruleId: String = "",
    val loaded: Boolean = false,
    val view: RuleView? = null,
    val occurrences: OccurrenceLists = OccurrenceLists(),
    /** The phone's own preview, used while the server has not yet sent this rule's dates. */
    val preview: SchedulePreview = SchedulePreview.Empty,
    /** The title of the rule a held split waits for. */
    val heldOnTitle: String? = null,
    val dialog: RuleDialog = RuleDialog.None,
    val busy: Boolean = false,
    val catchingUp: Boolean = false,
    val catchUpNote: CatchUpNote? = null,
    val category: CategoryDisplay? = null,
    val payoutCategory: CategoryDisplay? = null,
) {
    private val status: RuleStatus get() = view?.rule?.status ?: RuleStatus.Unknown

    /** Owner or editor, a rule the app understands and has not ended (FR-015). */
    val canChange: Boolean get() = view != null && view.canWrite && !view.readOnly && !view.hidden

    val canEdit: Boolean get() = canChange
    val canChangeFrom: Boolean
        get() = canChange && view?.rule?.let { it.preset == Preset.Custom && it.amountBasis == AmountBasis.PerOccurrence && !it.isPlaceholder } == true
    val canPause: Boolean get() = canChange && status == RuleStatus.Active
    val canResume: Boolean get() = canChange && status == RuleStatus.Paused
    val canEnd: Boolean get() = canChange && (status == RuleStatus.Active || status == RuleStatus.Paused)
    val canDelete: Boolean get() = view != null && view.canWrite && !view.hidden
    val canCatchUp: Boolean
        get() = canChange && (status == RuleStatus.Active || status == RuleStatus.Paused) && view?.rule?.isPlaceholder == false

    val readOnlyReason: ReadOnlyReason
        get() {
            val rule = view?.rule ?: return ReadOnlyReason.None
            return when {
                rule.hasUnknownValues -> ReadOnlyReason.Unknown
                view?.canWrite != true -> ReadOnlyReason.Viewer
                rule.status == RuleStatus.Ended -> ReadOnlyReason.Ended
                else -> ReadOnlyReason.None
            }
        }

    /** No dates from the server yet: the phone's preview stands in (FR-039). */
    val showPreview: Boolean
        get() = view != null && !view.rule.hasUnknownValues && (view.rule.isPlaceholder || !view.hasOccurrences)

    /** An edit waits while the server's dates are still the old ones (FR-039). */
    val datesUpdateAfterSync: Boolean get() = view != null && view.hasPendingChange && !showPreview

    val occurrenceCount: Int get() = occurrences.due.size + occurrences.upcoming.size + occurrences.history.size
}

sealed interface RuleDetailIntent {
    data object EditClicked : RuleDetailIntent
    data object ChangeFromClicked : RuleDetailIntent
    data object PauseClicked : RuleDetailIntent
    data object ResumeClicked : RuleDetailIntent
    data object EndClicked : RuleDetailIntent
    data object DeleteClicked : RuleDetailIntent
    data object DialogConfirmed : RuleDetailIntent
    data object DialogDismissed : RuleDetailIntent
    data object CatchUpClicked : RuleDetailIntent
    data class OccurrenceClicked(val id: String) : RuleDetailIntent
    data object KeepMine : RuleDetailIntent
    data object UseTheirs : RuleDetailIntent
    data object AcceptDeletion : RuleDetailIntent
    data object TryAgain : RuleDetailIntent
    data object Discard : RuleDetailIntent
    data object Back : RuleDetailIntent
}

sealed interface RuleDetailEffect {
    data class OpenEditor(val walletId: String, val ruleId: String, val fromDate: Boolean) : RuleDetailEffect
    data class OpenOccurrence(val id: String) : RuleDetailEffect
    data class ShowMessage(val message: RuleDetailMessage) : RuleDetailEffect
    data object RequireVerification : RuleDetailEffect
    data object Close : RuleDetailEffect
}

sealed interface RuleDetailChange {
    data class Loaded(val view: RuleView, val heldOnTitle: String?, val preview: SchedulePreview) : RuleDetailChange
    data class Occurrences(val lists: OccurrenceLists) : RuleDetailChange
    data class Dialog(val dialog: RuleDialog) : RuleDetailChange
    data class Busy(val busy: Boolean) : RuleDetailChange
    data class CatchingUp(val catchingUp: Boolean) : RuleDetailChange
    data class CatchUp(val note: CatchUpNote?) : RuleDetailChange
    data class Categories(val category: CategoryDisplay?, val payoutCategory: CategoryDisplay?) : RuleDetailChange
}

fun reduce(state: RuleDetailState, change: RuleDetailChange): RuleDetailState = when (change) {
    is RuleDetailChange.Loaded -> state.copy(loaded = true, view = change.view, heldOnTitle = change.heldOnTitle, preview = change.preview)
    is RuleDetailChange.Occurrences -> state.copy(occurrences = change.lists)
    is RuleDetailChange.Dialog -> state.copy(dialog = change.dialog)
    is RuleDetailChange.Busy -> state.copy(busy = change.busy)
    is RuleDetailChange.CatchingUp -> state.copy(catchingUp = change.catchingUp, catchUpNote = if (change.catchingUp) null else state.catchUpNote)
    is RuleDetailChange.CatchUp -> state.copy(catchUpNote = change.note)
    is RuleDetailChange.Categories -> state.copy(category = change.category, payoutCategory = change.payoutCategory)
}
