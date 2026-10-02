package com.fares.saveme.feature.categories.data

import com.fares.saveme.feature.categories.data.dto.CategoryReferenceDto
import com.fares.saveme.feature.categories.data.local.CategoryReferenceDao
import com.fares.saveme.feature.categories.data.local.CategoryReferenceEntity
import kotlinx.coroutines.flow.Flow

/**
 * A wallet's partner categories on the phone, kept current from the wallet's change feed (spec 009
 * FR-001) and never edited (spec 007 research R-09).
 */
class CategoryReferenceRepository(private val references: CategoryReferenceDao) {
    fun observe(walletId: String): Flow<List<CategoryReferenceEntity>> = references.observe(walletId)

    fun observeAll(): Flow<List<CategoryReferenceEntity>> = references.observeAll()

    /**
     * Applies a reference from the feed: a live one when its version is the same or newer (a new reference for
     * a category replaces the old one), a deleted one removes the row. Returns true when anything changed.
     */
    suspend fun storeFeedCopy(dto: CategoryReferenceDto): Boolean {
        if (dto.deletedAt != null) {
            references.delete(dto.id)
            return true
        }
        val stored = references.getById(dto.id)
        if (stored != null && stored.version > dto.version) return false
        // One reference per (wallet, category): the row a newer reference replaces may still be on the phone.
        references.deleteOthers(dto.walletId, dto.categoryId, dto.id)
        references.upsert(dto.toEntity())
        return true
    }

    /** After a complete read of a wallet from the start: references the server did not return are gone. */
    suspend fun sweep(walletId: String, seenIds: Set<String>) {
        references.get(walletId).filter { it.id !in seenIds }.forEach { references.delete(it.id) }
    }

    suspend fun removeFor(walletId: String) = references.deleteFor(walletId)

    suspend fun clear() = references.deleteAll()
}

internal fun CategoryReferenceDto.toEntity() = CategoryReferenceEntity(
    id = id, walletId = walletId, categoryId = categoryId, ownerUserId = ownerUserId, kind = kind, name = name,
    icon = icon, color = color, version = version, changeSeq = changeSeq, updatedAt = updatedAt,
)
