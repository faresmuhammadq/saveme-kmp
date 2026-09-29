package com.fares.saveme.feature.account.domain

import com.fares.saveme.core.network.ApiResult
import com.fares.saveme.feature.account.data.DeviceSessionRepository
import com.fares.saveme.feature.account.data.dto.DeviceSessionDto
import com.fares.saveme.feature.account.domain.model.ActionOutcome
import com.fares.saveme.feature.account.domain.model.DeviceSession
import com.fares.saveme.feature.account.domain.model.DeviceSessionList
import com.fares.saveme.feature.account.domain.model.toActionOutcome
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Instant

/**
 * Pulls every page of active sessions into the device's copy (research R-02, R-05). No change
 * feed exists yet, so a pull is a full re-read. Screens only ever observe [sessions].
 */
class SessionRefresher(private val repository: DeviceSessionRepository) {
    private val mutex = Mutex()

    val sessions: Flow<DeviceSessionList> = repository.observe().map { (rows, refreshedAt) ->
        DeviceSessionList(
            items = rows.map { row ->
                DeviceSession(
                    id = row.id,
                    deviceName = row.deviceName,
                    platform = row.platform,
                    appVersion = row.appVersion,
                    signedInAt = Instant.parse(row.signedInAt),
                    lastUsedAt = row.lastUsedAt?.let(Instant::parse),
                    isCurrent = row.current,
                )
            },
            refreshedAt = refreshedAt?.let(Instant::parse),
        )
    }

    suspend fun refresh(): ActionOutcome = mutex.withLock {
        val all = mutableListOf<DeviceSessionDto>()
        var cursor: String? = null
        repeat(MAX_PAGES) {
            when (val page = repository.fetchPage(cursor)) {
                is ApiResult.Success -> {
                    all += page.value.items
                    cursor = page.value.nextCursor
                    if (!page.value.hasMore || cursor == null) {
                        repository.store(all)
                        return@withLock ActionOutcome.Done
                    }
                }
                else -> return@withLock page.toActionOutcome() // stored rows stay as they are
            }
        }
        repository.store(all)
        ActionOutcome.Done
    }

    private companion object {
        const val MAX_PAGES = 20
    }
}
