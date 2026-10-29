package com.fares.saveme.feature.recurring.ui.list

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.fares.saveme.designsystem.component.AlertTone
import com.fares.saveme.designsystem.component.CalmCircleButton
import com.fares.saveme.designsystem.component.CalmScreen
import com.fares.saveme.designsystem.component.EmptyState
import com.fares.saveme.designsystem.component.InlineAlert
import com.fares.saveme.designsystem.component.Skeleton
import com.fares.saveme.designsystem.component.SyncBanner
import com.fares.saveme.designsystem.component.SyncBannerState
import com.fares.saveme.designsystem.icon.AppIcons
import com.fares.saveme.designsystem.theme.AppTheme
import com.fares.saveme.feature.recurring.domain.model.RuleView
import com.fares.saveme.feature.recurring.domain.model.OccurrenceState
import com.fares.saveme.feature.recurring.domain.model.RecurringSyncState
import com.fares.saveme.feature.recurring.ui.CalmDueCard
import com.fares.saveme.feature.recurring.ui.CalmOccurrenceCard
import com.fares.saveme.feature.recurring.ui.CalmRuleTile
import com.fares.saveme.feature.recurring.ui.CalmTileRow
import com.fares.saveme.feature.recurring.ui.CalmWeekStrip
import com.fares.saveme.feature.recurring.ui.RecurringHeading
import com.fares.saveme.feature.recurring.ui.rememberToday
import kotlinx.datetime.LocalDate
import org.jetbrains.compose.resources.stringResource
import saveme.shared.generated.resources.Res
import saveme.shared.generated.resources.cd_back
import saveme.shared.generated.resources.recurring_add
import saveme.shared.generated.resources.recurring_all_wallets
import saveme.shared.generated.resources.recurring_calm_needs_you
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
    CalmScreen(
        title = state.walletName ?: stringResource(Res.string.recurring_title),
        onBack = { onIntent(RecurringIntent.Back) },
        backDescription = stringResource(Res.string.cd_back),
        scrollable = false,
        actions = {
            if (state.canAdd) {
                CalmCircleButton(
                    icon = AppIcons.Plus,
                    contentDescription = stringResource(Res.string.recurring_cd_add),
                    onClick = { onIntent(RecurringIntent.AddClicked) },
                    modifier = Modifier.testTag("recurring.add"),
                    tint = AppTheme.colors.onPrimary,
                    container = AppTheme.colors.primary,
                    bordered = false,
                )
            }
        },
        modifier = Modifier.testTag("recurring.list"),
    ) {
        PullToRefreshBox(
            isRefreshing = state.refreshing,
            onRefresh = { onIntent(RecurringIntent.PullToRefresh) },
            modifier = Modifier.weight(1f).fillMaxWidth(),
        ) {
            RecurringContent(state, onIntent)
        }
    }
}

@Composable
private fun SubTitle(text: String) {
    Text(text, style = AppTheme.type.label, color = AppTheme.colors.ink2)
}

@Composable
private fun RecurringContent(state: RecurringState, onIntent: (RecurringIntent) -> Unit) {
    val today = rememberToday()
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 24.dp),
    ) {
        item(key = "week") { WeekStrip(state, today) }
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
                dueSection(state, today, onIntent)
                upcomingSection(state, onIntent)
                rulesSection(state, onIntent)
            }
        }
    }
}

@Composable
private fun WeekStrip(state: RecurringState, today: LocalDate) {
    val pending = AppTheme.money.pending
    val over = AppTheme.money.over
    val primary = AppTheme.colors.primary
    val dots = buildMap<LocalDate, Color> {
        (state.upcoming).forEach { put(it.shownDate, primary) }
        (state.due).forEach { put(it.shownDate, if (it.state == OccurrenceState.Missed) over else pending) }
    }
    CalmWeekStrip(today, dots)
}

private fun LazyListScope.dueSection(state: RecurringState, today: LocalDate, onIntent: (RecurringIntent) -> Unit) {
    if (state.due.isEmpty()) return
    val walletByRule = state.rules.associate { it.id to it.walletName }
    item(key = "due-title") { RecurringHeading(stringResource(Res.string.recurring_calm_needs_you, state.due.size), Modifier.padding(top = 4.dp)) }
    state.due.forEachIndexed { index, view ->
        item(key = "due-${view.id}") {
            CalmDueCard(
                view,
                walletName = walletByRule[view.occurrence.ruleId],
                today = today,
                actions = view.canWrite && !view.confirmationWaiting && view.syncState.let { it == RecurringSyncState.None || it == RecurringSyncState.Waiting },
                busy = view.id in state.busyIds,
                onOpen = { onIntent(RecurringIntent.OccurrenceClicked(view.id)) },
                onConfirm = { onIntent(RecurringIntent.ConfirmOccurrence(view.id)) },
                onSkip = { onIntent(RecurringIntent.SkipOccurrence(view.id)) },
                modifier = Modifier.testTag("recurring.due.$index"),
                tag = { Modifier.testTag("recurring.due.$index.$it") },
            )
        }
    }
}

private fun LazyListScope.upcomingSection(state: RecurringState, onIntent: (RecurringIntent) -> Unit) {
    if (state.upcoming.isEmpty()) return
    item(key = "upcoming-title") { RecurringHeading(stringResource(Res.string.recurring_section_upcoming), Modifier.padding(top = 4.dp)) }
    state.upcoming.forEachIndexed { index, view ->
        item(key = "upcoming-${view.id}") {
            CalmOccurrenceCard(
                view,
                modifier = Modifier.testTag("recurring.upcoming.$index"),
                onClick = { onIntent(RecurringIntent.OccurrenceClicked(view.id)) },
            )
        }
    }
}

private fun LazyListScope.rulesSection(state: RecurringState, onIntent: (RecurringIntent) -> Unit) {
    if (state.rules.isEmpty()) return
    item(key = "rules-title") { RecurringHeading(stringResource(Res.string.recurring_section_rules), Modifier.padding(top = 4.dp)) }
    var index = 0
    val groups = listOf(
        Res.string.recurring_section_active to state.activeRules,
        Res.string.recurring_section_paused to state.pausedRules,
        Res.string.recurring_section_ended to state.endedRules,
    )
    groups.forEach { (title, rules) ->
        if (rules.isEmpty()) return@forEach
        if (groups.count { it.second.isNotEmpty() } > 1) item(key = "rules-${title.key}") { SubTitle(stringResource(title)) }
        // In the all-wallets mode each wallet's rules sit under its name.
        val byWallet: List<Pair<String?, List<RuleView>>> =
            if (state.allWallets) rules.groupBy { it.walletName }.toList().sortedBy { it.first.orEmpty() } else listOf(null to rules)
        byWallet.forEach { (wallet, list) ->
            if (state.allWallets && wallet != null) {
                item(key = "rules-${title.key}-wallet-$wallet") {
                    Text(wallet, style = AppTheme.type.caption, color = AppTheme.colors.caption)
                }
            }
            list.chunked(2).forEach { pair ->
                val firstIndex = index
                index += pair.size
                item(key = "rules-${pair.first().id}") {
                    CalmTileRow(
                        first = { tile ->
                            val view = pair[0]
                            CalmRuleTile(
                                view, state.paidCount(view.id),
                                tile.testTag("recurring.rule.$firstIndex"),
                                onClick = { onIntent(RecurringIntent.RuleClicked(view.id)) },
                            )
                        },
                        second = if (pair.size > 1) {
                            { tile ->
                                val view = pair[1]
                                CalmRuleTile(
                                    view, state.paidCount(view.id),
                                    tile.testTag("recurring.rule.${firstIndex + 1}"),
                                    onClick = { onIntent(RecurringIntent.RuleClicked(view.id)) },
                                )
                            }
                        } else {
                            null
                        },
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
