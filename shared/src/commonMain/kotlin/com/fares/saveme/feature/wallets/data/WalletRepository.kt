package com.fares.saveme.feature.wallets.data

import com.fares.saveme.core.database.SyncScopeDao
import com.fares.saveme.core.network.ApiResult
import com.fares.saveme.core.sync.ScopeKind
import com.fares.saveme.core.sync.SyncScope
import com.fares.saveme.feature.wallets.data.dto.WalletDto
import com.fares.saveme.feature.wallets.data.dto.WalletMemberDto
import com.fares.saveme.feature.wallets.data.local.WalletDao
import com.fares.saveme.feature.wallets.data.local.WalletEntity
import com.fares.saveme.feature.wallets.data.local.WalletMemberDao
import com.fares.saveme.feature.wallets.data.local.WalletMemberEntity
import com.fares.saveme.feature.wallets.domain.model.WalletDraft
import com.fares.saveme.feature.wallets.domain.model.WalletRole
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlin.time.Clock

/**
 * API calls and the device's copy of wallets and members. No decisions here (P-10.6): reads give
 * rows and the domain decides what to do with them. Rows arrive from the change feeds (spec 009).
 *
 * @param hasQueuedChange whether the sync queue still holds a change for a wallet; such a wallet
 *   is never removed by a read (FR-011).
 * @param onWalletRemoved called after a wallet's rows are removed because the person no longer has
 *   it (C5 drops its references, C6 its transactions).
 * @param onWalletCopy called after a server copy of a wallet is stored, with its scope position (C6 settles the
 *   carries that copy already counts, spec 009 FR-013).
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
    private val onWalletCopy: suspend (walletId: String, changeSeq: Long) -> Unit = { _, _ -> },
) {
    fun observeAll(accountId: String): Flow<List<WalletEntity>> = wallets.observeAll(accountId)

    fun observe(id: String): Flow<WalletEntity?> = wallets.observe(id)

    suspend fun get(id: String): WalletEntity? = wallets.get(id)

    fun observeMembers(id: String): Flow<List<WalletMemberEntity>> = members.observe(id)

    suspend fun membersOf(id: String): List<WalletMemberEntity> = members.get(id)

    /**
     * When the wallet list was last read completely: the oldest completed feed read of the person's scope and
     * every wallet. Until every one of them has completed once (an upgraded phone), the time of the last
     * whole-list read stands in; null before any read.
     */
    fun observeListRefreshedAt(): Flow<String?> {
        val userKey = SyncScope(ScopeKind.User, accountId().orEmpty()).key
        return combine(scopes.observeFeedReadAt(userKey), wallets.observeOldestFeedRead(), scopes.observe(LIST_SCOPE)) { user, oldestWallet, legacy ->
            val feed = if (user != null && oldestWallet != "") minOf(user, oldestWallet ?: user) else null
            feed ?: legacy?.refreshedAt?.takeIf { it.isNotEmpty() }
        }
    }

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
        onWalletCopy(dto.id, dto.changeSeq)
        return false
    }

    /**
     * Applies a wallet record from its change feed: when its version is the same or newer (the same version
     * arrives again with a new balance, FR-004), and a deletion removes the wallet unless a change to the
     * wallet record waits (it stays until the person settles it, FR-005). Returns true when anything changed.
     */
    suspend fun storeFeedCopy(dto: WalletDto): Boolean {
        if (dto.deletedAt != null) {
            if (hasQueuedChange(dto.id)) return false
            remove(dto.id)
            return true
        }
        val stored = wallets.get(dto.id)
        if (stored != null && stored.version > dto.version) return false
        wallets.upsert(dto.toEntity(accountId().orEmpty(), clock.now().toString()))
        onWalletCopy(dto.id, dto.changeSeq)
        return true
    }

    /**
     * Applies a member row from a change feed; an older version is ignored. An EQUAL version is applied on purpose: the
     * backend re-stamps a member's row when their name changes and keeps the version (ADR-0021), so the guard must stay
     * `>` and not become `>=` (spec 011 R-08).
     */
    suspend fun storeMember(dto: WalletMemberDto): Boolean {
        if (dto.deletedAt != null) {
            members.delete(dto.id)
            return true
        }
        val stored = members.getById(dto.id)
        if (stored != null && stored.version > dto.version) return false
        members.upsert(dto.toEntity())
        // The person's own row also sets the wallet's role, so every screen gates on it at once (spec 013 FR-022, R-12).
        if (dto.memberUserId == accountId()) wallets.get(dto.walletId)?.let { wallets.updateRole(it.id, dto.role) }
        return true
    }

    /** After a complete read of a wallet from the start: members the server did not return are gone. */
    suspend fun sweepMembers(walletId: String, seenIds: Set<String>) {
        members.get(walletId).filter { it.id !in seenIds }.forEach { members.delete(it.id) }
    }

    /** After a complete read of a wallet from the start that did not return the wallet record itself. */
    suspend fun sweepWallet(walletId: String, seenWalletIds: Set<String>) {
        val row = wallets.get(walletId) ?: return
        if (walletId !in seenWalletIds && row.version > 0 && !hasQueuedChange(walletId)) remove(walletId)
    }

    /** The wallets the phone follows: the server has accepted them, or the person has a live own membership (spec 009 FR-002). */
    suspend fun followedIds(userId: String): List<String> =
        (wallets.getAll().filter { it.version > 0 }.map { it.id } + members.ownWalletIds(userId)).distinct()

    /** Wallets the server has accepted. */
    suspend fun heldIds(): List<String> = wallets.getAll().filter { it.version > 0 }.map { it.id }

    suspend fun isAccepted(id: String, userId: String): Boolean =
        (wallets.get(id)?.version ?: 0L) > 0 || id in members.ownWalletIds(userId)

    /**
     * The person's own membership ended: the member row goes, and so does the wallet with everything of it
     * (spec 009 FR-011, [accessLost]).
     */
    suspend fun endOwnMembership(dto: WalletMemberDto) {
        members.delete(dto.id)
        accessLost(dto.walletId)
    }

    /**
     * The person no longer has the wallet: it goes with its members and the data the participants hold, and
     * its feed position goes. Not a wallet made on this phone that the server has not accepted, and not the
     * wallet while a change to the wallet record itself waits: then only the position goes, and settling that
     * change removes the wallet (research R-03).
     */
    suspend fun accessLost(id: String) {
        val row = wallets.get(id)
        if (row != null && row.version == 0L) return
        scopes.clearFeed(SyncScope(ScopeKind.Wallet, id).key)
        if (hasQueuedChange(id)) return
        remove(id)
    }

    /**
     * A change to the wallet record was dropped. If the person has no live membership of it, the wallet may be
     * gone for good: its position is forgotten so the next read starts from the beginning and decides.
     */
    suspend fun reconsiderAfterDiscard(id: String, userId: String) {
        val row = wallets.get(id) ?: return
        if (row.version > 0 && id !in members.ownWalletIds(userId)) scopes.clearFeed(SyncScope(ScopeKind.Wallet, id).key)
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

    suspend fun transferOwnership(id: String, userId: String, version: Long): ApiResult<WalletDto> =
        api.transferOwnership(id, userId, version)

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
