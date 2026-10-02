package com.fares.saveme.core.sync

import com.fares.saveme.core.network.ApiResult
import com.fares.saveme.core.network.ProblemDetails
import kotlinx.serialization.json.JsonElement

/**
 * One per synced record type, registered by its feature in Koin (research R-01, R-04). The
 * engine decides what every answer means; a handler only talks to its endpoint and its table.
 */
interface SyncHandler {
    /** Stored in `pending_change.record_type`. */
    val recordType: String

    /**
     * Whether a record deleted on another device can be restored with the person's change
     * ("Restore with my change"). False for records whose deletion is final on the server
     * (wallets, backend spec 010); their screens offer only "Accept the deletion".
     */
    val canRestore: Boolean get() = true

    /**
     * The record's per-record PUT with `version = change.baseVersion` (0 = create), `deleted_at`
     * when [PendingChange.isDelete], `deleted_at: null` when [PendingChange.restore]. Returns the
     * server's record on success.
     */
    suspend fun send(change: PendingChange): ApiResult<JsonElement>

    /** Writes a server record into the feature's table, leaving queued edits alone. */
    suspend fun applyServerCopy(json: JsonElement)

    /** Side effects of dropping a local change (the profile restores the app language). */
    suspend fun onDiscarded(recordId: String) {}

    /** Maps validation pointers to the feature's field names for its screens. */
    fun refusalFields(problem: ProblemDetails): Map<String, String> = emptyMap()
}
