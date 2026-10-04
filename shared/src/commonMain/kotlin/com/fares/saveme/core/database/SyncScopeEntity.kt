package com.fares.saveme.core.database

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/** The stored place of a feed scope: the opaque cursor and the integer position that came with it (spec 011). */
data class FeedPlace(val cursor: String?, val position: Long?)

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
    /**
     * When the last read of this scope that reached its end started (spec 009, research R-09), or when the heads read
     * that found the scope unchanged started (spec 011, R-06).
     */
    @ColumnInfo(name = "feed_read_at") val feedReadAt: String? = null,
    /**
     * `meta.sync_position` of the same page as [feedCursor] (spec 011, data-model §1). Null = unknown (a place stored
     * before v8, an older server, or forgotten). Never parsed from the cursor, never sent, never logged.
     */
    @ColumnInfo(name = "feed_position") val feedPosition: Long? = null,
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

    @Query("SELECT feed_cursor AS cursor, feed_position AS position FROM sync_scope WHERE scope = :scope")
    suspend fun feedPlace(scope: String): FeedPlace?

    /**
     * Stores the place after an applied page: the cursor and the page's [position] (null when the page carried none)
     * in one statement; keeps every other column of the row.
     */
    @Query(
        """INSERT INTO sync_scope (scope, refreshedAt, feed_cursor, feed_position) VALUES (:scope, '', :cursor, :position)
        ON CONFLICT(scope) DO UPDATE SET feed_cursor = :cursor, feed_position = :position""",
    )
    suspend fun storeFeedPage(scope: String, cursor: String, position: Long?)

    /** Records that a read of the scope reached its end; [startedAt] is when that read began. */
    @Query(
        """INSERT INTO sync_scope (scope, refreshedAt, feed_read_at) VALUES (:scope, '', :startedAt)
        ON CONFLICT(scope) DO UPDATE SET feed_read_at = :startedAt""",
    )
    suspend fun markFeedRead(scope: String, startedAt: String)

    /** Forgets the scope's cursor, position and read stamp: the next read starts from the beginning. */
    @Query("UPDATE sync_scope SET feed_cursor = NULL, feed_position = NULL, feed_read_at = NULL WHERE scope = :scope")
    suspend fun clearFeed(scope: String)

    @Query("SELECT feed_read_at FROM sync_scope WHERE scope = :scope")
    fun observeFeedReadAt(scope: String): Flow<String?>
}
