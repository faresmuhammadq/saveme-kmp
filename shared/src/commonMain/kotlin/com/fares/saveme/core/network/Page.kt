package com.fares.saveme.core.network

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** The `meta` of a collection response. The cursor is opaque: never parsed or built here. */
@Serializable
data class CollectionMeta(
    @SerialName("next_cursor") val nextCursor: String? = null,
    @SerialName("has_more") val hasMore: Boolean = false,
    val limit: Int = 0,
)

data class Page<T>(
    val items: List<T>,
    val nextCursor: String?,
    val hasMore: Boolean,
)
