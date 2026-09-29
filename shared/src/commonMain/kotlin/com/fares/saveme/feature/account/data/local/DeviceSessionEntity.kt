package com.fares.saveme.feature.account.data.local

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

/** The device's copy of the person's active sessions; replaced whole on each pull. */
@Entity(tableName = "device_session")
data class DeviceSessionEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "user_id") val userId: String,
    @ColumnInfo(name = "device_name") val deviceName: String,
    val platform: String,
    @ColumnInfo(name = "app_version") val appVersion: String,
    val state: String,
    @ColumnInfo(name = "signed_in_at") val signedInAt: String,
    @ColumnInfo(name = "last_used_at") val lastUsedAt: String?,
    val current: Boolean,
)

@Dao
interface DeviceSessionDao {
    @Query("SELECT * FROM device_session ORDER BY signed_in_at DESC")
    fun observeAll(): Flow<List<DeviceSessionEntity>>

    @Query("DELETE FROM device_session")
    suspend fun deleteAll()

    @Insert
    suspend fun insertAll(items: List<DeviceSessionEntity>)

    @Transaction
    suspend fun replaceAll(items: List<DeviceSessionEntity>) {
        deleteAll()
        insertAll(items)
    }

    @Query("DELETE FROM device_session WHERE id = :id")
    suspend fun delete(id: String)
}
