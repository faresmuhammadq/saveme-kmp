package com.fares.saveme.feature.account.domain.model

import kotlin.time.Instant

data class DeviceSession(
    val id: String,
    val deviceName: String,
    val platform: String,
    val appVersion: String,
    val signedInAt: Instant,
    val lastUsedAt: Instant?,
    val isCurrent: Boolean,
)

data class DeviceSessionList(
    val items: List<DeviceSession>,
    val refreshedAt: Instant?,
)
