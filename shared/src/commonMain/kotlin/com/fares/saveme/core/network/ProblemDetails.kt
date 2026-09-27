package com.fares.saveme.core.network

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

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
)

@Serializable
data class ProblemError(
    val pointer: String? = null,
    val code: String = "",
)
