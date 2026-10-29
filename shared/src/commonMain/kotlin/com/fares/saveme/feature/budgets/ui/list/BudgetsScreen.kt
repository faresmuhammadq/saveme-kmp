package com.fares.saveme.feature.budgets.ui.list

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fares.saveme.designsystem.component.AlertTone
import com.fares.saveme.designsystem.component.CalmCircleButton
import com.fares.saveme.designsystem.component.CalmScreen
import com.fares.saveme.designsystem.component.CalmTitle
import com.fares.saveme.designsystem.component.InlineAlert
import com.fares.saveme.designsystem.component.PrimaryButton
import com.fares.saveme.designsystem.component.Skeleton
import com.fares.saveme.designsystem.component.SyncBanner
import com.fares.saveme.designsystem.component.SyncBannerState
import com.fares.saveme.designsystem.format.formatDateTime
import com.fares.saveme.designsystem.format.formatDayMonth
import com.fares.saveme.designsystem.icon.AppIcons
import com.fares.saveme.designsystem.icon.BudgetsArchive
import com.fares.saveme.designsystem.theme.AppTheme
import com.fares.saveme.feature.budgets.ui.BudgetRowUi
import com.fares.saveme.feature.budgets.ui.RowFigures
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import saveme.shared.generated.resources.Res
import saveme.shared.generated.resources.budgets_calm_archived_link
import saveme.shared.generated.resources.budgets_cd_add
import saveme.shared.generated.resources.budgets_days_left
import saveme.shared.generated.resources.budgets_empty
import saveme.shared.generated.resources.budgets_last_day
import saveme.shared.generated.resources.budgets_pending_start
import saveme.shared.generated.resources.budgets_period_line
import saveme.shared.generated.resources.budgets_period_range
import saveme.shared.generated.resources.budgets_refresh_failed
import saveme.shared.generated.resources.budgets_set
import saveme.shared.generated.resources.budgets_title
import saveme.shared.generated.resources.budgets_updated_at
import kotlin.time.Instant

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BudgetsScreen(state: BudgetsState, onIntent: (BudgetsIntent) -> Unit) {
    CalmScreen(
        title = null,
        scrollable = false,
        titleSlot = { BudgetsHeader(state, onIntent) },
    ) {
        PullToRefreshBox(
            isRefreshing = state.refreshing,
            onRefresh = { onIntent(BudgetsIntent.Refresh) },
            modifier = Modifier.weight(1f).fillMaxWidth(),
        ) {
            BudgetsContent(state, onIntent)
        }
    }
}

/** Title, the period's dates and days left (FR-016), and the round add button. */
@Composable
private fun BudgetsHeader(state: BudgetsState, onIntent: (BudgetsIntent) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(top = 10.dp)) {
        Column(Modifier.weight(1f)) {
            CalmTitle(stringResource(Res.string.budgets_title))
            state.period?.let { period ->
                val range = stringResource(Res.string.budgets_period_range, formatDayMonth(period.start), formatDayMonth(period.end))
                val left = when {
                    period.daysLeft == null -> null
                    period.lastDay -> stringResource(Res.string.budgets_last_day)
                    else -> pluralStringResource(Res.plurals.budgets_days_left, period.daysLeft, period.daysLeft)
                }
                Text(
                    if (left == null) range else stringResource(Res.string.budgets_period_line, range, left),
                    style = AppTheme.type.label.copy(fontSize = 13.5.sp, fontWeight = FontWeight.Medium),
                    color = AppTheme.colors.ink2,
                    modifier = Modifier.padding(top = 1.dp).testTag("budgets.period"),
                )
            }
        }
        CalmCircleButton(
            AppIcons.Plus, stringResource(Res.string.budgets_cd_add), { onIntent(BudgetsIntent.AddClicked) },
            tint = AppTheme.colors.primary,
            modifier = Modifier.testTag("budgets.add"),
        )
    }
}

@Composable
private fun BudgetsContent(state: BudgetsState, onIntent: (BudgetsIntent) -> Unit) {
    var archivedOpen by rememberSaveable { mutableStateOf(false) }
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.s),
        modifier = Modifier.fillMaxSize().testTag("budgets.list"),
        contentPadding = PaddingValues(bottom = AppTheme.spacing.l),
    ) {
        if (state.offline && state.waitingCount > 0) item { SyncBanner(SyncBannerState.Offline(state.waitingCount)) }
        if (state.refreshFailed) {
            item { InlineAlert(AlertTone.Warning, stringResource(Res.string.budgets_refresh_failed), Modifier.testTag("budgets.refreshFailed")) }
        }
        state.pendingDay?.let { pending ->
            item {
                Text(
                    stringResource(Res.string.budgets_pending_start, formatDayMonth(pending.from), pending.day),
                    style = AppTheme.type.caption,
                    color = AppTheme.colors.caption,
                    modifier = Modifier.testTag("budgets.pending"),
                )
            }
        }
        when {
            !state.loaded -> items(3) { Skeleton(Modifier.fillMaxWidth().height(96.dp), AppTheme.shapes.hero) }
            state.isEmpty -> item { EmptyBudgets(onIntent) }
            else -> {
                if (state.active.isNotEmpty()) {
                    val heroIndex = state.active.indexOfFirst { it.categoryName == null && it.figures != null }
                    if (heroIndex >= 0) {
                        val hero = state.active[heroIndex]
                        item(key = "active-${hero.id}") { BudgetHero(hero, hero.figures!!, "budgets.active.$heroIndex", onIntent) }
                    }
                    tiles(state.active.withIndex().filter { it.index != heroIndex }, "active", onIntent)
                }
                if (state.archived.isNotEmpty()) {
                    item { ArchivedLink(state.archived.size, archivedOpen) { archivedOpen = !archivedOpen } }
                    if (archivedOpen) tiles(state.archived.withIndex().toList(), "archived", onIntent)
                }
            }
        }
        if (state.offline && state.updatedAt != null) {
            item {
                Text(
                    stringResource(Res.string.budgets_updated_at, formatDateTime(state.updatedAt, state.timeZoneId)),
                    style = AppTheme.type.caption,
                    color = AppTheme.colors.caption,
                    modifier = Modifier.testTag("budgets.updatedAt"),
                )
            }
        }
    }
}

/** "Archived budgets · N": opens and closes the archived tiles under it. */
@Composable
private fun ArchivedLink(count: Int, open: Boolean, onToggle: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("budgets.archivedHeader")
            .clickable(role = Role.Button, onClick = onToggle)
            .padding(start = 4.dp, end = 4.dp, top = 4.dp, bottom = 16.dp),
    ) {
        Icon(AppIcons.BudgetsArchive, contentDescription = null, tint = AppTheme.colors.primary, modifier = Modifier.size(22.dp))
        Text(
            stringResource(Res.string.budgets_calm_archived_link, count),
            style = AppTheme.type.label.copy(fontSize = 13.5.sp, fontWeight = FontWeight.Bold),
            color = AppTheme.colors.primary,
        )
    }
}

/** Budget tiles two to a row; [rows] keep their index in the list they came from, which the test tags use. */
private fun LazyListScope.tiles(rows: List<IndexedValue<BudgetRowUi>>, group: String, onIntent: (BudgetsIntent) -> Unit) {
    items(rows.chunked(2), key = { pair -> "$group-${pair.first().value.id}" }) { pair ->
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.height(IntrinsicSize.Min)) {
            pair.forEach { (index, row) -> BudgetTile(row, "budgets.$group.$index", onIntent, Modifier.weight(1f).fillMaxHeight()) }
            if (pair.size == 1) Spacer(Modifier.weight(1f))
        }
    }
}

@Composable
private fun EmptyBudgets(onIntent: (BudgetsIntent) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.s), modifier = Modifier.fillMaxWidth().testTag("budgets.empty")) {
        Text(stringResource(Res.string.budgets_empty), style = AppTheme.type.body, color = AppTheme.colors.ink2)
        PrimaryButton(stringResource(Res.string.budgets_set), { onIntent(BudgetsIntent.AddClicked) }, modifier = Modifier.testTag("budgets.empty.add"))
    }
}

@Preview
@Composable
private fun BudgetsScreenPreview() {
    AppTheme {
        BudgetsScreen(
            BudgetsState(
                period = PeriodUi(kotlinx.datetime.LocalDate(2026, 9, 25), kotlinx.datetime.LocalDate(2026, 10, 24), 22),
                active = listOf(BudgetRowUi("b1", "Groceries", "EGP", 600_000, RowFigures(350_000, 1_312_500, emptyList(), false), false, null)),
                loaded = true,
                updatedAt = Instant.parse("2026-10-02T10:00:00Z"),
            ),
            {},
        )
    }
}
