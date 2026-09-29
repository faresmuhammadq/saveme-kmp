package com.fares.saveme.feature.account.domain.model

import kotlinx.datetime.LocalDate
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.time.Instant

/** The server's copy of the profile (glossary: Profile). */
data class Profile(
    val displayName: String?,
    val locale: String,
    val timezoneId: String,
    val defaultCurrency: String,
    val currencyLocked: Boolean,
    val budgetStartDay: Int,
    val budgetStartDayFrom: LocalDate?,
    val version: Long,
) {
    fun toDraft() = ProfileDraft(displayName, locale, timezoneId, defaultCurrency, budgetStartDay)
}

/** The whole writable profile, as the person edits it and as it waits to sync. */
@Serializable
data class ProfileDraft(
    @SerialName("display_name") val displayName: String?,
    val locale: String,
    val timezone: String,
    @SerialName("default_currency") val defaultCurrency: String,
    @SerialName("budget_period_start_day") val budgetStartDay: Int,
)

enum class ProfileField(val pointer: String) {
    DisplayName("/display_name"),
    Locale("/locale"),
    Timezone("/timezone"),
    DefaultCurrency("/default_currency"),
    BudgetStartDay("/budget_period_start_day");

    companion object {
        fun fromPointer(pointer: String?): ProfileField? = entries.firstOrNull { it.pointer == pointer }
    }
}

/** What the device holds: the server copy, any edit waiting to sync, and what the server said about it. */
data class ProfileView(
    val server: Profile,
    val pending: ProfileDraft?,
    val conflict: Profile?,
    val rejection: Map<ProfileField, String>,
    val refreshedAt: Instant?,
) {
    /** What the person sees: their pending edit if there is one, else the server copy. */
    val effective: ProfileDraft get() = pending ?: server.toDraft()
}

sealed interface SyncOutcome {
    data object Synced : SyncOutcome
    data object NothingToSync : SyncOutcome
    data object Conflict : SyncOutcome
    data object Rejected : SyncOutcome
    data object Offline : SyncOutcome
    data class Error(val requestId: String?) : SyncOutcome
}
