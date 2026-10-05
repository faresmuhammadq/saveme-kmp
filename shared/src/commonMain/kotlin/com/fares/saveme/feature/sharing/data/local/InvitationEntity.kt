package com.fares.saveme.feature.sharing.data.local

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/**
 * An invitation as the server last said it, or a placeholder (`version = 0`) for one made on this phone and not yet accepted
 * (spec 013 data-model §1). Closed invitations are tombstones and never stored. The invitation secret and the share link are
 * never columns (P-09.2); the invited address is stored for the list but never printed.
 */
@Entity(tableName = "invitation", indices = [Index(value = ["account_id", "wallet_id", "status"])])
data class InvitationEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "account_id") val accountId: String,
    @ColumnInfo(name = "wallet_id") val walletId: String,
    /** `editor` / `viewer`; another value is kept as the server sent it and never sent back (immutable). */
    val role: String,
    /** `email` / `link`. */
    val channel: String,
    @ColumnInfo(name = "invitee_email") val inviteeEmail: String?,
    @ColumnInfo(name = "invited_by") val invitedBy: String,
    val status: String,
    @ColumnInfo(name = "expires_at") val expiresAt: String?,
    val version: Long,
    @ColumnInfo(name = "change_seq") val changeSeq: Long,
    @ColumnInfo(name = "created_at") val createdAt: String,
    @ColumnInfo(name = "updated_at") val updatedAt: String,
    @ColumnInfo(name = "refreshed_at") val refreshedAt: String,
) {
    override fun toString() = "InvitationEntity($id, version=$version)"
}

@Dao
interface InvitationDao {
    /** Pending invitations of one wallet, oldest first. */
    @Query("SELECT * FROM invitation WHERE account_id = :accountId AND wallet_id = :walletId AND status = 'pending' ORDER BY created_at, id")
    fun observePending(accountId: String, walletId: String): Flow<List<InvitationEntity>>

    @Query("SELECT * FROM invitation WHERE id = :id")
    suspend fun get(id: String): InvitationEntity?

    @Query("SELECT * FROM invitation WHERE wallet_id = :walletId")
    suspend fun getForWallet(walletId: String): List<InvitationEntity>

    @Query("SELECT * FROM invitation")
    suspend fun getAll(): List<InvitationEntity>

    /**
     * Unexpired pending invitations of a wallet at [now] (ISO-8601 UTC text compares in time order); a null end time counts
     * (spec 013 R-06).
     */
    @Query(
        """SELECT COUNT(*) FROM invitation WHERE account_id = :accountId AND wallet_id = :walletId AND status = 'pending'
        AND (expires_at IS NULL OR expires_at > :now)""",
    )
    fun observeLivePendingCount(accountId: String, walletId: String, now: String): Flow<Int>

    @Upsert
    suspend fun upsert(entity: InvitationEntity)

    @Query("DELETE FROM invitation WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM invitation WHERE wallet_id = :walletId")
    suspend fun deleteForWallet(walletId: String)

    @Query("DELETE FROM invitation")
    suspend fun deleteAll()
}
