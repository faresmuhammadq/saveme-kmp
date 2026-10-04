package com.fares.saveme.feature.recurring.domain.model

import com.fares.saveme.feature.wallets.domain.model.WalletRole
import kotlinx.datetime.LocalDate

/** What the sync queue says about a rule or occurrence change (data-model §6). */
sealed interface RecurringSyncState {
    data object None : RecurringSyncState

    /** Waiting or being sent. */
    data object Waiting : RecurringSyncState

    /** Held back: a split's new rule waiting for the old rule's change ("Waiting for the change to <rule>", FR-015). */
    data class Held(val onRuleId: String) : RecurringSyncState

    /** The other device's copy differs; [deletedElsewhere] when it deleted the record. */
    data class Clash(val deletedElsewhere: Boolean, val mineIsDeletion: Boolean) : RecurringSyncState

    /** The server refused for good; copy is chosen by [code] and the pointed [fields] (plain field name to error code, FR-035). */
    data class Refused(val code: String?, val requestId: String?, val fields: Map<String, String>) : RecurringSyncState
}

/** A rule as a screen sees it. */
data class RuleView(
    val rule: Rule,
    /** The waiting change's content, else the server copy as a draft. */
    val shown: RuleDraft,
    val hasPendingChange: Boolean,
    val pendingDelete: Boolean,
    val syncState: RecurringSyncState,
    /** The next scheduled date from the occurrences held, else the phone's own preview. */
    val nextDate: LocalDate?,
    /** "Today" in the rule's zone (FR-018). */
    val today: LocalDate,
    /** Unknown values, an ended rule or a viewer: nothing but delete (FR-015, R-15). */
    val readOnly: Boolean,
    val frozen: FrozenMembers,
    val myRole: WalletRole?,
    val walletName: String?,
    /** At least one occurrence of the rule is on the phone (FR-017). */
    val hasOccurrences: Boolean,
    val authorName: String? = null,
) {
    val id: String get() = rule.id
    val canWrite: Boolean get() = myRole == WalletRole.Owner || myRole == WalletRole.Editor
    val hidden: Boolean get() = pendingDelete && syncState is RecurringSyncState.Waiting
    val waiting: Boolean get() = syncState is RecurringSyncState.Waiting || syncState is RecurringSyncState.Held
}

/** An occurrence as a screen sees it. */
data class OccurrenceView(
    val occurrence: Occurrence,
    val pending: OccurrencePending?,
    val state: OccurrenceState,
    /** The rule's description, else a fallback; the title of the row. */
    val ruleDescription: String?,
    val syncState: RecurringSyncState,
    /** The values shown: the waiting change's, else the server's. */
    val shownDate: LocalDate,
    val shownAmountMinor: Long,
    val shownCategoryId: String?,
    val shownDescription: String?,
    val shownMemo: String?,
    val shownCounterparty: String?,
    val currency: String,
    val canWrite: Boolean,
    val notice: String?,
) {
    val id: String get() = occurrence.id
    val isPayout: Boolean get() = occurrence.isPayout
    val waiting: Boolean get() = syncState is RecurringSyncState.Waiting
    val confirmationWaiting: Boolean get() = pending?.isConfirmation == true
}

/** Dates the phone computes for the editor and for rules the server has not yet answered (FR-006). */
data class PreviewRound(val ordinal: Int, val date: LocalDate, val amountMinor: Long, val isPayout: Boolean)

data class SchedulePreview(val rounds: List<PreviewRound>, val payoutMinor: Long? = null) {
    companion object {
        val Empty = SchedulePreview(emptyList())
    }
}
