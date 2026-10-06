package com.fares.saveme.feature.notifications

import com.fares.saveme.core.storage.DeviceIdentityStore
import com.fares.saveme.feature.auth.AuthTestEnv
import com.fares.saveme.feature.auth.InMemoryKeyValueStore
import com.fares.saveme.feature.auth.domain.SessionManager
import com.fares.saveme.feature.auth.domain.usecase.SignInUseCase
import com.fares.saveme.feature.auth.jsonResponse
import com.fares.saveme.feature.auth.signInSuccessBody
import com.fares.saveme.feature.notifications.data.NotificationsApi
import com.fares.saveme.feature.notifications.data.PushRegistrationStore
import com.fares.saveme.feature.notifications.domain.ChannelLabels
import com.fares.saveme.feature.notifications.domain.NotificationDisplay
import com.fares.saveme.feature.notifications.domain.NotificationPermissionState
import com.fares.saveme.feature.notifications.domain.PostedNotification
import com.fares.saveme.feature.notifications.domain.PushRegistrationScheduler
import com.fares.saveme.feature.notifications.domain.PushRegistrationService
import com.fares.saveme.feature.notifications.domain.PushTokenProvider
import com.fares.saveme.feature.notifications.domain.ReconcileTrigger
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import kotlinx.coroutines.CoroutineScope
import kotlin.time.Clock
import kotlin.time.Instant

const val SESSION_ID = "0192f7a0-1c3d-7abc-9f01-2b3c4d5e6f70"
const val USER_ID = "0192f7a0-0000-7000-8000-000000000001"
const val TOKEN_A = "dev-fake-push-00000000-0000-7000-8000-000000000001"
const val TOKEN_B = "dev-fake-push-00000000-0000-7000-8000-000000000002"
const val WALLET = "0192f7a0-0000-7000-8000-0000000000a1"
const val OCCURRENCE = "0192f7a0-0000-7000-8000-0000000000a2"
const val BUDGET = "0192f7a0-0000-7000-8000-0000000000a3"
const val NOTIFICATION = "0192f7a0-0000-7000-8000-0000000000a4"
const val EXPORT = "0192f7a0-0000-7000-8000-0000000000a5"
const val INVITATION = "0192f7a0-0000-7000-8000-0000000000a6"

class FakeProvider(var token: String? = TOKEN_A, override var isAvailable: Boolean = true) : PushTokenProvider {
    var discards = 0
    override suspend fun currentToken(): String? = token
    override suspend fun discard() {
        discards++
        token = null
    }
}

class FakePermission(var allowed: Boolean = true, var askable: Boolean = true) : NotificationPermissionState {
    override fun notificationsAllowed() = allowed
    override fun canAskAtRuntime() = askable
}

class FakeDisplay : NotificationDisplay {
    val log = mutableListOf<String>()
    val posted = mutableListOf<PostedNotification>()
    var channelCalls = 0
    var allowed = true
    override suspend fun ensureChannels(labels: ChannelLabels) {
        channelCalls++
        log += "channels"
    }
    override fun post(notification: PostedNotification): Boolean {
        log += "post"
        posted += notification
        return allowed
    }
    override fun cancelAll() {
        log += "cancelAll"
        posted.clear()
    }
}

class FakeScheduler : PushRegistrationScheduler {
    val requests = mutableListOf<ReconcileTrigger>()
    var cancels = 0
    override fun request(trigger: ReconcileTrigger) { requests += trigger }
    override fun cancel() { cancels++ }
}

class MutableTestClock(var instant: Instant = Instant.parse("2026-10-06T09:00:00Z")) : Clock {
    override fun now() = instant
}

fun registeredBody(session: String = SESSION_ID) =
    """{"data":{"session_id":"$session","registered_at":"2026-10-06T09:00:00.000000Z","refreshed_at":"2026-10-06T09:00:00.000000Z","future_member":1},"meta":{}}"""

fun preferencesBody(version: Int = 1, budgets: Boolean = true, start: String = "22:00", end: String = "08:00", updatedAt: String? = null) =
    """{"data":{"version":$version,"categories":{"recurring_due":true,"budget_exceeded":$budgets,"sharing":true,"billing":true,"export_ready":true,"deletion_reminder":true},"quiet_hours":{"enabled":true,"start":"$start","end":"$end"},"updated_at":${updatedAt?.let { "\"$it\"" } ?: "null"}},"meta":{}}"""

fun problem(status: Int, code: String, extra: String = "") =
    """{"type":"t","title":"x","status":$status,"detail":"server text never shown","code":"$code","request_id":"req-77"$extra}"""

fun HttpRequestData.bodyText(): String = (body as TextContent).text

/**
 * A signed-in account against a mock server whose device and preferences routes the test scripts. Everything the
 * registration, sign-out and preferences services need is here, with fakes for the phone's side.
 */
class NotificationsTestEnv(
    scope: CoroutineScope,
    var devices: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData = { jsonResponse(200, registeredBody()) },
    var preferences: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData = { jsonResponse(200, preferencesBody()) },
) {
    val env = AuthTestEnv { request ->
        val path = request.url.encodedPath
        when {
            path.endsWith("/auth/login") -> jsonResponse(200, signInSuccessBody())
            path.contains("/me/devices/") -> devices(request)
            path.endsWith("/me/notification-preferences") -> preferences(request)
            path.endsWith("/auth/logout") -> respond("", HttpStatusCode.NoContent)
            else -> error("unexpected $path")
        }
    }
    val sessions = SessionManager(env.repository, env.entitlementsApi, env.events, scope)
    val api = NotificationsApi(env.client)
    val records = InMemoryKeyValueStore()
    val store = PushRegistrationStore(records)
    val provider = FakeProvider()
    val permission = FakePermission()
    val clock = MutableTestClock()
    val service = PushRegistrationService(api, store, env.secureStore, sessions, provider, permission, clock)

    suspend fun signIn() {
        SignInUseCase(env.clientInfo, DeviceIdentityStore(env.keyValues), env.repository, sessions)("sara@example.com", "pw", SESSION_ID)
    }

    val deviceRequests: List<HttpRequestData> get() = env.requests.filter { it.url.encodedPath.contains("/me/devices/") }
}
