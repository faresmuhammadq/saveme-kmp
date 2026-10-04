package com.fares.saveme.feature.recurring.data

import com.fares.saveme.core.network.AppJson
import com.fares.saveme.core.sync.FeedApplier
import com.fares.saveme.core.sync.ScopeKind
import com.fares.saveme.core.sync.SyncScope
import com.fares.saveme.feature.recurring.data.dto.OccurrenceDto
import com.fares.saveme.feature.recurring.data.dto.RecurringRuleDto
import kotlinx.serialization.json.JsonElement

/** A wallet's recurring rules from its change feed (spec 010 research R-02, R-03). */
class RecurringRuleFeedApplier(private val repository: RecurringRepository) : FeedApplier {
    override val scopeKind: ScopeKind = ScopeKind.Wallet
    override val rowType: String = ROW_TYPE

    override suspend fun apply(scope: SyncScope, records: List<JsonElement>) {
        for (record in records) repository.storeCopy(AppJson.decodeFromJsonElement(RecurringRuleDto.serializer(), record), respectQueued = true)
    }

    override suspend fun sweep(scope: SyncScope, seenIds: Set<String>) = repository.sweepWallet(scope.id, seenIds)

    companion object {
        const val ROW_TYPE = "recurring_rule"
    }
}

/**
 * A wallet's occurrences from its change feed. When one becomes `confirmed` under a transaction id other
 * than the confirmation waiting on this phone (another device, or the server, confirmed first), the
 * phone-only transaction row goes (research R-07).
 *
 * @param dropConfirmation see [OccurrenceSyncHandler].
 * @param sweepConfirmations after a complete read: the confirmation rows of confirmed occurrences whose transaction
 *   never arrived go (research R-07).
 */
class OccurrenceFeedApplier(
    private val repository: OccurrenceRepository,
    private val dropConfirmation: suspend (occurrenceId: String, exceptTransactionId: String?) -> Unit,
    private val sweepConfirmations: suspend (walletId: String, confirmedTransactionIds: Set<String>) -> Unit = { _, _ -> },
) : FeedApplier {
    override val scopeKind: ScopeKind = ScopeKind.Wallet
    override val rowType: String = ROW_TYPE

    override suspend fun apply(scope: SyncScope, records: List<JsonElement>) {
        for (record in records) {
            val dto = AppJson.decodeFromJsonElement(OccurrenceDto.serializer(), record)
            val wasConfirmed = repository.get(dto.id)?.status == "confirmed"
            val stored = repository.storeCopy(dto, respectQueued = true)
            if (stored && dto.deletedAt == null && dto.status == "confirmed" && dto.transactionId != null && !wasConfirmed) {
                dropConfirmation(dto.id, dto.transactionId)
            }
        }
    }

    override suspend fun sweep(scope: SyncScope, seenIds: Set<String>) {
        repository.sweepWallet(scope.id, seenIds)
        val confirmed = repository.forWallet(scope.id).filter { it.status == "confirmed" }.mapNotNull { it.transactionId }.toSet()
        if (confirmed.isNotEmpty()) sweepConfirmations(scope.id, confirmed)
    }

    companion object {
        const val ROW_TYPE = "occurrence"
    }
}
