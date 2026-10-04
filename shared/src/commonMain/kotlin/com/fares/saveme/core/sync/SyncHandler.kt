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

    /**
     * The change as exactly its per-record write for the batch upload (spec 009 FR-017): `data` is
     * the body [send] would send. Null = per record only (archive, archive with an edit, restore,
     * or a handler without batch support), so the engine sends it through [send] at its place in
     * the order (FR-018).
     */
    suspend fun batchItem(change: PendingChange): BatchItem? = null

    /**
     * Records that must exist on the server before this change can be accepted (a transaction's
     * wallets and category). A create of one still waiting holds this change back (FR-022).
     */
    suspend fun prerequisites(change: PendingChange): List<RecordKey> = emptyList()

    /** Writes a server record into the feature's table, leaving queued edits alone. */
    suspend fun applyServerCopy(json: JsonElement)

    /**
     * The server's answer to this phone's own write, per record or as a batch item. Defaults to
     * [applyServerCopy]; transactions add their balance carry here.
     */
    suspend fun applySentAnswer(json: JsonElement) = applyServerCopy(json)

    /** Side effects of dropping a local change (the profile restores the app language). */
    suspend fun onDiscarded(recordId: String) {}

    /**
     * Whether a `VERSION_CONFLICT` carrying [current] needs no choice from the person: the engine then
     * treats it as "use theirs" (applies the server copy, drops the change, calls [onDiscarded]) and
     * never asks. Accepting the server's record never overwrites it (P-04.4). Spec 010 R-06.
     */
    suspend fun acceptsClash(change: PendingChange, current: JsonElement): Boolean = false

    /** Maps validation pointers to the feature's field names for its screens. */
    fun refusalFields(problem: ProblemDetails): Map<String, String> = emptyMap()
}
