package com.fares.saveme.feature.categories.data.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

/** categories.yaml `Category`; members the client does not use are ignored (P-02.3). */
@Serializable
data class CategoryDto(
    val id: String,
    val kind: String,
    val name: String,
    val icon: String? = null,
    val color: String? = null,
    @SerialName("archived_at") val archivedAt: String? = null,
    @SerialName("in_use") val inUse: Boolean = false,
    val version: Long,
    @SerialName("change_seq") val changeSeq: Long = 0,
    @SerialName("created_at") val createdAt: String,
    @SerialName("updated_at") val updatedAt: String,
    @SerialName("deleted_at") val deletedAt: String? = null,
)

/** categories.yaml `PlatformCategory`: the platform's default set, read-only reference data. */
@Serializable
data class PlatformCategoryDto(
    val id: String,
    @SerialName("system_key") val systemKey: String,
    val kind: String,
    val names: PlatformNamesDto,
    val icon: String,
    val color: String,
    val position: Int,
    @SerialName("archived_at") val archivedAt: String? = null,
    @SerialName("updated_at") val updatedAt: String,
)

@Serializable
data class PlatformNamesDto(val ar: String, val en: String)

/** categories.yaml `CategoryReference`: a partner's category as published into a wallet. */
@Serializable
data class CategoryReferenceDto(
    val id: String,
    @SerialName("wallet_id") val walletId: String,
    @SerialName("category_id") val categoryId: String,
    @SerialName("owner_user_id") val ownerUserId: String,
    val kind: String,
    val name: String,
    val icon: String? = null,
    val color: String? = null,
    val version: Long,
    @SerialName("change_seq") val changeSeq: Long = 0,
    @SerialName("updated_at") val updatedAt: String,
    @SerialName("deleted_at") val deletedAt: String? = null,
)

/** categories.yaml `CategoryWrite`: the whole writable category plus the last-seen version (0 = create). */
data class CategoryWriteRequest(
    val kind: String,
    val name: String,
    val icon: String?,
    val color: String?,
    val version: Long,
) {
    /**
     * Built by hand because `icon` and `color` are required-nullable: they must be sent as
     * explicit nulls, and the app's JSON settings drop nulls (`explicitNulls = false`).
     */
    fun toJson(): JsonObject = buildJsonObject {
        put("kind", JsonPrimitive(kind))
        put("name", JsonPrimitive(name))
        put("icon", icon?.let { JsonPrimitive(it) } ?: JsonNull)
        put("color", color?.let { JsonPrimitive(it) } ?: JsonNull)
        put("version", JsonPrimitive(version))
    }
}

/** The archive and delete bodies: only the last-seen version. */
@Serializable
data class CategoryVersionRequest(val version: Long)
