package com.fares.saveme.feature.transactions.domain

import com.fares.saveme.core.sync.SyncEngine
import com.fares.saveme.feature.transactions.data.TransactionRepository
import com.fares.saveme.feature.transactions.data.TransactionSyncHandler
import com.fares.saveme.feature.wallets.domain.WalletCopyParticipant
import com.fares.saveme.feature.wallets.domain.WalletDeletionParticipant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * A wallet that is deleted, or leaves the phone, takes its transactions with it, with any change
 * waiting for them; a transfer whose other wallet is still on the phone stays (FR-031, research R-19).
 *
 * The waiting changes are dropped from a launched job: this runs inside the sync engine's own lock
 * when the wallet's deletion is confirmed, and discarding takes that lock.
 *
 * It also settles, when a server copy of a wallet is stored, the source part of every carry that copy already
 * counts (spec 009 FR-013): the balance then shows the server's figure without counting those changes twice.
 *
 * @param walletOnPhone whether a wallet is still on the phone (after the removal that called this).
 */
class TransactionsWalletCleanup(
    private val repository: TransactionRepository,
    private val engine: () -> SyncEngine,
    private val walletOnPhone: suspend (String) -> Boolean,
    private val scope: CoroutineScope,
) : WalletDeletionParticipant, WalletCopyParticipant {

    override suspend fun onWalletCopy(walletId: String, changeSeq: Long) = repository.clearSourceCarryCoveredBy(walletId, changeSeq)

    override suspend fun onWalletDeleted(walletId: String) {
        for (row in repository.touching(walletId)) {
            val other = if (row.walletId == walletId) row.counterWalletId else row.walletId
            if (other != null && walletOnPhone(other)) continue
            repository.remove(row.id)
            scope.launch { engine().discard(TransactionSyncHandler.RECORD_TYPE, row.id) }
        }
    }
}
