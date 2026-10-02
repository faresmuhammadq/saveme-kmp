package com.fares.saveme.core.sync

import com.fares.saveme.core.network.ApiErrorCode
import com.fares.saveme.core.network.ApiResult
import com.fares.saveme.core.network.ProblemDetails
import com.fares.saveme.core.network.ProblemError
import com.fares.saveme.feature.account.FakeSyncScopeDao
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.JsonNull

/** The engine over fakes: one "note" record type, one signed-in account. */
class EngineHarness(var account: String? = "u1") {
    val dao = FakePendingChangeDao()
    val scopes = FakeSyncScopeDao()
    val scheduler = FakeSyncScheduler()
    val clock = FakeClock()
    val handler = FakeHandler()
    var onLoad: suspend () -> Unit = {}
    val engine = SyncEngine(dao, scopes, listOf(handler), scheduler, currentAccountId = { account }, loadAccount = { onLoad() }, clock = clock)
    val scope = SyncScope(ScopeKind.User, "u1")

    suspend fun save(id: String, payload: String, base: Long = 1) = engine.enqueue("note", id, scope, base, payload)

    fun row(id: String) = dao.rows.value["note" to id]

    companion object {
        fun record(version: Long, deleted: Boolean = false): JsonElement = JsonObject(
            mapOf("version" to JsonPrimitive(version), "deleted_at" to if (deleted) JsonPrimitive("2026-10-01T00:00:00Z") else JsonNull),
        )

        fun ok(version: Long): ApiResult<JsonElement> = ApiResult.Success(record(version))

        fun failure(code: ApiErrorCode, current: JsonElement? = null, retryAfter: Long? = null, pointer: String? = null): ApiResult<JsonElement> =
            ApiResult.Failure(
                ProblemDetails(
                    type = "t", title = "x", status = 400, code = code.name, requestId = "req-1", current = current,
                    errors = pointer?.let { listOf(ProblemError(pointer = it, code = "INVALID")) },
                    retryAfterSeconds = retryAfter,
                ),
                code,
                retryAfter,
            )
    }
}
