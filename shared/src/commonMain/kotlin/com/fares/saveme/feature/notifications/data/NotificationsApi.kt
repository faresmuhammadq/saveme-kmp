package com.fares.saveme.feature.notifications.data

import com.fares.saveme.core.network.ApiClient
import com.fares.saveme.core.network.ApiResult
import com.fares.saveme.core.network.AppJson
import com.fares.saveme.core.network.ProblemDetails
import com.fares.saveme.feature.notifications.data.dto.NotificationPreferencesDto
import com.fares.saveme.feature.notifications.data.dto.NotificationPreferencesWriteDto
import com.fares.saveme.feature.notifications.data.dto.PushRegistrationDto
import com.fares.saveme.feature.notifications.data.dto.PushRegistrationWriteDto
import io.ktor.client.request.setBody
import io.ktor.client.request.url
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.contentType

/**
 * The four calls of notifications.yaml (contracts/push-api-client.md). All credentialed, plain values in and out
 * (P-10.6), no `Idempotency-Key` (both writes are idempotent by construction) and no retry of their own.
 */
class NotificationsApi(private val client: ApiClient) {

    /** `{sessionId}` is the caller's own current device session; the body carries only the push address. */
    suspend fun registerDevice(sessionId: String, token: String): ApiResult<PushRegistrationDto> = client.call {
        method = HttpMethod.Put
        url("me/devices/$sessionId")
        contentType(ContentType.Application.Json)
        setBody(PushRegistrationWriteDto(token))
    }

    suspend fun unregisterDevice(sessionId: String): ApiResult<Unit> = client.call {
        method = HttpMethod.Delete
        url("me/devices/$sessionId")
    }

    suspend fun preferences(): ApiResult<NotificationPreferencesDto> = client.call {
        method = HttpMethod.Get
        url("me/notification-preferences")
    }

    /** 409 VERSION_CONFLICT carries the stored preferences in `current`; nothing is merged server-side. */
    suspend fun savePreferences(write: NotificationPreferencesWriteDto): ApiResult<NotificationPreferencesDto> = client.call {
        method = HttpMethod.Put
        url("me/notification-preferences")
        contentType(ContentType.Application.Json)
        setBody(write)
    }
}

/** The `current` of a 409, read with the same DTO as a GET; null when it is missing or unreadable. */
fun ProblemDetails.currentPreferences(): NotificationPreferencesDto? =
    current?.let { runCatching { AppJson.decodeFromJsonElement(NotificationPreferencesDto.serializer(), it) }.getOrNull() }
