package com.fares.saveme.feature.transactions.ui.list

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.fares.saveme.designsystem.component.AlertTone
import com.fares.saveme.designsystem.component.AppFab
import com.fares.saveme.designsystem.component.AppFilterChip
import com.fares.saveme.designsystem.component.AppIconButton
import com.fares.saveme.designsystem.component.AppScreen
import com.fares.saveme.designsystem.component.AppTextButton
import com.fares.saveme.designsystem.component.AppTextField
import com.fares.saveme.designsystem.component.CategoryTile
import com.fares.saveme.designsystem.component.DateHeader
import com.fares.saveme.designsystem.component.InlineAlert
import com.fares.saveme.designsystem.component.MoneyKind
import com.fares.saveme.designsystem.component.MoneyText
import com.fares.saveme.designsystem.component.Skeleton
import com.fares.saveme.designsystem.component.SyncBanner
import com.fares.saveme.designsystem.component.SyncBannerState
import com.fares.saveme.designsystem.component.TransactionRow
import com.fares.saveme.designsystem.format.formatDate
import com.fares.saveme.designsystem.format.formatDateTime
import com.fares.saveme.designsystem.icon.AppIcons
import com.fares.saveme.designsystem.theme.AppTheme
import com.fares.saveme.feature.transactions.domain.model.Counterpart
import com.fares.saveme.feature.transactions.domain.model.Direction
import com.fares.saveme.feature.transactions.domain.model.TransactionKind
import com.fares.saveme.feature.transactions.ui.TransactionsBackBar
import com.fares.saveme.feature.transactions.ui.label
import com.fares.saveme.feature.transactions.ui.labelRes
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import org.jetbrains.compose.resources.stringResource
import saveme.shared.generated.resources.Res
import saveme.shared.generated.resources.category_unavailable
import saveme.shared.generated.resources.filter_clear_all
import saveme.shared.generated.resources.tx_another_wallet
import saveme.shared.generated.resources.tx_cd_clear_search
import saveme.shared.generated.resources.tx_cd_money_in
import saveme.shared.generated.resources.tx_cd_money_out
import saveme.shared.generated.resources.tx_cd_remove_filter
import saveme.shared.generated.resources.tx_status_clash
import saveme.shared.generated.resources.tx_status_not_saved
import saveme.shared.generated.resources.tx_transfer_from
import saveme.shared.generated.resources.tx_transfer_to
import saveme.shared.generated.resources.transactions_add
import saveme.shared.generated.resources.transactions_empty
import saveme.shared.generated.resources.transactions_empty_viewer
import saveme.shared.generated.resources.transactions_filters
import saveme.shared.generated.resources.transactions_no_matches
import saveme.shared.generated.resources.transactions_refresh_failed
import saveme.shared.generated.resources.transactions_search
import saveme.shared.generated.resources.transactions_today
import saveme.shared.generated.resources.transactions_updated_at
import saveme.shared.generated.resources.transactions_yesterday
import saveme.shared.generated.resources.wallet_balance
import saveme.shared.generated.resources.wallet_field_amount_owed
import saveme.shared.generated.resources.wallet_status_waiting

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionsScreen(state: TransactionsState, onIntent: (TransactionsIntent) -> Unit) {
    Box(Modifier.fillMaxSize().testTag("transactions.list")) {
        AppScreen(
            scrollable = false,
            topBar = { TransactionsBackBar(state.walletName) { onIntent(TransactionsIntent.Back) } },
        ) {
            PullToRefreshBox(
                isRefreshing = state.refreshing,
                onRefresh = { onIntent(TransactionsIntent.PullToRefresh) },
                modifier = Modifier.weight(1f).fillMaxWidth(),
            ) {
                TransactionsContent(state, onIntent)
            }
        }
        if (state.canAdd) {
            AppFab(
                icon = AppIcons.Plus,
                contentDescription = stringResource(Res.string.transactions_add),
                onClick = { onIntent(TransactionsIntent.AddClicked) },
                text = stringResource(Res.string.transactions_add),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .navigationBarsPadding()
                    .padding(AppTheme.spacing.m)
                    .testTag("transactions.add"),
            )
        }
    }
    if (state.filterSheetOpen) FilterSheet(state, onIntent)
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TransactionsContent(state: TransactionsState, onIntent: (TransactionsIntent) -> Unit) {
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.xs),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 88.dp),
    ) {
        item { Balance(state) }
        if (state.offline && state.waitingCount > 0) item { SyncBanner(SyncBannerState.Offline(state.waitingCount)) }
        if (state.offline && state.updatedAt != null) {
            item {
                Text(
                    stringResource(Res.string.transactions_updated_at, formatDateTime(state.updatedAt, state.timeZoneId)),
                    style = AppTheme.type.caption,
                    color = AppTheme.colors.caption,
                    modifier = Modifier.testTag("transactions.updatedAt"),
                )
            }
        }
        if (state.refreshFailed) {
            item { InlineAlert(AlertTone.Warning, stringResource(Res.string.transactions_refresh_failed), Modifier.testTag("transactions.refreshFailed")) }
        }
        item { SearchAndFilters(state, onIntent) }
        when {
            state.loading -> items(4) { Skeleton(Modifier.fillMaxWidth().height(AppTheme.sizes.rowTwoLine)) }
            state.isEmpty -> item {
                Text(
                    stringResource(if (state.canAdd) Res.string.transactions_empty else Res.string.transactions_empty_viewer),
                    style = AppTheme.type.body,
                    color = AppTheme.colors.ink2,
                    modifier = Modifier.padding(vertical = AppTheme.spacing.m).testTag("transactions.empty"),
                )
            }
            state.noMatches -> item {
                Text(
                    stringResource(Res.string.transactions_no_matches),
                    style = AppTheme.type.body,
                    color = AppTheme.colors.ink2,
                    modifier = Modifier.padding(vertical = AppTheme.spacing.m).testTag("transactions.noMatches"),
                )
            }
            else -> {
                var index = 0
                state.days.forEach { day ->
                    stickyHeader(key = "day-${day.date}") {
                        DateHeader(
                            label = dayLabel(day.date, state.today),
                            dayTotalMinor = day.netMinor,
                            currency = state.currency,
                            modifier = Modifier.testTag("transactions.day.${day.date}"),
                        )
                    }
                    day.rows.forEach { row ->
                        val rowIndex = index++
                        item(key = row.id) { TransactionItem(row, state, rowIndex, onIntent) }
                    }
                }
            }
        }
    }
}

@Composable
private fun Balance(state: TransactionsState) {
    val liability = state.isLiability
    val shown = if (liability && state.balanceMinor <= 0) -state.balanceMinor else state.balanceMinor
    Column(verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.xxs)) {
        Text(
            stringResource(if (liability) Res.string.wallet_field_amount_owed else Res.string.wallet_balance),
            style = AppTheme.type.label,
            color = AppTheme.colors.ink2,
        )
        MoneyText(
            shown, state.currency, if (liability) MoneyKind.Liability else MoneyKind.Balance,
            modifier = Modifier.testTag("transactions.balance"),
            style = AppTheme.type.numeralL,
        )
    }
}

@Composable
private fun SearchAndFilters(state: TransactionsState, onIntent: (TransactionsIntent) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.xxs)) {
        AppTextField(
            value = state.searchText,
            onValueChange = { onIntent(TransactionsIntent.SearchChanged(it)) },
            label = stringResource(Res.string.transactions_search),
            modifier = Modifier.testTag("transactions.search"),
            trailingIcon = if (state.searchText.isNotEmpty()) {
                {
                    AppIconButton(
                        AppIcons.Close, stringResource(Res.string.tx_cd_clear_search),
                        { onIntent(TransactionsIntent.SearchChanged("")) },
                    )
                }
            } else {
                null
            },
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.xs), verticalArrangement = Arrangement.Center) {
            AppFilterChip(
                label = stringResource(Res.string.transactions_filters),
                selected = state.filtersActive,
                onClick = { onIntent(TransactionsIntent.FiltersOpened) },
                modifier = Modifier.testTag("transactions.filters"),
            )
            state.chips.forEachIndexed { index, chip ->
                val label = chipLabel(chip, state)
                val remove = stringResource(Res.string.tx_cd_remove_filter, label)
                AppFilterChip(
                    label = label,
                    selected = true,
                    onClick = { onIntent(TransactionsIntent.ChipCleared(chip)) },
                    modifier = Modifier.testTag("transactions.chip.$index").semantics { contentDescription = remove },
                )
            }
            if (state.chips.isNotEmpty()) {
                AppTextButton(
                    stringResource(Res.string.filter_clear_all),
                    { onIntent(TransactionsIntent.ClearAll) },
                    Modifier.testTag("transactions.clearAll"),
                )
            }
        }
    }
}

@Composable
private fun chipLabel(chip: FilterChip, state: TransactionsState): String = when (chip) {
    is FilterChip.DateRange -> listOfNotNull(chip.from?.let { formatDate(it) }, chip.to?.let { formatDate(it) }).joinToString(" – ")
    is FilterChip.Category -> state.filterChoices.firstOrNull { it.id == chip.id }?.label()
        ?: stringResource(Res.string.category_unavailable)
    is FilterChip.Kind -> stringResource(chip.kind.labelRes())
}

@Composable
private fun TransactionItem(row: RowUi, state: TransactionsState, index: Int, onIntent: (TransactionsIntent) -> Unit) {
    val title = when {
        row.kind == TransactionKind.Transfer -> {
            val name = (row.counterpart as? Counterpart.Named)?.name ?: stringResource(Res.string.tx_another_wallet)
            stringResource(if (row.direction == Direction.In) Res.string.tx_transfer_from else Res.string.tx_transfer_to, name)
        }
        row.kind == TransactionKind.Unknown -> stringResource(row.kind.labelRes())
        else -> row.category?.label() ?: stringResource(row.kind.labelRes())
    }
    val money = when (row.direction) {
        Direction.In -> MoneyKind.Income
        Direction.Out -> MoneyKind.Expense
        Direction.None -> MoneyKind.Balance
    }
    TransactionRow(
        title = title,
        subtitle = row.note ?: stringResource(row.kind.labelRes()),
        amountMinor = row.amountMinor,
        currency = state.currency,
        kind = money,
        pendingSync = row.status == RowStatus.Waiting,
        onClick = { onIntent(TransactionsIntent.RowClicked(row.id)) },
        leading = { CategoryTile(row.category?.iconKey, row.category?.colorHex) },
        status = when (row.status) {
            RowStatus.None -> null
            RowStatus.Waiting -> stringResource(Res.string.wallet_status_waiting)
            RowStatus.Changed -> stringResource(Res.string.tx_status_clash)
            RowStatus.NotSaved -> stringResource(Res.string.tx_status_not_saved)
        },
        directionLabel = when (row.direction) {
            Direction.In -> stringResource(Res.string.tx_cd_money_in)
            Direction.Out -> stringResource(Res.string.tx_cd_money_out)
            Direction.None -> null
        },
        modifier = Modifier.testTag("transactions.row.$index"),
        amountModifier = Modifier.testTag("transactions.row.$index.amount"),
        statusModifier = Modifier.testTag("transactions.row.$index.status"),
    )
}

@Composable
private fun dayLabel(date: LocalDate, today: LocalDate?): String = when {
    today != null && date == today -> stringResource(Res.string.transactions_today)
    today != null && date == today.minus(DatePeriod(days = 1)) -> stringResource(Res.string.transactions_yesterday)
    else -> formatDate(date)
}

@Preview
@Composable
private fun TransactionsScreenPreview() {
    AppTheme {
        TransactionsScreen(
            TransactionsState(walletId = "w1", walletName = "Cash", currency = "EGP", balanceMinor = 234_000, loading = false, canAdd = true),
            {},
        )
    }
}
