package com.fares.saveme.feature.account.data.dto

import com.fares.saveme.feature.auth.data.dto.AccountDto
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

/** identity.yaml `ChangePasswordRequest`: exactly these two members. */
@Serializable
data class ChangePasswordRequest(
    @SerialName("current_password") val currentPassword: String,
    @SerialName("new_password") val newPassword: String,
)

/** identity.yaml `Profile`; syncable members the client does not use are ignored. */
@Serializable
data class ProfileDto(
    val id: String,
    @SerialName("user_id") val userId: String,
    val version: Long,
    @SerialName("display_name") val displayName: String? = null,
    val locale: String,
    val timezone: String,
    @SerialName("default_currency") val defaultCurrency: String,
    @SerialName("default_currency_locked") val defaultCurrencyLocked: Boolean = false,
    @SerialName("budget_period_start_day") val budgetPeriodStartDay: Int,
    @SerialName("budget_period_start_day_from") val budgetPeriodStartDayFrom: String? = null,
)

/** identity.yaml `Me`. */
@Serializable
data class MeDto(
    val account: AccountDto,
    val profile: ProfileDto,
)

/** identity.yaml `ProfileWrite`: the whole writable profile plus the last-seen version. */
data class ProfileWriteRequest(
    val displayName: String?,
    val locale: String,
    val timezone: String,
    val defaultCurrency: String,
    val budgetPeriodStartDay: Int,
    val version: Long,
) {
    /**
     * Built by hand because `display_name` is required even when cleared, and the app's JSON
     * settings drop nulls (`explicitNulls = false`).
     */
    fun toJson(): JsonObject = buildJsonObject {
        put("display_name", displayName?.let { JsonPrimitive(it) } ?: JsonNull)
        put("locale", JsonPrimitive(locale))
        put("timezone", JsonPrimitive(timezone))
        put("default_currency", JsonPrimitive(defaultCurrency))
        put("budget_period_start_day", JsonPrimitive(budgetPeriodStartDay))
        put("version", JsonPrimitive(version))
    }
}

/** identity.yaml `DeletionSuccessor`: who would own a shared wallet the person owns. [reason] stays raw (P-02.3). */
@Serializable
data class DeletionSuccessorDto(
    @SerialName("wallet_id") val walletId: String,
    @SerialName("successor_user_id") val successorUserId: String,
    val reason: String,
    @SerialName("choice_expected") val choiceExpected: Boolean = false,
)

/** identity.yaml `DeletionPlan`. [state] stays raw so an unknown state never fails decoding. */
@Serializable
data class DeletionPlanDto(
    val state: String,
    @SerialName("deletion_requested_at") val deletionRequestedAt: String? = null,
    @SerialName("deletion_scheduled_for") val deletionScheduledFor: String? = null,
    val successors: List<DeletionSuccessorDto> = emptyList(),
)

/** One entry of `DeletionRequest.successors`. */
@Serializable
data class SuccessorChoiceDto(
    @SerialName("wallet_id") val walletId: String,
    @SerialName("member_user_id") val memberUserId: String,
)

/** identity.yaml `DeletionRequest`: the password and, only when owners were chosen, `successors`. Never prints the password. */
@Serializable
data class DeletionRequestDto(
    val password: String,
    val successors: List<SuccessorChoiceDto>? = null,
) {
    override fun toString() = "DeletionRequestDto(password=***, successors=${successors?.size ?: 0})"
}

/** identity.yaml `DeletionSchedule`. */
@Serializable
data class DeletionScheduleDto(
    val state: String,
    @SerialName("deletion_scheduled_for") val deletionScheduledFor: String,
    val successors: List<DeletionSuccessorDto> = emptyList(),
)
