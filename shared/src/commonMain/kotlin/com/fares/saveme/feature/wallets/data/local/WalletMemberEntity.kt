package com.fares.saveme.feature.wallets.data.local

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/** One person in a wallet, kept current from the wallet's change feed; the person's own rows come from their user feed too. */
@Entity(tableName = "wallet_member", indices = [Index(value = ["wallet_id"])])
data class WalletMemberEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "wallet_id") val walletId: String,
    @ColumnInfo(name = "member_user_id") val memberUserId: String,
    @ColumnInfo(name = "display_name") val displayName: String?,
    val role: String,
    val version: Long,
    @ColumnInfo(name = "created_at") val createdAt: String,
)

@Dao
interface WalletMemberDao {
    @Query("SELECT * FROM wallet_member WHERE wallet_id = :walletId ORDER BY created_at, id")
    fun observe(walletId: String): Flow<List<WalletMemberEntity>>

    @Query("SELECT * FROM wallet_member WHERE wallet_id = :walletId ORDER BY created_at, id")
    suspend fun get(walletId: String): List<WalletMemberEntity>

    @Insert
    suspend fun insertAll(items: List<WalletMemberEntity>)

    @Query("SELECT * FROM wallet_member WHERE id = :id")
    suspend fun getById(id: String): WalletMemberEntity?

    @Upsert
    suspend fun upsert(entity: WalletMemberEntity)

    @Query("DELETE FROM wallet_member WHERE id = :id")
    suspend fun delete(id: String)

    /** Wallets the person belongs to, from their own membership rows (the user feed and the wallet feeds carry them). */
    @Query("SELECT DISTINCT wallet_id FROM wallet_member WHERE member_user_id = :userId")
    suspend fun ownWalletIds(userId: String): List<String>

    @Query("DELETE FROM wallet_member WHERE wallet_id = :walletId")
    suspend fun deleteFor(walletId: String)

    @Query("DELETE FROM wallet_member")
    suspend fun deleteAll()

    @Transaction
    suspend fun replace(walletId: String, items: List<WalletMemberEntity>) {
        deleteFor(walletId)
        insertAll(items)
    }
}
