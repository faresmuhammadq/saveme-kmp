package com.fares.saveme.feature.budgets.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.fares.saveme.designsystem.component.BudgetProgress
import com.fares.saveme.designsystem.component.WalletCardStatus
import com.fares.saveme.designsystem.component.WalletCardStatusKind
import com.fares.saveme.designsystem.component.WalletStatusLine
import com.fares.saveme.designsystem.component.formatMoney
import com.fares.saveme.designsystem.theme.AppTheme
import org.jetbrains.compose.resources.stringResource
import saveme.shared.generated.resources.Res
import saveme.shared.generated.resources.budgets_all_expenses
import saveme.shared.generated.resources.budgets_category_unavailable
import saveme.shared.generated.resources.budgets_includes_waiting
import saveme.shared.generated.resources.budgets_limit_line
import saveme.shared.generated.resources.budgets_newer_version
import saveme.shared.generated.resources.budgets_on_pace
import saveme.shared.generated.resources.budgets_other_currencies
import saveme.shared.generated.resources.budgets_status_clash
import saveme.shared.generated.resources.budgets_status_deleted
import saveme.shared.generated.resources.budgets_status_not_saved
import saveme.shared.generated.resources.budgets_status_waiting
import saveme.shared.generated.resources.budgets_unknown_progress

/** The name a row shows: the category, "All expenses", or "Category unavailable" when it cannot be resolved. */
@Composable
fun budgetName(categoryName: String?): String = when {
    categoryName == null -> stringResource(Res.string.budgets_all_expenses)
    categoryName.isEmpty() -> stringResource(Res.string.budgets_category_unavailable)
    else -> categoryName
}

/**
 * One budget as the list and the editor draw it (FR-015): the design system's progress element, then the caption lines the
 * figures need, then the sync status. Neutral wording, no alarm icon, and the percentage is always written (P-08.3).
 */
@Composable
fun BudgetRowContent(row: BudgetRowUi, modifier: Modifier = Modifier, statusModifier: Modifier = Modifier) {
    val name = budgetName(row.categoryName)
    val figures = row.figures
    Column(verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.xxs), modifier = modifier.fillMaxWidth()) {
        if (figures != null) {
            BudgetProgress(name, figures.spentMinor, row.limitMinor, row.currency)
            figures.projectedMinor?.let { Caption(stringResource(Res.string.budgets_on_pace, formatMoney(it, row.currency))) }
            if (figures.otherCurrencies.isNotEmpty()) {
                Caption(stringResource(Res.string.budgets_other_currencies, figures.otherCurrencies.joinToString(", ")))
            }
            if (figures.includesWaiting) Caption(stringResource(Res.string.budgets_includes_waiting))
        } else {
            Text(name, style = AppTheme.type.itemTitle, color = AppTheme.colors.ink)
            Text(stringResource(Res.string.budgets_limit_line, formatMoney(row.limitMinor, row.currency)), style = AppTheme.type.body, color = AppTheme.colors.ink2)
            Caption(stringResource(if (row.readOnly) Res.string.budgets_newer_version else Res.string.budgets_unknown_progress))
        }
        row.status?.let { status ->
            WalletStatusLine(
                WalletCardStatus(
                    when (status) {
                        BudgetStatusUi.Waiting -> WalletCardStatusKind.Waiting
                        BudgetStatusUi.Changed, BudgetStatusUi.DeletedElsewhere -> WalletCardStatusKind.Changed
                        BudgetStatusUi.NotSaved -> WalletCardStatusKind.NotSaved
                    },
                    stringResource(
                        when (status) {
                            BudgetStatusUi.Waiting -> Res.string.budgets_status_waiting
                            BudgetStatusUi.Changed -> Res.string.budgets_status_clash
                            BudgetStatusUi.DeletedElsewhere -> Res.string.budgets_status_deleted
                            BudgetStatusUi.NotSaved -> Res.string.budgets_status_not_saved
                        },
                    ),
                ),
                statusModifier,
            )
        }
    }
}

@Composable
private fun Caption(text: String, modifier: Modifier = Modifier) {
    Text(text, style = AppTheme.type.caption, color = AppTheme.colors.caption, modifier = modifier.fillMaxWidth())
}

