package com.fares.saveme.feature.categories.data.local

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

/** A partner's category as published into one wallet: read-only, replaced per wallet as a set. */
@Entity(
    tableName = "category_reference",
    indices = [Index(value = ["wallet_id"]), Index(value = ["wallet_id", "category_id"], unique = true)],
)
data class CategoryReferenceEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "wallet_id") val walletId: String,
    @ColumnInfo(name = "category_id") val categoryId: String,
    @ColumnInfo(name = "owner_user_id") val ownerUserId: String,
    val kind: String,
    val name: String,
    val icon: String?,
    val color: String?,
    val version: Long,
    @ColumnInfo(name = "change_seq") val changeSeq: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: String,
)

@Dao
interface CategoryReferenceDao {
    @Query("SELECT * FROM category_reference WHERE wallet_id = :walletId ORDER BY id")
    fun observe(walletId: String): Flow<List<CategoryReferenceEntity>>

    @Query("SELECT * FROM category_reference ORDER BY wallet_id, id")
    fun observeAll(): Flow<List<CategoryReferenceEntity>>

    @Query("SELECT * FROM category_reference WHERE wallet_id = :walletId ORDER BY id")
    suspend fun get(walletId: String): List<CategoryReferenceEntity>

    @Insert
    suspend fun insertAll(items: List<CategoryReferenceEntity>)

    @Query("SELECT * FROM category_reference WHERE id = :id")
    suspend fun getById(id: String): CategoryReferenceEntity?

    @Upsert
    suspend fun upsert(entity: CategoryReferenceEntity)

    @Query("DELETE FROM category_reference WHERE id = :id")
    suspend fun delete(id: String)

    /** The unique (wallet, category) pair: another row for it must go before a new reference is written. */
    @Query("DELETE FROM category_reference WHERE wallet_id = :walletId AND category_id = :categoryId AND id != :keepId")
    suspend fun deleteOthers(walletId: String, categoryId: String, keepId: String)

    @Query("DELETE FROM category_reference WHERE wallet_id = :walletId")
    suspend fun deleteFor(walletId: String)

    @Query("DELETE FROM category_reference")
    suspend fun deleteAll()

    @Transaction
    suspend fun replace(walletId: String, items: List<CategoryReferenceEntity>) {
        deleteFor(walletId)
        insertAll(items)
    }
}
