package com.fares.saveme.core.sync

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface PendingChangeDao {
    @Upsert
    suspend fun upsert(entity: PendingChangeEntity)

    @Query("SELECT * FROM pending_change WHERE record_type = :type AND record_id = :id")
    suspend fun get(type: String, id: String): PendingChangeEntity?

    @Query("SELECT * FROM pending_change WHERE record_type = :type AND record_id = :id")
    fun observe(type: String, id: String): Flow<PendingChangeEntity?>

    /** Every row of one scope, in any state, oldest first (spec 013: the count a leave confirmation names). */
    @Query("SELECT * FROM pending_change WHERE scope_kind = :kind AND scope_id = :id ORDER BY created_order")
    suspend fun forScope(kind: String, id: String): List<PendingChangeEntity>

    /** Every row of one record type, in any state (research R-21 of spec 008). */
    @Query("SELECT * FROM pending_change WHERE record_type = :type ORDER BY created_order")
    fun observeByType(type: String): Flow<List<PendingChangeEntity>>

    /** Rows ready to send for [accountId], oldest first. ISO-8601 UTC strings compare in time order. */
    @Query(
        """SELECT * FROM pending_change WHERE account_id = :accountId AND state = 'Waiting'
        AND (next_attempt_at IS NULL OR next_attempt_at <= :now) ORDER BY created_order""",
    )
    suspend fun due(accountId: String, now: String): List<PendingChangeEntity>

    /** When [accountId]'s next waiting row is due: '' if one is due now, null if none waits. */
    @Query("SELECT MIN(COALESCE(next_attempt_at, '')) FROM pending_change WHERE account_id = :accountId AND state = 'Waiting'")
    suspend fun nextDueAt(accountId: String): String?

    @Query("SELECT COUNT(*) FROM pending_change WHERE state IN ('Waiting', 'Sending')")
    fun observeWaitingCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM pending_change WHERE state IN ('Clash', 'Refused')")
    fun observeNeedsDecisionCount(): Flow<Int>

    @Query("SELECT COALESCE(MAX(created_order), 0) FROM pending_change")
    suspend fun maxCreatedOrder(): Long

    /** A send interrupted by process death is simply sent again (backend Writes: replay). */
    @Query("UPDATE pending_change SET state = 'Waiting' WHERE state = 'Sending'")
    suspend fun resetSending()

    @Query("UPDATE pending_change SET next_attempt_at = NULL WHERE state = 'Waiting'")
    suspend fun clearWaits()

    @Query("DELETE FROM pending_change WHERE record_type = :type AND record_id = :id")
    suspend fun delete(type: String, id: String)

    @Query("DELETE FROM pending_change")
    suspend fun deleteAll()
}
