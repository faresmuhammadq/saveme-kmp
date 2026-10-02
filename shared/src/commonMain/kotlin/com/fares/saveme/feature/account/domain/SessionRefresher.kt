package com.fares.saveme.feature.account.domain

import com.fares.saveme.core.network.ApiErrorCode
import com.fares.saveme.core.sync.ReadOutcome
import com.fares.saveme.core.sync.ReadTarget
import com.fares.saveme.core.sync.SyncRunner
import com.fares.saveme.feature.account.data.DeviceSessionRepository
import com.fares.saveme.feature.account.domain.model.ActionOutcome
import com.fares.saveme.feature.account.domain.model.DeviceSession
import com.fares.saveme.feature.account.domain.model.DeviceSessionList
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlin.time.Instant

/**
 * Keeps the device's copy of the session list current by reading the person's change feed (spec 009
 * R-13): only what changed since the last read comes down. Screens only ever observe [sessions].
 */
class SessionRefresher(
    private val repository: DeviceSessionRepository,
    private val runner: SyncRunner,
) {
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

    /** A read of the person's scope, with no floor (as the whole-list read had none); stored rows stay as they are on failure. */
    suspend fun refresh(): ActionOutcome = when (runner.refresh(ReadTarget.User, force = true)) {
        ReadOutcome.Done -> ActionOutcome.Done
        ReadOutcome.Offline -> ActionOutcome.Offline
        ReadOutcome.Failed -> ActionOutcome.Error(ApiErrorCode.UNKNOWN, null)
    }
}
