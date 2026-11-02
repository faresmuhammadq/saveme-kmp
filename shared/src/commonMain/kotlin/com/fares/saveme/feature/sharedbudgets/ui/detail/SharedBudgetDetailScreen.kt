package com.fares.saveme.feature.sharedbudgets.ui.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
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
import com.fares.saveme.designsystem.component.CalmSectionTitle
import com.fares.saveme.designsystem.component.CalmTitle
import com.fares.saveme.designsystem.component.Skeleton
import com.fares.saveme.designsystem.component.formatMoney
import com.fares.saveme.designsystem.format.formatDateTime
import com.fares.saveme.designsystem.format.formatDayMonth
import com.fares.saveme.designsystem.theme.AppTheme
import com.fares.saveme.feature.sharedbudgets.domain.model.SharedBudgetProgress
import kotlinx.datetime.TimeZone
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import saveme.shared.generated.resources.Res
import saveme.shared.generated.resources.budgets_on_pace
import saveme.shared.generated.resources.budgets_period_range
import saveme.shared.generated.resources.cd_back
import saveme.shared.generated.resources.sb_categories
import saveme.shared.generated.resources.sb_category_unavailable
import saveme.shared.generated.resources.sb_former_member
import saveme.shared.generated.resources.sb_members
import saveme.shared.generated.resources.sb_no_limit
import saveme.shared.generated.resources.sb_other
import saveme.shared.generated.resources.sb_spent_label
import saveme.shared.generated.resources.sb_updated
import saveme.shared.generated.resources.sb_you
import kotlin.time.Instant

@Composable
fun SharedBudgetDetailRoute(
    budgetId: String,
    onEffect: (SharedBudgetDetailEffect) -> Unit,
    viewModel: SharedBudgetDetailViewModel = koinViewModel(key = "shared-budget-$budgetId") { parametersOf(budgetId) },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) { viewModel.effects.collect(onEffect) }
    SharedBudgetDetailScreen(state, viewModel::onIntent)
}

@Composable
fun SharedBudgetDetailScreen(state: SharedBudgetDetailState, onIntent: (SharedBudgetDetailIntent) -> Unit) {
    val progress = state.progress
    CalmScreen(
        title = null,
        onBack = { onIntent(SharedBudgetDetailIntent.Back) },
        backDescription = stringResource(Res.string.cd_back),
        titleSlot = { CalmTitle(progress?.name.orEmpty()) },
    ) {
        if (progress == null) {
            repeat(3) { Skeleton(Modifier.fillMaxWidth().height(96.dp), AppTheme.shapes.hero) }
            return@CalmScreen
        }
        Text(
            stringResource(Res.string.budgets_period_range, formatDayMonth(progress.period.start), formatDayMonth(progress.period.end)),
            style = AppTheme.type.label, color = AppTheme.colors.ink2, modifier = Modifier.testTag("sharedBudget.period"),
        )
        Overall(progress)
        Categories(progress)
        Members(progress)
        progress.refreshedAt?.let { at ->
            Text(
                stringResource(Res.string.sb_updated, formatDateTime(Instant.parse(at), TimeZone.currentSystemDefault().id)),
                style = AppTheme.type.caption, color = AppTheme.colors.caption, modifier = Modifier.testTag("sharedBudget.updatedAt"),
            )
        }
    }
}

@Composable
private fun Overall(progress: SharedBudgetProgress) {
    CalmCard(modifier = Modifier.testTag("sharedBudget.overall")) {
        val limit = progress.limitMinor
        if (limit != null) {
            BudgetProgress(progress.name, progress.spentMinor, limit, progress.currency)
        } else {
            Text(stringResource(Res.string.sb_spent_label), style = AppTheme.type.label, color = AppTheme.colors.ink2)
            Text(formatMoney(progress.spentMinor, progress.currency), style = AppTheme.type.itemTitle, color = AppTheme.colors.ink)
            Text(stringResource(Res.string.sb_no_limit), style = AppTheme.type.caption, color = AppTheme.colors.caption)
        }
        progress.projectedMinor?.let {
            Text(stringResource(Res.string.budgets_on_pace, formatMoney(it, progress.currency)), style = AppTheme.type.caption, color = AppTheme.colors.caption)
        }
    }
}

/** Each category of the budget's list, then what went into other categories. */
@Composable
private fun Categories(progress: SharedBudgetProgress) {
    if (progress.lines.isEmpty() && progress.unlistedMinor == 0L) return
    CalmSectionTitle(stringResource(Res.string.sb_categories))
    CalmCard(modifier = Modifier.testTag("sharedBudget.categories")) {
        Column16 {
            progress.lines.forEach { line ->
                val name = line.name.ifEmpty { stringResource(Res.string.sb_category_unavailable) }
                val limit = line.limitMinor
                if (limit != null) {
                    BudgetProgress(name, line.spentMinor, limit, progress.currency)
                } else {
                    MoneyRow(name, line.spentMinor, progress.currency)
                }
            }
            if (progress.unlistedMinor > 0) MoneyRow(stringResource(Res.string.sb_other), progress.unlistedMinor, progress.currency)
        }
    }
}

@Composable
private fun Members(progress: SharedBudgetProgress) {
    if (progress.members.isEmpty()) return
    CalmSectionTitle(stringResource(Res.string.sb_members))
    CalmCard(modifier = Modifier.testTag("sharedBudget.members")) {
        Column16 {
            progress.members.forEach { member ->
                val name = when {
                    member.isMe -> stringResource(Res.string.sb_you)
                    member.name != null -> member.name
                    else -> stringResource(Res.string.sb_former_member)
                }
                MoneyRow(name, member.spentMinor, progress.currency)
            }
        }
    }
}

@Composable
private fun MoneyRow(label: String, minor: Long, currency: String) {
    Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
        Text(label, style = AppTheme.type.body, color = AppTheme.colors.ink)
        Text(formatMoney(minor, currency), style = AppTheme.type.body, color = AppTheme.colors.ink)
    }
}

@Composable
private fun Column16(content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) =
    androidx.compose.foundation.layout.Column(verticalArrangement = Arrangement.spacedBy(16.dp), content = content)
