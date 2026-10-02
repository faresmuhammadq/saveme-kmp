package com.fares.saveme.feature.account.data

import com.fares.saveme.core.network.AppJson
import com.fares.saveme.core.sync.FeedApplier
import com.fares.saveme.core.sync.ScopeKind
import com.fares.saveme.core.sync.SyncScope
import com.fares.saveme.feature.account.data.dto.DeviceSessionDto
import kotlinx.serialization.json.JsonElement

/** The person's device sessions from their change feed (spec 009 R-13): active ones are kept, the rest removed. */
class DeviceSessionFeedApplier(private val repository: DeviceSessionRepository) : FeedApplier {
    override val scopeKind: ScopeKind = ScopeKind.User
    override val rowType: String = ROW_TYPE

    override suspend fun apply(scope: SyncScope, records: List<JsonElement>) {
        for (record in records) repository.applyFeed(AppJson.decodeFromJsonElement(DeviceSessionDto.serializer(), record))
    }

    override suspend fun sweep(scope: SyncScope, seenIds: Set<String>) = repository.sweep(seenIds)

    companion object {
        const val ROW_TYPE = "device_session"
    }
}
