package com.fares.saveme.feature.notifications.data.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** notifications.yaml `PushRegistrationWrite`: the push address is the only member (FR-039). Never printed (P-09.2). */
@Serializable
class PushRegistrationWriteDto(@SerialName("push_token") val pushToken: String) {
    override fun toString() = "PushRegistrationWriteDto(<redacted>)"
}

/** notifications.yaml `PushRegistration`; read for its outcome only, unknown members ignored. */
@Serializable
data class PushRegistrationDto(
    @SerialName("session_id") val sessionId: String,
    @SerialName("registered_at") val registeredAt: String,
    @SerialName("refreshed_at") val refreshedAt: String,
)

@Serializable
data class NotificationCategoriesDto(
    @SerialName("recurring_due") val recurringDue: Boolean,
    @SerialName("budget_exceeded") val budgetExceeded: Boolean,
    val sharing: Boolean,
    val billing: Boolean,
    @SerialName("export_ready") val exportReady: Boolean,
    @SerialName("deletion_reminder") val deletionReminder: Boolean,
)

@Serializable
data class QuietHoursDto(
    val enabled: Boolean,
    /** `HH:MM` on a quarter hour. */
    val start: String,
    val end: String,
)

/** notifications.yaml `NotificationPreferences`: the GET and PUT answer, and `ProblemDetails.current` on 409. */
@Serializable
data class NotificationPreferencesDto(
    val version: Long,
    val categories: NotificationCategoriesDto,
    @SerialName("quiet_hours") val quietHours: QuietHoursDto,
    @SerialName("updated_at") val updatedAt: String? = null,
)

/** notifications.yaml `NotificationPreferencesWrite`: no `updated_at` (FR-039). */
@Serializable
data class NotificationPreferencesWriteDto(
    val version: Long,
    val categories: NotificationCategoriesDto,
    @SerialName("quiet_hours") val quietHours: QuietHoursDto,
)
