package com.fares.saveme.feature.categories.data

import com.fares.saveme.core.database.SyncScopeDao
import com.fares.saveme.core.database.SyncScopeEntity
import com.fares.saveme.core.network.ApiResult
import com.fares.saveme.feature.categories.data.dto.CategoryDto
import com.fares.saveme.feature.categories.data.dto.PlatformCategoryDto
import com.fares.saveme.feature.categories.data.local.CategoryDao
import com.fares.saveme.feature.categories.data.local.CategoryEntity
import com.fares.saveme.feature.categories.data.local.PlatformCategoryDao
import com.fares.saveme.feature.categories.data.local.PlatformCategoryEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlin.time.Clock

/**
 * API calls and the device's copy of own categories and the default set. No decisions here
 * (P-10.6): reads give rows, refreshes give a result, and the domain decides what to do with them.
 *
 * @param hasQueuedChange whether the sync queue still holds a change for a category; such a
 *   category is never removed by a refresh (FR-005).
 */
class CategoryRepository(
    private val api: CategoryApi,
    private val categories: CategoryDao,
    private val platform: PlatformCategoryDao,
    private val scopes: SyncScopeDao,
    private val accountId: () -> String?,
    private val hasQueuedChange: suspend (String) -> Boolean,
    private val clock: Clock = Clock.System,
) {
    fun observeOwn(accountId: String): Flow<List<CategoryEntity>> = categories.observeAll(accountId)

    fun observe(id: String): Flow<CategoryEntity?> = categories.observe(id)

    suspend fun get(id: String): CategoryEntity? = categories.get(id)

    fun observeDefaults(): Flow<List<PlatformCategoryEntity>> = platform.observeAll()

    /** When the own list was last read completely; null before the first complete read. */
    fun observeOwnRefreshedAt(): Flow<String?> = observeStamp(OWN_SCOPE)

    fun observeDefaultsRefreshedAt(): Flow<String?> = observeStamp(DEFAULTS_SCOPE)

    private fun observeStamp(scope: String): Flow<String?> =
        scopes.observe(scope).map { it?.refreshedAt?.takeIf { at -> at.isNotEmpty() } }

    /** A category made on this phone: version 0, not in use, until the server answers. */
    suspend fun insertPlaceholder(id: String, accountId: String, kind: String, name: String, icon: String?, color: String?) {
        val now = clock.now().toString()
        categories.upsert(
            CategoryEntity(
                id = id, accountId = accountId, kind = kind, name = name, icon = icon, color = color, archivedAt = null,
                inUse = false, version = 0, changeSeq = 0, createdAt = now, updatedAt = now, refreshedAt = now,
            ),
        )
    }

    /** Writes the server's copy; a tombstone removes the category. Returns true when it was removed. */
    suspend fun storeServerCopy(dto: CategoryDto): Boolean {
        if (dto.deletedAt != null) {
            categories.delete(dto.id)
            return true
        }
        categories.upsert(dto.toEntity(accountId().orEmpty(), clock.now().toString()))
        return false
    }

    suspend fun remove(id: String) = categories.delete(id)

    /** Removes a placeholder that never reached the server; nothing else holds data for it. */
    suspend fun removePlaceholder(id: String) {
        if (categories.get(id)?.version == 0L) categories.delete(id)
    }

    /** A refused deletion proves the category is in use (research R-07). */
    suspend fun markInUse(id: String) = categories.markInUse(id)

    suspend fun clear() {
        categories.deleteAll()
        platform.deleteAll()
    }

    /**
     * Reads every page of `GET /categories`; only when all of them succeed does it merge (R-08):
     * listed categories become server copies, and a synced one that is not listed and has no
     * waiting change is removed. A never-synced placeholder is always kept.
     */
    suspend fun refreshOwn(): ApiResult<Unit> {
        val listed = mutableListOf<CategoryDto>()
        var cursor: String? = null
        do {
            when (val page = api.list(cursor)) {
                is ApiResult.Success -> {
                    listed += page.value.items
                    cursor = page.value.nextCursor.takeIf { page.value.hasMore }
                }
                is ApiResult.Failure -> return page
                ApiResult.Offline -> return ApiResult.Offline
                ApiResult.Timeout -> return ApiResult.Timeout
            }
        } while (cursor != null)

        val now = clock.now().toString()
        val account = accountId().orEmpty()
        val listedIds = listed.map { it.id }.toSet()
        categories.upsertAll(listed.filter { it.deletedAt == null }.map { it.toEntity(account, now) })
        categories.getAll()
            .filter { it.id !in listedIds && it.version > 0 && !hasQueuedChange(it.id) }
            .forEach { categories.delete(it.id) }
        scopes.upsert(SyncScopeEntity(OWN_SCOPE, now))
        return ApiResult.Success(Unit)
    }

    /** Replaces the cached default set (retired entries included) on a successful read. */
    suspend fun refreshDefaults(): ApiResult<Unit> = when (val result = api.listDefaults()) {
        is ApiResult.Success -> {
            platform.replaceAll(result.value.items.map { it.toEntity() })
            scopes.upsert(SyncScopeEntity(DEFAULTS_SCOPE, clock.now().toString()))
            ApiResult.Success(Unit)
        }
        is ApiResult.Failure -> result
        ApiResult.Offline -> ApiResult.Offline
        ApiResult.Timeout -> ApiResult.Timeout
    }

    companion object {
        const val OWN_SCOPE = "categories"
        const val DEFAULTS_SCOPE = "platform_categories"
    }
}

internal fun CategoryDto.toEntity(accountId: String, refreshedAt: String) = CategoryEntity(
    id = id, accountId = accountId, kind = kind, name = name, icon = icon, color = color, archivedAt = archivedAt,
    inUse = inUse, version = version, changeSeq = changeSeq, createdAt = createdAt, updatedAt = updatedAt,
    refreshedAt = refreshedAt,
)

internal fun PlatformCategoryDto.toEntity() = PlatformCategoryEntity(
    id = id, systemKey = systemKey, kind = kind, nameAr = names.ar, nameEn = names.en, icon = icon, color = color,
    position = position, archivedAt = archivedAt, updatedAt = updatedAt,
)
