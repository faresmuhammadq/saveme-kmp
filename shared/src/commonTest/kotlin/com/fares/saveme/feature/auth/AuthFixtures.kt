package com.fares.saveme.feature.auth

import com.fares.saveme.core.config.AppConfig
import com.fares.saveme.core.config.Environment
import com.fares.saveme.core.network.ApiClient
import com.fares.saveme.core.network.ApiEvents
import com.fares.saveme.core.network.FakeClientInfo
import com.fares.saveme.core.network.createHttpClient
import com.fares.saveme.core.storage.InMemorySecureStore
import com.fares.saveme.core.storage.KeyValueStore
import com.fares.saveme.feature.auth.data.AccountSnapshotStore
import com.fares.saveme.feature.auth.data.AuthRepository
import com.fares.saveme.feature.auth.data.EntitlementsApi
import com.fares.saveme.feature.auth.data.IdentityApi
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf

class InMemoryKeyValueStore : KeyValueStore {
    val values = mutableMapOf<String, String>()
    override suspend fun get(key: String) = values[key]
    override suspend fun set(key: String, value: String) { values[key] = value }
    override suspend fun remove(key: String) { values.remove(key) }
}

const val CREDENTIAL = "0192f7a0-1c3d-7abc-9f01-2b3c4d5e6f70|aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"

fun signInSuccessBody(deletionCancelled: Boolean = false, verified: Boolean = true) = """
    {"data":{
      "credential":"$CREDENTIAL",
      "session":{"id":"0192f7a0-1c3d-7abc-9f01-2b3c4d5e6f70","user_id":"u1","version":1,"device_name":"Pixel 8",
                 "platform":"android","app_version":"0.1.0","state":"active","end_reason":null,
                 "signed_in_at":"2026-09-26T10:00:00Z","last_used_at":null,"ended_at":null,"current":true},
      "account":{"id":"0192f7a0-0000-7000-8000-000000000001","email":"sara@example.com","email_verified":$verified,
                 "state":"active","deletion_scheduled_for":null},
      "entitlements":{"plan":{"id":"01a0e69a-962e-7239-9101-0671bf5529d6","name":"Free"},"features":{"wallet_sharing":false,"wallets_max":3}},
      "deletion_cancelled":$deletionCancelled,
      "something_new":"ignored"},
     "meta":{}}
""".trimIndent()

fun problemBody(status: Int, code: String, extra: String = "") =
    """{"type":"t","title":"x","status":$status,"detail":"server text never shown","code":"$code","request_id":"req-42"$extra}"""

fun MockRequestHandleScope.jsonResponse(status: Int, body: String, vararg headers: Pair<String, String>): HttpResponseData =
    respond(
        body,
        HttpStatusCode.fromValue(status),
        headersOf(*(listOf(HttpHeaders.ContentType to listOf("application/json")) + headers.map { it.first to listOf(it.second) }).toTypedArray()),
    )

class AuthTestEnv(
    handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData,
) {
    val requests = mutableListOf<HttpRequestData>()
    val events = ApiEvents()
    val secureStore = InMemorySecureStore()
    val keyValues = InMemoryKeyValueStore()
    val clientInfo = FakeClientInfo()
    val client = ApiClient(
        createHttpClient(
            engine = MockEngine { request -> requests += request; handler(request) },
            config = AppConfig("http://192.168.1.20:8080", "192.168.1.20", Environment.Dev, isDebugTools = true),
            clientInfo = clientInfo,
            credentialProvider = { secureStore.read()?.credential },
            // The timeout's timer would run on runTest's virtual time and fire before the mock answers.
            requestTimeoutMillis = null,
        ),
        events,
    )
    val api = IdentityApi(client)
    val entitlementsApi = EntitlementsApi(client)
    val snapshots = AccountSnapshotStore(keyValues)
    val repository = AuthRepository(api, secureStore, snapshots)
}
