package com.fares.saveme.feature.sharing.data

import com.fares.saveme.core.network.AppJson
import com.fares.saveme.core.sync.FeedApplier
import com.fares.saveme.core.sync.ScopeKind
import com.fares.saveme.core.sync.SyncScope
import com.fares.saveme.feature.sharing.data.dto.InvitationDto
import kotlinx.serialization.json.JsonElement

/** A wallet's pending invitations from its change feed: version guard, tombstones, sweep (spec 009; spec 013 FR-029). */
class InvitationFeedApplier(private val repository: InvitationRepository) : FeedApplier {
    override val scopeKind: ScopeKind = ScopeKind.Wallet
    override val rowType: String = ROW_TYPE

    override suspend fun apply(scope: SyncScope, records: List<JsonElement>) {
        for (record in records) repository.storeFeedCopy(AppJson.decodeFromJsonElement(InvitationDto.serializer(), record))
    }

    override suspend fun sweep(scope: SyncScope, seenIds: Set<String>) = repository.sweep(scope.id, seenIds)

    companion object {
        const val ROW_TYPE = "invitation"
    }
}
