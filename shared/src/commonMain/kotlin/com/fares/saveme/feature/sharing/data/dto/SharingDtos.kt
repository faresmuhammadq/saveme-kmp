package com.fares.saveme.feature.sharing.data.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

/** sharing.yaml `Invitation`; members the client does not use are ignored (P-02.3). The invited address never prints. */
@Serializable
data class InvitationDto(
    val id: String,
    @SerialName("wallet_id") val walletId: String,
    val role: String,
    val channel: String,
    @SerialName("invitee_email") val inviteeEmail: String? = null,
    @SerialName("invited_by") val invitedBy: String,
    val status: String,
    @SerialName("expires_at") val expiresAt: String? = null,
    val version: Long,
    @SerialName("change_seq") val changeSeq: Long = 0,
    @SerialName("created_at") val createdAt: String,
    @SerialName("updated_at") val updatedAt: String,
    @SerialName("deleted_at") val deletedAt: String? = null,
) {
    override fun toString() = "InvitationDto($id, version=$version)"
}

/** sharing.yaml `InvitationWrite`: the address is an explicit `null` for a link invitation. */
data class InvitationWriteRequest(val role: String, val inviteeEmail: String?, val version: Long) {
    fun toJson(): JsonObject = buildJsonObject {
        put("role", JsonPrimitive(role))
        put("invitee_email", inviteeEmail?.let { JsonPrimitive(it) } ?: JsonNull)
        put("version", JsonPrimitive(version))
    }

    override fun toString() = "InvitationWriteRequest(role=$role, version=$version)"
}

/** The withdraw, remove and leave bodies: only the last-seen version. */
@Serializable
data class VersionRequest(val version: Long)

/** The body of preview, accept and decline. Never printed (P-09.2). */
@Serializable
data class TokenRequest(val token: String) {
    override fun toString() = "TokenRequest(token=<redacted>)"
}

/** sharing.yaml `InvitationCreateMeta`: the one-time link of a link invitation. Never printed. */
@Serializable
data class InvitationCreateMetaDto(@SerialName("share_url") val shareUrl: String? = null) {
    override fun toString() = "InvitationCreateMetaDto(url=<redacted>)"
}

/** sharing.yaml `InvitationPreview`: what a link offers. Not a syncable record. */
@Serializable
data class InvitationPreviewDto(
    @SerialName("invitation_id") val invitationId: String,
    @SerialName("wallet_id") val walletId: String,
    @SerialName("wallet_name") val walletName: String,
    val role: String,
    @SerialName("invited_by_display_name") val invitedByDisplayName: String? = null,
    @SerialName("expires_at") val expiresAt: String,
    @SerialName("already_member") val alreadyMember: Boolean = false,
)

/** wallets.yaml `WalletMember` as the sharing endpoints answer it (accept, role change, remove, leave). */
@Serializable
data class MemberDto(
    val id: String,
    @SerialName("wallet_id") val walletId: String,
    @SerialName("member_user_id") val memberUserId: String,
    @SerialName("display_name") val displayName: String? = null,
    val role: String,
    val version: Long,
    @SerialName("created_at") val createdAt: String,
    @SerialName("deleted_at") val deletedAt: String? = null,
)

/** sharing.yaml `MemberRoleWrite`. */
@Serializable
data class MemberRoleRequest(val role: String, val version: Long)

/** sharing.yaml `ActivityEntry`. */
@Serializable
data class ActivityEntryDto(
    val id: String,
    @SerialName("wallet_id") val walletId: String = "",
    @SerialName("occurred_at") val occurredAt: String,
    @SerialName("actor_user_id") val actorUserId: String? = null,
    @SerialName("actor_display_name") val actorDisplayName: String? = null,
    val action: String,
    @SerialName("subject_type") val subjectType: String,
    @SerialName("subject_id") val subjectId: String,
    val role: String? = null,
)
