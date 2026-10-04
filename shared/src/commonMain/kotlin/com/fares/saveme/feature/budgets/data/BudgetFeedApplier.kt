package com.fares.saveme.feature.budgets.data

import com.fares.saveme.core.network.AppJson
import com.fares.saveme.core.sync.FeedApplier
import com.fares.saveme.core.sync.ScopeKind
import com.fares.saveme.core.sync.SyncScope
import com.fares.saveme.feature.budgets.data.dto.BudgetDto
import kotlinx.serialization.json.JsonElement

/** The person's own budgets from their change feed (spec 009 FR-004, FR-005, FR-007; spec 012 FR-019). */
class BudgetFeedApplier(private val repository: BudgetRepository) : FeedApplier {
    override val scopeKind: ScopeKind = ScopeKind.User
    override val rowType: String = ROW_TYPE

    override suspend fun apply(scope: SyncScope, records: List<JsonElement>) {
        for (record in records) repository.storeFeedCopy(AppJson.decodeFromJsonElement(BudgetDto.serializer(), record))
    }

    override suspend fun sweep(scope: SyncScope, seenIds: Set<String>) = repository.sweepOwn(seenIds)

    companion object {
        const val ROW_TYPE = "budget"
    }
}
