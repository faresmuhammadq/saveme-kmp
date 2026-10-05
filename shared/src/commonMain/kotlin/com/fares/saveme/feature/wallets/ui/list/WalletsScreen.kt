package com.fares.saveme.feature.wallets.ui.list

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.fares.saveme.designsystem.component.AlertTone
import com.fares.saveme.designsystem.component.AppFab
import com.fares.saveme.designsystem.component.AppScreen
import com.fares.saveme.designsystem.component.BalanceHero
import com.fares.saveme.designsystem.component.EmptyState
import com.fares.saveme.designsystem.component.InlineAlert
import com.fares.saveme.designsystem.component.Skeleton
import com.fares.saveme.designsystem.component.SyncBanner
import com.fares.saveme.designsystem.component.SyncBannerState
import com.fares.saveme.designsystem.component.WalletCard
import com.fares.saveme.designsystem.component.WalletCardStatus
import com.fares.saveme.designsystem.component.WalletCardStatusKind
import com.fares.saveme.designsystem.format.formatDateTime
import com.fares.saveme.designsystem.icon.AppIcons
import com.fares.saveme.designsystem.theme.AppTheme
import com.fares.saveme.feature.wallets.domain.model.CurrencyTotal
import com.fares.saveme.feature.wallets.domain.model.WalletRole
import com.fares.saveme.feature.wallets.domain.model.WalletType
import com.fares.saveme.feature.wallets.ui.WalletsBackBar
import com.fares.saveme.feature.wallets.ui.cardType
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import saveme.shared.generated.resources.Res
import saveme.shared.generated.resources.action_done
import saveme.shared.generated.resources.error_feature_not_entitled
import saveme.shared.generated.resources.role_editor
import saveme.shared.generated.resources.role_owner
import saveme.shared.generated.resources.role_viewer
import saveme.shared.generated.resources.wallet_status_clash
import saveme.shared.generated.resources.wallet_status_not_saved
import saveme.shared.generated.resources.wallet_status_waiting
import saveme.shared.generated.resources.leave_done
import saveme.shared.generated.resources.wallets_archived
import saveme.shared.generated.resources.wallets_empty_action
import saveme.shared.generated.resources.wallets_empty_body
import saveme.shared.generated.resources.wallets_empty_title
import saveme.shared.generated.resources.wallets_new
import saveme.shared.generated.resources.wallets_refresh_failed
import saveme.shared.generated.resources.wallets_title
import saveme.shared.generated.resources.wallets_total_caption
import saveme.shared.generated.resources.wallets_total_label
import saveme.shared.generated.resources.wallets_updated_at
import kotlin.time.Instant

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WalletsScreen(state: WalletsState, onIntent: (WalletsIntent) -> Unit) {
    Box(Modifier.fillMaxSize().testTag("wallets.list")) {
        AppScreen(
            scrollable = false,
            topBar = { WalletsBackBar(stringResource(Res.string.wallets_title)) { onIntent(WalletsIntent.Back) } },
        ) {
            PullToRefreshBox(
                isRefreshing = state.refreshing,
                onRefresh = { onIntent(WalletsIntent.Refresh) },
                modifier = Modifier.weight(1f).fillMaxWidth(),
            ) {
                WalletsContent(state, onIntent)
            }
        }
        AppFab(
            icon = AppIcons.Plus,
            contentDescription = stringResource(Res.string.wallets_new),
            onClick = { onIntent(WalletsIntent.CreateClicked) },
            text = stringResource(Res.string.wallets_new),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .navigationBarsPadding()
                .padding(AppTheme.spacing.m)
                .testTag("wallets.create"),
        )
    }
}

@Composable
private fun WalletsContent(state: WalletsState, onIntent: (WalletsIntent) -> Unit) {
    Column(
        verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.s),
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 88.dp),
    ) {
        if (state.offline && state.waitingCount > 0) SyncBanner(SyncBannerState.Offline(state.waitingCount))
        if (state.notEntitledNotice) {
            InlineAlert(
                tone = AlertTone.Warning,
                text = stringResource(Res.string.error_feature_not_entitled),
                modifier = Modifier.testTag("wallets.notEntitled"),
                actionLabel = stringResource(Res.string.action_done),
                onAction = { onIntent(WalletsIntent.DismissNotice) },
            )
        }
        state.leftNotice?.let { name ->
            InlineAlert(
                tone = AlertTone.Info,
                text = stringResource(Res.string.leave_done, name),
                modifier = Modifier.testTag("wallets.leftNotice"),
                actionLabel = stringResource(Res.string.action_done),
                onAction = { onIntent(WalletsIntent.DismissLeftNotice) },
            )
        }
        if (state.refreshFailed) InlineAlert(AlertTone.Warning, stringResource(Res.string.wallets_refresh_failed))
        when {
            !state.loaded -> repeat(3) { Skeleton(Modifier.fillMaxWidth().height(72.dp), AppTheme.shapes.card) }
            state.isEmpty -> EmptyState(
                title = stringResource(Res.string.wallets_empty_title),
                body = stringResource(Res.string.wallets_empty_body),
                modifier = Modifier.testTag("wallets.empty"),
                actionLabel = stringResource(Res.string.wallets_empty_action),
                onAction = { onIntent(WalletsIntent.CreateClicked) },
            )
            else -> {
                state.totals.forEach { total -> TotalHero(total) }
                state.active.forEachIndexed { index, card -> Card(card, "wallets.card.$index", onIntent) }
                if (state.archived.isNotEmpty()) {
                    Text(
                        stringResource(Res.string.wallets_archived),
                        style = AppTheme.type.label,
                        color = AppTheme.colors.ink2,
                        modifier = Modifier.padding(top = AppTheme.spacing.xs).semantics { heading() }.testTag("wallets.archivedHeader"),
                    )
                    state.archived.forEachIndexed { index, card -> Card(card, "wallets.archived.$index", onIntent) }
                }
            }
        }
        if (state.offline && state.updatedAt != null) {
            Text(
                stringResource(Res.string.wallets_updated_at, formatDateTime(state.updatedAt, state.timeZoneId)),
                style = AppTheme.type.caption,
                color = AppTheme.colors.caption,
                modifier = Modifier.testTag("wallets.updatedAt"),
            )
        }
    }
}

@Composable
private fun TotalHero(total: CurrencyTotal) {
    BalanceHero(
        label = stringResource(Res.string.wallets_total_label),
        amountMinor = total.totalMinor,
        currency = total.currency,
        modifier = Modifier.testTag("wallets.total.${total.currency}"),
        caption = pluralStringResource(Res.plurals.wallets_total_caption, total.walletCount, total.walletCount),
    )
}

@Composable
private fun Card(card: WalletCardUi, tag: String, onIntent: (WalletsIntent) -> Unit) {
    val status = card.status?.let { kind ->
        when (kind) {
            WalletStatusUi.Waiting -> WalletCardStatus(WalletCardStatusKind.Waiting, stringResource(Res.string.wallet_status_waiting))
            WalletStatusUi.Changed -> WalletCardStatus(WalletCardStatusKind.Changed, stringResource(Res.string.wallet_status_clash))
            WalletStatusUi.NotSaved -> WalletCardStatus(WalletCardStatusKind.NotSaved, stringResource(Res.string.wallet_status_not_saved))
        }
    }
    WalletCard(
        name = card.name,
        type = card.type.cardType(),
        amountMinor = card.amountMinor,
        currency = card.currency,
        modifier = Modifier.testTag(tag),
        iconKey = card.iconKey,
        accentKey = card.accentKey,
        status = status,
        archived = card.archived,
        roleLabel = card.role?.let { role ->
            stringResource(
                when (role) {
                    WalletRole.Owner -> Res.string.role_owner
                    WalletRole.Editor -> Res.string.role_editor
                    WalletRole.Viewer -> Res.string.role_viewer
                },
            )
        },
        onClick = { onIntent(WalletsIntent.WalletClicked(card.id)) },
        statusModifier = Modifier.testTag("$tag.status"),
    )
}

private val previewCards = listOf(
    WalletCardUi("1", "Pocket", WalletType.Cash, 25_000, "EGP", "cash", "#2C7A5B", WalletStatusUi.Waiting, false, null),
    WalletCardUi("2", "Visa", WalletType.CreditCard, -100_000, "EGP", null, null, null, false, null),
)

@Preview
@Composable
private fun WalletsScreenPreview() {
    AppTheme {
        WalletsScreen(
            WalletsState(
                active = previewCards,
                totals = listOf(CurrencyTotal("EGP", -75_000, 2)),
                loaded = true,
                updatedAt = Instant.parse("2026-10-02T10:00:00Z"),
            ),
            {},
        )
    }
}
