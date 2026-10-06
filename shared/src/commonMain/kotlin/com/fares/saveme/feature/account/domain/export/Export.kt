package com.fares.saveme.feature.account.domain.export

import com.fares.saveme.core.network.ApiErrorCode
import com.fares.saveme.feature.account.data.dto.ExportDto
import kotlin.time.Instant

enum class ExportScope { Account, Wallet }

/** The server owns the transitions: `queued`, `running`, then `ready` and later `expired`, or `failed`. Unknown values keep their raw text. */
sealed interface ExportStatus {
    data object Queued : ExportStatus
    data object Running : ExportStatus
    data object Ready : ExportStatus
    data object Failed : ExportStatus
    data object Expired : ExportStatus
    data class Unknown(val raw: String) : ExportStatus

    /** Still being produced, so reading again can change it. */
    val isActive: Boolean get() = this == Queued || this == Running

    companion object {
        fun fromWire(raw: String): ExportStatus = when (raw) {
            "queued" -> Queued
            "running" -> Running
            "ready" -> Ready
            "failed" -> Failed
            "expired" -> Expired
            else -> Unknown(raw)
        }
    }
}

/** Why an export failed. An unknown code reads like [GenerationFailed] on screen. */
sealed interface ExportFailure {
    data object WalletUnavailable : ExportFailure
    data object GenerationFailed : ExportFailure
    data object TimedOut : ExportFailure
    data class Unknown(val raw: String) : ExportFailure

    companion object {
        fun fromWire(raw: String): ExportFailure = when (raw) {
            "wallet_unavailable" -> WalletUnavailable
            "generation_failed" -> GenerationFailed
            "timed_out" -> TimedOut
            else -> Unknown(raw)
        }
    }
}

/** One export as the screens see it. It never carries the download link (spec 016 R-02). */
data class Export(
    val id: String,
    val scope: ExportScope,
    val walletId: String?,
    val status: ExportStatus,
    val failure: ExportFailure?,
    val requestedAt: Instant,
    val completedAt: Instant?,
    val expiresAt: Instant?,
    val sizeBytes: Long?,
)

/** Maps the wire export; the link is dropped here. An unparseable required instant fails the read, not the app. */
fun ExportDto.toExport(): Export = Export(
    id = id,
    scope = if (scope == "wallet") ExportScope.Wallet else ExportScope.Account,
    walletId = walletId,
    status = ExportStatus.fromWire(status),
    failure = failureCode?.let { ExportFailure.fromWire(it) },
    requestedAt = Instant.parse(requestedAt),
    completedAt = completedAt?.let { runCatching { Instant.parse(it) }.getOrNull() },
    expiresAt = expiresAt?.let { runCatching { Instant.parse(it) }.getOrNull() },
    sizeBytes = sizeBytes,
)

/** What asking for an export came to. Each is one request; none is retried (P-02.6). */
sealed interface RequestOutcome {
    data class Created(val export: Export) : RequestOutcome

    /** [current] is the export already queued or running, when the answer carried a readable one. */
    data class InProgress(val current: Export?) : RequestOutcome
    data class RateLimited(val retryAfterSeconds: Long?) : RequestOutcome
    data object WalletGone : RequestOutcome
    data object Offline : RequestOutcome
    data class Failed(val code: ApiErrorCode, val requestId: String?) : RequestOutcome
}

sealed interface ReadOutcome {
    data class Read(val export: Export) : ReadOutcome
    data object NotFound : ReadOutcome
    data object Offline : ReadOutcome
    data class Failed(val code: ApiErrorCode, val requestId: String?) : ReadOutcome
}

sealed interface PageOutcome {
    data class Page(val items: List<Export>, val nextCursor: String?, val hasMore: Boolean) : PageOutcome
    data object CursorInvalid : PageOutcome
    data object Offline : PageOutcome
    data class Failed(val code: ApiErrorCode, val requestId: String?) : PageOutcome
}

sealed interface LinkOutcome {
    data class Open(val link: DownloadLink) : LinkOutcome

    /** Not ready (any more), or ready without a link: the screen updates instead of opening the browser. */
    data class NotReady(val export: Export) : LinkOutcome
    data object NotFound : LinkOutcome
    data object Offline : LinkOutcome
    data class Failed(val code: ApiErrorCode, val requestId: String?) : LinkOutcome
}

/** What one visit's reading of an export reports. The flow ends after any event but [Status] of an active export. */
sealed interface WatchEvent {
    data class Status(val export: Export) : WatchEvent

    /** Still active after 10 minutes or 120 reads: polling stopped and the mail covers the rest. */
    data class StillPreparing(val export: Export) : WatchEvent
    data object NotFound : WatchEvent
    data object Offline : WatchEvent
    data class Failed(val code: ApiErrorCode, val requestId: String?) : WatchEvent
}
