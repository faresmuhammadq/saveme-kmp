package com.fares.saveme.feature.wallets.data.local

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/**
 * A wallet as the server last said it (research R-03). A wallet made on this phone has a
 * placeholder row with `version = 0`; changes waiting to sync live in the sync queue.
 */
@Entity(tableName = "wallet", indices = [Index(value = ["account_id", "archived_at", "created_at"])])
data class WalletEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "account_id") val accountId: String,
    val type: String,
    val name: String,
    val currency: String,
    @ColumnInfo(name = "opening_balance_minor") val openingBalanceMinor: Long,
    @ColumnInfo(name = "balance_minor") val balanceMinor: Long,
    val icon: String?,
    val color: String?,
    @ColumnInfo(name = "archived_at") val archivedAt: String?,
    @ColumnInfo(name = "currency_locked") val currencyLocked: Boolean,
    @ColumnInfo(name = "my_role") val myRole: String,
    val version: Long,
    @ColumnInfo(name = "change_seq") val changeSeq: Long,
    @ColumnInfo(name = "created_at") val createdAt: String,
    @ColumnInfo(name = "updated_at") val updatedAt: String,
    @ColumnInfo(name = "refreshed_at") val refreshedAt: String,
) {
    override fun toString() = "WalletEntity($id, version=$version)"
}

@Dao
interface WalletDao {
    /** Active wallets by creation, then archived ones. */
    @Query("SELECT * FROM wallet WHERE account_id = :accountId ORDER BY (archived_at IS NOT NULL), created_at, id")
    fun observeAll(accountId: String): Flow<List<WalletEntity>>

    @Query("SELECT * FROM wallet WHERE id = :id")
    fun observe(id: String): Flow<WalletEntity?>

    @Query("SELECT * FROM wallet WHERE id = :id")
    suspend fun get(id: String): WalletEntity?

    @Query("SELECT * FROM wallet")
    suspend fun getAll(): List<WalletEntity>

    /**
     * The oldest completed feed read among the wallets the server has accepted: '' when one was never
     * read, null when there are none (the wallet list's "Updated …", spec 009 R-11).
     */
    @Query(
        """SELECT MIN(COALESCE(s.feed_read_at, '')) FROM wallet w LEFT JOIN sync_scope s ON s.scope = 'wallet:' || w.id
        WHERE w.version > 0""",
    )
    fun observeOldestFeedRead(): Flow<String?>

    @Upsert
    suspend fun upsert(entity: WalletEntity)

    @Upsert
    suspend fun upsertAll(entities: List<WalletEntity>)

    /** The person's own member row decides their role at once; the wallet record brings the same value (spec 013 R-12). */
    @Query("UPDATE wallet SET my_role = :role WHERE id = :id")
    suspend fun updateRole(id: String, role: String)

    @Query("DELETE FROM wallet WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM wallet")
    suspend fun deleteAll()
}
