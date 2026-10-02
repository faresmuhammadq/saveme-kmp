package com.fares.saveme.feature.categories.data.dto

import kotlinx.serialization.Serializable

/**
 * What a category's waiting change holds in `pending_change.payload_json` (research R-04). It is
 * not a wire type; the domain maps its draft to and from it so `data` imports nothing from `domain`.
 */
@Serializable
data class CategoryPayload(
    val kind: String,
    val name: String,
    val icon: String? = null,
    val color: String? = null,
    /** Archive after saving; one-way, so a payload never sets it back to false once true. */
    val archive: Boolean = false,
)
