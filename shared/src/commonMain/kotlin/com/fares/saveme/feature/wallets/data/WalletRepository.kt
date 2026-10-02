package com.fares.saveme.feature.wallets.data

import com.fares.saveme.core.database.SyncScopeDao
import com.fares.saveme.core.database.SyncScopeEntity
import com.fares.saveme.core.network.ApiErrorCode
import com.fares.saveme.core.network.ApiResult
import com.fares.saveme.feature.wallets.data.dto.WalletDto
import com.fares.saveme.feature.wallets.data.dto.WalletMemberDto
import com.fares.saveme.feature.wallets.data.local.WalletDao
import com.fares.saveme.feature.wallets.data.local.WalletEntity
import com.fares.saveme.feature.wallets.data.local.WalletMemberDao
import com.fares.saveme.feature.wallets.data.local.WalletMemberEntity
import com.fares.saveme.feature.wallets.domain.model.WalletDraft
import com.fares.saveme.feature.wallets.domain.model.WalletRole
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlin.time.Clock

/**
 * API calls and the device's copy of wallets and members. No decisions here (P-10.6): reads give
 * rows, refreshes give a result, and the domain decides what to do with them.
 *
 * @param hasQueuedChange whether the sync queue still holds a change for a wallet; such a wallet
 *   is never removed by a refresh (FR-008).
 * @param onWalletRemoved called after a wallet's rows are removed because the server no longer
 *   has it (C6 drops its transactions).
 */
class WalletRepository(
    private val api: WalletApi,
    private val wallets: WalletDao,
    private val members: WalletMemberDao,
    private val scopes: SyncScopeDao,
    private val accountId: () -> String?,
    private val hasQueuedChange: suspend (String) -> Boolean,
    private val onWalletRemoved: suspend (String) -> Unit = {},
    private val clock: Clock = Clock.System,
) {
    fun observeAll(accountId: String): Flow<List<WalletEntity>> = wallets.observeAll(accountId)

    fun observe(id: String): Flow<WalletEntity?> = wallets.observe(id)

    suspend fun get(id: String): WalletEntity? = wallets.get(id)

    fun observeMembers(id: String): Flow<List<WalletMemberEntity>> = members.observe(id)

    /** When the whole list was last read completely; null before the first complete read. */
    fun observeListRefreshedAt(): Flow<String?> = scopes.observe(LIST_SCOPE).map { it?.refreshedAt?.takeIf { at -> at.isNotEmpty() } }

    /** A wallet made on this phone: owner, balance = opening, version 0, until the server answers. */
    suspend fun insertPlaceholder(id: String, draft: WalletDraft, accountId: String) {
        val now = clock.now().toString()
        wallets.upsert(
            WalletEntity(
                id = id, accountId = accountId, type = draft.type, name = draft.name, currency = draft.currency,
                openingBalanceMinor = draft.openingBalanceMinor, balanceMinor = draft.openingBalanceMinor,
                icon = draft.icon, color = draft.color, archivedAt = null, currencyLocked = false,
                myRole = WalletRole.Owner.wire, version = 0, changeSeq = 0, createdAt = now, updatedAt = now, refreshedAt = now,
            ),
        )
    }

    /** Writes the server's copy; a tombstone removes the wallet and its members. Returns true when it was removed. */
    suspend fun storeServerCopy(dto: WalletDto): Boolean {
        if (dto.deletedAt != null) {
            remove(dto.id)
            return true
        }
        wallets.upsert(dto.toEntity(accountId().orEmpty(), clock.now().toString()))
        return false
    }

    /** Removes a wallet and its members, and tells the registered participants. */
    suspend fun remove(id: String) {
        removeRows(id)
        onWalletRemoved(id)
    }

    /** Removes a placeholder that never reached the server; nothing else holds data for it. */
    suspend fun removePlaceholder(id: String) {
        if (wallets.get(id)?.version == 0L) removeRows(id)
    }

    private suspend fun removeRows(id: String) {
        members.deleteFor(id)
        wallets.delete(id)
    }

    suspend fun clear() {
        members.deleteAll()
        wallets.deleteAll()
    }

    /**
     * Reads every page of `GET /wallets`; only when all of them succeed does it merge (R-09):
     * listed wallets become server copies, and a synced wallet that is not listed and has no
     * waiting change is removed.
     */
    suspend fun refreshAll(): ApiResult<Unit> {
        val listed = mutableListOf<WalletDto>()
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
        wallets.upsertAll(listed.filter { it.deletedAt == null }.map { it.toEntity(account, now) })
        wallets.getAll()
            .filter { it.id !in listedIds && it.version > 0 && !hasQueuedChange(it.id) }
            .forEach { remove(it.id) }
        scopes.upsert(SyncScopeEntity(LIST_SCOPE, now))
        return ApiResult.Success(Unit)
    }

    /** Replaces the wallet's members once the whole list has been read. A 404 removes the wallet unless a change waits. */
    suspend fun refreshMembers(id: String): ApiResult<Unit> {
        val listed = mutableListOf<WalletMemberDto>()
        var cursor: String? = null
        do {
            when (val page = api.members(id, cursor)) {
                is ApiResult.Success -> {
                    listed += page.value.items
                    cursor = page.value.nextCursor.takeIf { page.value.hasMore }
                }
                is ApiResult.Failure -> {
                    if (page.code == ApiErrorCode.NOT_FOUND) removeIfNothingWaits(id)
                    return page
                }
                ApiResult.Offline -> return ApiResult.Offline
                ApiResult.Timeout -> return ApiResult.Timeout
            }
        } while (cursor != null)
        members.replace(id, listed.filter { it.deletedAt == null }.map { it.toEntity() })
        return ApiResult.Success(Unit)
    }

    /** Re-reads one wallet; a 404 removes it unless a change waits. */
    suspend fun refreshOne(id: String): ApiResult<Unit> = when (val result = api.get(id)) {
        is ApiResult.Success -> {
            storeServerCopy(result.value)
            ApiResult.Success(Unit)
        }
        is ApiResult.Failure -> {
            if (result.code == ApiErrorCode.NOT_FOUND) removeIfNothingWaits(id)
            result
        }
        ApiResult.Offline -> ApiResult.Offline
        ApiResult.Timeout -> ApiResult.Timeout
    }

    suspend fun transferOwnership(id: String, userId: String, version: Long): ApiResult<WalletDto> =
        api.transferOwnership(id, userId, version)

    private suspend fun removeIfNothingWaits(id: String) {
        if (!hasQueuedChange(id) && wallets.get(id) != null) remove(id)
    }

    companion object {
        const val LIST_SCOPE = "wallets"
    }
}

internal fun WalletDto.toEntity(accountId: String, refreshedAt: String) = WalletEntity(
    id = id, accountId = accountId, type = type, name = name, currency = currency,
    openingBalanceMinor = openingBalanceMinor, balanceMinor = balanceMinor, icon = icon, color = color,
    archivedAt = archivedAt, currencyLocked = currencyLocked, myRole = myRole, version = version, changeSeq = changeSeq,
    createdAt = createdAt, updatedAt = updatedAt, refreshedAt = refreshedAt,
)

internal fun WalletMemberDto.toEntity() = WalletMemberEntity(
    id = id, walletId = walletId, memberUserId = memberUserId, displayName = displayName, role = role,
    version = version, createdAt = createdAt,
)
