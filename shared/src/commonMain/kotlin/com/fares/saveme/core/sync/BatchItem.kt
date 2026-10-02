package com.fares.saveme.core.sync

import kotlinx.serialization.json.JsonElement

/** The two writes the batch upload carries (backend ADR-0016); archives and restores stay per record (spec 009 R-05). */
enum class BatchOp(val wire: String) {
    Upsert("upsert"),
    Delete("delete"),
}

/**
 * One waiting change as exactly the per-record write it would have made (spec 009 FR-017): [data]
 * is the body that write sends, [walletId] only for transactions (FR-024).
 */
data class BatchItem(
    val type: String,
    val op: BatchOp,
    val id: String,
    val walletId: String?,
    val data: JsonElement,
) {
    /** The record a later item may depend on. */
    val key: RecordKey get() = RecordKey(type, id)

    /** [data] is the person's content; it is never printed (P-09.2). */
    override fun toString() = "BatchItem($type/$id, $op)"
}

/** A record's type and id: what a change needs to exist on the server first (spec 009 R-07). */
data class RecordKey(val recordType: String, val recordId: String)
