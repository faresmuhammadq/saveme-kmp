package com.fares.saveme.feature.reports.ui.categories

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.foundation.layout.height
import androidx.compose.ui.unit.dp
import com.fares.saveme.designsystem.component.AppScreen
import com.fares.saveme.designsystem.component.ReportFigureRow
import com.fares.saveme.designsystem.component.SegmentedControl
import com.fares.saveme.designsystem.component.Skeleton
import com.fares.saveme.designsystem.component.AlertTone
import com.fares.saveme.designsystem.component.InlineAlert
import com.fares.saveme.designsystem.component.formatMoney
import com.fares.saveme.designsystem.theme.AppTheme
import com.fares.saveme.feature.reports.ui.ReportsBackBar
import com.fares.saveme.feature.reports.ui.rangeText
import com.fares.saveme.feature.reports.ui.reports.CategoryGroupKind
import com.fares.saveme.feature.reports.ui.reports.CategoryRow
import com.fares.saveme.feature.reports.ui.reports.RangeUi
import kotlinx.datetime.LocalDate
import org.jetbrains.compose.resources.stringResource
import saveme.shared.generated.resources.Res
import saveme.shared.generated.resources.report_categories_expenses
import saveme.shared.generated.resources.report_categories_income
import saveme.shared.generated.resources.report_categories_title
import saveme.shared.generated.resources.reports_no_income
import saveme.shared.generated.resources.reports_no_spending
import saveme.shared.generated.resources.reports_scheduled_amount
import saveme.shared.generated.resources.reports_total
import saveme.shared.generated.resources.reports_too_large
import saveme.shared.generated.resources.reports_waiting

@Composable
fun ReportCategoriesScreen(state: ReportCategoriesState, onIntent: (ReportCategoriesIntent) -> Unit) {
    AppScreen(
        scrollable = false,
        topBar = {
            ReportsBackBar(
                title = state.title?.let { "${stringResource(Res.string.report_categories_title)} · $it" } ?: stringResource(Res.string.report_categories_title),
                onBack = { onIntent(ReportCategoriesIntent.BackClicked) },
            )
        },
    ) {
        if (state.status != ReportCategoriesStatus.Ready) {
            Column(verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.s), modifier = Modifier.fillMaxSize().testTag("report_categories.screen")) {
                Skeleton(Modifier.fillMaxWidth().height(96.dp), AppTheme.shapes.card)
            }
        } else {
            Body(state, onIntent)
        }
    }
}

@Composable
private fun Body(state: ReportCategoriesState, onIntent: (ReportCategoriesIntent) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.s), modifier = Modifier.fillMaxSize().testTag("report_categories.screen")) {
        state.range?.let { range: RangeUi ->
            Text(rangeText(range.from, range.to, state.thisYear), style = AppTheme.type.itemTitle, color = AppTheme.colors.ink)
        }
        SegmentedControl(
            options = CategoryGroupKind.entries,
            label = { kind ->
                when (kind) {
                    CategoryGroupKind.Expenses -> stringResource(Res.string.report_categories_expenses)
                    CategoryGroupKind.Income -> stringResource(Res.string.report_categories_income)
                }
            },
            selected = state.kind,
            onSelect = { onIntent(ReportCategoriesIntent.KindPicked(it)) },
            modifier = Modifier.testTag("report_categories.kind"),
            optionModifier = { Modifier.testTag("report_categories.kind.${it.wire}") },
        )
        if (state.includesWaiting) InlineAlert(AlertTone.Info, stringResource(Res.string.reports_waiting), Modifier.testTag("report_categories.waiting"))
        if (state.tooLarge) {
            Text(stringResource(Res.string.reports_too_large), style = AppTheme.type.body, color = AppTheme.colors.ink2)
        } else {
            ReportFigureRow(
                label = stringResource(Res.string.reports_total),
                amountText = formatMoney(state.totalMinor, state.currency),
                modifier = Modifier.testTag("report_categories.total"),
                emphasis = true,
            )
            state.plannedMinor?.takeIf { it != 0L }?.let {
                Text(
                    stringResource(Res.string.reports_scheduled_amount, formatMoney(it, state.currency)),
                    style = AppTheme.type.caption,
                    color = AppTheme.colors.caption,
                    modifier = Modifier.testTag("report_categories.planned"),
                )
            }
            if (state.rows.isEmpty()) {
                Text(
                    if (state.kind == CategoryGroupKind.Expenses) stringResource(Res.string.reports_no_spending) else stringResource(Res.string.reports_no_income),
                    style = AppTheme.type.body,
                    color = AppTheme.colors.ink2,
                    modifier = Modifier.padding(top = AppTheme.spacing.xs).testTag("report_categories.empty"),
                )
            }
            LazyColumn(Modifier.weight(1f).fillMaxWidth()) {
                itemsIndexed(state.rows, key = { index, row -> "$index-${row.categoryId}" }) { index, row ->
                    CategoryRow(row, Modifier.testTag("report_categories.row.$index"))
                }
            }
        }
    }
}

@Preview
@Composable
private fun ReportCategoriesPreview() {
    AppTheme {
        ReportCategoriesScreen(
            ReportCategoriesState(
                status = ReportCategoriesStatus.Ready,
                range = RangeUi(LocalDate(2026, 10, 1), LocalDate(2026, 10, 31), custom = false),
                thisYear = 2026,
                currency = "EGP",
            ),
            {},
        )
    }
}
