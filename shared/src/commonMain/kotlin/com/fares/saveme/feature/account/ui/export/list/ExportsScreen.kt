package com.fares.saveme.feature.account.ui.export.list

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Preview
import com.fares.saveme.designsystem.component.AlertTone
import com.fares.saveme.designsystem.component.AppListItem
import com.fares.saveme.designsystem.component.AppScreen
import com.fares.saveme.designsystem.component.AppTextButton
import com.fares.saveme.designsystem.component.EmptyState
import com.fares.saveme.designsystem.component.InlineAlert
import com.fares.saveme.designsystem.component.ListTrailing
import com.fares.saveme.designsystem.component.PrimaryButton
import com.fares.saveme.designsystem.component.SecondaryButton
import com.fares.saveme.designsystem.theme.AppTheme
import com.fares.saveme.feature.account.domain.export.Export
import com.fares.saveme.feature.account.domain.export.ExportFailure
import com.fares.saveme.feature.account.domain.export.ExportScope
import com.fares.saveme.feature.account.domain.export.ExportStatus
import com.fares.saveme.feature.account.ui.AccountBanner
import com.fares.saveme.feature.account.ui.BackBar
import com.fares.saveme.feature.account.ui.export.kindText
import com.fares.saveme.feature.account.ui.export.label
import com.fares.saveme.feature.account.ui.export.shown
import com.fares.saveme.feature.account.ui.text
import org.jetbrains.compose.resources.stringResource
import saveme.shared.generated.resources.Res
import saveme.shared.generated.resources.action_retry
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
    AppScreen(topBar = { BackBar(stringResource(Res.string.exports_title)) { onIntent(ExportsIntent.Back) } }) {
        PrimaryButton(stringResource(Res.string.exports_new), { onIntent(ExportsIntent.NewClicked) }, Modifier.testTag("exports.new"))
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
            Column(Modifier.fillMaxWidth().background(AppTheme.colors.surface, AppTheme.shapes.card).testTag("exports.list")) {
                state.items.forEach { export ->
                    AppListItem(
                        headline = export.kindText(state.walletNames),
                        supporting = stringResource(export.status.label()) + "\n" +
                            stringResource(Res.string.export_requested_at, export.requestedAt.shown(state.timeZoneId)),
                        trailing = ListTrailing.Chevron,
                        modifier = Modifier.testTag("exports.row.${export.id}"),
                        onClick = { onIntent(ExportsIntent.RowClicked(export.id)) },
                    )
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
