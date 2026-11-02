package com.fares.saveme.feature.sharedbudgets.ui.list

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.ui.Alignment
import com.fares.saveme.designsystem.component.CalmCircleButton
import com.fares.saveme.designsystem.icon.AppIcons
import saveme.shared.generated.resources.sb_cd_add
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fares.saveme.designsystem.component.BudgetProgress
import com.fares.saveme.designsystem.component.CalmCard
import com.fares.saveme.designsystem.component.CalmScreen
import com.fares.saveme.designsystem.component.CalmTitle
import com.fares.saveme.designsystem.component.Skeleton
import com.fares.saveme.designsystem.component.formatMoney
import com.fares.saveme.designsystem.format.formatDayMonth
import com.fares.saveme.designsystem.theme.AppTheme
import com.fares.saveme.feature.sharedbudgets.domain.model.SharedBudgetProgress
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import saveme.shared.generated.resources.Res
import saveme.shared.generated.resources.budgets_period_range
import saveme.shared.generated.resources.cd_back
import saveme.shared.generated.resources.sb_archived
import saveme.shared.generated.resources.sb_empty
import saveme.shared.generated.resources.sb_no_limit
import saveme.shared.generated.resources.sb_title

@Composable
fun SharedBudgetsRoute(onEffect: (SharedBudgetsEffect) -> Unit, viewModel: SharedBudgetsViewModel = koinViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) { viewModel.effects.collect(onEffect) }
    SharedBudgetsScreen(state, viewModel::onIntent)
}

@Composable
fun SharedBudgetsScreen(state: SharedBudgetsState, onIntent: (SharedBudgetsIntent) -> Unit) {
    CalmScreen(
        title = null,
        onBack = { onIntent(SharedBudgetsIntent.Back) },
        backDescription = stringResource(Res.string.cd_back),
        titleSlot = {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.weight(1f)) { CalmTitle(stringResource(Res.string.sb_title)) }
                CalmCircleButton(
                    AppIcons.Plus, stringResource(Res.string.sb_cd_add), { onIntent(SharedBudgetsIntent.AddClicked) },
                    tint = AppTheme.colors.primary, modifier = Modifier.testTag("sharedBudgets.add"),
                )
            }
        },
    ) {
        when {
            !state.loaded -> repeat(2) { Skeleton(Modifier.fillMaxWidth().height(96.dp), AppTheme.shapes.hero) }
            state.isEmpty -> Text(
                stringResource(Res.string.sb_empty), style = AppTheme.type.body, color = AppTheme.colors.ink2,
                modifier = Modifier.testTag("sharedBudgets.empty"),
            )
            else -> Column(verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.s), modifier = Modifier.testTag("sharedBudgets.list")) {
                state.active.forEach { SharedBudgetCard(it, onIntent) }
                if (state.archived.isNotEmpty()) {
                    Text(stringResource(Res.string.sb_archived), style = AppTheme.type.label, color = AppTheme.colors.ink2)
                    state.archived.forEach { SharedBudgetCard(it, onIntent) }
                }
            }
        }
    }
}

/** One budget: its name and period, then the overall bar when it has a limit, else just what has been spent. */
@Composable
private fun SharedBudgetCard(budget: SharedBudgetProgress, onIntent: (SharedBudgetsIntent) -> Unit) {
    CalmCard(onClick = { onIntent(SharedBudgetsIntent.BudgetClicked(budget.id)) }, modifier = Modifier.testTag("sharedBudgets.item.${budget.id}")) {
        Text(budget.name, style = AppTheme.type.itemTitle, color = AppTheme.colors.ink)
        Text(
            stringResource(Res.string.budgets_period_range, formatDayMonth(budget.period.start), formatDayMonth(budget.period.end)),
            style = AppTheme.type.caption, color = AppTheme.colors.caption,
        )
        val limit = budget.limitMinor
        if (limit != null) {
            BudgetProgress(budget.name, budget.spentMinor, limit, budget.currency)
        } else {
            Text(formatMoney(budget.spentMinor, budget.currency), style = AppTheme.type.body, color = AppTheme.colors.ink)
            Text(stringResource(Res.string.sb_no_limit), style = AppTheme.type.caption, color = AppTheme.colors.caption)
        }
    }
}
