package com.fares.saveme.feature.auth.data.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonPrimitive

// Mirrors saveme-backend/docs/openapi/paths/identity.yaml. Requests carry only declared members;
// the server refuses anything else with 422 UNKNOWN_FIELD (P-02.2).

@Serializable
data class DeviceDescriptionDto(
    @SerialName("session_id") val sessionId: String,
    @SerialName("device_id") val deviceId: String,
    val name: String,
    val platform: String,
    @SerialName("app_version") val appVersion: String,
)

@Serializable
data class SignInRequest(
    val email: String,
    val password: String,
    val device: DeviceDescriptionDto,
)

@Serializable
data class DeviceSessionDto(
    val id: String,
    @SerialName("device_name") val deviceName: String? = null,
    val platform: String? = null,
    @SerialName("app_version") val appVersion: String? = null,
    val state: String? = null,
    @SerialName("signed_in_at") val signedInAt: String? = null,
)

@Serializable
data class AccountDto(
    val id: String,
    val email: String,
    @SerialName("email_verified") val emailVerified: Boolean,
    val state: String,
    @SerialName("deletion_scheduled_for") val deletionScheduledFor: String? = null,
)

/** billing.yaml `PlanRef`: the governing plan, for display only. Gate on features. */
@Serializable
data class PlanRefDto(
    val id: String,
    val name: String,
)

@Serializable
data class EntitlementSummaryDto(
    val plan: PlanRefDto,
    val features: Map<String, JsonPrimitive> = emptyMap(),
)

@Serializable
data class SignInResultDto(
    val credential: String,
    val session: DeviceSessionDto,
    val account: AccountDto,
    val entitlements: EntitlementSummaryDto,
    @SerialName("deletion_cancelled") val deletionCancelled: Boolean = false,
)

@Serializable
data class AcceptedDto(val status: String)

/** What the device keeps between launches so relaunch needs no network call (SC-006). */
@Serializable
data class AccountSnapshotDto(
    val account: AccountDto,
    val entitlements: EntitlementSummaryDto,
)
