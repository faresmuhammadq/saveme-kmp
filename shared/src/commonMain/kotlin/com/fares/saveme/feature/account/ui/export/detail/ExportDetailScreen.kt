package com.fares.saveme.feature.account.ui.export.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.fares.saveme.designsystem.component.AlertTone
import com.fares.saveme.designsystem.component.AppScreen
import com.fares.saveme.designsystem.component.InlineAlert
import com.fares.saveme.designsystem.component.PrimaryButton
import com.fares.saveme.designsystem.component.SecondaryButton
import com.fares.saveme.designsystem.theme.AppTheme
import com.fares.saveme.feature.account.domain.export.Export
import com.fares.saveme.feature.account.domain.export.ExportScope
import com.fares.saveme.feature.account.domain.export.ExportStatus
import com.fares.saveme.feature.account.ui.AccountBanner
import com.fares.saveme.feature.account.ui.BackBar
import com.fares.saveme.feature.account.ui.export.kindText
import com.fares.saveme.feature.account.ui.export.label
import com.fares.saveme.feature.account.ui.export.shown
import com.fares.saveme.feature.account.ui.export.sizeLabel
import com.fares.saveme.feature.account.ui.text
import org.jetbrains.compose.resources.stringResource
import saveme.shared.generated.resources.Res
import saveme.shared.generated.resources.action_retry
import saveme.shared.generated.resources.exports_new
import saveme.shared.generated.resources.exports_title
import saveme.shared.generated.resources.export_check_again
import saveme.shared.generated.resources.export_expired_body
import saveme.shared.generated.resources.export_gone
import saveme.shared.generated.resources.export_link_missing
import saveme.shared.generated.resources.export_offline
import saveme.shared.generated.resources.export_open
import saveme.shared.generated.resources.export_open_failed
import saveme.shared.generated.resources.export_prepared_at
import saveme.shared.generated.resources.export_requested_at
import saveme.shared.generated.resources.export_still_preparing
import saveme.shared.generated.resources.export_until
import kotlin.time.Instant

@Composable
fun ExportDetailScreen(state: ExportDetailState, onIntent: (ExportDetailIntent) -> Unit) {
    val export = state.export
    AppScreen(
        topBar = {
            BackBar(export?.kindText(state.walletNames) ?: stringResource(Res.string.exports_title)) { onIntent(ExportDetailIntent.Back) }
        },
    ) {
        state.message?.let { message ->
            val text = when (message) {
                ExportDetailMessage.Offline -> stringResource(Res.string.export_offline)
                ExportDetailMessage.NotFound -> stringResource(Res.string.export_gone)
                ExportDetailMessage.LinkMissing -> stringResource(Res.string.export_link_missing)
                ExportDetailMessage.OpenFailed -> stringResource(Res.string.export_open_failed)
                is ExportDetailMessage.Error -> AccountBanner.Error(message.code, message.requestId).text()
            }
            InlineAlert(AlertTone.Error, text, Modifier.testTag("exportDetail.message"))
        }
        if (export == null) {
            if (state.message == null) Progress(stringResource(Res.string.exports_title), Modifier.testTag("exportDetail.progress"))
            return@AppScreen
        }
        Column(
            verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.xs),
            modifier = Modifier.fillMaxWidth().background(AppTheme.colors.surface, AppTheme.shapes.card).padding(AppTheme.spacing.m),
        ) {
            val statusText = stringResource(export.status.label())
            Text(
                statusText,
                style = AppTheme.type.title,
                color = AppTheme.colors.ink,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }.testTag("exportDetail.status"),
            )
            if (export.status.isActive && !state.stillPreparing) {
                Progress(statusText, Modifier.testTag("exportDetail.progress"))
            }
            Text(
                stringResource(Res.string.export_requested_at, export.requestedAt.shown(state.timeZoneId)),
                style = AppTheme.type.caption,
                color = AppTheme.colors.caption,
            )
            if (export.status == ExportStatus.Ready) {
                export.sizeBytes?.let {
                    Text(it.sizeLabel(), style = AppTheme.type.body, color = AppTheme.colors.ink2, modifier = Modifier.testTag("exportDetail.size"))
                }
                export.completedAt?.let {
                    Text(
                        stringResource(Res.string.export_prepared_at, it.shown(state.timeZoneId)),
                        style = AppTheme.type.caption,
                        color = AppTheme.colors.caption,
                    )
                }
                export.expiresAt?.let {
                    Text(
                        stringResource(Res.string.export_until, it.shown(state.timeZoneId)),
                        style = AppTheme.type.caption,
                        color = AppTheme.colors.caption,
                        modifier = Modifier.testTag("exportDetail.until"),
                    )
                }
            }
        }
        when (export.status) {
            ExportStatus.Ready -> PrimaryButton(
                stringResource(Res.string.export_open),
                { onIntent(ExportDetailIntent.OpenDownload) },
                Modifier.testTag("exportDetail.open"),
                loading = state.opening,
            )
            ExportStatus.Expired -> {
                Text(stringResource(Res.string.export_expired_body), style = AppTheme.type.body, color = AppTheme.colors.ink2)
                PrimaryButton(stringResource(Res.string.exports_new), { onIntent(ExportDetailIntent.RequestAgain) }, Modifier.testTag("exportDetail.new"))
            }
            ExportStatus.Failed -> {
                Text(stringResource(export.failure.label()), style = AppTheme.type.body, color = AppTheme.colors.ink2)
                PrimaryButton(stringResource(Res.string.action_retry), { onIntent(ExportDetailIntent.RequestAgain) }, Modifier.testTag("exportDetail.retry"))
            }
            else -> Unit
        }
        if (state.stillPreparing) {
            InlineAlert(AlertTone.Info, stringResource(Res.string.export_still_preparing), Modifier.testTag("exportDetail.message"))
        }
        if (state.canCheckAgain) {
            SecondaryButton(stringResource(Res.string.export_check_again), { onIntent(ExportDetailIntent.CheckAgain) }, Modifier.testTag("exportDetail.checkAgain"))
        }
    }
}

/** An indeterminate progress ring that reads out [description]; a screen-private use of the platform indicator, no new component. */
@Composable
private fun Progress(description: String, modifier: Modifier = Modifier) {
    CircularProgressIndicator(
        modifier = modifier.size(28.dp).semantics { contentDescription = description },
        strokeWidth = 3.dp,
        color = AppTheme.colors.primary,
    )
}

@Preview
@Composable
private fun ExportDetailScreenPreview() {
    AppTheme {
        ExportDetailScreen(
            ExportDetailState(
                export = Export("e", ExportScope.Account, null, ExportStatus.Running, null, Instant.parse("2026-10-06T10:00:00Z"), null, null, null),
                loaded = true,
            ),
        ) {}
    }
}
