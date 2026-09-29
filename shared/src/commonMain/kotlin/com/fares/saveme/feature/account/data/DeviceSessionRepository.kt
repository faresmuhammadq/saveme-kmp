package com.fares.saveme.feature.account.data

import com.fares.saveme.core.database.SyncScopeDao
import com.fares.saveme.core.database.SyncScopeEntity
import com.fares.saveme.core.network.ApiResult
import com.fares.saveme.core.network.Page
import com.fares.saveme.feature.account.data.dto.DeviceSessionDto
import com.fares.saveme.feature.account.data.local.DeviceSessionDao
import com.fares.saveme.feature.account.data.local.DeviceSessionEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlin.time.Clock

/** API calls and the device's copy of the session list. No decisions here (P-10.6). */
class DeviceSessionRepository(
    private val api: AccountApi,
    private val sessions: DeviceSessionDao,
    private val scopes: SyncScopeDao,
    private val clock: Clock = Clock.System,
) {
    /** The stored sessions and when they were last pulled (ISO instant, or null if never). */
    fun observe(): Flow<Pair<List<DeviceSessionEntity>, String?>> =
        combine(sessions.observeAll(), scopes.observe(SyncScopeEntity.DEVICE_SESSIONS)) { rows, scope ->
            rows to scope?.refreshedAt
        }

    suspend fun fetchPage(cursor: String?): ApiResult<Page<DeviceSessionDto>> = api.listSessions(cursor)

    suspend fun store(items: List<DeviceSessionDto>) {
        sessions.replaceAll(items.map { it.toEntity() })
        scopes.upsert(SyncScopeEntity(SyncScopeEntity.DEVICE_SESSIONS, clock.now().toString()))
    }

    suspend fun end(id: String): ApiResult<Unit> = api.endSession(id)

    suspend fun endOthers(): ApiResult<Unit> = api.endOtherSessions()

    suspend fun forget(id: String) = sessions.delete(id)
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
