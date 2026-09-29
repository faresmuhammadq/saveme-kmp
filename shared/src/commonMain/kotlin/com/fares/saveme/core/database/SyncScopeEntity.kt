package com.fares.saveme.core.database

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/** When each syncable scope was last pulled from the server, shown as "Updated …" offline. */
@Entity(tableName = "sync_scope")
data class SyncScopeEntity(
    @PrimaryKey val scope: String,
    val refreshedAt: String,
) {
    companion object {
        const val PROFILE = "profile"
        const val DEVICE_SESSIONS = "device_sessions"
    }
}

@Dao
interface SyncScopeDao {
    @Upsert
    suspend fun upsert(entity: SyncScopeEntity)

    @Query("DELETE FROM sync_scope")
    suspend fun deleteAll()

    @Query("SELECT * FROM sync_scope WHERE scope = :scope")
    fun observe(scope: String): Flow<SyncScopeEntity?>
}
