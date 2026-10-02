package com.fares.saveme.feature.categories.data

import com.fares.saveme.core.network.AppJson
import com.fares.saveme.core.sync.FeedApplier
import com.fares.saveme.core.sync.ScopeKind
import com.fares.saveme.core.sync.SyncScope
import com.fares.saveme.feature.categories.data.dto.CategoryDto
import com.fares.saveme.feature.categories.data.dto.CategoryReferenceDto
import kotlinx.serialization.json.JsonElement

/** The person's own categories from their change feed (spec 009 FR-004, FR-005, FR-007). */
class CategoryFeedApplier(private val repository: CategoryRepository) : FeedApplier {
    override val scopeKind: ScopeKind = ScopeKind.User
    override val rowType: String = ROW_TYPE

    override suspend fun apply(scope: SyncScope, records: List<JsonElement>) {
        for (record in records) repository.storeFeedCopy(AppJson.decodeFromJsonElement(CategoryDto.serializer(), record))
    }

    override suspend fun sweep(scope: SyncScope, seenIds: Set<String>) = repository.sweepOwn(seenIds)

    companion object {
        const val ROW_TYPE = "category"
    }
}

/** A wallet's partner categories from its change feed (spec 009 FR-001; replaces the per-wallet on-demand read). */
class CategoryReferenceFeedApplier(private val repository: CategoryReferenceRepository) : FeedApplier {
    override val scopeKind: ScopeKind = ScopeKind.Wallet
    override val rowType: String = ROW_TYPE

    override suspend fun apply(scope: SyncScope, records: List<JsonElement>) {
        for (record in records) repository.storeFeedCopy(AppJson.decodeFromJsonElement(CategoryReferenceDto.serializer(), record))
    }

    override suspend fun sweep(scope: SyncScope, seenIds: Set<String>) = repository.sweep(scope.id, seenIds)

    companion object {
        const val ROW_TYPE = "category_reference"
    }
}
