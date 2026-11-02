package com.fares.saveme.feature.sharedbudgets.data.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * shared-budgets.yaml `LimitHistory` entry: the limit a Budget Period reads is the latest entry at or before its start. `limitMinor`
 * null means no limit from then on. Amounts are the person's own and never printed (P-09.2).
 */
@Serializable
data class LimitStepDto(
    @SerialName("applies_from") val appliesFrom: String,
    @SerialName("limit_minor") val limitMinor: Long? = null,
) {
    override fun toString() = "LimitStepDto()"
}

/** shared-budgets.yaml `StartDayHistory` entry; the first has `applies_from` null. */
@Serializable
data class StartDayStepDto(
    @SerialName("start_day") val startDay: Int,
    @SerialName("applies_from") val appliesFrom: String? = null,
)

/** shared-budgets.yaml `SharedBudget`; members a later server adds are ignored (P-02.3). */
@Serializable
data class SharedBudgetDto(
    val id: String,
    @SerialName("owner_id") val ownerId: String = "",
    val name: String = "",
    val currency: String = "",
    @SerialName("start_day") val startDay: Int = 1,
    val timezone: String = "",
    @SerialName("limit_minor") val limitMinor: Long? = null,
    @SerialName("archived_at") val archivedAt: String? = null,
    @SerialName("limit_history") val limitHistory: List<LimitStepDto> = emptyList(),
    @SerialName("start_day_history") val startDayHistory: List<StartDayStepDto> = emptyList(),
    val version: Long,
    @SerialName("change_seq") val changeSeq: Long = 0,
    @SerialName("created_at") val createdAt: String,
    @SerialName("updated_at") val updatedAt: String,
    @SerialName("deleted_at") val deletedAt: String? = null,
) {
    override fun toString() = "SharedBudgetDto($id, version=$version)"
}

/** shared-budgets.yaml `SharedBudgetMember`. A name is null for a person who left the product ("Former member"). */
@Serializable
data class SharedBudgetMemberDto(
    val id: String,
    @SerialName("shared_budget_id") val sharedBudgetId: String,
    @SerialName("user_id") val userId: String,
    @SerialName("display_name") val displayName: String? = null,
    val role: String = "member",
    @SerialName("read_only") val readOnly: Boolean = false,
    val version: Long,
    @SerialName("change_seq") val changeSeq: Long = 0,
    @SerialName("created_at") val createdAt: String,
    @SerialName("updated_at") val updatedAt: String,
    @SerialName("deleted_at") val deletedAt: String? = null,
) {
    override fun toString() = "SharedBudgetMemberDto($id, version=$version)"
}

/** shared-budgets.yaml `SharedBudgetLine`. The display fields let a member render a category they do not own. */
@Serializable
data class SharedBudgetLineDto(
    val id: String,
    @SerialName("shared_budget_id") val sharedBudgetId: String,
    @SerialName("category_id") val categoryId: String,
    @SerialName("limit_minor") val limitMinor: Long? = null,
    val name: String? = null,
    val icon: String? = null,
    val color: String? = null,
    @SerialName("limit_history") val limitHistory: List<LimitStepDto> = emptyList(),
    val version: Long,
    @SerialName("change_seq") val changeSeq: Long = 0,
    @SerialName("created_at") val createdAt: String,
    @SerialName("updated_at") val updatedAt: String,
    @SerialName("deleted_at") val deletedAt: String? = null,
) {
    override fun toString() = "SharedBudgetLineDto($id, version=$version)"
}

/**
 * shared-budgets.yaml `SharedBudgetEntry`: author, category, date, amount and note; never a wallet, memo or counterparty. The
 * note and amount are the author's and never printed (P-09.2).
 */
@Serializable
data class SharedBudgetEntryDto(
    val id: String,
    @SerialName("shared_budget_id") val sharedBudgetId: String,
    @SerialName("created_by") val createdBy: String,
    @SerialName("category_id") val categoryId: String,
    val date: String,
    @SerialName("amount_minor") val amountMinor: Long,
    val description: String? = null,
    @SerialName("detached_at") val detachedAt: String? = null,
    val version: Long,
    @SerialName("change_seq") val changeSeq: Long = 0,
    @SerialName("created_at") val createdAt: String,
    @SerialName("updated_at") val updatedAt: String,
    @SerialName("deleted_at") val deletedAt: String? = null,
) {
    override fun toString() = "SharedBudgetEntryDto($id, version=$version)"
}

/** shared-budgets.yaml `SharedBudgetWrite`: the whole writable budget; an explicit null limit means "no limit". */
data class SharedBudgetWriteRequest(
    val name: String,
    val currency: String,
    val startDay: Int,
    val limitMinor: Long?,
    val version: Long,
) {
    fun toJson(): kotlinx.serialization.json.JsonObject = kotlinx.serialization.json.buildJsonObject {
        put("name", kotlinx.serialization.json.JsonPrimitive(name))
        put("currency", kotlinx.serialization.json.JsonPrimitive(currency))
        put("start_day", kotlinx.serialization.json.JsonPrimitive(startDay))
        put("limit_minor", limitMinor?.let { kotlinx.serialization.json.JsonPrimitive(it) } ?: kotlinx.serialization.json.JsonNull)
        put("version", kotlinx.serialization.json.JsonPrimitive(version))
    }

    override fun toString() = "SharedBudgetWriteRequest(version=$version)"
}

/** shared-budgets.yaml `SharedBudgetLineWrite`. */
data class SharedBudgetLineWriteRequest(val categoryId: String, val limitMinor: Long?, val version: Long) {
    fun toJson(): kotlinx.serialization.json.JsonObject = kotlinx.serialization.json.buildJsonObject {
        put("category_id", kotlinx.serialization.json.JsonPrimitive(categoryId))
        put("limit_minor", limitMinor?.let { kotlinx.serialization.json.JsonPrimitive(it) } ?: kotlinx.serialization.json.JsonNull)
        put("version", kotlinx.serialization.json.JsonPrimitive(version))
    }

    override fun toString() = "SharedBudgetLineWriteRequest(version=$version)"
}

/** The delete, archive and invitation-withdraw bodies: nothing but the last-seen version. */
@Serializable
data class SharedBudgetVersionRequest(val version: Long)

@Serializable
data class SharedBudgetTransferRequest(@SerialName("user_id") val userId: String, val version: Long)

/** A token body: never printed (P-09.2). */
@Serializable
data class SharedBudgetTokenRequest(val token: String) {
    override fun toString() = "SharedBudgetTokenRequest(token=<redacted>)"
}

/** shared-budgets.yaml `SharedBudgetInvitation`: the owner's record of an invitation, never its secret. */
@Serializable
data class SharedBudgetInvitationDto(
    val id: String,
    @SerialName("shared_budget_id") val sharedBudgetId: String,
    @SerialName("invited_by") val invitedBy: String = "",
    val status: String = "pending",
    @SerialName("expires_at") val expiresAt: String = "",
    val version: Long = 1,
    @SerialName("change_seq") val changeSeq: Long = 0,
    @SerialName("created_at") val createdAt: String = "",
    @SerialName("updated_at") val updatedAt: String = "",
    @SerialName("deleted_at") val deletedAt: String? = null,
)

/** `meta` of a new invitation: the one-time link, never printed. */
@Serializable
data class SharedBudgetInvitationMetaDto(@SerialName("share_url") val shareUrl: String? = null) {
    override fun toString() = "SharedBudgetInvitationMetaDto(link=<redacted>)"
}

/** shared-budgets.yaml `SharedBudgetInvitationPreview`: exactly what joining shows the person. */
@Serializable
data class SharedBudgetInvitationPreviewDto(
    @SerialName("invitation_id") val invitationId: String,
    @SerialName("shared_budget_id") val sharedBudgetId: String,
    val name: String,
    val currency: String,
    @SerialName("owner_display_name") val ownerDisplayName: String? = null,
    @SerialName("member_count") val memberCount: Int = 1,
    @SerialName("expires_at") val expiresAt: String = "",
    @SerialName("already_member") val alreadyMember: Boolean = false,
)
