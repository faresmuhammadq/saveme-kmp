package com.fares.saveme.feature.wallets.data

import com.fares.saveme.core.network.ApiResult
import com.fares.saveme.core.network.AppJson
import com.fares.saveme.core.network.ProblemDetails
import com.fares.saveme.core.network.ProblemError
import com.fares.saveme.core.sync.ChangeState
import com.fares.saveme.core.sync.PendingChange
import com.fares.saveme.core.sync.ScopeKind
import com.fares.saveme.core.sync.SyncScope
import com.fares.saveme.feature.account.jsonBody
import com.fares.saveme.feature.auth.jsonResponse
import com.fares.saveme.feature.auth.problemBody
import com.fares.saveme.feature.wallets.WalletTestEnv
import com.fares.saveme.feature.wallets.domain.model.WalletDraft
import com.fares.saveme.feature.wallets.walletBody
import com.fares.saveme.feature.wallets.walletJson
import io.ktor.http.HttpMethod
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.time.Instant

class WalletSyncHandlerTest {
    private val draft = WalletDraft("cash", "Pocket", "EGP", 25_000, "cash", "#4C6FD6")

    private fun change(base: Long, draft: WalletDraft = this.draft, delete: Boolean = false) = PendingChange(
        recordType = "wallet", recordId = "w1", accountId = "u1", scope = SyncScope(ScopeKind.Wallet, "w1"),
        baseVersion = base, payloadJson = AppJson.encodeToString(WalletDraft.serializer(), draft), isDelete = delete,
        restore = false, revision = 0, createdOrder = 1, state = ChangeState.Waiting, attempts = 0, nextAttemptAt = null,
        clashJson = null, refusal = null, updatedAt = Instant.parse("2026-10-02T10:00:00Z"),
    )

    private fun versionOf(result: ApiResult<*>) =
        (assertIs<ApiResult.Success<kotlinx.serialization.json.JsonElement>>(result).value.jsonObject["version"]!!.jsonPrimitive.content).toLong()

    @Test
    fun recordTypeAndRestoreFlag() = runTest {
        val env = WalletTestEnv(backgroundScope) { jsonResponse(200, walletBody()) }
        assertEquals("wallet", env.syncHandler.recordType)
        assertFalse(env.syncHandler.canRestore)
    }

    @Test
    fun createSendsPutWithVersionZero() = runTest {
        val env = WalletTestEnv(backgroundScope) { jsonResponse(201, walletBody(walletJson(version = 1))) }
        env.signedIn()
        assertEquals(1L, versionOf(env.syncHandler.send(change(base = 0))))
        val request = env.requests.single()
        assertEquals(HttpMethod.Put, request.method)
        assertEquals("0", request.jsonBody()["version"].toString())
        assertEquals("\"Pocket\"", request.jsonBody()["name"].toString())
    }

    @Test
    fun editSendsPutWithTheBaseVersion() = runTest {
        val env = WalletTestEnv(backgroundScope) { jsonResponse(200, walletBody(walletJson(version = 4, name = "Renamed"))) }
        env.signedIn()
        env.seed(walletJson(version = 3))
        assertEquals(4L, versionOf(env.syncHandler.send(change(base = 3, draft = draft.copy(name = "Renamed")))))
        assertEquals("3", env.requests.single().jsonBody()["version"].toString())
    }

    @Test
    fun anArchiveOnlyChangeSendsNoPutAndArchivesWithTheBase() = runTest {
        val env = WalletTestEnv(backgroundScope) { jsonResponse(200, walletBody(walletJson(version = 4, archivedAt = "2026-10-02T00:00:00Z"))) }
        env.signedIn()
        env.seed(walletJson(version = 3, icon = "cash", color = "#4C6FD6"))
        assertEquals(4L, versionOf(env.syncHandler.send(change(base = 3, draft = draft.copy(archive = true)))))
        val request = env.requests.single()
        assertEquals(HttpMethod.Post, request.method)
        assertEquals("3", request.jsonBody()["version"].toString())
    }

    @Test
    fun editAndArchiveSendPutThenArchiveWithThePutsVersion() = runTest {
        val env = WalletTestEnv(backgroundScope) { request ->
            if (request.method == HttpMethod.Put) jsonResponse(200, walletBody(walletJson(version = 4, name = "Renamed")))
            else jsonResponse(200, walletBody(walletJson(version = 5, name = "Renamed", archivedAt = "2026-10-02T00:00:00Z")))
        }
        env.signedIn()
        env.seed(walletJson(version = 3))
        assertEquals(5L, versionOf(env.syncHandler.send(change(base = 3, draft = draft.copy(name = "Renamed", archive = true)))))
        assertEquals(listOf(HttpMethod.Put, HttpMethod.Post), env.requests.map { it.method })
        assertEquals("4", env.requests[1].jsonBody()["version"].toString())
    }

    @Test
    fun aFailedPutNeverArchives() = runTest {
        val env = WalletTestEnv(backgroundScope) { jsonResponse(409, problemBody(409, "VERSION_CONFLICT", ""","current":${walletJson(version = 6)}""")) }
        env.signedIn()
        env.seed(walletJson(version = 3))
        assertIs<ApiResult.Failure>(env.syncHandler.send(change(base = 3, draft = draft.copy(name = "Renamed", archive = true))))
        assertEquals(listOf(HttpMethod.Put), env.requests.map { it.method })
    }

    @Test
    fun aPutThatAlreadyReturnsAnArchivedWalletDoesNotArchiveAgain() = runTest {
        val env = WalletTestEnv(backgroundScope) { jsonResponse(200, walletBody(walletJson(version = 4, archivedAt = "2026-10-02T00:00:00Z"))) }
        env.signedIn()
        env.seed(walletJson(version = 3))
        env.syncHandler.send(change(base = 3, draft = draft.copy(name = "Renamed", archive = true)))
        assertEquals(1, env.requests.size)
    }

    @Test
    fun deleteSendsDeleteWithTheBaseInTheBody() = runTest {
        val env = WalletTestEnv(backgroundScope) { jsonResponse(200, walletBody(walletJson(version = 4, deletedAt = "2026-10-02T00:00:00Z"))) }
        env.signedIn()
        versionOf(env.syncHandler.send(change(base = 3, delete = true)))
        val request = env.requests.single()
        assertEquals(HttpMethod.Delete, request.method)
        assertEquals("3", request.jsonBody()["version"].toString())
    }

    @Test
    fun anUnknownIconAndColourAreSentBackUnchanged() = runTest {
        val env = WalletTestEnv(backgroundScope) { jsonResponse(200, walletBody(walletJson(version = 4))) }
        env.signedIn()
        env.seed(walletJson(version = 3, icon = "rocket", color = "#123456"))
        env.syncHandler.send(change(base = 3, draft = draft.copy(name = "Renamed", icon = "rocket", color = "#123456")))
        val body = env.requests.single().jsonBody()
        assertEquals("\"rocket\"", body["icon"].toString())
        assertEquals("\"#123456\"", body["color"].toString())
    }

    @Test
    fun applyServerCopyUpsertsALiveWalletAndRemovesATombstone() = runTest {
        val env = WalletTestEnv(backgroundScope) { jsonResponse(200, walletBody()) }
        env.syncHandler.applyServerCopy(AppJson.parseToJsonElement(walletJson(version = 6, name = "Web")))
        assertEquals("Web", env.walletDao.rows.value["w1"]!!.name)
        env.memberDao.insertAll(listOf(com.fares.saveme.feature.wallets.data.local.WalletMemberEntity("m1", "w1", "u1", "Sara", "owner", 1, "2026-09-01T00:00:00Z")))
        env.syncHandler.applyServerCopy(AppJson.parseToJsonElement(walletJson(version = 7, deletedAt = "2026-10-02T00:00:00Z")))
        assertNull(env.walletDao.rows.value["w1"])
        assertEquals(emptyList(), env.memberDao.rows.value)
        assertEquals(listOf("w1"), env.removed)
    }

    @Test
    fun onDiscardedRemovesAPlaceholderOnly() = runTest {
        val env = WalletTestEnv(backgroundScope) { jsonResponse(200, walletBody()) }
        env.repository.insertPlaceholder("p1", draft, "u1")
        env.seed(walletJson(id = "w1", version = 3))
        env.syncHandler.onDiscarded("p1")
        env.syncHandler.onDiscarded("w1")
        assertNull(env.walletDao.rows.value["p1"])
        assertNotNull(env.walletDao.rows.value["w1"])
    }

    @Test
    fun refusalPointersMapToWalletFields() = runTest {
        val env = WalletTestEnv(backgroundScope) { jsonResponse(200, walletBody()) }
        val fields = env.syncHandler.refusalFields(
            ProblemDetails(
                code = "VALIDATION_FAILED",
                errors = listOf(ProblemError("/currency", "IMMUTABLE_FIELD"), ProblemError("/name", "OUT_OF_RANGE"), ProblemError("/other", "X")),
            ),
        )
        assertEquals(mapOf("Currency" to "IMMUTABLE_FIELD", "Name" to "OUT_OF_RANGE"), fields)
    }
}
