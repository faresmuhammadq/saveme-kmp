package com.fares.saveme.feature.categories.data

import com.fares.saveme.core.network.ApiErrorCode
import com.fares.saveme.core.network.ApiResult
import com.fares.saveme.feature.categories.data.dto.CategoryReferenceDto
import com.fares.saveme.feature.categories.data.local.CategoryReferenceDao
import com.fares.saveme.feature.categories.data.local.CategoryReferenceEntity
import kotlinx.coroutines.flow.Flow

/** A wallet's partner categories on the phone: replaced as a set per wallet, never edited (research R-09). */
class CategoryReferenceRepository(
    private val api: CategoryReferenceApi,
    private val references: CategoryReferenceDao,
) {
    fun observe(walletId: String): Flow<List<CategoryReferenceEntity>> = references.observe(walletId)

    fun observeAll(): Flow<List<CategoryReferenceEntity>> = references.observeAll()

    /**
     * Reads every page of a wallet's references and replaces the wallet's rows only when all of
     * them succeeded. A 404 removes them; any other failure keeps what is there.
     */
    suspend fun refresh(walletId: String): ApiResult<Unit> {
        val listed = mutableListOf<CategoryReferenceDto>()
        var cursor: String? = null
        do {
            when (val page = api.listReferences(walletId, cursor)) {
                is ApiResult.Success -> {
                    listed += page.value.items
                    cursor = page.value.nextCursor.takeIf { page.value.hasMore }
                }
                is ApiResult.Failure -> {
                    if (page.code == ApiErrorCode.NOT_FOUND) references.deleteFor(walletId)
                    return page
                }
                ApiResult.Offline -> return ApiResult.Offline
                ApiResult.Timeout -> return ApiResult.Timeout
            }
        } while (cursor != null)
        references.replace(
            walletId,
            listed.filter { it.deletedAt == null }.distinctBy { it.categoryId }.map { it.toEntity() },
        )
        return ApiResult.Success(Unit)
    }

    suspend fun removeFor(walletId: String) = references.deleteFor(walletId)

    suspend fun clear() = references.deleteAll()
}

internal fun CategoryReferenceDto.toEntity() = CategoryReferenceEntity(
    id = id, walletId = walletId, categoryId = categoryId, ownerUserId = ownerUserId, kind = kind, name = name,
    icon = icon, color = color, version = version, changeSeq = changeSeq, updatedAt = updatedAt,
)
