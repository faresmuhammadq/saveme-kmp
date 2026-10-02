package com.fares.saveme.core.database

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/**
 * Per scope: when it was last pulled from the server (shown as "Updated …" offline), when a
 * queued change last reached the server (spec 004, "Last synced"), and the change feed position
 * and last completed read of the scope (spec 009). Feed scopes are keyed `user:<id>` / `wallet:<id>`.
 */
@Entity(tableName = "sync_scope")
data class SyncScopeEntity(
    @PrimaryKey val scope: String,
    val refreshedAt: String,
    @ColumnInfo(name = "last_pushed_at") val lastPushedAt: String? = null,
    /** The server's position after the last applied feed page; opaque, never parsed or logged (spec 009, FR-003). Null = read from the start. */
    @ColumnInfo(name = "feed_cursor") val feedCursor: String? = null,
    /** When the last read of this scope that reached its end started (spec 009, research R-09). */
    @ColumnInfo(name = "feed_read_at") val feedReadAt: String? = null,
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

    @Query("SELECT feed_cursor FROM sync_scope WHERE scope = :scope")
    suspend fun feedCursor(scope: String): String?

    /** Stores the position after an applied page; keeps every other column of the row. */
    @Query(
        """INSERT INTO sync_scope (scope, refreshedAt, feed_cursor) VALUES (:scope, '', :cursor)
        ON CONFLICT(scope) DO UPDATE SET feed_cursor = :cursor""",
    )
    suspend fun storeFeedPage(scope: String, cursor: String)

    /** Records that a read of the scope reached its end; [startedAt] is when that read began. */
    @Query(
        """INSERT INTO sync_scope (scope, refreshedAt, feed_read_at) VALUES (:scope, '', :startedAt)
        ON CONFLICT(scope) DO UPDATE SET feed_read_at = :startedAt""",
    )
    suspend fun markFeedRead(scope: String, startedAt: String)

    /** Forgets the scope's position and read stamp: the next read starts from the beginning. */
    @Query("UPDATE sync_scope SET feed_cursor = NULL, feed_read_at = NULL WHERE scope = :scope")
    suspend fun clearFeed(scope: String)

    @Query("SELECT feed_read_at FROM sync_scope WHERE scope = :scope")
    fun observeFeedReadAt(scope: String): Flow<String?>
}
