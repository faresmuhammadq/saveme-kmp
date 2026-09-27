package com.fares.saveme.core.network

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

sealed interface ApiResult<out T> {
    data class Success<T>(val value: T) : ApiResult<T>

    data class Failure(
        val problem: ProblemDetails,
        val code: ApiErrorCode,
        val retryAfterSeconds: Long?,
    ) : ApiResult<Nothing>

    /** No response at all: no connection, DNS failure, connection refused. */
    data object Offline : ApiResult<Nothing>

    data object Timeout : ApiResult<Nothing>
}

/** Success envelope: `meta` is always present (api-conventions: Success envelope). */
@Serializable
data class Envelope<T>(
    val data: T,
    val meta: JsonObject = JsonObject(emptyMap()),
)
