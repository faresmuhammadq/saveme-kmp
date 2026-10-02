package com.fares.saveme.core.database

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/**
 * Per scope: when it was last pulled from the server (shown as "Updated …" offline), and when a
 * queued change last reached the server (spec 004, "Last synced").
 */
@Entity(tableName = "sync_scope")
data class SyncScopeEntity(
    @PrimaryKey val scope: String,
    val refreshedAt: String,
    @ColumnInfo(name = "last_pushed_at") val lastPushedAt: String? = null,
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

    /** Records a successful send; creates the scope row if it was never pulled. */
    @Query(
        """INSERT INTO sync_scope (scope, refreshedAt, last_pushed_at) VALUES (:scope, '', :at)
        ON CONFLICT(scope) DO UPDATE SET last_pushed_at = :at""",
    )
    suspend fun markPushed(scope: String, at: String)

    @Query("SELECT MAX(last_pushed_at) FROM sync_scope")
    fun observeLastPushed(): Flow<String?>
}
