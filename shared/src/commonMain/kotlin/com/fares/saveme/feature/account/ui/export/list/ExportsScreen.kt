package com.fares.saveme.feature.account.ui.export.list

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fares.saveme.designsystem.component.AlertTone
import com.fares.saveme.designsystem.component.AppTextButton
import com.fares.saveme.designsystem.component.CalmCard
import com.fares.saveme.designsystem.component.CalmIconTile
import com.fares.saveme.designsystem.component.CalmScreen
import com.fares.saveme.designsystem.component.EmptyState
import com.fares.saveme.designsystem.component.InlineAlert
import com.fares.saveme.designsystem.component.SecondaryButton
import com.fares.saveme.designsystem.format.formatDate
import com.fares.saveme.designsystem.icon.AccClock
import com.fares.saveme.designsystem.icon.AccDownloadDone
import com.fares.saveme.designsystem.icon.AccFolderZip
import com.fares.saveme.designsystem.icon.AppIcons
import com.fares.saveme.designsystem.theme.AppTheme
import com.fares.saveme.feature.account.domain.export.Export
import com.fares.saveme.feature.account.domain.export.ExportFailure
import com.fares.saveme.feature.account.domain.export.ExportScope
import com.fares.saveme.feature.account.domain.export.ExportStatus
import com.fares.saveme.feature.account.ui.AccountBanner
import com.fares.saveme.feature.account.ui.export.kindText
import com.fares.saveme.feature.account.ui.export.label
import com.fares.saveme.feature.account.ui.export.shown
import com.fares.saveme.feature.account.ui.text
import org.jetbrains.compose.resources.stringResource
import saveme.shared.generated.resources.Res
import saveme.shared.generated.resources.acct_export_email_note
import saveme.shared.generated.resources.acct_export_open
import saveme.shared.generated.resources.acct_export_preparing
import saveme.shared.generated.resources.acct_export_ready
import saveme.shared.generated.resources.acct_export_waiting
import saveme.shared.generated.resources.acct_exports_last_30
import saveme.shared.generated.resources.action_retry
import saveme.shared.generated.resources.cd_back
import saveme.shared.generated.resources.export_offline
import saveme.shared.generated.resources.export_requested_at
import saveme.shared.generated.resources.exports_empty
import saveme.shared.generated.resources.exports_more
import saveme.shared.generated.resources.exports_new
import saveme.shared.generated.resources.exports_refresh
import saveme.shared.generated.resources.exports_title
import kotlin.time.Instant

@Composable
fun ExportsScreen(state: ExportsState, onIntent: (ExportsIntent) -> Unit) {
    CalmScreen(
        title = stringResource(Res.string.exports_title),
        onBack = { onIntent(ExportsIntent.Back) },
        backDescription = stringResource(Res.string.cd_back),
        spacing = 12.dp,
        actions = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier
                    .testTag("exports.new")
                    .height(44.dp)
                    .background(AppTheme.colors.primary, AppTheme.shapes.pill)
                    .clickable(role = Role.Button, onClick = { onIntent(ExportsIntent.NewClicked) })
                    .padding(start = 12.dp, end = 16.dp),
            ) {
                Icon(AppIcons.Plus, contentDescription = null, tint = AppTheme.colors.onPrimary, modifier = Modifier.size(20.dp))
                Text(
                    stringResource(Res.string.exports_new),
                    style = AppTheme.type.caption.copy(fontWeight = FontWeight.Bold),
                    color = AppTheme.colors.onPrimary,
                )
            }
        },
    ) {
        state.banner?.let { banner ->
            InlineAlert(
                AlertTone.Error,
                when (banner) {
                    ExportsBanner.Offline -> stringResource(Res.string.export_offline)
                    is ExportsBanner.Error -> AccountBanner.Error(banner.code, banner.requestId).text()
                },
                Modifier.testTag("exports.message"),
                actionLabel = stringResource(Res.string.action_retry),
                onAction = { onIntent(ExportsIntent.Refresh) },
            )
        }
        if (state.loaded && state.items.isEmpty()) {
            EmptyState(
                title = stringResource(Res.string.exports_title),
                body = stringResource(Res.string.exports_empty),
                modifier = Modifier.testTag("exports.empty"),
            )
        }
        if (state.items.isNotEmpty()) {
            val (active, past) = state.items.partition { it.status.isActive }
            Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.testTag("exports.list")) {
                active.forEach { export -> ActiveExportCard(export, state, onIntent) }
                if (past.isNotEmpty()) {
                    Text(
                        stringResource(Res.string.acct_exports_last_30),
                        style = AppTheme.type.body.copy(fontWeight = FontWeight.Bold),
                        color = AppTheme.colors.ink,
                        modifier = Modifier.padding(top = 4.dp).semantics { heading() },
                    )
                    past.forEach { export -> ExportRow(export, state, onIntent) }
                }
            }
        }
        if (state.hasMore) {
            SecondaryButton(
                stringResource(Res.string.exports_more),
                { onIntent(ExportsIntent.ShowMore) },
                Modifier.testTag("exports.more"),
                loading = state.loadingMore,
            )
        }
        AppTextButton(
            stringResource(Res.string.exports_refresh),
            { onIntent(ExportsIntent.Refresh) },
            Modifier.testTag("exports.refresh"),
            enabled = !state.loading,
        )
    }
}

/** An export still being produced: its kind, when it was asked for and three steps of progress. */
@Composable
private fun ActiveExportCard(export: Export, state: ExportsState, onIntent: (ExportsIntent) -> Unit) {
    val running = export.status == ExportStatus.Running
    val statusText = stringResource(export.status.label())
    CalmCard(
        padding = PaddingValues(18.dp),
        onClick = { onIntent(ExportsIntent.RowClicked(export.id)) },
        modifier = Modifier.testTag("exports.row.${export.id}"),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            CalmIconTile(AppIcons.AccFolderZip, AppTheme.money.pending, AppTheme.money.pendingContainer, size = 48.dp, iconSize = 24.dp, radius = 15.dp)
            Column(Modifier.weight(1f)) {
                Text(export.kindText(state.walletNames), style = AppTheme.type.title.copy(fontSize = 16.sp, fontWeight = FontWeight.ExtraBold), color = AppTheme.colors.ink)
                Text(
                    stringResource(Res.string.export_requested_at, export.requestedAt.shown(state.timeZoneId)),
                    style = AppTheme.type.caption.copy(fontSize = 12.5.sp),
                    color = AppTheme.colors.ink2,
                )
            }
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.padding(top = 16.dp).semantics(mergeDescendants = true) { contentDescription = statusText },
        ) {
            ProgressStep(stringResource(Res.string.acct_export_waiting), if (running) AppTheme.money.positive else AppTheme.money.pending, Modifier.weight(1f))
            ProgressStep(
                stringResource(Res.string.acct_export_preparing),
                if (running) AppTheme.money.pending else AppTheme.colors.ink2,
                Modifier.weight(1f),
                filled = running,
            )
            ProgressStep(stringResource(Res.string.acct_export_ready), AppTheme.colors.ink2, Modifier.weight(1f), filled = false)
        }
        Text(
            stringResource(Res.string.acct_export_email_note),
            style = AppTheme.type.caption.copy(fontSize = 13.sp),
            color = AppTheme.colors.ink2,
            modifier = Modifier.padding(top = 12.dp),
        )
    }
}

@Composable
private fun ProgressStep(label: String, color: Color, modifier: Modifier = Modifier, filled: Boolean = true) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = modifier) {
        Box(Modifier.fillMaxWidth().height(6.dp).background(if (filled) color else AppTheme.colors.sunken, RoundedCornerShape(9.dp)))
        Text(label, style = AppTheme.type.overline.copy(fontSize = 11.5.sp, letterSpacing = TextUnit.Unspecified, fontWeight = FontWeight.Bold), color = color, maxLines = 1)
    }
}

@Composable
private fun ExportRow(export: Export, state: ExportsState, onIntent: (ExportsIntent) -> Unit) {
    val (icon, tint) = when (export.status) {
        ExportStatus.Ready -> AppIcons.AccDownloadDone to AppTheme.money.positive
        ExportStatus.Failed -> AppIcons.Error to AppTheme.money.over
        else -> AppIcons.AccClock to AppTheme.colors.ink2
    }
    CalmCard(
        radius = 20.dp,
        padding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
        onClick = { onIntent(ExportsIntent.RowClicked(export.id)) },
        modifier = Modifier.testTag("exports.row.${export.id}"),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    export.kindText(state.walletNames),
                    style = AppTheme.type.body.copy(fontSize = 14.5.sp, fontWeight = FontWeight.Bold),
                    color = AppTheme.colors.primary,
                )
                Text(
                    formatDate(export.requestedAt, state.timeZoneId) + " · " + stringResource(export.status.label()),
                    style = AppTheme.type.caption.copy(fontSize = 12.5.sp),
                    color = AppTheme.colors.ink2,
                )
            }
            if (export.status == ExportStatus.Ready) {
                Text(
                    stringResource(Res.string.acct_export_open),
                    style = AppTheme.type.caption.copy(fontSize = 13.sp, fontWeight = FontWeight.Bold),
                    color = AppTheme.colors.primary,
                )
            }
        }
    }
}

internal fun sampleExport(id: String, status: ExportStatus, scope: ExportScope = ExportScope.Account, walletId: String? = null, failure: ExportFailure? = null) =
    Export(id, scope, walletId, status, failure, Instant.parse("2026-10-06T10:00:00Z"), null, null, null)

@Preview
@Composable
private fun ExportsScreenPreview() {
    AppTheme {
        ExportsScreen(
            ExportsState(items = listOf(sampleExport("a", ExportStatus.Ready), sampleExport("b", ExportStatus.Queued)), loaded = true),
        ) {}
    }
}
