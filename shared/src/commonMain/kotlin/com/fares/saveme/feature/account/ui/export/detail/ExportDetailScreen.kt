package com.fares.saveme.feature.account.ui.export.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fares.saveme.designsystem.component.AlertTone
import com.fares.saveme.designsystem.component.CalmActionBar
import com.fares.saveme.designsystem.component.CalmCard
import com.fares.saveme.designsystem.component.CalmInfoPill
import com.fares.saveme.designsystem.component.CalmScreen
import com.fares.saveme.designsystem.component.InlineAlert
import com.fares.saveme.designsystem.component.PrimaryButton
import com.fares.saveme.designsystem.icon.AccFolderZip
import com.fares.saveme.designsystem.icon.AccRefresh
import com.fares.saveme.designsystem.icon.AppIcons
import com.fares.saveme.designsystem.theme.AppTheme
import com.fares.saveme.feature.account.domain.export.Export
import com.fares.saveme.feature.account.domain.export.ExportScope
import com.fares.saveme.feature.account.domain.export.ExportStatus
import com.fares.saveme.feature.account.ui.AccountBanner
import com.fares.saveme.feature.account.ui.export.kindText
import com.fares.saveme.feature.account.ui.export.label
import com.fares.saveme.feature.account.ui.export.shown
import com.fares.saveme.feature.account.ui.export.sizeLabel
import com.fares.saveme.feature.account.ui.text
import org.jetbrains.compose.resources.stringResource
import saveme.shared.generated.resources.Res
import saveme.shared.generated.resources.action_retry
import saveme.shared.generated.resources.cd_back
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
    CalmScreen(
        title = null,
        onBack = { onIntent(ExportDetailIntent.Back) },
        backDescription = stringResource(Res.string.cd_back),
        scrollable = false,
        bottomBar = {
            if (export != null) {
                CalmActionBar {
                    when (export.status) {
                        ExportStatus.Ready -> PrimaryButton(
                            stringResource(Res.string.export_open),
                            { onIntent(ExportDetailIntent.OpenDownload) },
                            Modifier.testTag("exportDetail.open"),
                            loading = state.opening,
                        )
                        ExportStatus.Expired -> PrimaryButton(
                            stringResource(Res.string.exports_new),
                            { onIntent(ExportDetailIntent.RequestAgain) },
                            Modifier.testTag("exportDetail.new"),
                        )
                        ExportStatus.Failed -> PrimaryButton(
                            stringResource(Res.string.action_retry),
                            { onIntent(ExportDetailIntent.RequestAgain) },
                            Modifier.testTag("exportDetail.retry"),
                        )
                        else -> Unit
                    }
                    if (state.canCheckAgain) {
                        OutlineButton(
                            stringResource(Res.string.export_check_again),
                            { onIntent(ExportDetailIntent.CheckAgain) },
                            Modifier.testTag("exportDetail.checkAgain"),
                        )
                    }
                }
            }
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
            if (state.message == null) {
                CircularProgressIndicator(
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .size(28.dp)
                        .testTag("exportDetail.progress")
                        .semantics { contentDescription = "" },
                    strokeWidth = 3.dp,
                    color = AppTheme.colors.primary,
                )
            }
            return@CalmScreen
        }
        val statusText = stringResource(export.status.label())
        val tone = when (export.status) {
            ExportStatus.Ready -> AppTheme.money.positive
            ExportStatus.Failed -> AppTheme.money.over
            ExportStatus.Queued, ExportStatus.Running -> AppTheme.money.pending
            else -> AppTheme.colors.ink2
        }
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
            modifier = Modifier.weight(1f).fillMaxWidth().padding(bottom = 40.dp),
        ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(120.dp)) {
                if (export.status.isActive) {
                    CircularProgressIndicator(
                        modifier = Modifier
                            .size(120.dp)
                            .then(
                                if (state.stillPreparing) Modifier else Modifier.testTag("exportDetail.progress").semantics { contentDescription = statusText },
                            ),
                        strokeWidth = 10.dp,
                        color = tone,
                        trackColor = AppTheme.colors.sunken,
                        strokeCap = StrokeCap.Round,
                    )
                } else {
                    Box(Modifier.size(120.dp).border(10.dp, if (export.status == ExportStatus.Ready) tone else AppTheme.colors.sunken, CircleShape))
                }
                Icon(AppIcons.AccFolderZip, contentDescription = null, tint = tone, modifier = Modifier.size(40.dp))
            }
            Text(
                statusText,
                style = AppTheme.type.headline.copy(fontSize = 26.sp),
                color = AppTheme.colors.ink,
                textAlign = TextAlign.Center,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }.testTag("exportDetail.status"),
            )
            val body = when {
                state.stillPreparing -> stringResource(Res.string.export_still_preparing)
                export.status == ExportStatus.Expired -> stringResource(Res.string.export_expired_body)
                export.status == ExportStatus.Failed -> stringResource(export.failure.label())
                else -> null
            }
            body?.let {
                Text(
                    it,
                    style = AppTheme.type.body.copy(lineHeight = 22.sp),
                    color = AppTheme.colors.ink2,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .widthIn(max = 280.dp)
                        .then(if (state.stillPreparing) Modifier.semantics { liveRegion = LiveRegionMode.Polite }.testTag("exportDetail.message") else Modifier),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)) {
                CalmInfoPill(export.kindText(state.walletNames))
                CalmInfoPill(stringResource(Res.string.export_requested_at, export.requestedAt.shown(state.timeZoneId)))
            }
            if (export.status == ExportStatus.Ready) {
                CalmCard(radius = 20.dp, padding = PaddingValues(horizontal = 16.dp, vertical = 14.dp)) {
                    export.sizeBytes?.let {
                        Text(it.sizeLabel(), style = AppTheme.type.body.copy(fontWeight = FontWeight.SemiBold), color = AppTheme.colors.ink, modifier = Modifier.testTag("exportDetail.size"))
                    }
                    export.completedAt?.let {
                        Text(
                            stringResource(Res.string.export_prepared_at, it.shown(state.timeZoneId)),
                            style = AppTheme.type.caption,
                            color = AppTheme.colors.ink2,
                        )
                    }
                    export.expiresAt?.let {
                        Text(
                            stringResource(Res.string.export_until, it.shown(state.timeZoneId)),
                            style = AppTheme.type.caption,
                            color = AppTheme.colors.ink2,
                            modifier = Modifier.testTag("exportDetail.until"),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun OutlineButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(18.dp)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        modifier = modifier
            .fillMaxWidth()
            .height(AppTheme.sizes.button)
            .background(AppTheme.colors.surface, shape)
            .border(1.dp, AppTheme.colors.outlineVariant, shape)
            .clickable(role = Role.Button, onClick = onClick),
    ) {
        Icon(AppIcons.AccRefresh, contentDescription = null, tint = AppTheme.colors.ink, modifier = Modifier.size(22.dp))
        Text(text, style = AppTheme.type.body.copy(fontWeight = FontWeight.Bold), color = AppTheme.colors.ink)
    }
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
