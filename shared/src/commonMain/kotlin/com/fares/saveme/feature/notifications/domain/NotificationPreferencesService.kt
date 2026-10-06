package com.fares.saveme.feature.notifications.domain

import com.fares.saveme.core.network.ApiErrorCode
import com.fares.saveme.core.network.ApiResult
import com.fares.saveme.feature.notifications.data.NotificationsApi
import com.fares.saveme.feature.notifications.data.currentPreferences
import com.fares.saveme.feature.notifications.data.dto.NotificationCategoriesDto
import com.fares.saveme.feature.notifications.data.dto.NotificationPreferencesDto
import com.fares.saveme.feature.notifications.data.dto.NotificationPreferencesWriteDto
import com.fares.saveme.feature.notifications.data.dto.QuietHoursDto
import kotlin.time.Instant

sealed interface PreferencesLoad {
    data class Loaded(val preferences: NotificationPreferences) : PreferencesLoad
    data object Offline : PreferencesLoad
    data class Failed(val requestId: String?) : PreferencesLoad
}

sealed interface PreferencesSave {
    data class Saved(val preferences: NotificationPreferences) : PreferencesSave

    /** A stale version: [current] replaces the edits, nothing is merged (FR-018). */
    data class Conflict(val current: NotificationPreferences) : PreferencesSave
    data class RateLimited(val seconds: Long?) : PreferencesSave
    data object Offline : PreferencesSave
    data class Invalid(val requestId: String?) : PreferencesSave
    data class Failed(val requestId: String?) : PreferencesSave
}

/**
 * Online-only reads and one explicit save (spec clarification 3): one request per call, never retried (P-02.6), nothing
 * cached or queued. Outcomes are chosen by the problem `code` (P-02.4).
 */
class NotificationPreferencesService(private val api: NotificationsApi) {

    suspend fun load(): PreferencesLoad = when (val result = api.preferences()) {
        is ApiResult.Success -> result.value.toDomain()?.let { PreferencesLoad.Loaded(it) } ?: PreferencesLoad.Failed(null)
        ApiResult.Offline, ApiResult.Timeout -> PreferencesLoad.Offline
        is ApiResult.Failure -> PreferencesLoad.Failed(result.problem.requestId)
    }

    suspend fun save(preferences: NotificationPreferences, version: Long): PreferencesSave =
        when (val result = api.savePreferences(preferences.toWrite(version))) {
            is ApiResult.Success -> result.value.toDomain()?.let { PreferencesSave.Saved(it) } ?: PreferencesSave.Failed(null)
            ApiResult.Offline, ApiResult.Timeout -> PreferencesSave.Offline
            is ApiResult.Failure -> when (result.code) {
                ApiErrorCode.VERSION_CONFLICT ->
                    result.problem.currentPreferences()?.toDomain()?.let { PreferencesSave.Conflict(it) }
                        ?: PreferencesSave.Failed(result.problem.requestId)
                ApiErrorCode.RATE_LIMITED -> PreferencesSave.RateLimited(result.retryAfterSeconds)
                ApiErrorCode.VALIDATION_FAILED -> PreferencesSave.Invalid(result.problem.requestId)
                else -> PreferencesSave.Failed(result.problem.requestId)
            }
        }
}

internal fun NotificationPreferencesDto.toDomain(): NotificationPreferences? {
    val start = QuarterHour.parse(quietHours.start) ?: return null
    val end = QuarterHour.parse(quietHours.end) ?: return null
    return NotificationPreferences(
        version = version,
        categories = mapOf(
            NotificationCategory.RecurringDue to categories.recurringDue,
            NotificationCategory.BudgetExceeded to categories.budgetExceeded,
            NotificationCategory.Sharing to categories.sharing,
            NotificationCategory.Billing to categories.billing,
            NotificationCategory.ExportReady to categories.exportReady,
            NotificationCategory.DeletionReminder to categories.deletionReminder,
        ),
        quietHours = QuietHours(quietHours.enabled, start, end),
        updatedAt = updatedAt?.let { runCatching { Instant.parse(it) }.getOrNull() },
    )
}

internal fun NotificationPreferences.toWrite(version: Long) = NotificationPreferencesWriteDto(
    version = version,
    categories = NotificationCategoriesDto(
        recurringDue = isOn(NotificationCategory.RecurringDue),
        budgetExceeded = isOn(NotificationCategory.BudgetExceeded),
        sharing = isOn(NotificationCategory.Sharing),
        billing = isOn(NotificationCategory.Billing),
        exportReady = isOn(NotificationCategory.ExportReady),
        deletionReminder = isOn(NotificationCategory.DeletionReminder),
    ),
    quietHours = QuietHoursDto(quietHours.enabled, quietHours.start.format(), quietHours.end.format()),
)
