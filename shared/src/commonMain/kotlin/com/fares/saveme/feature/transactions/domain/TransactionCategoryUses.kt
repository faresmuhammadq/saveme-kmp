package com.fares.saveme.feature.transactions.domain

import com.fares.saveme.core.sync.SyncEngine
import com.fares.saveme.feature.categories.domain.PendingCategoryUses
import com.fares.saveme.feature.transactions.data.TransactionSyncHandler
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** The categories unsent transaction changes use, so C5 offers no Delete for them (FR-030, research R-10). */
class TransactionCategoryUses(private val engine: () -> SyncEngine) : PendingCategoryUses {
    override fun usedLocally(): Flow<Set<String>> = engine().observeAll(TransactionSyncHandler.RECORD_TYPE).map { changes ->
        changes.filter { !it.isDelete }.mapNotNull { decodePayload(it.payloadJson)?.categoryId }.toSet()
    }
}
