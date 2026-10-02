package com.fares.saveme.feature.categories.data

import com.fares.saveme.core.network.ApiResult
import com.fares.saveme.core.network.ProblemDetails
import com.fares.saveme.core.network.ProblemError
import com.fares.saveme.core.sync.ChangeState
import com.fares.saveme.core.sync.PendingChange
import com.fares.saveme.core.sync.ScopeKind
import com.fares.saveme.core.sync.SyncScope
import com.fares.saveme.feature.account.jsonBody
import com.fares.saveme.feature.auth.jsonResponse
import com.fares.saveme.feature.auth.problemBody
import com.fares.saveme.feature.categories.CategoryTestEnv
import com.fares.saveme.feature.categories.categoryBody
import com.fares.saveme.feature.categories.categoryJson
import com.fares.saveme.core.network.AppJson
import com.fares.saveme.feature.categories.data.dto.CategoryPayload
import io.ktor.http.HttpMethod
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.time.Instant

class CategorySyncHandlerTest {
    private val payload = CategoryPayload("expense", "Coffee", "restaurant", "#1F7F8C")

    private fun change(base: Long, payload: CategoryPayload = this.payload, delete: Boolean = false) = PendingChange(
        recordType = "category", recordId = "c1", accountId = "u1", scope = SyncScope(ScopeKind.User, "u1"),
        baseVersion = base, payloadJson = AppJson.encodeToString(CategoryPayload.serializer(), payload), isDelete = delete,
        restore = false, revision = 0, createdOrder = 1, state = ChangeState.Waiting, attempts = 0, nextAttemptAt = null,
        clashJson = null, refusal = null, updatedAt = Instant.parse("2026-10-02T10:00:00Z"),
    )

    private fun versionOf(result: ApiResult<JsonElement>) =
        assertIs<ApiResult.Success<JsonElement>>(result).value.jsonObject["version"]!!.jsonPrimitive.content.toLong()

    @Test
    fun recordTypeAndRestoreFlag() = runTest {
        val env = CategoryTestEnv(backgroundScope) { jsonResponse(200, categoryBody()) }
        assertEquals("category", env.syncHandler.recordType)
        assertFalse(env.syncHandler.canRestore)
    }

    @Test
    fun createSendsPutWithVersionZero() = runTest {
        val env = CategoryTestEnv(backgroundScope) { jsonResponse(201, categoryBody(categoryJson(version = 1))) }
        env.signedIn()
        assertEquals(1L, versionOf(env.syncHandler.send(change(base = 0))))
        val request = env.requests.single()
        assertEquals(HttpMethod.Put, request.method)
        assertEquals("0", request.jsonBody()["version"].toString())
        assertEquals("\"Coffee\"", request.jsonBody()["name"].toString())
    }

    @Test
    fun editSendsPutWithTheBaseAndTheStoredKind() = runTest {
        val env = CategoryTestEnv(backgroundScope) { jsonResponse(200, categoryBody(categoryJson(version = 4, name = "Renamed"))) }
        env.signedIn()
        env.seed(categoryJson(version = 3, kind = "expense"))
        // The payload says income; the kind is never sent changed (FR-008).
        assertEquals(4L, versionOf(env.syncHandler.send(change(base = 3, payload = payload.copy(name = "Renamed", kind = "income")))))
        val body = env.requests.single().jsonBody()
        assertEquals("3", body["version"].toString())
        assertEquals("\"expense\"", body["kind"].toString())
    }

    @Test
    fun anArchiveOnlyChangeSendsNoPutAndArchivesWithTheBase() = runTest {
        val env = CategoryTestEnv(backgroundScope) { jsonResponse(200, categoryBody(categoryJson(version = 4, archivedAt = "2026-10-02T00:00:00Z"))) }
        env.signedIn()
        env.seed(categoryJson(version = 3, name = "Coffee", icon = "restaurant", color = "#1F7F8C"))
        assertEquals(4L, versionOf(env.syncHandler.send(change(base = 3, payload = payload.copy(archive = true)))))
        val request = env.requests.single()
        assertEquals(HttpMethod.Post, request.method)
        assertEquals("3", request.jsonBody()["version"].toString())
    }

    @Test
    fun editAndArchiveSendPutThenArchiveWithThePutsVersion() = runTest {
        val env = CategoryTestEnv(backgroundScope) { request ->
            if (request.method == HttpMethod.Put) jsonResponse(200, categoryBody(categoryJson(version = 4, name = "Renamed")))
            else jsonResponse(200, categoryBody(categoryJson(version = 5, name = "Renamed", archivedAt = "2026-10-02T00:00:00Z")))
        }
        env.signedIn()
        env.seed(categoryJson(version = 3))
        assertEquals(5L, versionOf(env.syncHandler.send(change(base = 3, payload = payload.copy(name = "Renamed", archive = true)))))
        assertEquals(listOf(HttpMethod.Put, HttpMethod.Post), env.requests.map { it.method })
        assertEquals("4", env.requests[1].jsonBody()["version"].toString())
    }

    @Test
    fun aFailedPutNeverArchives() = runTest {
        val env = CategoryTestEnv(backgroundScope) { jsonResponse(409, problemBody(409, "VERSION_CONFLICT", ""","current":${categoryJson(version = 6)}""")) }
        env.signedIn()
        env.seed(categoryJson(version = 3))
        assertIs<ApiResult.Failure>(env.syncHandler.send(change(base = 3, payload = payload.copy(name = "Renamed", archive = true))))
        assertEquals(listOf(HttpMethod.Put), env.requests.map { it.method })
    }

    @Test
    fun deleteSendsDeleteWithTheBaseInTheBody() = runTest {
        val env = CategoryTestEnv(backgroundScope) { jsonResponse(200, categoryBody(categoryJson(version = 4, deletedAt = "2026-10-02T00:00:00Z"))) }
        env.signedIn()
        versionOf(env.syncHandler.send(change(base = 3, delete = true)))
        val request = env.requests.single()
        assertEquals(HttpMethod.Delete, request.method)
        assertEquals("3", request.jsonBody()["version"].toString())
    }

    @Test
    fun anUnknownIconAndColourAreSentBackUnchanged() = runTest {
        val env = CategoryTestEnv(backgroundScope) { jsonResponse(200, categoryBody(categoryJson(version = 4))) }
        env.signedIn()
        env.seed(categoryJson(version = 3, icon = "rocket", color = "#123456"))
        env.syncHandler.send(change(base = 3, payload = payload.copy(name = "Renamed", icon = "rocket", color = "#123456")))
        val body = env.requests.single().jsonBody()
        assertEquals("\"rocket\"", body["icon"].toString())
        assertEquals("\"#123456\"", body["color"].toString())
    }

    @Test
    fun applyServerCopyUpsertsALiveCategoryAndRemovesATombstone() = runTest {
        val env = CategoryTestEnv(backgroundScope) { jsonResponse(200, categoryBody()) }
        env.syncHandler.applyServerCopy(AppJson.parseToJsonElement(categoryJson(version = 6, name = "Web")))
        assertEquals("Web", env.categoryDao.rows.value["c1"]!!.name)
        env.syncHandler.applyServerCopy(AppJson.parseToJsonElement(categoryJson(version = 7, deletedAt = "2026-10-02T00:00:00Z")))
        assertNull(env.categoryDao.rows.value["c1"])
    }

    @Test
    fun onDiscardedRemovesAPlaceholderOnly() = runTest {
        val env = CategoryTestEnv(backgroundScope) { jsonResponse(200, categoryBody()) }
        env.repository.insertPlaceholder("p1", "u1", "expense", "Typo", null, null)
        env.seed(categoryJson(id = "c1", version = 3))
        env.syncHandler.onDiscarded("p1")
        env.syncHandler.onDiscarded("c1")
        assertNull(env.categoryDao.rows.value["p1"])
        assertNotNull(env.categoryDao.rows.value["c1"])
    }

    @Test
    fun refusalPointersMapToPlainFieldNames() = runTest {
        val env = CategoryTestEnv(backgroundScope) { jsonResponse(200, categoryBody()) }
        val fields = env.syncHandler.refusalFields(
            ProblemDetails(
                code = "VALIDATION_FAILED",
                errors = listOf(
                    ProblemError("/name", "OUT_OF_RANGE"), ProblemError("/kind", "IMMUTABLE_FIELD"),
                    ProblemError("/icon", "INVALID"), ProblemError("/color", "INVALID"), ProblemError("/other", "X"),
                ),
            ),
        )
        assertEquals(mapOf("name" to "OUT_OF_RANGE", "kind" to "IMMUTABLE_FIELD", "icon" to "INVALID", "color" to "INVALID"), fields)
    }
}
