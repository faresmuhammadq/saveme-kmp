package com.fares.saveme.feature.account.data.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** identity.yaml `DeviceSession`; syncable members the client does not use are ignored. */
@Serializable
data class DeviceSessionDto(
    val id: String,
    @SerialName("user_id") val userId: String,
    val version: Long = 0,
    @SerialName("device_name") val deviceName: String,
    val platform: String,
    @SerialName("app_version") val appVersion: String,
    val state: String,
    @SerialName("end_reason") val endReason: String? = null,
    @SerialName("signed_in_at") val signedInAt: String,
    @SerialName("last_used_at") val lastUsedAt: String? = null,
    @SerialName("ended_at") val endedAt: String? = null,
    val current: Boolean = false,
    @SerialName("deleted_at") val deletedAt: String? = null,
)
