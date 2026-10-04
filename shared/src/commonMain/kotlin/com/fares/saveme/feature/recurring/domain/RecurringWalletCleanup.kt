package com.fares.saveme.feature.recurring.domain

import com.fares.saveme.core.sync.SyncEngine
import com.fares.saveme.feature.recurring.data.OccurrenceRepository
import com.fares.saveme.feature.recurring.data.OccurrenceSyncHandler
import com.fares.saveme.feature.recurring.data.RecurringRepository
import com.fares.saveme.feature.recurring.data.RecurringRuleSyncHandler
import com.fares.saveme.feature.transactions.domain.OccurrenceTransactions
import com.fares.saveme.feature.wallets.domain.WalletDeletionParticipant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * A wallet that leaves the phone takes its rules, occurrences, their waiting changes and any pending confirmation with
 * it (FR-033). The waiting changes are dropped from a launched job: this runs inside the sync engine's lock when the
 * wallet's deletion is confirmed, and discarding takes that lock (spec 008 lesson).
 */
class RecurringWalletCleanup(
    private val rules: RecurringRepository,
    private val occurrences: OccurrenceRepository,
    private val occurrenceTransactions: OccurrenceTransactions,
    private val engine: () -> SyncEngine,
    private val scope: CoroutineScope,
) : WalletDeletionParticipant {
    override suspend fun onWalletDeleted(walletId: String) {
        val ruleIds = rules.forWallet(walletId).map { it.id }
        val occurrenceIds = occurrences.forWallet(walletId).map { it.id }
        for (id in occurrenceIds) occurrenceTransactions.dropConfirmationOf(id)
        rules.clearWallet(walletId)
        scope.launch {
            ruleIds.forEach { engine().discard(RecurringRuleSyncHandler.RECORD_TYPE, it) }
            occurrenceIds.forEach { engine().discard(OccurrenceSyncHandler.RECORD_TYPE, it) }
        }
    }
}
