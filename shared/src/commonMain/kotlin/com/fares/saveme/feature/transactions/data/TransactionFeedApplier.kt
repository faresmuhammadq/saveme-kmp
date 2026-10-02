package com.fares.saveme.feature.transactions.data

import com.fares.saveme.core.network.AppJson
import com.fares.saveme.core.sync.FeedApplier
import com.fares.saveme.core.sync.ScopeKind
import com.fares.saveme.core.sync.SyncScope
import com.fares.saveme.feature.transactions.data.dto.TransactionDto
import kotlinx.serialization.json.JsonElement

/**
 * A wallet's transactions from its change feed (spec 009 FR-004, FR-005, FR-007, FR-012). A transfer
 * arrives on both wallets' feeds and is stored once (the row is keyed by id); it is listed under
 * both wallets by the table's two wallet columns.
 */
class TransactionFeedApplier(private val repository: TransactionRepository) : FeedApplier {
    override val scopeKind: ScopeKind = ScopeKind.Wallet
    override val rowType: String = ROW_TYPE

    override suspend fun apply(scope: SyncScope, records: List<JsonElement>) {
        for (record in records) repository.storeFeedCopy(AppJson.decodeFromJsonElement(TransactionDto.serializer(), record))
    }

    override suspend fun sweep(scope: SyncScope, seenIds: Set<String>) = repository.sweepWallet(scope.id, seenIds)

    companion object {
        const val ROW_TYPE = "transaction"
    }
}
