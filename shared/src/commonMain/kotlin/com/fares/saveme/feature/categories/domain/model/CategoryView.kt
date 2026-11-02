package com.fares.saveme.feature.categories.domain.model

import kotlin.time.Instant

/** Why the server refused a change for good, in the app's own terms (research R-07). */
sealed interface RefusalReason {
    data object NotVerified : RefusalReason
    data object NoLongerAvailable : RefusalReason
    data object InUse : RefusalReason
    data object Invalid : RefusalReason

    /** An unexpected refusal; [requestId] is the server's reference for support (P-13.2). */
    data class Other(val requestId: String?) : RefusalReason

    /** A refusal that resending unchanged can cure, so "Try again" is offered. */
    val canRetryUnchanged: Boolean get() = this == NotVerified
}

/** What the sync queue says about a category's change (data-model §3). */
sealed interface SyncState {
    data object None : SyncState

    /** Waiting or being sent. */
    data object Waiting : SyncState

    /** [theirs] is the other device's copy; null with [deletedElsewhere] true when it deleted the category. */
    data class Clash(val theirs: Category?, val deletedElsewhere: Boolean) : SyncState

    /** [fields] maps the fields the server named to its error code. */
    data class Refused(val reason: RefusalReason, val fields: Map<CategoryField, String>) : SyncState
}

/** What the phone shows for one own category: the row, any change waiting on top of it, and the queue's state. */
data class CategoryView(
    val server: Category,
    val pending: CategoryDraft?,
    val pendingDelete: Boolean,
    val syncState: SyncState,
) {
    val id: String get() = server.id

    /** What the person sees: their waiting change if there is one, else the server copy. */
    val shown: CategoryDraft get() = pending ?: server.toDraft()

    /** A delete that is still only on this phone hides the category everywhere (research R-06). */
    val hidden: Boolean get() = pendingDelete && syncState is SyncState.Waiting

    val archived: Boolean get() = shown.archive || server.archivedAt != null

    val waiting: Boolean get() = syncState is SyncState.Waiting

    /**
     * Delete is offered while nothing uses the category: not reported by the server, not by an
     * unsent change here ([usedLocally]), and no deletion already waiting (research R-06). An
     * archived category may still be deleted.
     */
    fun canDelete(usedLocally: Set<String>): Boolean = !server.inUse && server.id !in usedLocally && !pendingDelete
}

/** One category ready to draw: the name is already in the app language for a default. */
data class CategoryDisplay(
    val id: String,
    /** Empty for [CategorySource.Unavailable]; the screen shows its own placeholder text. */
    val name: String,
    val iconKey: String?,
    val colorHex: String?,
    val kindWire: String?,
    val source: CategorySource,
    val archived: Boolean,
    /** The platform's key of a default category (`other_expense`); null for any other. */
    val systemKey: String? = null,
)

/**
 * One snapshot for a screen of transactions: [resolve] answers from the phone's copy. A miss
 * calls [onMiss] (the service uses it to ask for the wallet's references, under a per-wallet floor).
 */
class CategoryDisplays(
    private val lookup: (String) -> CategoryDisplay,
    private val onMiss: () -> Unit = {},
) {
    fun resolve(id: String): CategoryDisplay = lookup(id).also { if (it.source == CategorySource.Unavailable) onMiss() }
}

/** The choosable categories for one kind: own first (by name), then defaults (platform order). */
data class CategoryChoices(
    /** The transaction's current category when it is not already in the lists below (archived, retired, a partner's). */
    val current: CategoryDisplay?,
    val own: List<CategoryDisplay>,
    val defaults: List<CategoryDisplay>,
) {
    val isEmpty: Boolean get() = current == null && own.isEmpty() && defaults.isEmpty()
}

/** The categories screen's data for one kind. */
data class CategoriesOverview(
    val kind: CategoryKind,
    val own: List<CategoryView>,
    val defaults: List<CategoryDisplay>,
    val archived: List<CategoryView>,
    val updatedAt: Instant?,
    val loaded: Boolean,
    val defaultsMissing: Boolean,
    /** Active own plus active default categories of the other kind, for the kind switch's count. */
    val otherKindCount: Int = 0,
)
