package com.fares.saveme.feature.account.ui.export.detail

import com.fares.saveme.core.network.ApiErrorCode
import com.fares.saveme.feature.account.domain.export.DownloadLink
import com.fares.saveme.feature.account.domain.export.Export
import com.fares.saveme.feature.account.domain.export.ExportScope
import com.fares.saveme.feature.account.domain.export.ExportStatus

sealed interface ExportDetailMessage {
    data object Offline : ExportDetailMessage
    data object NotFound : ExportDetailMessage

    /** Ready but the answer carried no link (a contract breach): the next tap reads again (Edge Cases). */
    data object LinkMissing : ExportDetailMessage

    /** No app could open the link; the link is never shown (FR-016). */
    data object OpenFailed : ExportDetailMessage
    data class Error(val code: ApiErrorCode, val requestId: String?) : ExportDetailMessage
}

/** Memory only. There is no download link here, by construction: it travels only in [ExportDetailEffect.OpenDownload]. */
data class ExportDetailState(
    val export: Export? = null,
    val walletNames: Map<String, String> = emptyMap(),
    /** A read is running or the next one is scheduled. */
    val polling: Boolean = false,
    /** Stopped after 10 minutes or 120 reads; the mail covers the rest (FR-011). */
    val stillPreparing: Boolean = false,
    val opening: Boolean = false,
    val loaded: Boolean = false,
    val message: ExportDetailMessage? = null,
    val timeZoneId: String = "UTC",
) {
    val status: ExportStatus? get() = export?.status

    /** Whether "Check again" is offered: a stopped read of something that may still change. */
    val canCheckAgain: Boolean
        get() = !polling && (stillPreparing || message == ExportDetailMessage.Offline || status is ExportStatus.Unknown ||
            message is ExportDetailMessage.Error && export != null)
}

sealed interface ExportDetailIntent {
    /** The screen reached the foreground. */
    data object Started : ExportDetailIntent

    /** The screen left the foreground. */
    data object Stopped : ExportDetailIntent
    data object CheckAgain : ExportDetailIntent
    data object OpenDownload : ExportDetailIntent

    /** The platform could not open the link. */
    data object OpenFailed : ExportDetailIntent

    /** A new request of the same scope (after a failure or an expiry). */
    data object RequestAgain : ExportDetailIntent
    data object Back : ExportDetailIntent
}

sealed interface ExportDetailEffect {
    /** The only carrier of the link; it prints redacted. */
    data class OpenDownload(val link: DownloadLink) : ExportDetailEffect

    /** [walletId] presets the one-wallet scope. */
    data class OpenRequest(val walletId: String?) : ExportDetailEffect
    data object NavigateBack : ExportDetailEffect
}

sealed interface ExportDetailChange {
    data class Polling(val polling: Boolean) : ExportDetailChange
    data class Status(val export: Export) : ExportDetailChange
    data class StillPreparing(val export: Export) : ExportDetailChange
    data class Problem(val message: ExportDetailMessage) : ExportDetailChange
    data class Opening(val opening: Boolean) : ExportDetailChange
    data class WalletNames(val names: Map<String, String>) : ExportDetailChange
    data class TimeZone(val id: String) : ExportDetailChange
    data object SignedOut : ExportDetailChange
}

fun reduce(state: ExportDetailState, change: ExportDetailChange): ExportDetailState = when (change) {
    is ExportDetailChange.Polling -> state.copy(polling = change.polling)
    is ExportDetailChange.Status -> state.copy(export = change.export, loaded = true, stillPreparing = false, message = null)
    is ExportDetailChange.StillPreparing -> state.copy(export = change.export, loaded = true, stillPreparing = true, polling = false, message = null)
    is ExportDetailChange.Problem -> state.copy(
        loaded = true,
        polling = false,
        opening = false,
        message = change.message,
        // Gone: nothing of the export is shown any more.
        export = if (change.message == ExportDetailMessage.NotFound) null else state.export,
    )
    is ExportDetailChange.Opening -> state.copy(opening = change.opening)
    is ExportDetailChange.WalletNames -> state.copy(walletNames = change.names)
    is ExportDetailChange.TimeZone -> state.copy(timeZoneId = change.id)
    ExportDetailChange.SignedOut -> ExportDetailState(timeZoneId = state.timeZoneId)
}

/** The scope a "Try again" or "New export" presets: the wallet of a wallet export. */
fun Export.presetWalletId(): String? = if (scope == ExportScope.Wallet) walletId else null
