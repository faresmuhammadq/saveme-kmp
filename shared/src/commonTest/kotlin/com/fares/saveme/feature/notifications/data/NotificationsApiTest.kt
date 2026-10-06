package com.fares.saveme.feature.notifications.data

import com.fares.saveme.core.network.ApiErrorCode
import com.fares.saveme.core.network.ApiResult
import com.fares.saveme.feature.auth.jsonResponse
import com.fares.saveme.feature.notifications.NotificationsTestEnv
import com.fares.saveme.feature.notifications.SESSION_ID
import com.fares.saveme.feature.notifications.TOKEN_A
import com.fares.saveme.feature.notifications.bodyText
import com.fares.saveme.feature.notifications.data.dto.NotificationCategoriesDto
import com.fares.saveme.feature.notifications.data.dto.NotificationPreferencesWriteDto
import com.fares.saveme.feature.notifications.data.dto.QuietHoursDto
import com.fares.saveme.feature.notifications.preferencesBody
import com.fares.saveme.feature.notifications.problem
import com.fares.saveme.feature.notifications.registeredBody
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Contract tests against the `notifications.yaml` v1.14 examples (contracts/push-api-client.md). */
class NotificationsApiTest {

    private val quieterNights = NotificationPreferencesWriteDto(
        version = 1,
        categories = NotificationCategoriesDto(
            recurringDue = true, budgetExceeded = false, sharing = true, billing = true, exportReady = true, deletionReminder = true,
        ),
        quietHours = QuietHoursDto(true, "23:00", "07:00"),
    )

    @Test
    fun registerSendsExactlyThePushTokenToTheSessionPath() = runTest {
        val env = NotificationsTestEnv(backgroundScope)
        env.signIn()
        val result = env.api.registerDevice(SESSION_ID, TOKEN_A)
        assertIs<ApiResult.Success<*>>(result)
        val request = env.deviceRequests.single()
        assertEquals(HttpMethod.Put, request.method)
        assertTrue(request.url.encodedPath.endsWith("/api/v1/me/devices/$SESSION_ID"))
        assertEquals("""{"push_token":"$TOKEN_A"}""", request.bodyText())
        assertNotNull(request.headers["Authorization"])
        assertNotNull(request.headers["X-Correlation-Id"])
        assertEquals("android", request.headers["X-Client-Platform"])
        assertNotNull(request.headers["X-Client-Version"])
        assertNull(request.headers["Idempotency-Key"])
        assertTrue(request.body.contentType.toString().startsWith("application/json"))
    }

    @Test
    fun registeredAndRotatedDecodeAndAnUnknownMemberIsIgnored() = runTest {
        val env = NotificationsTestEnv(backgroundScope)
        env.signIn()
        val registered = assertIs<ApiResult.Success<com.fares.saveme.feature.notifications.data.dto.PushRegistrationDto>>(env.api.registerDevice(SESSION_ID, TOKEN_A))
        assertEquals(SESSION_ID, registered.value.sessionId)
        env.devices = { jsonResponse(200, registeredBody().replace("\"refreshed_at\":\"2026-10-06T09:00:00.000000Z\"", "\"refreshed_at\":\"2026-10-13T18:42:10.250000Z\"")) }
        val rotated = assertIs<ApiResult.Success<com.fares.saveme.feature.notifications.data.dto.PushRegistrationDto>>(env.api.registerDevice(SESSION_ID, TOKEN_A))
        assertEquals("2026-10-13T18:42:10.250000Z", rotated.value.refreshedAt)
    }

    @Test
    fun registerRefusalsKeepTheirCodes() = runTest {
        val env = NotificationsTestEnv(backgroundScope)
        env.signIn()
        env.devices = { jsonResponse(404, problem(404, "NOT_FOUND")) }
        assertEquals(ApiErrorCode.NOT_FOUND, assertIs<ApiResult.Failure>(env.api.registerDevice(SESSION_ID, TOKEN_A)).code)
        env.devices = { jsonResponse(422, problem(422, "VALIDATION_FAILED", ""","errors":[{"pointer":"/push_token","code":"REQUIRED"}]""")) }
        val invalid = assertIs<ApiResult.Failure>(env.api.registerDevice(SESSION_ID, TOKEN_A))
        assertEquals(ApiErrorCode.VALIDATION_FAILED, invalid.code)
        assertEquals("/push_token", invalid.problem.errors?.single()?.pointer)
        env.devices = { jsonResponse(500, problem(500, "INTERNAL_ERROR")) }
        assertEquals(500, assertIs<ApiResult.Failure>(env.api.registerDevice(SESSION_ID, TOKEN_A)).problem.status)
    }

    @Test
    fun rateLimitedReadsTheHeaderAndTheBodyMember() = runTest {
        val env = NotificationsTestEnv(backgroundScope)
        env.signIn()
        env.devices = { jsonResponse(429, problem(429, "RATE_LIMITED"), "Retry-After" to "120") }
        assertEquals(120L, assertIs<ApiResult.Failure>(env.api.registerDevice(SESSION_ID, TOKEN_A)).retryAfterSeconds)
        env.devices = { jsonResponse(429, problem(429, "RATE_LIMITED", ""","retry_after_seconds":45""")) }
        val limited = assertIs<ApiResult.Failure>(env.api.registerDevice(SESSION_ID, TOKEN_A))
        assertEquals(ApiErrorCode.RATE_LIMITED, limited.code)
        assertEquals(45L, limited.retryAfterSeconds)
    }

    @Test
    fun unregisterIsADeleteWithNoBodyAnswering204OrNotFound() = runTest {
        val env = NotificationsTestEnv(backgroundScope)
        env.signIn()
        env.devices = { respond("", HttpStatusCode.NoContent) }
        assertIs<ApiResult.Success<Unit>>(env.api.unregisterDevice(SESSION_ID))
        val request = env.deviceRequests.single()
        assertEquals(HttpMethod.Delete, request.method)
        assertTrue(request.url.encodedPath.endsWith("/me/devices/$SESSION_ID"))
        env.devices = { jsonResponse(404, problem(404, "NOT_FOUND")) }
        assertEquals(ApiErrorCode.NOT_FOUND, assertIs<ApiResult.Failure>(env.api.unregisterDevice(SESSION_ID)).code)
    }

    @Test
    fun theDefaultsExampleDecodes() = runTest {
        val env = NotificationsTestEnv(backgroundScope)
        env.signIn()
        val defaults = assertIs<ApiResult.Success<com.fares.saveme.feature.notifications.data.dto.NotificationPreferencesDto>>(env.api.preferences()).value
        assertEquals(1, defaults.version)
        assertTrue(defaults.categories.recurringDue && defaults.categories.deletionReminder)
        assertEquals("22:00", defaults.quietHours.start)
        assertEquals("08:00", defaults.quietHours.end)
        assertNull(defaults.updatedAt)
        assertEquals(HttpMethod.Get, env.env.requests.last().method)
    }

    @Test
    fun saveSendsEveryMemberAndNoUpdatedAtAndDecodesTheSavedExample() = runTest {
        val env = NotificationsTestEnv(backgroundScope)
        env.signIn()
        env.preferences = { jsonResponse(200, preferencesBody(version = 2, budgets = false, start = "23:00", end = "07:00", updatedAt = "2026-10-06T19:05:00.000000Z")) }
        val saved = assertIs<ApiResult.Success<com.fares.saveme.feature.notifications.data.dto.NotificationPreferencesDto>>(env.api.savePreferences(quieterNights)).value
        assertEquals(2, saved.version)
        assertEquals("2026-10-06T19:05:00.000000Z", saved.updatedAt)
        val request = env.env.requests.last()
        assertEquals(HttpMethod.Put, request.method)
        assertEquals(
            """{"version":1,"categories":{"recurring_due":true,"budget_exceeded":false,"sharing":true,"billing":true,"export_ready":true,"deletion_reminder":true},"quiet_hours":{"enabled":true,"start":"23:00","end":"07:00"}}""",
            request.bodyText(),
        )
        assertFalse(request.bodyText().contains("updated_at"))
    }

    @Test
    fun aConflictCarriesCurrentDecodedWithTheSameDto() = runTest {
        val env = NotificationsTestEnv(backgroundScope)
        env.signIn()
        val current = preferencesBody(version = 3, budgets = false).removePrefix("""{"data":""").substringBeforeLast(""","meta":{}}""")
        env.preferences = { jsonResponse(409, problem(409, "VERSION_CONFLICT", ""","current":$current""")) }
        val conflict = assertIs<ApiResult.Failure>(env.api.savePreferences(quieterNights))
        assertEquals(ApiErrorCode.VERSION_CONFLICT, conflict.code)
        val decoded = assertNotNull(conflict.problem.currentPreferences())
        assertEquals(3, decoded.version)
        assertFalse(decoded.categories.budgetExceeded)
    }

    @Test
    fun anUnreadableCurrentIsNull() = runTest {
        val env = NotificationsTestEnv(backgroundScope)
        env.signIn()
        env.preferences = { jsonResponse(409, problem(409, "VERSION_CONFLICT", ""","current":{"version":"x"}""")) }
        assertNull(assertIs<ApiResult.Failure>(env.api.savePreferences(quieterNights)).problem.currentPreferences())
    }

    @Test
    fun validationPointsAtTheQuietHoursMemberAndRateLimitsKeepTheirWait() = runTest {
        val env = NotificationsTestEnv(backgroundScope)
        env.signIn()
        env.preferences = { jsonResponse(422, problem(422, "VALIDATION_FAILED", ""","errors":[{"pointer":"/quiet_hours/start","code":"OUT_OF_RANGE"}]""")) }
        assertEquals("/quiet_hours/start", assertIs<ApiResult.Failure>(env.api.savePreferences(quieterNights)).problem.errors?.single()?.pointer)
        env.preferences = { jsonResponse(429, problem(429, "RATE_LIMITED"), "Retry-After" to "1800") }
        assertEquals(1800L, assertIs<ApiResult.Failure>(env.api.savePreferences(quieterNights)).retryAfterSeconds)
    }

    @Test
    fun theRequestDtoNeverPrintsTheAddress() {
        assertFalse(com.fares.saveme.feature.notifications.data.dto.PushRegistrationWriteDto(TOKEN_A).toString().contains("fake"))
    }
}
