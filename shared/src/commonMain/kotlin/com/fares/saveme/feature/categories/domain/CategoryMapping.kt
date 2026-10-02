package com.fares.saveme.feature.categories.domain

import com.fares.saveme.core.network.AppJson
import com.fares.saveme.core.sync.ChangeState
import com.fares.saveme.core.sync.PendingChange
import com.fares.saveme.feature.categories.data.dto.CategoryDto
import com.fares.saveme.feature.categories.data.dto.CategoryPayload
import com.fares.saveme.feature.categories.data.local.CategoryEntity
import com.fares.saveme.feature.categories.data.local.CategoryReferenceEntity
import com.fares.saveme.feature.categories.data.local.PlatformCategoryEntity
import com.fares.saveme.feature.categories.domain.model.Category
import com.fares.saveme.feature.categories.domain.model.CategoryDraft
import com.fares.saveme.feature.categories.domain.model.CategoryField
import com.fares.saveme.feature.categories.domain.model.CategoryReference
import com.fares.saveme.feature.categories.domain.model.CategoryView
import com.fares.saveme.feature.categories.domain.model.DefaultCategory
import com.fares.saveme.feature.categories.domain.model.RefusalReason
import com.fares.saveme.feature.categories.domain.model.SyncState
import kotlin.time.Instant

private val epoch = Instant.fromEpochSeconds(0)

private fun parse(value: String?): Instant? = value?.let { runCatching { Instant.parse(it) }.getOrNull() }

internal fun CategoryEntity.toCategory() = Category(
    id = id, kindWire = kind, name = name, icon = icon, color = color, archivedAt = parse(archivedAt),
    inUse = inUse, version = version, createdAt = parse(createdAt) ?: epoch,
)

internal fun CategoryDto.toCategory() = Category(
    id = id, kindWire = kind, name = name, icon = icon, color = color, archivedAt = parse(archivedAt),
    inUse = inUse, version = version, createdAt = parse(createdAt) ?: epoch,
)

internal fun PlatformCategoryEntity.toDefault() = DefaultCategory(
    id = id, systemKey = systemKey, kindWire = kind, nameAr = nameAr, nameEn = nameEn, icon = icon, color = color,
    position = position, retiredAt = parse(archivedAt),
)

internal fun CategoryReferenceEntity.toReference() = CategoryReference(
    walletId = walletId, categoryId = categoryId, ownerUserId = ownerUserId, kindWire = kind, name = name,
    icon = icon, color = color,
)

internal fun CategoryDraft.toPayload() = CategoryPayload(kind, name, icon, color, archive)

internal fun CategoryPayload.toDraft() = CategoryDraft(kind, name, icon, color, archive)

internal fun CategoryDraft.toPayloadJson(): String = AppJson.encodeToString(CategoryPayload.serializer(), toPayload())

/** Maps the queue row (research R-07) to what the screens show. */
internal fun PendingChange?.toSyncState(): SyncState = when (this?.state) {
    null -> SyncState.None
    ChangeState.Waiting, ChangeState.Sending -> SyncState.Waiting
    ChangeState.Clash -> {
        val theirs = clashJson?.let { runCatching { AppJson.decodeFromString(CategoryDto.serializer(), it) }.getOrNull() }
        if (theirs == null || theirs.deletedAt != null) SyncState.Clash(null, deletedElsewhere = true)
        else SyncState.Clash(theirs.toCategory(), deletedElsewhere = false)
    }
    ChangeState.Refused -> {
        val refusal = refusal
        SyncState.Refused(
            reason = when (refusal?.code) {
                "EMAIL_NOT_VERIFIED" -> RefusalReason.NotVerified
                "NOT_FOUND" -> RefusalReason.NoLongerAvailable
                "CATEGORY_IN_USE" -> RefusalReason.InUse
                "VALIDATION_FAILED" -> RefusalReason.Invalid
                else -> RefusalReason.Other(refusal?.requestId)
            },
            fields = refusal?.fields.orEmpty().mapNotNull { (name, code) ->
                CategoryField.fromName(name)?.let { it to code }
            }.toMap(),
        )
    }
}

/** The waiting content of an edit, or null when nothing waits or the change is a deletion. */
internal fun PendingChange?.pendingDraft(): CategoryDraft? =
    this?.takeIf { !isDelete }?.let { decodeDraft(it.payloadJson) }

internal fun decodeDraft(json: String): CategoryDraft? =
    runCatching { AppJson.decodeFromString(CategoryPayload.serializer(), json).toDraft() }.getOrNull()

internal fun buildView(row: CategoryEntity, change: PendingChange?) = CategoryView(
    server = row.toCategory(),
    pending = change.pendingDraft(),
    pendingDelete = change?.isDelete == true,
    syncState = change.toSyncState(),
)
