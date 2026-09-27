package com.fares.saveme.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.fares.saveme.designsystem.theme.AppTheme
import org.jetbrains.compose.resources.stringResource
import saveme.shared.generated.resources.Res
import saveme.shared.generated.resources.budget_left
import saveme.shared.generated.resources.budget_over
import saveme.shared.generated.resources.budget_percent
import saveme.shared.generated.resources.budget_spent_of

enum class BudgetState { Positive, Pending, Over }

/** Whole percent spent, rounded to nearest as on the board (3,420 of 4,000 = 86%). */
fun budgetPercent(spentMinor: Long, limitMinor: Long): Int =
    if (limitMinor <= 0) (if (spentMinor > 0) 101 else 0) else ((spentMinor * 100 + limitMinor / 2) / limitMinor).toInt()

/** Under 80% positive, 80–100% pending, over 100% over (board: BudgetProgress). Exact, not rounded. */
fun budgetState(spentMinor: Long, limitMinor: Long): BudgetState = when {
    limitMinor <= 0 -> if (spentMinor > 0) BudgetState.Over else BudgetState.Positive
    spentMinor > limitMinor -> BudgetState.Over
    spentMinor * 100 >= limitMinor * 80 -> BudgetState.Pending
    else -> BudgetState.Positive
}

/** The percentage is always written, so the state never relies on colour (P-08.3). */
@Composable
fun BudgetProgress(
    name: String,
    spentMinor: Long,
    limitMinor: Long,
    currency: String,
    modifier: Modifier = Modifier,
) {
    val money = AppTheme.money
    val state = budgetState(spentMinor, limitMinor)
    val percent = budgetPercent(spentMinor, limitMinor)
    val (fill, track) = when (state) {
        BudgetState.Positive -> money.positive to money.positiveContainer
        BudgetState.Pending -> money.pending to money.pendingContainer
        BudgetState.Over -> money.over to money.overContainer
    }
    val digits = com.fares.saveme.designsystem.money.MoneyFormatter.digitsFor(currency)
    val caption = if (state == BudgetState.Over) {
        stringResource(Res.string.budget_over, formatMoney(spentMinor - limitMinor, currency))
    } else {
        stringResource(Res.string.budget_spent_of, formatMoney(spentMinor, currency), com.fares.saveme.designsystem.money.MoneyFormatter.number(limitMinor, digits)) +
            " · " + stringResource(Res.string.budget_left, formatMoney(limitMinor - spentMinor, currency))
    }
    Column(
        verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.xs),
        modifier = modifier.fillMaxWidth().padding(vertical = AppTheme.spacing.xs),
    ) {
        Row {
            Text(name, style = AppTheme.type.itemTitle, color = AppTheme.colors.ink, modifier = Modifier.weight(1f))
            Text(stringResource(Res.string.budget_percent, percent), style = AppTheme.type.label, color = fill)
        }
        // Fill follows layout direction: in RTL it grows from the right.
        Box(Modifier.fillMaxWidth().height(8.dp).background(track, AppTheme.shapes.pill)) {
            Box(
                Modifier
                    .fillMaxWidth(percent.coerceIn(0, 100) / 100f)
                    .fillMaxHeight()
                    .background(fill, AppTheme.shapes.pill),
            )
        }
        Text(caption, style = AppTheme.type.caption, color = AppTheme.colors.caption)
    }
}
