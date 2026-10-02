package com.fares.saveme.feature.transactions.domain.model

import kotlinx.datetime.LocalDate
import kotlin.time.Instant

enum class RefusalKind {
    NotVerified, NoLongerAvailable, NoRightHere, NoRightInOtherWallet, CategoryUnusable, DestinationUnavailable,
    CurrencyChanged, CurrenciesDiffer, Invalid, Other,
}

/** Why the server refused a change for good, in the app's own terms (research R-07). */
data class RefusalReason(val kind: RefusalKind, val requestId: String? = null) {
    /** A refusal that sending again unchanged can cure, so "Try again" is offered. */
    val canRetryUnchanged: Boolean get() = kind == RefusalKind.NotVerified
}

/** What the sync queue says about a transaction's change (data-model §3). */
sealed interface TransactionSyncState {
    data object None : TransactionSyncState

    /** Waiting or being sent. */
    data object Waiting : TransactionSyncState

    /** [theirs] is the other device's live copy; null with [deletedElsewhere] true when it deleted the transaction. */
    data class Clash(val theirs: Transaction?, val deletedElsewhere: Boolean, val mineIsDeletion: Boolean) : TransactionSyncState

    /** [fields] maps the fields the server named to its error code. */
    data class Refused(val reason: RefusalReason, val fields: Map<TransactionField, String>) : TransactionSyncState
}

/** Money in, money out, or neither, from one wallet's side. */
enum class Direction { In, Out, None }

/** The other wallet of a transfer, seen from one side. */
sealed interface Counterpart {
    data class Named(val name: String) : Counterpart
    data object Unknown : Counterpart
}

sealed interface AddedBy {
    data object You : AddedBy
    data class Member(val name: String) : AddedBy
    data object AnotherMember : AddedBy
}

/** One transaction as a screen sees it, from one wallet's side. */
data class TransactionView(
    val id: String,
    /** Null for a create that never reached the server. */
    val server: Transaction?,
    /** The waiting change's content, else the server copy. */
    val shown: TransactionDraft,
    val createdAt: Instant,
    val pendingDelete: Boolean,
    val syncState: TransactionSyncState,
    val direction: Direction,
    val counterpart: Counterpart?,
    val addedBy: AddedBy,
    val fromRecurringRule: Boolean,
    val canEdit: Boolean,
    val canDelete: Boolean,
    /** The transaction's own wallet's present currency on this phone, else the shown one (FR-026). */
    val walletCurrency: String,
) {
    /** The version this phone last saw; 0 for a create that never reached the server. */
    val baseVersion: Long get() = server?.version ?: 0

    /** A delete that is still only on this phone hides the transaction everywhere (research R-06). */
    val hidden: Boolean get() = pendingDelete && syncState is TransactionSyncState.Waiting

    val waiting: Boolean get() = syncState is TransactionSyncState.Waiting
}

data class DayGroup(val date: LocalDate, val netMinor: Long, val items: List<TransactionView>)

data class TransactionList(
    val walletId: String,
    val currency: String,
    val days: List<DayGroup>,
    val updatedAt: Instant?,
    val loaded: Boolean,
    val canAdd: Boolean,
    /** Everything the wallet holds before search and filters, so "no matches" is told from "nothing yet". */
    val totalCount: Int = 0,
    /** The categories the wallet's transactions use (shown values), for the category filter (FR-028). */
    val categoryIds: Set<String> = emptySet(),
    /** How many of them are waiting to sync, for the offline banner. */
    val waitingCount: Int = 0,
)

/** Search and filters (FR-027, FR-028); values of one filter combine with "or", filters with "and". */
data class TransactionQuery(
    val search: String = "",
    val from: LocalDate? = null,
    val to: LocalDate? = null,
    val categoryIds: Set<String> = emptySet(),
    val kinds: Set<TransactionKind> = emptySet(),
) {
    val isActive: Boolean get() = search.isNotBlank() || from != null || to != null || categoryIds.isNotEmpty() || kinds.isNotEmpty()
}
