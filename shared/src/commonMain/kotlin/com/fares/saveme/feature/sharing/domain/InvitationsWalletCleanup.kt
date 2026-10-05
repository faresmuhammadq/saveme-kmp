package com.fares.saveme.feature.sharing.domain

import com.fares.saveme.core.sync.ScopeKind
import com.fares.saveme.core.sync.SyncEngine
import com.fares.saveme.feature.sharing.data.InvitationRepository
import com.fares.saveme.feature.sharing.data.InvitationSyncHandler
import com.fares.saveme.feature.wallets.domain.WalletDeletionParticipant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * A wallet that leaves the phone takes its invitations with it, with the links held for them and any change waiting for them
 * (spec 013 R-13, FR-023). The waiting changes are dropped from a launched job: this runs inside the sync engine's lock when a
 * wallet's access is lost, and discarding takes that lock (spec 008 lesson).
 */
class InvitationsWalletCleanup(
    private val repository: InvitationRepository,
    private val shareLinks: ShareLinks,
    private val engine: () -> SyncEngine,
    private val scope: CoroutineScope,
) : WalletDeletionParticipant {

    override suspend fun onWalletDeleted(walletId: String) {
        val ids = repository.getForWallet(walletId).map { it.id }
        repository.deleteForWallet(walletId)
        ids.forEach(shareLinks::forget)
        scope.launch {
            val waiting = engine().observeAll(InvitationSyncHandler.RECORD_TYPE).first()
                .filter { it.scope.kind == ScopeKind.Wallet && it.scope.id == walletId }
                .map { it.recordId }
            (ids + waiting).distinct().forEach { id ->
                shareLinks.forget(id)
                engine().discard(InvitationSyncHandler.RECORD_TYPE, id)
            }
        }
    }
}
