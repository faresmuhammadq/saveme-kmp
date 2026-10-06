package com.fares.saveme.feature.account.domain.export

import co.touchlab.kermit.Logger
import com.fares.saveme.core.network.ApiErrorCode
import com.fares.saveme.core.network.ApiResult
import com.fares.saveme.core.network.AppJson
import com.fares.saveme.feature.account.data.ExportApi
import com.fares.saveme.feature.account.data.dto.ExportDto
import com.fares.saveme.feature.account.data.dto.ExportRequestDto
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/**
 * The export domain API (contracts/account-domain.md). Online only: nothing is queued, cached or retried, and nothing here
 * reads a plan, a feature or the address verification (FR-004). The download link leaves this class only as a [DownloadLink]
 * from [openableLink].
 */
interface ExportService {
    /** One POST per call, never retried. [walletId] is required with [ExportScope.Wallet]. */
    suspend fun request(scope: ExportScope, walletId: String?): RequestOutcome

    /** 20 per page, newest first. */
    suspend fun page(cursor: String?): PageOutcome

    suspend fun read(id: String): ReadOutcome

    /** A fresh read for one tap; the only way a URL leaves the domain. */
    suspend fun openableLink(id: String): LinkOutcome

    /**
     * Reads at once, then every 5 seconds while the export is queued or running. [windowStart] is when the 10-minute window
     * began; null means the export's own request time. Ends after a final status, [WatchEvent.StillPreparing], not found,
     * offline or a failure; cancelling the collector stops the reads.
     */
    fun watch(id: String, windowStart: Instant? = null): Flow<WatchEvent>
}

class DefaultExportService(
    private val api: ExportApi,
    private val now: () -> Instant = { Clock.System.now() },
    private val pollEvery: Duration = POLL_EVERY,
    private val maxAge: Duration = MAX_AGE,
    private val maxReads: Int = MAX_READS,
) : ExportService {

    override suspend fun request(scope: ExportScope, walletId: String?): RequestOutcome {
        require(scope == ExportScope.Account || walletId != null) { "a wallet export names its wallet" }
        val body = when (scope) {
            ExportScope.Account -> ExportRequestDto("account")
            ExportScope.Wallet -> ExportRequestDto("wallet", walletId)
        }
        val outcome = try {
            when (val result = api.requestExport(body)) {
                is ApiResult.Success -> RequestOutcome.Created(result.value.toExport())
                ApiResult.Offline, ApiResult.Timeout -> RequestOutcome.Offline
                is ApiResult.Failure -> when (result.code) {
                    ApiErrorCode.EXPORT_IN_PROGRESS -> RequestOutcome.InProgress(result.problem.current?.decodeExport())
                    ApiErrorCode.RATE_LIMITED -> RequestOutcome.RateLimited(result.retryAfterSeconds)
                    ApiErrorCode.NOT_FOUND -> RequestOutcome.WalletGone
                    else -> RequestOutcome.Failed(result.code, result.problem.requestId)
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            RequestOutcome.Failed(ApiErrorCode.UNKNOWN, null)
        }
        Logger.i(tag = "export") { "request outcome=${outcome::class.simpleName}" + ((outcome as? RequestOutcome.Created)?.let { " id=${it.export.id}" } ?: "") }
        return outcome
    }

    override suspend fun page(cursor: String?): PageOutcome = try {
        when (val result = api.listExports(cursor)) {
            is ApiResult.Success -> PageOutcome.Page(result.value.items.map { it.toExport() }, result.value.nextCursor, result.value.hasMore)
            ApiResult.Offline, ApiResult.Timeout -> PageOutcome.Offline
            is ApiResult.Failure ->
                if (result.code == ApiErrorCode.INVALID_CURSOR) PageOutcome.CursorInvalid else PageOutcome.Failed(result.code, result.problem.requestId)
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        PageOutcome.Failed(ApiErrorCode.UNKNOWN, null)
    }

    override suspend fun read(id: String): ReadOutcome = try {
        when (val result = api.getExport(id)) {
            is ApiResult.Success -> ReadOutcome.Read(result.value.toExport())
            ApiResult.Offline, ApiResult.Timeout -> ReadOutcome.Offline
            is ApiResult.Failure ->
                if (result.code == ApiErrorCode.NOT_FOUND) ReadOutcome.NotFound else ReadOutcome.Failed(result.code, result.problem.requestId)
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        ReadOutcome.Failed(ApiErrorCode.UNKNOWN, null)
    }

    override suspend fun openableLink(id: String): LinkOutcome {
        val outcome = try {
            when (val result = api.getExport(id)) {
                is ApiResult.Success -> {
                    val dto = result.value
                    val url = dto.downloadUrl?.takeIf { it.isNotBlank() }
                    val export = dto.toExport()
                    if (export.status == ExportStatus.Ready && url != null) LinkOutcome.Open(DownloadLink(url)) else LinkOutcome.NotReady(export)
                }
                ApiResult.Offline, ApiResult.Timeout -> LinkOutcome.Offline
                is ApiResult.Failure ->
                    if (result.code == ApiErrorCode.NOT_FOUND) LinkOutcome.NotFound else LinkOutcome.Failed(result.code, result.problem.requestId)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            LinkOutcome.Failed(ApiErrorCode.UNKNOWN, null)
        }
        Logger.i(tag = "export") { "link id=$id outcome=${outcome::class.simpleName}" }
        return outcome
    }

    override fun watch(id: String, windowStart: Instant?): Flow<WatchEvent> = flow {
        var reads = 0
        var start = windowStart
        while (true) {
            when (val outcome = read(id)) {
                is ReadOutcome.Read -> {
                    reads++
                    val export = outcome.export
                    emit(WatchEvent.Status(export))
                    if (!export.status.isActive) return@flow
                    start = start ?: export.requestedAt
                    if (now() - start > maxAge || reads >= maxReads) {
                        emit(WatchEvent.StillPreparing(export))
                        return@flow
                    }
                    delay(pollEvery)
                }
                ReadOutcome.NotFound -> { emit(WatchEvent.NotFound); return@flow }
                ReadOutcome.Offline -> { emit(WatchEvent.Offline); return@flow }
                is ReadOutcome.Failed -> { emit(WatchEvent.Failed(outcome.code, outcome.requestId)); return@flow }
            }
        }
    }

    private fun kotlinx.serialization.json.JsonElement.decodeExport(): Export? =
        runCatching { AppJson.decodeFromJsonElement(ExportDto.serializer(), this).toExport() }.getOrNull()

    companion object {
        val POLL_EVERY = 5.seconds
        val MAX_AGE = 10.minutes
        const val MAX_READS = 120
    }
}
