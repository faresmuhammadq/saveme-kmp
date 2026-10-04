package com.fares.saveme.feature.transactions.domain

import com.fares.saveme.core.sync.PendingChange
import com.fares.saveme.core.sync.SyncEngine
import com.fares.saveme.feature.transactions.data.BalanceEffect
import com.fares.saveme.feature.transactions.data.TransactionRepository
import com.fares.saveme.feature.transactions.data.TransactionSyncHandler
import com.fares.saveme.feature.transactions.data.local.TransactionEntity
import com.fares.saveme.feature.wallets.domain.PendingPostings
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map

/**
 * What unsent transactions do to wallet balances (FR-017, FR-029; research R-09), bound in place of
 * C4's `ZeroPendingPostings`. For one wallet: the sum over waiting, sending, clashed and refused
 * changes of the effect of what is shown minus the effect of the stored row, plus the carry of
 * changes the server confirmed that no wallet read has included yet.
 *
 * @param engine resolved on use: the engine is built from every sync handler (spec 006 lesson).
 */
class TransactionPostings(
    private val engine: () -> SyncEngine,
    private val repository: TransactionRepository,
) : PendingPostings {

    /**
     * Waiting changes and the rows they change, plus every carried row, read together so the delta
     * never counts a change twice or not at all while a send's answer is being stored (research R-09).
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    private fun snapshot(): Flow<Snapshot> = engine().observeAll(TransactionSyncHandler.RECORD_TYPE).flatMapLatest { changes ->
        repository.observeRelevant(changes.map { it.recordId }).map { rows ->
            val byId = rows.associateBy { it.id }
            Snapshot(changes.map { QueuedEffect(it, byId[it.recordId]) }, rows)
        }
    }

    override fun deltaFor(walletId: String): Flow<Long> =
        snapshot().map { snap -> snap.queue.sumOf { it.deltaFor(walletId) } + snap.carryFor(walletId) + snap.pendingConfirmationsFor(walletId) }

    /**
     * True while a change touching the wallet waits, and also after it synced until a wallet read has
     * included it (its carry): until then the phone's copy of the wallet predates the change, so its
     * `currency_locked` may still read false and the editor must keep the currency locked (US1-S10).
     */
    override fun hasPending(walletId: String): Flow<Boolean> = snapshot().map { snap ->
        snap.queue.any { walletId in it.touched } || snap.carriedTouch(walletId) || snap.pendingTouch(walletId)
    }

    private class Snapshot(val queue: List<QueuedEffect>, val rows: List<TransactionEntity>) {
        fun carriedTouch(walletId: String): Boolean =
            rows.any { it.carryConfirmedAt != null && (it.walletId == walletId || it.counterWalletId == walletId) }

        private fun pendingRows() = rows.filter { it.pendingConfirmation && it.version == 0L }

        fun pendingTouch(walletId: String): Boolean = pendingRows().any { it.walletId == walletId || it.counterWalletId == walletId }

        /** The full effect of the phone-only confirmation rows on [walletId] (both wallets of a transfer; spec 010 R-07). */
        fun pendingConfirmationsFor(walletId: String): Long = pendingRows().sumOf {
            BalanceEffect.of(it.kind, it.amountMinor, it.walletId, it.counterWalletId, it.deletedAt != null, walletId)
        }

        /** The effect the server confirmed on [walletId] that no wallet read has included yet. */
        fun carryFor(walletId: String): Long = rows.sumOf { row ->
            (if (row.walletId == walletId) row.carrySourceMinor else 0L) +
                (if (row.counterWalletId == walletId) row.carryCounterMinor else 0L)
        }
    }
}

/** One waiting change next to the row it changes. */
internal class QueuedEffect(private val change: PendingChange, private val row: TransactionEntity?) {
    private val payload = if (change.isDelete) null else decodePayload(change.payloadJson)

    /** The wallets this change can move: the scope's source and any destination. */
    val touched: Set<String> = setOfNotNull(change.scope.id, row?.walletId, row?.counterWalletId, payload?.counterWalletId)

    private val walletId = row?.walletId ?: change.scope.id
    private val counterWalletId = row?.counterWalletId ?: payload?.counterWalletId

    /** The stored row's effect: a placeholder (never sent) and a hidden deletion have none in the wallet's balance. */
    private fun storedEffect(forWallet: String): Long {
        if (row == null || row.version == 0L) return 0
        return BalanceEffect.of(row.kind, row.amountMinor, row.walletId, row.counterWalletId, row.deletedAt != null, forWallet)
    }

    /** What the person sees: nothing for a deletion, the waiting content otherwise. */
    private fun shownEffect(forWallet: String): Long {
        val content = payload ?: return 0
        return BalanceEffect.of(row?.kind ?: content.kind, content.amountMinor, walletId, counterWalletId, deleted = false, forWallet = forWallet)
    }

    fun deltaFor(forWallet: String): Long = shownEffect(forWallet) - storedEffect(forWallet)
}
