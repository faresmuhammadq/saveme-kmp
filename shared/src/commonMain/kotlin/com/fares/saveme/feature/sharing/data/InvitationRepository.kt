package com.fares.saveme.feature.sharing.data

import com.fares.saveme.feature.sharing.data.dto.InvitationDto
import com.fares.saveme.feature.sharing.data.local.InvitationDao
import com.fares.saveme.feature.sharing.data.local.InvitationEntity
import kotlinx.coroutines.flow.Flow
import kotlin.time.Clock

/**
 * The device's copy of the invitations of the wallets the person belongs to. No decisions here (P-10.6): reads give rows and
 * the domain decides what to do with them. Invitations reach the phone through the wallet feeds (spec 009) and the sync
 * engine's answers.
 *
 * @param hasQueuedChange whether the sync queue still holds a change for an invitation; such a row is never removed by a read
 *   (spec 013 FR-029). Resolved on use by the module (the engine is built from every handler).
 */
class InvitationRepository(
    private val invitations: InvitationDao,
    private val accountId: () -> String?,
    private val hasQueuedChange: suspend (String) -> Boolean,
    private val clock: Clock = Clock.System,
) {
    fun observePending(accountId: String, walletId: String): Flow<List<InvitationEntity>> = invitations.observePending(accountId, walletId)

    fun observeLivePendingCount(accountId: String, walletId: String): Flow<Int> =
        invitations.observeLivePendingCount(accountId, walletId, clock.now().toString())

    suspend fun get(id: String): InvitationEntity? = invitations.get(id)

    suspend fun getForWallet(walletId: String): List<InvitationEntity> = invitations.getForWallet(walletId)

    /** An invitation made on this phone: version 0, until the server answers (research R-05). */
    suspend fun insertPlaceholder(id: String, walletId: String, accountId: String, role: String, email: String?) {
        val now = clock.now().toString()
        invitations.upsert(
            InvitationEntity(
                id = id, accountId = accountId, walletId = walletId, role = role,
                channel = if (email == null) CHANNEL_LINK else CHANNEL_EMAIL, inviteeEmail = email, invitedBy = accountId,
                status = PENDING, expiresAt = null, version = 0, changeSeq = 0, createdAt = now, updatedAt = now, refreshedAt = now,
            ),
        )
    }

    /** Writes the server's answer to this phone's own write, or a clash's copy; a tombstone removes the row. */
    suspend fun storeServerCopy(dto: InvitationDto): Boolean {
        if (dto.deletedAt != null) {
            invitations.delete(dto.id)
            return true
        }
        invitations.upsert(dto.toEntity(accountId().orEmpty(), clock.now().toString()))
        return false
    }

    /**
     * Applies an invitation from a wallet's change feed (spec 009 FR-004, FR-005; spec 013 FR-029): when its version is the same
     * or newer; a tombstone (any closed status) removes it unless a change to it waits. Returns true when anything changed.
     */
    suspend fun storeFeedCopy(dto: InvitationDto): Boolean {
        if (dto.deletedAt != null) {
            if (hasQueuedChange(dto.id)) return false
            invitations.delete(dto.id)
            return true
        }
        val stored = invitations.get(dto.id)
        if (stored != null && stored.version > dto.version) return false
        invitations.upsert(dto.toEntity(accountId().orEmpty(), clock.now().toString()))
        return true
    }

    /**
     * After a complete read of a wallet from the start: a synced invitation of it that the server did not return and that has no
     * waiting change is removed. A never-synced placeholder is always kept.
     */
    suspend fun sweep(walletId: String, seenIds: Set<String>) {
        invitations.getForWallet(walletId)
            .filter { it.id !in seenIds && it.version > 0 && !hasQueuedChange(it.id) }
            .forEach { invitations.delete(it.id) }
    }

    /** Removes a placeholder that never reached the server; nothing else holds data for it. */
    suspend fun removePlaceholder(id: String) {
        if (invitations.get(id)?.version == 0L) invitations.delete(id)
    }

    suspend fun remove(id: String) = invitations.delete(id)

    /** The person no longer has the wallet: its invitations go with it. */
    suspend fun deleteForWallet(walletId: String) = invitations.deleteForWallet(walletId)

    suspend fun clear() = invitations.deleteAll()

    companion object {
        const val PENDING = "pending"
        const val CHANNEL_EMAIL = "email"
        const val CHANNEL_LINK = "link"
    }
}

internal fun InvitationDto.toEntity(accountId: String, refreshedAt: String) = InvitationEntity(
    id = id, accountId = accountId, walletId = walletId, role = role, channel = channel, inviteeEmail = inviteeEmail,
    invitedBy = invitedBy, status = status, expiresAt = expiresAt, version = version, changeSeq = changeSeq,
    createdAt = createdAt, updatedAt = updatedAt, refreshedAt = refreshedAt,
)
