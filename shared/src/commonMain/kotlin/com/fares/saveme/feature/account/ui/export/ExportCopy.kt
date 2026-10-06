package com.fares.saveme.feature.account.ui.export

import androidx.compose.runtime.Composable
import com.fares.saveme.designsystem.format.formatDateTime
import com.fares.saveme.feature.account.domain.export.Export
import com.fares.saveme.feature.account.domain.export.ExportFailure
import com.fares.saveme.feature.account.domain.export.ExportScope
import com.fares.saveme.feature.account.domain.export.ExportStatus
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import saveme.shared.generated.resources.Res
import saveme.shared.generated.resources.export_failed_generic
import saveme.shared.generated.resources.export_failed_wallet
import saveme.shared.generated.resources.export_kind_account
import saveme.shared.generated.resources.export_kind_wallet_gone
import saveme.shared.generated.resources.export_limit_hours
import saveme.shared.generated.resources.export_limit_later
import saveme.shared.generated.resources.export_limit_minutes
import saveme.shared.generated.resources.export_size_kb
import saveme.shared.generated.resources.export_size_mb
import saveme.shared.generated.resources.export_status_expired
import saveme.shared.generated.resources.export_status_failed
import saveme.shared.generated.resources.export_status_queued
import saveme.shared.generated.resources.export_status_ready
import saveme.shared.generated.resources.export_status_running
import saveme.shared.generated.resources.export_status_unknown

fun ExportStatus.label(): StringResource = when (this) {
    ExportStatus.Queued -> Res.string.export_status_queued
    ExportStatus.Running -> Res.string.export_status_running
    ExportStatus.Ready -> Res.string.export_status_ready
    ExportStatus.Failed -> Res.string.export_status_failed
    ExportStatus.Expired -> Res.string.export_status_expired
    is ExportStatus.Unknown -> Res.string.export_status_unknown
}

/** An unknown failure code reads as a generation failure (FR-022). */
fun ExportFailure?.label(): StringResource = when (this) {
    ExportFailure.WalletUnavailable -> Res.string.export_failed_wallet
    else -> Res.string.export_failed_generic
}

/** "Everything", the wallet's name, or "A wallet you no longer have" (FR-005). */
@Composable
fun Export.kindText(walletNames: Map<String, String>): String = when (scope) {
    ExportScope.Account -> stringResource(Res.string.export_kind_account)
    ExportScope.Wallet -> walletId?.let { walletNames[it] } ?: stringResource(Res.string.export_kind_wallet_gone)
}

/** A size to show: whole KB (rounded up) under 1 MB, otherwise MB with one decimal. Western digits (FR-012). */
sealed interface SizeText {
    data class Kilobytes(val value: Long) : SizeText
    data class Megabytes(val text: String) : SizeText
}

fun sizeText(bytes: Long): SizeText =
    if (bytes < BYTES_PER_MB) {
        SizeText.Kilobytes(((bytes + 1023) / 1024).coerceAtLeast(1))
    } else {
        val tenths = (bytes * 10 + BYTES_PER_MB / 2) / BYTES_PER_MB
        SizeText.Megabytes("${tenths / 10}.${tenths % 10}")
    }

private const val BYTES_PER_MB = 1_048_576L

@Composable
fun Long.sizeLabel(): String = when (val size = sizeText(this)) {
    is SizeText.Kilobytes -> stringResource(Res.string.export_size_kb, size.value.toString())
    is SizeText.Megabytes -> stringResource(Res.string.export_size_mb, size.text)
}

/** How long to wait, in whole units rounded up: minutes under an hour, otherwise hours; null when no wait time was given (FR-020). */
sealed interface WaitText {
    data class Minutes(val value: Int) : WaitText
    data class Hours(val value: Int) : WaitText
    data object Later : WaitText
}

fun waitText(seconds: Long?): WaitText = when {
    seconds == null || seconds <= 0 -> WaitText.Later
    seconds < 3600 -> WaitText.Minutes(((seconds + 59) / 60).toInt())
    else -> WaitText.Hours(((seconds + 3599) / 3600).toInt())
}

@Composable
fun rateLimitText(seconds: Long?): String = when (val wait = waitText(seconds)) {
    is WaitText.Minutes -> pluralStringResource(Res.plurals.export_limit_minutes, wait.value, wait.value)
    is WaitText.Hours -> pluralStringResource(Res.plurals.export_limit_hours, wait.value, wait.value)
    WaitText.Later -> stringResource(Res.string.export_limit_later)
}

@Composable
fun kotlin.time.Instant.shown(timeZoneId: String): String = formatDateTime(this, timeZoneId)
