package com.fares.saveme.core.sync

import androidx.room.ColumnInfo
import androidx.room.Entity

/**
 * One record's change that hasn't reached the server yet (spec 004 data-model §1). One row per
 * record: later edits rewrite it (research R-02). Phone-only; emptied at sign-out.
 */
@Entity(tableName = "pending_change", primaryKeys = ["record_type", "record_id"])
data class PendingChangeEntity(
    @ColumnInfo(name = "record_type") val recordType: String,
    @ColumnInfo(name = "record_id") val recordId: String,
    @ColumnInfo(name = "account_id") val accountId: String,
    /** "user" or "wallet". */
    @ColumnInfo(name = "scope_kind") val scopeKind: String,
    @ColumnInfo(name = "scope_id") val scopeId: String,
    /** Last server version the person saw; 0 = never sent (create). */
    @ColumnInfo(name = "base_version") val baseVersion: Long,
    @ColumnInfo(name = "payload_json") val payloadJson: String,
    @ColumnInfo(name = "is_delete") val isDelete: Boolean,
    /** Send `deleted_at: null` with the content ("Restore with my change"). */
    val restore: Boolean,
    /** Bumped on every local edit; a send only clears the row if it is unchanged. */
    val revision: Long,
    @ColumnInfo(name = "created_order") val createdOrder: Long,
    /** Waiting · Sending · Clash · Refused. */
    val state: String,
    val attempts: Int,
    @ColumnInfo(name = "next_attempt_at") val nextAttemptAt: String?,
    @ColumnInfo(name = "clash_json") val clashJson: String?,
    @ColumnInfo(name = "refusal_json") val refusalJson: String?,
    @ColumnInfo(name = "updated_at") val updatedAt: String,
) {
    override fun toString() =
        "PendingChange($recordType/$recordId, state=$state, base=$baseVersion, revision=$revision, attempts=$attempts)"
}
