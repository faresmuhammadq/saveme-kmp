package com.fares.saveme.core.sync

import kotlinx.serialization.json.JsonElement

/**
 * Writes one kind of change feed row into its feature's table (spec 009 research R-02). One per
 * (scope kind, row type), registered in Koin by the feature that owns the table, so `core.sync`
 * imports no feature (P-10.7).
 *
 * Applying never touches `pending_change` (the queue overlays the copy, P-04.4): an older version
 * never replaces a newer one, and a deletion never removes a record that has a change waiting.
 */
interface FeedApplier {
    val scopeKind: ScopeKind

    /** The feed row `type` this applier consumes. */
    val rowType: String

    /** Applies [records] of [scope] in feed order. Unknown members are ignored (P-02.3). */
    suspend fun apply(scope: SyncScope, records: List<JsonElement>)

    /**
     * After a complete read that began without a position: removes this type's synced rows of
     * [scope] whose id is not in [seenIds], except rows with a waiting change, placeholders and
     * transactions still carrying an unsettled balance effect (spec 009 FR-007).
     */
    suspend fun sweep(scope: SyncScope, seenIds: Set<String>)
}
