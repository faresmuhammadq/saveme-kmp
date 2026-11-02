package com.fares.saveme.core.sync

import com.fares.saveme.core.network.ApiErrorCode
import com.fares.saveme.core.network.ApiResult
import com.fares.saveme.core.network.CollectionMeta
import com.fares.saveme.core.network.ProblemDetails
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** A scripted [SyncApi]: each feed read and batch is answered by a lambda, and every call is recorded. */
class FakeSyncApi : SyncApi {
    val userCalls = mutableListOf<String?>()
    val walletCalls = mutableListOf<Pair<String, String?>>()
    val batches = mutableListOf<List<BatchItemDto>>()
    val headsCalls = mutableListOf<String?>()
    val sharedBudgetCalls = mutableListOf<Pair<String, String?>>()

    var user: suspend (cursor: String?) -> ApiResult<FeedPageDto> = { ApiResult.Success(emptyPage("u-end")) }
    var wallet: suspend (walletId: String, cursor: String?) -> ApiResult<FeedPageDto> = { _, _ -> ApiResult.Success(emptyPage("w-end")) }
    var sharedBudget: suspend (budgetId: String, cursor: String?) -> ApiResult<FeedPageDto> = { _, _ -> ApiResult.Success(emptyPage("s-end")) }
    var heads: suspend (cursor: String?) -> ApiResult<HeadsPageDto> = { ApiResult.Success(HeadsPageDto()) }
    var batchAnswer: suspend (List<BatchItemDto>) -> ApiResult<List<BatchItemResultDto>> = { items -> ApiResult.Success(items.map { appliedResult(it) }) }

    override suspend fun userChanges(cursor: String?, limit: Int): ApiResult<FeedPageDto> {
        userCalls += cursor
        return user(cursor)
    }

    override suspend fun walletChanges(walletId: String, cursor: String?, limit: Int): ApiResult<FeedPageDto> {
        walletCalls += walletId to cursor
        return wallet(walletId, cursor)
    }

    override suspend fun sharedBudgetChanges(budgetId: String, cursor: String?, limit: Int): ApiResult<FeedPageDto> {
        sharedBudgetCalls += budgetId to cursor
        return sharedBudget(budgetId, cursor)
    }

    override suspend fun walletHeads(cursor: String?, limit: Int): ApiResult<HeadsPageDto> {
        headsCalls += cursor
        return heads(cursor)
    }

    override suspend fun batch(items: List<BatchItemDto>): ApiResult<List<BatchItemResultDto>> {
        batches += items
        return batchAnswer(items)
    }

    companion object {
        fun emptyPage(sync: String, position: Long? = null) = FeedPageDto(emptyList(), FeedMetaDto(null, false, 200, sync, position))

        fun row(type: String, id: String, version: Long = 1, extra: Map<String, JsonElement> = emptyMap()) =
            ChangeRowDto(type, record(id, version, extra))

        fun record(id: String, version: Long = 1, extra: Map<String, JsonElement> = emptyMap()): JsonObject = JsonObject(
            mapOf("id" to JsonPrimitive(id), "version" to JsonPrimitive(version), "deleted_at" to JsonNull) + extra,
        )

        /** A page: [next] set means more pages follow (`has_more`), and the position after it is [sync]. */
        fun page(rows: List<ChangeRowDto>, sync: String, next: String? = null, position: Long? = null) =
            FeedPageDto(rows, FeedMetaDto(next, next != null, 200, sync, position))

        fun head(walletId: String, changeSeq: Long, purgedChangeSeq: Long = 0) = WalletHeadDto(walletId, changeSeq, purgedChangeSeq)

        /** A heads page: [next] set means more pages follow. */
        fun headsPage(vararg heads: WalletHeadDto, next: String? = null) =
            HeadsPageDto(heads.toList(), CollectionMeta(next, next != null, 200))

        fun appliedResult(item: BatchItemDto, version: Long = 1) = BatchItemResultDto(
            item.type, item.id, "applied", 200, record(item.id, version, mapOf("wallet_id" to (item.walletId?.let { JsonPrimitive(it) } ?: JsonNull))), null,
        )

        fun problem(code: ApiErrorCode, status: Int = 400, current: JsonElement? = null) =
            ProblemDetails(type = "t", title = "x", status = status, code = code.name, requestId = "req-1", current = current)

        fun rejected(item: BatchItemDto, code: ApiErrorCode, status: Int = 422, current: JsonElement? = null) = BatchItemResultDto(
            item.type, item.id, if (code == ApiErrorCode.VERSION_CONFLICT) "conflict" else "rejected", status, null, problem(code, status, current),
        )

        fun failure(code: ApiErrorCode, status: Int = 400, retryAfter: Long? = null) =
            ApiResult.Failure(problem(code, status), code, retryAfter)
    }
}

/** Records what a feed applier is asked; [failOn] makes it throw on a given call (a crash mid-page). */
class RecordingApplier(
    override val scopeKind: ScopeKind,
    override val rowType: String,
    private val log: MutableList<String> = mutableListOf(),
) : FeedApplier {
    val applied = mutableListOf<Pair<SyncScope, List<JsonElement>>>()
    val swept = mutableListOf<Pair<SyncScope, Set<String>>>()
    var failOnApply: (Int) -> Boolean = { false }

    override suspend fun apply(scope: SyncScope, records: List<JsonElement>) {
        if (failOnApply(applied.size)) error("boom")
        applied += scope to records
        log += "$rowType x${records.size}"
    }

    override suspend fun sweep(scope: SyncScope, seenIds: Set<String>) {
        swept += scope to seenIds
    }
}

class FakeWalletScopes(
    var followed: List<String> = emptyList(),
    var accepted: (String) -> Boolean = { true },
    var unconfirmedFor: (Set<String>) -> List<String> = { emptyList() },
) : WalletScopes {
    val lost = mutableListOf<String>()
    val unconfirmedCalls = mutableListOf<Set<String>>()
    override suspend fun followed(): List<String> = followed
    override suspend fun isAccepted(walletId: String): Boolean = accepted(walletId)
    override suspend fun unconfirmed(seenWalletIds: Set<String>): List<String> {
        unconfirmedCalls += seenWalletIds
        return unconfirmedFor(seenWalletIds)
    }
    override suspend fun accessLost(walletId: String) { lost += walletId }
}
