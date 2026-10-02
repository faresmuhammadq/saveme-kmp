package com.fares.saveme.feature.wallets.data.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

/** wallets.yaml `Wallet`; members the client does not use are ignored (P-02.3). */
@Serializable
data class WalletDto(
    val id: String,
    val type: String,
    val name: String,
    val currency: String,
    @SerialName("opening_balance_minor") val openingBalanceMinor: Long,
    @SerialName("balance_minor") val balanceMinor: Long,
    val icon: String? = null,
    val color: String? = null,
    @SerialName("archived_at") val archivedAt: String? = null,
    @SerialName("currency_locked") val currencyLocked: Boolean = false,
    @SerialName("my_role") val myRole: String,
    val version: Long,
    @SerialName("change_seq") val changeSeq: Long = 0,
    @SerialName("created_at") val createdAt: String,
    @SerialName("updated_at") val updatedAt: String,
    @SerialName("deleted_at") val deletedAt: String? = null,
)

/** wallets.yaml `WalletMember`. */
@Serializable
data class WalletMemberDto(
    val id: String,
    @SerialName("wallet_id") val walletId: String,
    @SerialName("member_user_id") val memberUserId: String,
    @SerialName("display_name") val displayName: String? = null,
    val role: String,
    val version: Long,
    @SerialName("created_at") val createdAt: String,
    @SerialName("deleted_at") val deletedAt: String? = null,
)

/** wallets.yaml `WalletWrite`: the whole writable wallet plus the last-seen version (0 = create). */
data class WalletWriteRequest(
    val type: String,
    val name: String,
    val currency: String,
    val openingBalanceMinor: Long,
    val icon: String?,
    val color: String?,
    val version: Long,
) {
    /**
     * Built by hand because `icon` and `color` are required-nullable: they must be sent as
     * explicit nulls, and the app's JSON settings drop nulls (`explicitNulls = false`).
     */
    fun toJson(): JsonObject = buildJsonObject {
        put("type", JsonPrimitive(type))
        put("name", JsonPrimitive(name))
        put("currency", JsonPrimitive(currency))
        put("opening_balance_minor", JsonPrimitive(openingBalanceMinor))
        put("icon", icon?.let { JsonPrimitive(it) } ?: JsonNull)
        put("color", color?.let { JsonPrimitive(it) } ?: JsonNull)
        put("version", JsonPrimitive(version))
    }
}

/** The archive and delete bodies: only the last-seen version. */
@Serializable
data class VersionOnlyRequest(val version: Long)

/** wallets.yaml ownership transfer body. */
@Serializable
data class OwnershipTransferRequest(
    @SerialName("user_id") val userId: String,
    val version: Long,
)
