package com.fares.saveme.feature.account.data

import com.fares.saveme.core.database.SyncScopeDao
import com.fares.saveme.core.database.SyncScopeEntity
import com.fares.saveme.core.network.ApiResult
import com.fares.saveme.core.sync.ScopeKind
import com.fares.saveme.core.sync.SyncScope
import com.fares.saveme.feature.account.data.dto.DeviceSessionDto
import com.fares.saveme.feature.account.data.local.DeviceSessionDao
import com.fares.saveme.feature.account.data.local.DeviceSessionEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/** API calls and the device's copy of the session list. No decisions here (P-10.6). */
class DeviceSessionRepository(
    private val api: AccountApi,
    private val sessions: DeviceSessionDao,
    private val scopes: SyncScopeDao,
    private val accountId: () -> String? = { null },
) {
    /**
     * The stored sessions and when the person's scope was last read completely (ISO instant, or null if
     * never). An upgraded phone keeps the time of its last whole-list read until its first feed read ends.
     */
    fun observe(): Flow<Pair<List<DeviceSessionEntity>, String?>> =
        combine(
            sessions.observeAll(),
            scopes.observeFeedReadAt(SyncScope(ScopeKind.User, accountId().orEmpty()).key),
            scopes.observe(SyncScopeEntity.DEVICE_SESSIONS),
        ) { rows, feedReadAt, legacy -> rows to (feedReadAt ?: legacy?.refreshedAt?.takeIf { it.isNotEmpty() }) }

    /**
     * A session from the person's feed: an active one is kept, any other state or a deletion removes it
     * (the screen lists active sessions only). The table keeps no version, so rows are applied in feed order.
     */
    suspend fun applyFeed(dto: DeviceSessionDto) {
        if (dto.deletedAt != null || dto.state != ACTIVE) sessions.delete(dto.id) else sessions.upsert(dto.toEntity())
    }

    /** After a complete read from the start: sessions the server did not return are gone. */
    suspend fun sweep(seenIds: Set<String>) {
        sessions.getAll().filter { it.id !in seenIds }.forEach { sessions.delete(it.id) }
    }

    suspend fun end(id: String): ApiResult<Unit> = api.endSession(id)

    suspend fun endOthers(): ApiResult<Unit> = api.endOtherSessions()

    suspend fun forget(id: String) = sessions.delete(id)

    private companion object {
        const val ACTIVE = "active"
    }
}

private fun DeviceSessionDto.toEntity() = DeviceSessionEntity(
    id = id,
    userId = userId,
    deviceName = deviceName,
    platform = platform,
    appVersion = appVersion,
    state = state,
    signedInAt = signedInAt,
    lastUsedAt = lastUsedAt,
    current = current,
)
