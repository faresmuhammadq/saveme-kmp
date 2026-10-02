package com.fares.saveme.feature.wallets.data.local

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

/** One person in a wallet. A wallet's rows are replaced as a set on each members read. */
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
