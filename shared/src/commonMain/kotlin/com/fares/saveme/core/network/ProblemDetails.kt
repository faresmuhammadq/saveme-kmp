package com.fares.saveme.core.network

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/** RFC 9457 problem body. `detail` is for logs only and is never shown (P-06.5). */
@Serializable
data class ProblemDetails(
    val type: String? = null,
    val title: String? = null,
    val status: Int = 0,
    val detail: String? = null,
    val code: String = "",
    @SerialName("request_id") val requestId: String? = null,
    val instance: String? = null,
    val errors: List<ProblemError>? = null,
    @SerialName("retry_after_seconds") val retryAfterSeconds: Long? = null,
    @SerialName("minimum_version") val minimumVersion: String? = null,
    /** VERSION_CONFLICT only: the record's present state, in the schema a read returns. */
    val current: JsonElement? = null,
    /** FEATURE_NOT_ENTITLED only: the plan feature the answer is about, e.g. `sharing.enabled` (api-conventions: Problem). */
    val feature: String? = null,
)

@Serializable
data class ProblemError(
    val pointer: String? = null,
    val code: String = "",
)
