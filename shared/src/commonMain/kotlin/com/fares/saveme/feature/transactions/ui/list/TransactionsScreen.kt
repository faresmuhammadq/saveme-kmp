package com.fares.saveme.feature.transactions.ui.list

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import kotlin.math.abs
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.em
import androidx.compose.foundation.layout.heightIn
import com.fares.saveme.designsystem.component.plainAmount
import com.fares.saveme.designsystem.component.signedPlainAmount
import com.fares.saveme.designsystem.icon.TxEventUpcoming
import com.fares.saveme.designsystem.money.MoneyFormatter
import org.jetbrains.compose.resources.pluralStringResource
import saveme.shared.generated.resources.recurring_due_count
import saveme.shared.generated.resources.txl_day_mon
import saveme.shared.generated.resources.txl_day_tue
import saveme.shared.generated.resources.txl_day_wed
import saveme.shared.generated.resources.txl_day_thu
import saveme.shared.generated.resources.txl_day_fri
import saveme.shared.generated.resources.txl_day_sat
import saveme.shared.generated.resources.txl_day_sun
import kotlinx.datetime.DayOfWeek
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import com.fares.saveme.designsystem.component.AppFilterChip
import com.fares.saveme.designsystem.component.AppIconButton
import com.fares.saveme.designsystem.component.AppTextButton
import com.fares.saveme.designsystem.component.CalmCard
import com.fares.saveme.designsystem.component.CalmChip
import com.fares.saveme.designsystem.component.CalmCircleButton
import com.fares.saveme.designsystem.component.CalmDivider
import com.fares.saveme.designsystem.component.CalmScreen
import com.fares.saveme.designsystem.component.formatMoney
import com.fares.saveme.designsystem.component.AppTextField
import com.fares.saveme.designsystem.component.CategoryTile
import com.fares.saveme.designsystem.component.InlineAlert
import com.fares.saveme.designsystem.component.MoneyKind
import com.fares.saveme.designsystem.component.MoneyText
import com.fares.saveme.designsystem.component.OccurrenceRow
import com.fares.saveme.designsystem.component.OccurrenceVisualState
import com.fares.saveme.designsystem.component.Skeleton
import com.fares.saveme.designsystem.component.SyncBanner
import com.fares.saveme.designsystem.component.SyncBannerState
import com.fares.saveme.designsystem.component.TransactionRow
import com.fares.saveme.designsystem.format.formatDate
import com.fares.saveme.designsystem.format.formatDateTime
import com.fares.saveme.designsystem.icon.AppIcons
import com.fares.saveme.designsystem.icon.TxArrowDown
import com.fares.saveme.designsystem.icon.TxArrowUp
import com.fares.saveme.designsystem.icon.TxSearch
import com.fares.saveme.designsystem.icon.TxTune
import com.fares.saveme.designsystem.theme.AppTheme
import com.fares.saveme.feature.transactions.domain.DueState
import com.fares.saveme.feature.transactions.domain.model.Counterpart
import com.fares.saveme.feature.transactions.domain.model.Direction
import com.fares.saveme.feature.transactions.domain.model.TransactionKind
import com.fares.saveme.feature.transactions.ui.label
import com.fares.saveme.feature.transactions.ui.labelRes
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import org.jetbrains.compose.resources.stringResource
import saveme.shared.generated.resources.Res
import saveme.shared.generated.resources.cd_back
import saveme.shared.generated.resources.category_unavailable
import saveme.shared.generated.resources.tx_kind_expense
import saveme.shared.generated.resources.tx_kind_income
import saveme.shared.generated.resources.txl_all
import saveme.shared.generated.resources.txl_bar_cd
import saveme.shared.generated.resources.txl_chart_cd
import saveme.shared.generated.resources.txl_dow_fri
import saveme.shared.generated.resources.txl_dow_mon
import saveme.shared.generated.resources.txl_dow_sat
import saveme.shared.generated.resources.txl_dow_sun
import saveme.shared.generated.resources.txl_dow_thu
import saveme.shared.generated.resources.txl_dow_tue
import saveme.shared.generated.resources.txl_dow_wed
import saveme.shared.generated.resources.txl_in
import saveme.shared.generated.resources.txl_more
import saveme.shared.generated.resources.txl_out
import saveme.shared.generated.resources.txl_week_caption
import saveme.shared.generated.resources.txl_week_summary
import saveme.shared.generated.resources.filter_clear_all
import saveme.shared.generated.resources.recurring_open
import saveme.shared.generated.resources.recurring_payout
import saveme.shared.generated.resources.recurring_rule_title
import saveme.shared.generated.resources.recurring_section_due
import saveme.shared.generated.resources.recurring_see_all
import saveme.shared.generated.resources.recurring_state_due
import saveme.shared.generated.resources.recurring_state_missed
import saveme.shared.generated.resources.recurring_state_upcoming
import saveme.shared.generated.resources.tx_another_wallet
import saveme.shared.generated.resources.tx_from_recurring
import saveme.shared.generated.resources.tx_cd_clear_search
import saveme.shared.generated.resources.tx_cd_money_in
import saveme.shared.generated.resources.tx_cd_money_out
import saveme.shared.generated.resources.tx_cd_remove_filter
import saveme.shared.generated.resources.tx_status_clash
import saveme.shared.generated.resources.tx_status_not_saved
import saveme.shared.generated.resources.tx_transfer_from
import saveme.shared.generated.resources.tx_transfer_to
import saveme.shared.generated.resources.transactions_add
import saveme.shared.generated.resources.viewer_note
import saveme.shared.generated.resources.viewer_note_plan
import saveme.shared.generated.resources.transactions_empty
import saveme.shared.generated.resources.transactions_empty_viewer
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
    var searching by rememberSaveable { mutableStateOf(false) }
    Box(Modifier.fillMaxSize().testTag("transactions.list")) {
        CalmScreen(
            title = state.walletName,
            onBack = { onIntent(TransactionsIntent.Back) },
            backDescription = stringResource(Res.string.cd_back),
            scrollable = false,
            spacing = 12.dp,
            actions = {
                CalmCircleButton(
                    AppIcons.TxSearch,
                    stringResource(Res.string.transactions_search),
                    { searching = !searching },
                )
            },
        ) {
            PullToRefreshBox(
                isRefreshing = state.refreshing,
                onRefresh = { onIntent(TransactionsIntent.PullToRefresh) },
                modifier = Modifier.weight(1f).fillMaxWidth(),
            ) {
                TransactionsContent(state, searching || state.searchText.isNotEmpty(), onIntent)
            }
        }
    }
    if (state.filterSheetOpen) FilterSheet(state, onIntent)
}

@Composable
private fun TransactionsContent(state: TransactionsState, showSearch: Boolean, onIntent: (TransactionsIntent) -> Unit) {
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = AppTheme.spacing.m),
    ) {
        item { Balance(state) }
        if (state.isViewer) {
            item {
                InlineAlert(
                    AlertTone.Info,
                    stringResource(if (state.planLimited) Res.string.viewer_note_plan else Res.string.viewer_note),
                    Modifier.testTag("transactions.viewerNote"),
                )
            }
        }
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
        item { SearchAndFilters(state, showSearch, onIntent) }
        dueSection(state, onIntent)
        when {
            state.loading -> item {
                CalmCard(padding = PaddingValues(horizontal = 18.dp, vertical = 12.dp)) {
                    repeat(4) { Skeleton(Modifier.fillMaxWidth().height(AppTheme.sizes.rowTwoLine).padding(vertical = 4.dp)) }
                }
            }
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
                    val first = index
                    index += day.rows.size
                    item(key = "day-${day.date}") {
                        CalmCard(padding = PaddingValues(horizontal = 2.dp, vertical = 2.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 4.dp)
                                    .semantics { heading() }
                                    .testTag("transactions.day.${day.date}"),
                            ) {
                                val header = AppTheme.type.overline.copy(fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.08.em)
                                Text(
                                    dayLabel(day.date, state.today).uppercase(),
                                    style = header,
                                    color = AppTheme.colors.ink2,
                                    modifier = Modifier.weight(1f),
                                )
                                Text(signedPlainAmount(day.netMinor, state.currency), style = header.copy(textDirection = TextDirection.Ltr), color = AppTheme.colors.ink2)
                            }
                            day.rows.forEachIndexed { i, row -> TransactionItem(row, state, first + i, onIntent) }
                        }
                    }
                }
            }
        }
    }
}

/** Due and missed occurrences (spec 010 FR-019): one banner with the way to them, never in totals, search or filters. */
private fun androidx.compose.foundation.lazy.LazyListScope.dueSection(state: TransactionsState, onIntent: (TransactionsIntent) -> Unit) {
    if (state.loading) return
    item(key = "recurring-link") {
        if (state.due.isEmpty()) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                AppTextButton(
                    stringResource(Res.string.recurring_open),
                    { onIntent(TransactionsIntent.RecurringClicked) },
                    Modifier.testTag("transactions.recurring"),
                )
            }
        } else {
            val single = state.due.singleOrNull()
            val count = pluralStringResource(Res.plurals.recurring_due_count, state.dueTotal, state.dueTotal)
            val fallback = stringResource(Res.string.recurring_rule_title)
            val titles = state.due.joinToString(", ") { it.description?.takeIf { d -> d.isNotBlank() } ?: fallback }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(AppTheme.money.pendingContainer)
                    .clickable(role = Role.Button) {
                        if (single != null && state.dueTotal == 1) onIntent(TransactionsIntent.DueClicked(single.id)) else onIntent(TransactionsIntent.RecurringClicked)
                    }
                    .heightIn(min = 48.dp)
                    .padding(horizontal = 14.dp, vertical = 10.dp)
                    .testTag(if (single != null && state.dueTotal == 1) "transactions.due.0" else "transactions.recurring"),
            ) {
                Icon(AppIcons.TxEventUpcoming, contentDescription = null, tint = AppTheme.money.pending, modifier = Modifier.size(22.dp))
                Text(
                    buildAnnotatedString {
                        withStyle(SpanStyle(fontWeight = FontWeight.ExtraBold)) { append(count) }
                        append(" · $titles")
                    },
                    style = AppTheme.type.caption.copy(fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold),
                    color = AppTheme.colors.ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Icon(AppIcons.ChevronForward, contentDescription = null, tint = AppTheme.money.pending)
            }
        }
    }
}

/** Spending per day for the seven days ending today, from the rows the list shows (searches and filters narrow it too). */
private class WeekFigures(val dates: List<LocalDate>, val spent: List<Long>, val inMinor: Long, val outMinor: Long)

private fun weekFigures(state: TransactionsState): WeekFigures? {
    val today = state.today ?: return null
    val dates = (6 downTo 0).map { today.minus(DatePeriod(days = it)) }
    val perDay = state.days.associate { day ->
        day.date to day.rows.filter { it.direction == Direction.Out && it.kind != TransactionKind.Transfer && !it.kind.isAdjustment }.sumOf { abs(it.amountMinor) }
    }
    val rows = state.days.flatMap { it.rows }
    return WeekFigures(
        dates = dates,
        spent = dates.map { perDay[it] ?: 0L },
        inMinor = rows.filter { it.direction == Direction.In }.sumOf { abs(it.amountMinor) },
        outMinor = rows.filter { it.direction == Direction.Out }.sumOf { abs(it.amountMinor) },
    )
}

@Composable
private fun dayShortLabel(date: LocalDate, today: LocalDate?): String = if (date == today) {
    stringResource(Res.string.transactions_today)
} else {
    stringResource(
        when (date.dayOfWeek) {
            DayOfWeek.MONDAY -> Res.string.txl_dow_mon
            DayOfWeek.TUESDAY -> Res.string.txl_dow_tue
            DayOfWeek.WEDNESDAY -> Res.string.txl_dow_wed
            DayOfWeek.THURSDAY -> Res.string.txl_dow_thu
            DayOfWeek.FRIDAY -> Res.string.txl_dow_fri
            DayOfWeek.SATURDAY -> Res.string.txl_dow_sat
            else -> Res.string.txl_dow_sun
        },
    )
}

private fun roundedToUnit(minor: Long, currency: String): Long {
    var unit = 1L
    repeat(MoneyFormatter.digitsFor(currency)) { unit *= 10 }
    return (minor + unit / 2) / unit * unit
}

@Composable
private fun dayFullLabel(date: LocalDate, today: LocalDate?): String = if (date == today) {
    stringResource(Res.string.transactions_today)
} else {
    stringResource(
        when (date.dayOfWeek) {
            DayOfWeek.MONDAY -> Res.string.txl_day_mon
            DayOfWeek.TUESDAY -> Res.string.txl_day_tue
            DayOfWeek.WEDNESDAY -> Res.string.txl_day_wed
            DayOfWeek.THURSDAY -> Res.string.txl_day_thu
            DayOfWeek.FRIDAY -> Res.string.txl_day_fri
            DayOfWeek.SATURDAY -> Res.string.txl_day_sat
            else -> Res.string.txl_day_sun
        },
    )
}

@Composable
private fun Balance(state: TransactionsState) {
    val liability = state.isLiability
    val shown = if (liability && state.balanceMinor <= 0) -state.balanceMinor else state.balanceMinor
    val week = weekFigures(state)
    CalmCard(padding = PaddingValues(start = 18.dp, end = 18.dp, top = 16.dp, bottom = 14.dp)) {
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.xxs)) {
                Text(
                    stringResource(if (liability) Res.string.wallet_field_amount_owed else Res.string.wallet_balance),
                    style = AppTheme.type.label,
                    color = AppTheme.colors.ink2,
                )
                if (liability) {
                    MoneyText(
                        shown, state.currency, MoneyKind.Liability,
                        modifier = Modifier.testTag("transactions.balance"),
                        style = AppTheme.type.numeralL,
                    )
                } else {
                    val digits = MoneyFormatter.digitsFor(state.currency)
                    val full = MoneyFormatter.number(kotlin.math.abs(shown), digits)
                    val dot = full.lastIndexOf('.')
                    val whole = if (dot < 0) full else full.substring(0, dot)
                    val fraction = if (dot < 0) "" else full.substring(dot)
                    Text(
                        buildAnnotatedString {
                            if (shown < 0) append("\u2212")
                            append(whole)
                            withStyle(SpanStyle(fontSize = 17.sp, color = AppTheme.colors.ink2)) { append("$fraction ${state.currency}") }
                        },
                        style = AppTheme.type.numeralL.copy(textDirection = TextDirection.Ltr),
                        color = if (shown < 0) AppTheme.colors.error else AppTheme.colors.ink,
                        maxLines = 1,
                        modifier = Modifier.testTag("transactions.balance"),
                    )
                }
            }
            if (week != null && !state.loading) {
                Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(top = 2.dp)) {
                    FlowFigure(AppIcons.TxArrowDown, stringResource(Res.string.txl_in, plainAmount(week.inMinor, state.currency)), AppTheme.money.positive)
                    FlowFigure(AppIcons.TxArrowUp, stringResource(Res.string.txl_out, plainAmount(week.outMinor, state.currency)), AppTheme.colors.ink2)
                }
            }
        }
        if (week != null && !state.loading) WeekChart(week, state)
    }
}

@Composable
private fun FlowFigure(icon: ImageVector, text: String, tint: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(15.dp))
        Text(text, style = AppTheme.type.caption.copy(fontWeight = FontWeight.Bold), color = tint, maxLines = 1)
    }
}

@Composable
private fun WeekChart(week: WeekFigures, state: TransactionsState) {
    var picked by rememberSaveable { mutableIntStateOf(week.dates.lastIndex) }
    val total = week.spent.sum()
    val max = week.spent.max()
    val pick = picked.coerceIn(0, week.dates.lastIndex)
    Column(Modifier.padding(top = 12.dp)) {
        CalmDivider()
        Row(
            verticalAlignment = Alignment.Bottom,
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
        ) {
            Text(
                stringResource(
                    Res.string.txl_week_caption,
                    dayFullLabel(week.dates[pick], state.today),
                    "${state.currency} ${plainAmount(week.spent[pick], state.currency)}",
                ),
                style = AppTheme.type.caption.copy(fontWeight = FontWeight.Bold),
                color = AppTheme.colors.ink,
                modifier = Modifier.weight(1f),
            )
            Text(
                stringResource(Res.string.txl_week_summary, plainAmount(total, state.currency), plainAmount(roundedToUnit(total / 7, state.currency), state.currency)),
                style = AppTheme.type.caption,
                color = AppTheme.colors.ink2,
                maxLines = 1,
            )
        }
        val chart = stringResource(Res.string.txl_chart_cd)
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.Bottom,
            modifier = Modifier.fillMaxWidth().padding(top = 10.dp).height(92.dp).semantics { contentDescription = chart }.testTag("transactions.weekChart"),
        ) {
            week.dates.forEachIndexed { i, date ->
                val on = i == pick
                val label = dayShortLabel(date, state.today)
                val description = stringResource(Res.string.txl_bar_cd, dayFullLabel(date, state.today), "${state.currency} ${plainAmount(week.spent[i], state.currency)}")
                val barHeight = if (max <= 0L) 8.dp else maxOf(8f, week.spent[i].toFloat() / max * 66f).dp
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.Bottom),
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .semantics { contentDescription = description }
                        .selectable(selected = on, role = Role.Button) { picked = i }
                        .testTag("transactions.weekBar.$i"),
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(barHeight)
                            .background(if (on) AppTheme.colors.primary else AppTheme.colors.outlineVariant, RoundedCornerShape(8.dp)),
                    )
                    Text(
                        label,
                        style = AppTheme.type.overline.copy(fontSize = 11.sp, lineHeight = 14.sp, letterSpacing = TextUnit.Unspecified, fontWeight = if (on) FontWeight.ExtraBold else FontWeight.SemiBold),
                        color = if (on) AppTheme.colors.primary else AppTheme.colors.ink2,
                        maxLines = 1,
                        overflow = TextOverflow.Clip,
                    )
                }
            }
        }
    }
}

@Composable
private fun SearchAndFilters(state: TransactionsState, showSearch: Boolean, onIntent: (TransactionsIntent) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.xxs)) {
        if (showSearch) {
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
        }
        val kinds = state.query.kinds
        Row(horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.xs), modifier = Modifier.horizontalScroll(rememberScrollState())) {
            CalmChip(
                stringResource(Res.string.txl_all),
                selected = kinds.isEmpty(),
                onClick = { kinds.forEach { onIntent(TransactionsIntent.KindToggled(it)) } },
                modifier = Modifier.testTag("transactions.kind.all"),
            )
            CalmChip(
                stringResource(Res.string.tx_kind_expense),
                selected = TransactionKind.Expense in kinds,
                onClick = { onIntent(TransactionsIntent.KindToggled(TransactionKind.Expense)) },
                modifier = Modifier.testTag("transactions.kind.expense"),
            )
            CalmChip(
                stringResource(Res.string.tx_kind_income),
                selected = TransactionKind.Income in kinds,
                onClick = { onIntent(TransactionsIntent.KindToggled(TransactionKind.Income)) },
                modifier = Modifier.testTag("transactions.kind.income"),
            )
            CalmChip(
                stringResource(Res.string.txl_more),
                selected = state.query.from != null || state.query.to != null || state.query.categoryIds.isNotEmpty(),
                onClick = { onIntent(TransactionsIntent.FiltersOpened) },
                modifier = Modifier.testTag("transactions.filters"),
                leadingIcon = AppIcons.TxTune,
            )
        }
        if (state.chips.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.xs), verticalArrangement = Arrangement.Center) {
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
    val amount = when (row.direction) {
        Direction.In -> signedPlainAmount(abs(row.amountMinor), state.currency)
        Direction.Out -> signedPlainAmount(-abs(row.amountMinor), state.currency)
        Direction.None -> plainAmount(row.amountMinor, state.currency)
    }
    CalmTransactionRow(
        title = title,
        subtitle = row.note ?: stringResource(if (row.occurrenceId != null) Res.string.tx_from_recurring else row.kind.labelRes()),
        amountText = amount,
        amountColor = if (money == MoneyKind.Income) AppTheme.money.positive else AppTheme.colors.ink,
        iconKey = row.category?.iconKey,
        colorHex = row.category?.colorHex,
        onClick = { onIntent(TransactionsIntent.RowClicked(row.id)) },
        status = when (row.status) {
            RowStatus.None -> null
            RowStatus.Waiting -> stringResource(Res.string.wallet_status_waiting)
            RowStatus.Changed -> stringResource(Res.string.tx_status_clash)
            RowStatus.NotSaved -> stringResource(Res.string.tx_status_not_saved)
        },
        waiting = row.status == RowStatus.Waiting,
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
