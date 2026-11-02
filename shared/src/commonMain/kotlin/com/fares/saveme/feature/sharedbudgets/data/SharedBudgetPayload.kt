package com.fares.saveme.feature.sharedbudgets.data

import kotlinx.serialization.Serializable

/**
 * What a Shared Budget's waiting change holds in `pending_change.payload_json`. Not a wire type: the domain maps its draft to
 * and from it so `data` imports nothing from `domain`. Never printed (P-09.2).
 */
@Serializable
data class SharedBudgetPayload(
    val name: String,
    val currency: String,
    val startDay: Int,
    /** Null: no overall limit. */
    val limitMinor: Long? = null,
) {
    override fun toString() = "SharedBudgetPayload"
}
