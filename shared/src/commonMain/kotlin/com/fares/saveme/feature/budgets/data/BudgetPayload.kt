package com.fares.saveme.feature.budgets.data

import kotlinx.serialization.Serializable

/**
 * What a budget's waiting change holds in `pending_change.payload_json` (data-model §5). It is not a wire type; the
 * domain maps its draft to and from it so `data` imports nothing from `domain`. Never printed (P-09.2).
 */
@Serializable
data class BudgetPayload(
    val categoryId: String? = null,
    val currency: String,
    val amountMinor: Long,
    /** Archive after saving; one-way, so a payload never sets it back to false once true. */
    val archive: Boolean = false,
) {
    override fun toString() = "BudgetPayload"
}
