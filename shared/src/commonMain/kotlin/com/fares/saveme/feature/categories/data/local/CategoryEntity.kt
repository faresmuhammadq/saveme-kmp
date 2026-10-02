package com.fares.saveme.feature.categories.data.local

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/**
 * An own category as the server last said it (research R-03). One made on this phone has a
 * placeholder row with `version = 0`; changes waiting to sync live in the sync queue.
 */
@Entity(tableName = "category", indices = [Index(value = ["account_id", "kind", "archived_at"])])
data class CategoryEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "account_id") val accountId: String,
    val kind: String,
    val name: String,
    val icon: String?,
    val color: String?,
    @ColumnInfo(name = "archived_at") val archivedAt: String?,
    @ColumnInfo(name = "in_use") val inUse: Boolean,
    val version: Long,
    @ColumnInfo(name = "change_seq") val changeSeq: Long,
    @ColumnInfo(name = "created_at") val createdAt: String,
    @ColumnInfo(name = "updated_at") val updatedAt: String,
    @ColumnInfo(name = "refreshed_at") val refreshedAt: String,
) {
    /** Names are the person's own words; they are never printed (P-09.2). */
    override fun toString() = "CategoryEntity($id, version=$version)"
}

@Dao
interface CategoryDao {
    @Query("SELECT * FROM category WHERE account_id = :accountId ORDER BY created_at, id")
    fun observeAll(accountId: String): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM category WHERE id = :id")
    fun observe(id: String): Flow<CategoryEntity?>

    @Query("SELECT * FROM category WHERE id = :id")
    suspend fun get(id: String): CategoryEntity?

    @Query("SELECT * FROM category")
    suspend fun getAll(): List<CategoryEntity>

    @Upsert
    suspend fun upsert(entity: CategoryEntity)

    @Upsert
    suspend fun upsertAll(entities: List<CategoryEntity>)

    @Query("UPDATE category SET in_use = 1 WHERE id = :id")
    suspend fun markInUse(id: String)

    @Query("DELETE FROM category WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM category")
    suspend fun deleteAll()
}
