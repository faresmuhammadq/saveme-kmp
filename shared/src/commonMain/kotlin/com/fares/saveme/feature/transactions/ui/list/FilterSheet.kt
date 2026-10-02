package com.fares.saveme.feature.transactions.ui.list

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import com.fares.saveme.designsystem.component.AppBottomSheet
import com.fares.saveme.designsystem.component.AppDatePickerDialog
import com.fares.saveme.designsystem.component.AppFilterChip
import com.fares.saveme.designsystem.component.CategoryRow
import com.fares.saveme.designsystem.component.DateField
import com.fares.saveme.designsystem.component.PrimaryButton
import com.fares.saveme.designsystem.format.formatDate
import com.fares.saveme.designsystem.theme.AppTheme
import com.fares.saveme.feature.transactions.domain.DatePresets
import com.fares.saveme.feature.transactions.domain.model.TransactionKind
import com.fares.saveme.feature.transactions.ui.label
import com.fares.saveme.feature.transactions.ui.labelRes
import kotlinx.datetime.LocalDate
import org.jetbrains.compose.resources.stringResource
import saveme.shared.generated.resources.Res
import saveme.shared.generated.resources.filter_apply
import saveme.shared.generated.resources.filter_category
import saveme.shared.generated.resources.filter_custom
import saveme.shared.generated.resources.filter_from
import saveme.shared.generated.resources.filter_kind
import saveme.shared.generated.resources.filter_last_month
import saveme.shared.generated.resources.filter_this_month
import saveme.shared.generated.resources.filter_to
import saveme.shared.generated.resources.tx_field_date
import saveme.shared.generated.resources.transactions_filters

/** The filters in a bottom sheet. Filters apply as they are chosen; "Show results" only closes it. */
@Composable
fun FilterSheet(state: TransactionsState, onIntent: (TransactionsIntent) -> Unit) {
    AppBottomSheet(onDismiss = { onIntent(TransactionsIntent.FiltersDismissed) }, title = stringResource(Res.string.transactions_filters)) {
        FilterSheetContent(state, onIntent)
    }
}

/** The sheet's content, usable without the sheet window (screenshots). */
@Composable
fun FilterSheetContent(state: TransactionsState, onIntent: (TransactionsIntent) -> Unit, modifier: Modifier = Modifier) {
    val today = state.today
    val thisMonth = today?.let { DatePresets.thisMonth(it) }
    val lastMonth = today?.let { DatePresets.lastMonth(it) }
    var customOpen by remember { mutableStateOf(false) }
    var picking by remember { mutableStateOf<Picking?>(null) }
    val from = state.query.from
    val to = state.query.to
    val isThisMonth = thisMonth != null && from == thisMonth.first && to == thisMonth.second
    val isLastMonth = lastMonth != null && from == lastMonth.first && to == lastMonth.second
    val isCustom = customOpen || ((from != null || to != null) && !isThisMonth && !isLastMonth)

    Column(verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.xs), modifier = modifier.fillMaxWidth()) {
        SectionTitle(stringResource(Res.string.tx_field_date))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.xs)) {
            AppFilterChip(
                stringResource(Res.string.filter_this_month), isThisMonth,
                { onIntent(TransactionsIntent.ThisMonth) }, Modifier.testTag("transactions.filter.thisMonth"),
            )
            AppFilterChip(
                stringResource(Res.string.filter_last_month), isLastMonth,
                { onIntent(TransactionsIntent.LastMonth) }, Modifier.testTag("transactions.filter.lastMonth"),
            )
            AppFilterChip(
                stringResource(Res.string.filter_custom), isCustom,
                { customOpen = !customOpen }, Modifier.testTag("transactions.filter.custom"),
            )
        }
        if (isCustom) {
            DateField(
                stringResource(Res.string.filter_from), from?.let { formatDate(it) }.orEmpty(),
                { picking = Picking.From }, Modifier.testTag("transactions.filter.from"),
            )
            DateField(
                stringResource(Res.string.filter_to), to?.let { formatDate(it) }.orEmpty(),
                { picking = Picking.To }, Modifier.testTag("transactions.filter.to"),
            )
        }
        SectionTitle(stringResource(Res.string.filter_kind))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.xs)) {
            TransactionKind.selectable.forEach { kind ->
                AppFilterChip(
                    stringResource(kind.labelRes()), kind in state.query.kinds,
                    { onIntent(TransactionsIntent.KindToggled(kind)) }, Modifier.testTag("transactions.filter.kind.${kind.wire}"),
                )
            }
        }
        if (state.filterChoices.isNotEmpty()) {
            SectionTitle(stringResource(Res.string.filter_category))
            state.filterChoices.forEachIndexed { index, display ->
                CategoryRow(
                    name = display.label(),
                    iconKey = display.iconKey,
                    colorHex = display.colorHex,
                    selected = display.id in state.query.categoryIds,
                    onClick = { onIntent(TransactionsIntent.CategoryToggled(display.id)) },
                    modifier = Modifier.testTag("transactions.filter.category.$index"),
                )
            }
        }
        PrimaryButton(
            stringResource(Res.string.filter_apply),
            { onIntent(TransactionsIntent.FiltersDismissed) },
            modifier = Modifier.testTag("transactions.filter.apply"),
        )
    }

    picking?.let { which ->
        val initial = (if (which == Picking.From) from else to) ?: today ?: LocalDate(2026, 1, 1)
        AppDatePickerDialog(
            initial = initial,
            onPick = { date ->
                picking = null
                onIntent(
                    if (which == Picking.From) TransactionsIntent.DateRange(date, to?.takeIf { it >= date })
                    else TransactionsIntent.DateRange(from?.takeIf { it <= date }, date),
                )
            },
            onDismiss = { picking = null },
        )
    }
}

private enum class Picking { From, To }

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = AppTheme.type.label, color = AppTheme.colors.ink2, modifier = Modifier.semantics { heading() })
}
