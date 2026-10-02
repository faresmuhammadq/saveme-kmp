package com.fares.saveme.feature.categories.data.local

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

/** One entry of the platform's default set, cached whole; retired entries are kept (they still resolve). */
@Entity(tableName = "platform_category", indices = [Index(value = ["kind", "position"])])
data class PlatformCategoryEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "system_key") val systemKey: String,
    val kind: String,
    @ColumnInfo(name = "name_ar") val nameAr: String,
    @ColumnInfo(name = "name_en") val nameEn: String,
    val icon: String,
    val color: String,
    val position: Int,
    @ColumnInfo(name = "archived_at") val archivedAt: String?,
    @ColumnInfo(name = "updated_at") val updatedAt: String,
)

@Dao
interface PlatformCategoryDao {
    @Query("SELECT * FROM platform_category ORDER BY position, id")
    fun observeAll(): Flow<List<PlatformCategoryEntity>>

    @Query("SELECT * FROM platform_category ORDER BY position, id")
    suspend fun getAll(): List<PlatformCategoryEntity>

    @Insert
    suspend fun insertAll(items: List<PlatformCategoryEntity>)

    @Query("DELETE FROM platform_category")
    suspend fun deleteAll()

    /** The whole set is swapped in one transaction, so a reader never sees half of it. */
    @Transaction
    suspend fun replaceAll(items: List<PlatformCategoryEntity>) {
        deleteAll()
        insertAll(items)
    }
}
