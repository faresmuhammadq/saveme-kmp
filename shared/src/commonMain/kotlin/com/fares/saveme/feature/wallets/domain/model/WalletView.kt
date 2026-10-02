package com.fares.saveme.feature.wallets.domain.model

import kotlin.time.Instant

enum class RefusalKind { NotVerified, NotEntitled, NotOwner, NoLongerAvailable, Invalid, Other }

/** Why the server refused a change for good, in the app's own terms (research R-08). */
data class RefusalReason(val kind: RefusalKind, val requestId: String? = null) {
    /** A refusal that fixing and resending can cure, so "Try again" is offered. */
    val canRetryUnchanged: Boolean get() = kind == RefusalKind.NotVerified || kind == RefusalKind.NotEntitled
}

/** What the sync queue says about a wallet's change (data-model §3). */
sealed interface SyncState {
    data object None : SyncState

    /** Waiting or being sent. */
    data object Waiting : SyncState

    /** [theirs] is the other device's copy; null with [deletedElsewhere] true when it deleted the wallet. */
    data class Clash(val theirs: Wallet?, val deletedElsewhere: Boolean) : SyncState

    /** [fields] maps the fields the server named to its error code. */
    data class Refused(val reason: RefusalReason, val fields: Map<WalletField, String>) : SyncState
}

/** What the phone shows for one wallet: the row, any change waiting on top of it, and the queue's state. */
data class WalletView(
    val server: Wallet,
    val pending: WalletDraft?,
    val pendingDelete: Boolean,
    val syncState: SyncState,
    val displayedBalanceMinor: Long,
    /** C6's unsent transactions; keeps the "waiting" mark while anything of the wallet is unsent. */
    val hasPendingPostings: Boolean = false,
) {
    /** What the person sees: their waiting change if there is one, else the server copy. */
    val shown: WalletDraft get() = pending ?: server.toDraft()

    /** A delete that is still only on this phone hides the wallet everywhere (research R-06). */
    val hidden: Boolean get() = pendingDelete && syncState is SyncState.Waiting

    val archived: Boolean get() = shown.archive || server.archivedAt != null

    val isLiability: Boolean get() = shown.walletType.isLiability

    val waiting: Boolean get() = syncState is SyncState.Waiting || hasPendingPostings

    val id: String get() = server.id
}

/** The list screen's data: visible wallets (hidden ones dropped) and the per-currency totals. */
data class WalletList(
    val active: List<WalletView>,
    val archived: List<WalletView>,
    val totals: List<CurrencyTotal>,
    val updatedAt: Instant?,
)
