package com.fares.saveme.feature.budgets.ui.list

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.fares.saveme.designsystem.component.AlertTone
import com.fares.saveme.designsystem.component.AppIconButton
import com.fares.saveme.designsystem.component.AppScreen
import com.fares.saveme.designsystem.component.InlineAlert
import com.fares.saveme.designsystem.component.PrimaryButton
import com.fares.saveme.designsystem.component.Skeleton
import com.fares.saveme.designsystem.component.SyncBanner
import com.fares.saveme.designsystem.component.SyncBannerState
import com.fares.saveme.designsystem.format.formatDateTime
import com.fares.saveme.designsystem.format.formatDayMonth
import com.fares.saveme.designsystem.icon.AppIcons
import com.fares.saveme.designsystem.theme.AppTheme
import com.fares.saveme.feature.budgets.ui.BudgetRowContent
import com.fares.saveme.feature.budgets.ui.BudgetRowUi
import com.fares.saveme.feature.budgets.ui.BudgetsBackBar
import com.fares.saveme.feature.budgets.ui.RowFigures
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import saveme.shared.generated.resources.Res
import saveme.shared.generated.resources.budgets_cd_add
import saveme.shared.generated.resources.budgets_cd_open
import saveme.shared.generated.resources.budgets_days_left
import saveme.shared.generated.resources.budgets_empty
import saveme.shared.generated.resources.budgets_last_day
import saveme.shared.generated.resources.budgets_pending_start
import saveme.shared.generated.resources.budgets_period_line
import saveme.shared.generated.resources.budgets_period_range
import saveme.shared.generated.resources.budgets_refresh_failed
import saveme.shared.generated.resources.budgets_section_active
import saveme.shared.generated.resources.budgets_section_archived
import saveme.shared.generated.resources.budgets_set
import saveme.shared.generated.resources.budgets_title
import saveme.shared.generated.resources.budgets_updated_at
import kotlin.time.Instant

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BudgetsScreen(state: BudgetsState, onIntent: (BudgetsIntent) -> Unit) {
    AppScreen(
        scrollable = false,
        topBar = {
            BudgetsBackBar(
                title = stringResource(Res.string.budgets_title),
                onBack = { onIntent(BudgetsIntent.Back) },
                actions = {
                    AppIconButton(
                        AppIcons.Plus, stringResource(Res.string.budgets_cd_add), { onIntent(BudgetsIntent.AddClicked) },
                        modifier = Modifier.testTag("budgets.add"),
                    )
                },
            )
        },
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

@Composable
private fun BudgetsContent(state: BudgetsState, onIntent: (BudgetsIntent) -> Unit) {
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.xs),
        modifier = Modifier.fillMaxSize().testTag("budgets.list"),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = AppTheme.spacing.l),
    ) {
        if (state.offline && state.waitingCount > 0) item { SyncBanner(SyncBannerState.Offline(state.waitingCount)) }
        if (state.refreshFailed) {
            item { InlineAlert(AlertTone.Warning, stringResource(Res.string.budgets_refresh_failed), Modifier.testTag("budgets.refreshFailed")) }
        }
        periodLines(state)
        when {
            !state.loaded -> items(3) { Skeleton(Modifier.fillMaxWidth().height(96.dp), AppTheme.shapes.card) }
            state.isEmpty -> item { EmptyBudgets(onIntent) }
            else -> {
                if (state.active.isNotEmpty()) {
                    item { SectionTitle(stringResource(Res.string.budgets_section_active), Modifier.testTag("budgets.activeHeader")) }
                    itemsIndexed(state.active, key = { _, row -> "active-${row.id}" }) { index, row -> Card(row, "budgets.active.$index", onIntent) }
                }
                if (state.archived.isNotEmpty()) {
                    item { SectionTitle(stringResource(Res.string.budgets_section_archived), Modifier.testTag("budgets.archivedHeader")) }
                    itemsIndexed(state.archived, key = { _, row -> "archived-${row.id}" }) { index, row -> Card(row, "budgets.archived.$index", onIntent) }
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

/** The period's dates and days left, and a pending start day change (FR-016, FR-010). */
private fun LazyListScope.periodLines(state: BudgetsState) {
    state.period?.let { period ->
        item {
            val range = stringResource(Res.string.budgets_period_range, formatDayMonth(period.start), formatDayMonth(period.end))
            val left = when {
                period.daysLeft == null -> null
                period.lastDay -> stringResource(Res.string.budgets_last_day)
                else -> pluralStringResource(Res.plurals.budgets_days_left, period.daysLeft, period.daysLeft)
            }
            Text(
                if (left == null) range else stringResource(Res.string.budgets_period_line, range, left),
                style = AppTheme.type.label,
                color = AppTheme.colors.ink2,
                modifier = Modifier.testTag("budgets.period"),
            )
        }
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
}

@Composable
private fun EmptyBudgets(onIntent: (BudgetsIntent) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.s), modifier = Modifier.fillMaxWidth().testTag("budgets.empty")) {
        Text(stringResource(Res.string.budgets_empty), style = AppTheme.type.body, color = AppTheme.colors.ink2)
        PrimaryButton(stringResource(Res.string.budgets_set), { onIntent(BudgetsIntent.AddClicked) }, modifier = Modifier.testTag("budgets.empty.add"))
    }
}

@Composable
private fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = AppTheme.type.label,
        color = AppTheme.colors.ink2,
        modifier = modifier.padding(top = AppTheme.spacing.xs).semantics { heading() },
    )
}

@Composable
private fun Card(row: BudgetRowUi, tag: String, onIntent: (BudgetsIntent) -> Unit) {
    val description = stringResource(Res.string.budgets_cd_open, com.fares.saveme.feature.budgets.ui.budgetName(row.categoryName))
    BudgetRowContent(
        row = row,
        statusModifier = Modifier.testTag("$tag.status"),
        modifier = Modifier
            .clip(AppTheme.shapes.card)
            .background(AppTheme.colors.surface, AppTheme.shapes.card)
            .clickable(role = Role.Button, onClickLabel = description) { onIntent(BudgetsIntent.BudgetClicked(row.id)) }
            .padding(horizontal = AppTheme.spacing.m, vertical = AppTheme.spacing.xs)
            .testTag(tag),
    )
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
