package com.fares.saveme.core.sync

import co.touchlab.kermit.Logger
import com.fares.saveme.core.network.ApiClient
import com.fares.saveme.core.network.ApiErrorCode
import com.fares.saveme.core.network.ApiEvent
import com.fares.saveme.core.network.ApiEvents
import com.fares.saveme.core.network.ApiResult
import com.fares.saveme.core.network.CollectionMeta
import com.fares.saveme.core.network.Envelope
import com.fares.saveme.core.network.ProblemDetails
import io.ktor.client.call.body
import io.ktor.client.plugins.timeout
import io.ktor.client.request.parameter
import io.ktor.client.request.setBody
import io.ktor.client.request.url
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.contentType
import kotlinx.coroutines.CancellationException
import kotlinx.io.IOException
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/** One row of a change feed: its kind and the record exactly as its own read returns it (sync.yaml `ChangeRow`). */
@Serializable
data class ChangeRowDto(val type: String, val record: JsonElement) {
    /** The record is the person's content; it is never printed (P-09.2). */
    override fun toString() = "ChangeRowDto($type)"
}

/** sync.yaml `ChangeFeedMeta`. The positions are opaque: never parsed, compared or logged. */
@Serializable
data class FeedMetaDto(
    @SerialName("next_cursor") val nextCursor: String? = null,
    @SerialName("has_more") val hasMore: Boolean = false,
    val limit: Int = 0,
    @SerialName("sync_cursor") val syncCursor: String = "",
    /** The integer position of [syncCursor]'s place, sent by backend v1.10 and later; null from an older server (spec 011). */
    @SerialName("sync_position") val syncPosition: Long? = null,
) {
    override fun toString() = "FeedMetaDto(hasMore=$hasMore, limit=$limit)"
}

@Serializable
data class FeedPageDto(val data: List<ChangeRowDto> = emptyList(), val meta: FeedMetaDto = FeedMetaDto())

/** sync.yaml `WalletHead`: where a followed wallet's feed stands (spec 011). The values are never logged. */
@Serializable
data class WalletHeadDto(
    @SerialName("wallet_id") val walletId: String,
    @SerialName("change_seq") val changeSeq: Long,
    @SerialName("purged_change_seq") val purgedChangeSeq: Long,
) {
    override fun toString() = "WalletHeadDto()"
}

/** One page of `listWalletHeads` (spec 011). */
@Serializable
data class HeadsPageDto(val data: List<WalletHeadDto> = emptyList(), val meta: CollectionMeta = CollectionMeta()) {
    override fun toString() = "HeadsPageDto(${data.size} heads, $meta)"
}

/** sync.yaml `BatchItem`: `wallet_id` is omitted when null (only transactions carry one). */
@Serializable
data class BatchItemDto(
    val type: String,
    val op: String,
    val id: String,
    @SerialName("wallet_id") val walletId: String? = null,
    val data: JsonElement,
)

@Serializable
data class BatchRequestDto(val items: List<BatchItemDto>)

/** sync.yaml `BatchItemResult`: exactly one of [record] and [problem] is set. */
@Serializable
data class BatchItemResultDto(
    val type: String,
    val id: String,
    val outcome: String,
    val status: Int = 0,
    val record: JsonElement? = null,
    val problem: ProblemDetails? = null,
)

/** The sync endpoints of sync.yaml (spec 009 contracts/sync-api-client.md). All credentialed. */
interface SyncApi {
    suspend fun userChanges(cursor: String?, limit: Int = HttpSyncApi.PAGE_SIZE): ApiResult<FeedPageDto>

    suspend fun walletChanges(walletId: String, cursor: String?, limit: Int = HttpSyncApi.PAGE_SIZE): ApiResult<FeedPageDto>

    /** One page of the heads of the wallets the person follows (`listWalletHeads`, sync.yaml v1.10). Only `cursor` and `limit` are sent. */
    suspend fun walletHeads(cursor: String?, limit: Int = HttpSyncApi.PAGE_SIZE): ApiResult<HeadsPageDto>

    /**
     * Sends up to 50 items. A refusal of the whole request comes back as [ApiResult.Failure]; an
     * answer that is not the documented shape comes back with `problem.code` [HttpSyncApi.UNREADABLE].
     * Item problems raise the same app-wide signals their single writes raise (FR-033).
     */
    suspend fun batch(items: List<BatchItemDto>): ApiResult<List<BatchItemResultDto>>
}

class HttpSyncApi(
    private val client: ApiClient,
    private val events: ApiEvents,
    /** The batch answer allowance (FR-023); null leaves the client's default (tests: its timer would run on virtual time). */
    private val batchTimeoutMillis: Long? = BATCH_TIMEOUT_MILLIS,
) : SyncApi {
    override suspend fun userChanges(cursor: String?, limit: Int): ApiResult<FeedPageDto> =
        feed("sync/user/changes", cursor, limit)

    override suspend fun walletChanges(walletId: String, cursor: String?, limit: Int): ApiResult<FeedPageDto> =
        feed("sync/wallets/$walletId/changes", cursor, limit)

    override suspend fun walletHeads(cursor: String?, limit: Int): ApiResult<HeadsPageDto> = guarded {
        client.execute({
            method = HttpMethod.Get
            url("sync/wallets/heads")
            parameter("limit", limit)
            if (cursor != null) parameter("cursor", cursor)
        }) { response -> response.body<HeadsPageDto>() }
    }

    private suspend fun feed(path: String, cursor: String?, limit: Int): ApiResult<FeedPageDto> = guarded {
        client.execute({
            method = HttpMethod.Get
            url(path)
            parameter("limit", limit)
            if (cursor != null) parameter("cursor", cursor)
        }) { response -> response.body<FeedPageDto>() }
    }

    override suspend fun batch(items: List<BatchItemDto>): ApiResult<List<BatchItemResultDto>> {
        val result = guarded {
            client.execute({
                method = HttpMethod.Post
                url("sync/batch")
                contentType(ContentType.Application.Json)
                if (batchTimeoutMillis != null) timeout { requestTimeoutMillis = batchTimeoutMillis }
                setBody(BatchRequestDto(items))
            }) { response -> response.body<Envelope<List<BatchItemResultDto>>>().data }
        }
        if (result is ApiResult.Success) result.value.forEach { publishEvents(it.problem) }
        return result
    }

    private fun publishEvents(problem: ProblemDetails?) {
        when (ApiErrorCode.from(problem?.code.orEmpty())) {
            ApiErrorCode.EMAIL_NOT_VERIFIED -> events.publish(ApiEvent.EmailNotVerified)
            ApiErrorCode.FEATURE_NOT_ENTITLED -> events.publish(ApiEvent.FeatureNotEntitled)
            else -> Unit
        }
    }

    /** A 200 whose body can't be read is not a connection problem: the engine falls back or stops the scope. */
    private suspend fun <T> guarded(block: suspend () -> ApiResult<T>): ApiResult<T> = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: IOException) {
        Logger.withTag("sync").w { "answer cut off: ${e::class.simpleName}" }
        ApiResult.Offline
    } catch (e: Exception) {
        Logger.withTag("sync").w { "unreadable answer: ${e::class.simpleName}" }
        ApiResult.Failure(ProblemDetails(status = 200, code = UNREADABLE), ApiErrorCode.UNKNOWN, null)
    }

    companion object {
        const val PAGE_SIZE = 200
        const val BATCH_TIMEOUT_MILLIS = 60_000L

        /** `problem.code` of a 200 answer that could not be decoded. */
        const val UNREADABLE = "UNREADABLE_ANSWER"
    }
}
