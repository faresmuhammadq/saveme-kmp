package com.fares.saveme.feature.wallets.domain

import com.fares.saveme.feature.wallets.data.WalletRepository
import com.fares.saveme.feature.wallets.data.dto.WalletMemberDto
import com.fares.saveme.feature.wallets.data.local.WalletMemberEntity
import com.fares.saveme.feature.wallets.domain.model.WalletRole
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * One member row with the version sharing needs to send (spec 013 R-01). [role] is the wire value, kept so an unknown role is
 * never sent back changed. A [deleted] record is a tombstone.
 */
data class MemberRecord(
    val id: String,
    val walletId: String,
    val userId: String,
    val displayName: String?,
    val role: String,
    val version: Long,
    val createdAt: String,
    val deleted: Boolean = false,
)

/**
 * What sharing may do with a wallet's members (spec 013 R-01, R-11, R-12): the `wallet_member` table stays in wallets, and the
 * sharing feature reaches it only through this domain API (P-10.4).
 */
interface WalletMemberships {
    /** Members with their versions, the owner first, then in joining order. */
    fun observe(walletId: String): Flow<List<MemberRecord>>

    /** The person's role in the wallet from the wallet row, else their own member row; null when neither is on the phone. */
    suspend fun ownRole(walletId: String): WalletRole?

    /** The version of the person's own member row, null when there is none. */
    suspend fun ownMembershipVersion(walletId: String): Long?

    /**
     * Applies a member record the server answered with, under the feed's version guard (an older version is ignored, an equal
     * one applied, spec 011 R-08); a tombstone deletes the row. The person's own live row also sets the wallet's stored role.
     */
    suspend fun store(record: MemberRecord)

    /** The person no longer has the wallet: it goes with everything of it, as when the server ends the membership. */
    suspend fun leftWallet(walletId: String)
}

class DefaultWalletMemberships(
    private val repository: WalletRepository,
    private val accountId: () -> String?,
    private val left: LeftWallets = LeftWallets(),
) : WalletMemberships {

    override fun observe(walletId: String): Flow<List<MemberRecord>> = repository.observeMembers(walletId).map { rows ->
        rows.map { it.toRecord() }.sortedBy { it.role != WalletRole.Owner.wire }
    }

    override suspend fun ownRole(walletId: String): WalletRole? {
        repository.get(walletId)?.let { return WalletRole.fromWire(it.myRole) }
        val me = accountId() ?: return null
        return repository.membersOf(walletId).firstOrNull { it.memberUserId == me }?.let { WalletRole.fromWire(it.role) }
    }

    override suspend fun ownMembershipVersion(walletId: String): Long? {
        val me = accountId() ?: return null
        return repository.membersOf(walletId).firstOrNull { it.memberUserId == me }?.version
    }

    override suspend fun store(record: MemberRecord) {
        repository.storeMember(
            WalletMemberDto(
                id = record.id, walletId = record.walletId, memberUserId = record.userId, displayName = record.displayName,
                role = record.role, version = record.version, createdAt = record.createdAt,
                deletedAt = if (record.deleted) record.createdAt else null,
            ),
        )
    }

    override suspend fun leftWallet(walletId: String) {
        left.mark(walletId, repository.get(walletId)?.name)
        repository.accessLost(walletId)
    }
}

private fun WalletMemberEntity.toRecord() = MemberRecord(id, walletId, memberUserId, displayName, role, version, createdAt)
