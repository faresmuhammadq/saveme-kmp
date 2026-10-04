package com.fares.saveme.feature.recurring.ui.list

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.fares.saveme.designsystem.component.AlertTone
import com.fares.saveme.designsystem.component.AppFab
import com.fares.saveme.designsystem.component.AppScreen
import com.fares.saveme.designsystem.component.EmptyState
import com.fares.saveme.designsystem.component.InlineAlert
import com.fares.saveme.designsystem.component.Skeleton
import com.fares.saveme.designsystem.component.SyncBanner
import com.fares.saveme.designsystem.component.SyncBannerState
import com.fares.saveme.designsystem.icon.AppIcons
import com.fares.saveme.designsystem.theme.AppTheme
import com.fares.saveme.feature.recurring.domain.model.RuleView
import com.fares.saveme.feature.recurring.ui.OccurrenceListRow
import com.fares.saveme.feature.recurring.ui.RecurringBackBar
import com.fares.saveme.feature.recurring.ui.RuleListRow
import org.jetbrains.compose.resources.stringResource
import saveme.shared.generated.resources.Res
import saveme.shared.generated.resources.recurring_add
import saveme.shared.generated.resources.recurring_all_wallets
import saveme.shared.generated.resources.recurring_cd_add
import saveme.shared.generated.resources.recurring_empty
import saveme.shared.generated.resources.recurring_empty_hint
import saveme.shared.generated.resources.recurring_empty_viewer
import saveme.shared.generated.resources.recurring_no_upcoming
import saveme.shared.generated.resources.recurring_refresh_failed
import saveme.shared.generated.resources.recurring_section_active
import saveme.shared.generated.resources.recurring_section_due
import saveme.shared.generated.resources.recurring_section_ended
import saveme.shared.generated.resources.recurring_section_paused
import saveme.shared.generated.resources.recurring_section_rules
import saveme.shared.generated.resources.recurring_section_upcoming
import saveme.shared.generated.resources.recurring_title

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecurringScreen(state: RecurringState, onIntent: (RecurringIntent) -> Unit) {
    Box(Modifier.fillMaxSize().testTag("recurring.list")) {
        AppScreen(
            scrollable = false,
            topBar = {
                RecurringBackBar(state.walletName ?: stringResource(Res.string.recurring_title)) { onIntent(RecurringIntent.Back) }
            },
        ) {
            PullToRefreshBox(
                isRefreshing = state.refreshing,
                onRefresh = { onIntent(RecurringIntent.PullToRefresh) },
                modifier = Modifier.weight(1f).fillMaxWidth(),
            ) {
                RecurringContent(state, onIntent)
            }
        }
        if (state.canAdd) {
            AppFab(
                icon = AppIcons.Plus,
                contentDescription = stringResource(Res.string.recurring_cd_add),
                onClick = { onIntent(RecurringIntent.AddClicked) },
                text = stringResource(Res.string.recurring_add),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .navigationBarsPadding()
                    .padding(AppTheme.spacing.m)
                    .testTag("recurring.add"),
            )
        }
    }
}

@Composable
private fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(text, style = AppTheme.type.title, color = AppTheme.colors.ink, modifier = modifier.padding(top = AppTheme.spacing.xs))
}

@Composable
private fun SubTitle(text: String) {
    Text(text, style = AppTheme.type.label, color = AppTheme.colors.ink2)
}

@Composable
private fun RecurringContent(state: RecurringState, onIntent: (RecurringIntent) -> Unit) {
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.xs),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 88.dp),
    ) {
        if (state.offline && state.waitingCount > 0) item { SyncBanner(SyncBannerState.Offline(state.waitingCount)) }
        if (state.refreshFailed) {
            item { InlineAlert(AlertTone.Warning, stringResource(Res.string.recurring_refresh_failed), Modifier.testTag("recurring.refreshFailed")) }
        }
        when {
            state.loading -> items(4) { Skeleton(Modifier.fillMaxWidth().height(AppTheme.sizes.rowTwoLine)) }
            state.isEmpty -> item {
                EmptyState(
                    title = stringResource(Res.string.recurring_empty),
                    body = stringResource(if (state.canAdd) Res.string.recurring_empty_hint else Res.string.recurring_empty_viewer),
                    modifier = Modifier.testTag("recurring.empty"),
                    actionLabel = if (state.canAdd) stringResource(Res.string.recurring_add) else null,
                    onAction = if (state.canAdd) ({ onIntent(RecurringIntent.AddClicked) }) else null,
                )
            }
            else -> {
                dueSection(state, onIntent)
                upcomingSection(state, onIntent)
                rulesSection(state, onIntent)
            }
        }
    }
}

private fun LazyListScope.dueSection(state: RecurringState, onIntent: (RecurringIntent) -> Unit) {
    if (state.due.isEmpty()) return
    item(key = "due-title") { SectionTitle(stringResource(Res.string.recurring_section_due)) }
    state.due.forEachIndexed { index, view ->
        item(key = "due-${view.id}") {
            OccurrenceListRow(
                view,
                modifier = Modifier.testTag("recurring.due.$index"),
                onClick = { onIntent(RecurringIntent.OccurrenceClicked(view.id)) },
            )
        }
    }
}

private fun LazyListScope.upcomingSection(state: RecurringState, onIntent: (RecurringIntent) -> Unit) {
    item(key = "upcoming-title") { SectionTitle(stringResource(Res.string.recurring_section_upcoming)) }
    if (state.upcoming.isEmpty()) {
        item(key = "upcoming-none") {
            Text(stringResource(Res.string.recurring_no_upcoming), style = AppTheme.type.body, color = AppTheme.colors.ink2)
        }
    }
    state.upcoming.forEachIndexed { index, view ->
        item(key = "upcoming-${view.id}") {
            OccurrenceListRow(
                view,
                modifier = Modifier.testTag("recurring.upcoming.$index"),
                onClick = { onIntent(RecurringIntent.OccurrenceClicked(view.id)) },
            )
        }
    }
}

private fun LazyListScope.rulesSection(state: RecurringState, onIntent: (RecurringIntent) -> Unit) {
    if (state.rules.isEmpty()) return
    item(key = "rules-title") { SectionTitle(stringResource(Res.string.recurring_section_rules)) }
    var index = 0
    val groups = listOf(
        Res.string.recurring_section_active to state.activeRules,
        Res.string.recurring_section_paused to state.pausedRules,
        Res.string.recurring_section_ended to state.endedRules,
    )
    groups.forEach { (title, rules) ->
        if (rules.isEmpty()) return@forEach
        item(key = "rules-${title.key}") { SubTitle(stringResource(title)) }
        // In the all-wallets mode each wallet's rules sit under its name.
        val byWallet: List<Pair<String?, List<RuleView>>> =
            if (state.allWallets) rules.groupBy { it.walletName }.toList().sortedBy { it.first.orEmpty() } else listOf(null to rules)
        byWallet.forEach { (wallet, list) ->
            if (state.allWallets && wallet != null) {
                item(key = "rules-${title.key}-wallet-$wallet") {
                    Text(wallet, style = AppTheme.type.caption, color = AppTheme.colors.caption)
                }
            }
            list.forEach { view ->
                val rowIndex = index++
                item(key = "rule-${view.id}") {
                    RuleListRow(
                        view,
                        modifier = Modifier.testTag("recurring.rule.$rowIndex"),
                        onClick = { onIntent(RecurringIntent.RuleClicked(view.id)) },
                    )
                }
            }
        }
    }
}

@Preview
@Composable
private fun RecurringScreenPreview() {
    AppTheme {
        RecurringScreen(RecurringState(loading = false, canAdd = true, walletName = stringResource(Res.string.recurring_all_wallets)), {})
    }
}
