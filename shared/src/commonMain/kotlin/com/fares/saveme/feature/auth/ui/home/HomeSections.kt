package com.fares.saveme.feature.auth.ui.home

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.sp
import com.fares.saveme.designsystem.component.AppIconButton
import com.fares.saveme.designsystem.component.CalmCard
import com.fares.saveme.designsystem.component.CalmDivider
import com.fares.saveme.designsystem.component.CalmIconTile
import com.fares.saveme.designsystem.component.CalmVRule
import com.fares.saveme.designsystem.component.PrimaryButton
import com.fares.saveme.designsystem.component.AppTextButton
import com.fares.saveme.designsystem.component.trimHeight
import com.fares.saveme.designsystem.format.formatDayMonth
import com.fares.saveme.designsystem.icon.AppIcons
import com.fares.saveme.designsystem.icon.CategoryIcons
import com.fares.saveme.designsystem.icon.WalletIcons
import com.fares.saveme.designsystem.theme.AppTheme
import com.fares.saveme.designsystem.tokens.CategoryColors
import com.fares.saveme.feature.auth.domain.HomeOverview
import com.fares.saveme.feature.auth.domain.HomeRecent
import com.fares.saveme.feature.auth.domain.HomeRecentKind
import com.fares.saveme.feature.auth.domain.HomeWallet
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlinx.datetime.DatePeriod
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import saveme.shared.generated.resources.Res
import saveme.shared.generated.resources.budgets_status_waiting
import saveme.shared.generated.resources.homec_add_wallet
import saveme.shared.generated.resources.homec_chart_cd
import saveme.shared.generated.resources.homec_days_left
import saveme.shared.generated.resources.homec_dismiss
import saveme.shared.generated.resources.homec_figures_after_sync
import saveme.shared.generated.resources.homec_income
import saveme.shared.generated.resources.homec_left_over
import saveme.shared.generated.resources.homec_more_categories
import saveme.shared.generated.resources.homec_other_currencies
import saveme.shared.generated.resources.homec_recent
import saveme.shared.generated.resources.homec_report
import saveme.shared.generated.resources.homec_set_budget
import saveme.shared.generated.resources.homec_spent_period
import saveme.shared.generated.resources.homec_suggestion
import saveme.shared.generated.resources.homec_suggestion_plain
import saveme.shared.generated.resources.homec_today
import saveme.shared.generated.resources.homec_transfer
import saveme.shared.generated.resources.homec_trend_less
import saveme.shared.generated.resources.homec_trend_more
import saveme.shared.generated.resources.homec_trend_same
import saveme.shared.generated.resources.homec_wallets_empty
import saveme.shared.generated.resources.homec_wallets_total
import saveme.shared.generated.resources.homec_where
import saveme.shared.generated.resources.homec_yesterday
import saveme.shared.generated.resources.home_wallets
import saveme.shared.generated.resources.reports_too_large
import saveme.shared.generated.resources.budgets_category_unavailable

private fun money(currency: String, minor: Long): String = "$currency ${plainAmount(kotlin.math.abs(minor), currency)}"

private fun signed(currency: String, minor: Long, plus: Boolean): String = when {
    minor < 0 -> "−${money(currency, minor)}"
    plus && minor > 0 -> "+${money(currency, minor)}"
    else -> money(currency, minor)
}

/** The period's spending: the figure, how it compares with the period before, a bar per day, and income and what is left. */
@Composable
internal fun SpentCard(overview: HomeOverview, modifier: Modifier = Modifier) {
    val colors = AppTheme.colors
    val currency = overview.currency ?: return
    CalmCard(padding = PaddingValues(horizontal = 20.dp, vertical = 18.dp), modifier = modifier.testTag("home.spent")) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(Res.string.homec_spent_period),
                style = AppTheme.type.caption.copy(fontWeight = FontWeight.SemiBold),
                color = colors.ink2,
                modifier = Modifier.weight(1f),
            )
            overview.daysLeft?.takeIf { overview.periodStart != null }?.let { days ->
                Text(
                    pluralStringResource(Res.plurals.homec_days_left, days, days),
                    style = AppTheme.type.caption.copy(fontWeight = FontWeight.Bold, fontSize = 11.5.sp),
                    color = colors.primary,
                    modifier = Modifier.background(colors.primaryContainer, RoundedCornerShape(50)).padding(horizontal = 11.dp, vertical = 5.dp),
                )
            }
        }
        val spent = overview.spentMinor
        when {
            overview.tooLarge -> Text(
                stringResource(Res.string.reports_too_large),
                style = AppTheme.type.title,
                color = colors.ink2,
                modifier = Modifier.padding(top = 8.dp),
            )
            spent == null -> Text(
                stringResource(Res.string.homec_figures_after_sync),
                style = AppTheme.type.label,
                color = colors.ink2,
                modifier = Modifier.padding(top = 8.dp),
            )
            else -> {
                Text(
                    money(currency, spent),
                    style = AppTheme.type.numeralHero.copy(fontSize = 36.sp, textDirection = TextDirection.Ltr),
                    color = colors.ink,
                    maxLines = 1,
                    modifier = Modifier.padding(top = 8.dp),
                )
                overview.changePercent?.let { change ->
                    val less = change < 0
                    Text(
                        (if (change == 0) "" else if (less) "↓ " else "↑ ") + when {
                            change == 0 -> stringResource(Res.string.homec_trend_same)
                            less -> stringResource(Res.string.homec_trend_less, -change)
                            else -> stringResource(Res.string.homec_trend_more, change)
                        },
                        style = AppTheme.type.caption.copy(fontWeight = FontWeight.SemiBold, fontSize = 13.sp),
                        color = if (less || change == 0) AppTheme.money.positive else AppTheme.money.over,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
                val start = overview.periodStart
                val end = overview.periodEnd
                if (start != null && end != null && overview.dailySpend.isNotEmpty()) {
                    DailyBars(overview, spent, currency, start, end)
                }
                val income = overview.incomeMinor
                val left = overview.leftOverMinor
                if (income != null && left != null) {
                    CalmDivider(Modifier.padding(top = 14.dp))
                    Row(Modifier.padding(top = 12.dp).height(IntrinsicSize.Min)) {
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(Res.string.homec_income), style = AppTheme.type.caption.copy(fontSize = 11.5.sp), color = colors.ink2)
                            Text(
                                money(currency, income),
                                style = AppTheme.type.title.copy(fontWeight = FontWeight.ExtraBold, textDirection = TextDirection.Ltr),
                                color = colors.ink,
                                modifier = Modifier.padding(top = 2.dp),
                            )
                        }
                        CalmVRule(Modifier.padding(horizontal = 14.dp, vertical = 2.dp).fillMaxHeight())
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(Res.string.homec_left_over), style = AppTheme.type.caption.copy(fontSize = 11.5.sp), color = colors.ink2)
                            Text(
                                signed(currency, left, plus = true),
                                style = AppTheme.type.title.copy(fontWeight = FontWeight.ExtraBold, textDirection = TextDirection.Ltr),
                                color = if (left < 0) AppTheme.money.over else AppTheme.money.positive,
                                modifier = Modifier.padding(top = 2.dp),
                            )
                        }
                    }
                }
            }
        }
        if (overview.includesWaiting) {
            Text(stringResource(Res.string.budgets_status_waiting), style = AppTheme.type.caption, color = colors.caption, modifier = Modifier.padding(top = 8.dp))
        }
        if (overview.otherCurrencies.isNotEmpty()) {
            Text(
                stringResource(Res.string.homec_other_currencies, currency, overview.otherCurrencies.joinToString(", ")),
                style = AppTheme.type.caption,
                color = colors.caption,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

/** One bar per day, the days up to today solid, today strongest, later days a small tick. Mirrors in a right-to-left layout. */
@Composable
private fun DailyBars(overview: HomeOverview, spent: Long, currency: String, start: LocalDate, end: LocalDate) {
    val colors = AppTheme.colors
    val bars = overview.dailySpend
    val today = overview.todayIndex
    val primary = colors.primary
    val faded = colors.primary.copy(alpha = 0.5f)
    val future = colors.outlineVariant
    val max = (bars.maxOrNull() ?: 0L).coerceAtLeast(1L)
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val description = stringResource(Res.string.homec_chart_cd, formatDayMonth(start), formatDayMonth(end), money(currency, spent))
    Canvas(
        Modifier
            .padding(top = 16.dp)
            .fillMaxWidth()
            .height(56.dp)
            .semantics { contentDescription = description }
            .testTag("home.spent.chart"),
    ) {
        val count = bars.size
        val gap = 4.dp.toPx()
        val barWidth = ((size.width - gap * (count - 1)) / count).coerceAtLeast(1f)
        val radius = CornerRadius(2.dp.toPx())
        bars.forEachIndexed { index, value ->
            val isFuture = index > today
            val height = if (isFuture) 4.dp.toPx() else (size.height * value.toFloat() / max).coerceAtLeast(2.dp.toPx())
            val x = index * (barWidth + gap)
            val left = if (rtl) size.width - x - barWidth else x
            drawRoundRect(
                color = when {
                    isFuture -> future
                    index == today -> primary
                    else -> faded
                },
                topLeft = Offset(left, size.height - height),
                size = Size(barWidth, height),
                cornerRadius = radius,
            )
        }
    }
    Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        val style = AppTheme.type.caption.copy(fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
        Text(formatDayMonth(start), style = style, color = colors.ink2)
        Text(stringResource(Res.string.homec_today), style = style, color = colors.ink2)
        Text(formatDayMonth(end), style = style, color = colors.ink2)
    }
}

private fun walletIcon(wallet: HomeWallet): ImageVector = WalletIcons.find(wallet.iconKey) ?: when (wallet.typeWire) {
    "checking" -> AppIcons.Bank
    "savings" -> AppIcons.Savings
    "credit_card" -> AppIcons.Card
    "loan" -> AppIcons.Loan
    "investment" -> AppIcons.Investment
    else -> AppIcons.Cash
}

/** Every wallet's balance as a tile, with one total per currency (never added across currencies). */
@Composable
internal fun WalletsCard(overview: HomeOverview, onIntent: (HomeIntent) -> Unit) {
    val colors = AppTheme.colors
    CalmCard(padding = PaddingValues(vertical = 14.dp), modifier = Modifier.testTag("home.wallets")) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 18.dp, end = 18.dp, bottom = 10.dp)) {
            Text(
                stringResource(Res.string.home_wallets),
                style = AppTheme.type.itemTitle.copy(fontSize = 15.sp, fontWeight = FontWeight.Bold),
                color = colors.ink,
                modifier = Modifier.weight(1f).semantics { heading() },
            )
            val totals = overview.totals
            if (totals.isNotEmpty()) {
                Text(
                    stringResource(Res.string.homec_wallets_total, totals.joinToString(" · ") { signed(it.currency, it.minor, plus = false) }),
                    style = AppTheme.type.caption.copy(fontSize = 13.sp, fontWeight = FontWeight.Bold, textDirection = TextDirection.Ltr),
                    color = colors.ink2,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (overview.wallets.isEmpty()) {
            Text(
                stringResource(Res.string.homec_wallets_empty),
                style = AppTheme.type.body,
                color = colors.ink2,
                modifier = Modifier.padding(horizontal = 18.dp),
            )
            AppTextButton(stringResource(Res.string.homec_add_wallet), { onIntent(HomeIntent.WalletsClicked) }, modifier = Modifier.padding(horizontal = 6.dp))
        } else {
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 18.dp),
            ) {
                overview.wallets.forEach { wallet ->
                    Column(
                        modifier = Modifier
                            .width(132.dp)
                            .background(colors.sunken, RoundedCornerShape(16.dp))
                            .clickable(role = Role.Button) { onIntent(HomeIntent.WalletClicked(wallet.id)) }
                            .padding(12.dp)
                            .testTag("home.wallet.${wallet.id}"),
                    ) {
                        Icon(walletIcon(wallet), contentDescription = null, tint = if (wallet.liability) AppTheme.money.over else colors.primary, modifier = Modifier.size(20.dp))
                        Text(
                            wallet.name,
                            style = AppTheme.type.caption.copy(fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold),
                            color = colors.ink2,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                        Text(
                            signed(wallet.currency, wallet.balanceMinor, plus = false),
                            style = AppTheme.type.label.copy(fontWeight = FontWeight.ExtraBold, fontSize = 15.sp, textDirection = TextDirection.Ltr),
                            color = if (wallet.balanceMinor < 0) AppTheme.money.over else colors.ink,
                            maxLines = 1,
                            modifier = Modifier.padding(top = 2.dp),
                        )
                    }
                }
            }
        }
    }
}

/** The top spending categories with their share, "N more", and the suggestion to set a monthly budget. */
@Composable
internal fun WhereItWentCard(
    overview: HomeOverview,
    showSuggestion: Boolean,
    typicalSpendMinor: Long?,
    onIntent: (HomeIntent) -> Unit,
) {
    val colors = AppTheme.colors
    val currency = overview.currency ?: return
    if (overview.categories.isEmpty() && !showSuggestion) return
    CalmCard(padding = PaddingValues(start = 18.dp, end = 18.dp, top = 4.dp, bottom = 16.dp), modifier = Modifier.testTag("home.where")) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
            Text(
                stringResource(Res.string.homec_where),
                style = AppTheme.type.itemTitle.copy(fontSize = 15.sp, fontWeight = FontWeight.Bold),
                color = colors.ink,
                modifier = Modifier.weight(1f).semantics { heading() },
            )
            Box(
                contentAlignment = Alignment.CenterEnd,
                modifier = Modifier.trimHeight(16.dp).heightIn(min = 48.dp).clickable(role = Role.Button) { onIntent(HomeIntent.ReportClicked) },
            ) {
                Text(
                    stringResource(Res.string.homec_report),
                    style = AppTheme.type.caption.copy(fontSize = 12.5.sp, fontWeight = FontWeight.Bold),
                    color = colors.primary,
                )
            }
        }
        overview.categories.forEachIndexed { index, category ->
            if (index > 0) CalmDivider()
            val swatch = CategoryColors.of(category.colorHex)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
            ) {
                CalmIconTile(CategoryIcons.of(category.iconKey), tint = swatch.tint, container = swatch.container, size = 34.dp, iconSize = 18.dp, radius = 11.dp)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Row {
                        Text(
                            category.name.ifEmpty { stringResource(Res.string.budgets_category_unavailable) },
                            style = AppTheme.type.label.copy(fontWeight = FontWeight.SemiBold),
                            color = colors.ink,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            money(currency, category.amountMinor),
                            style = AppTheme.type.label.copy(fontWeight = FontWeight.Bold, textDirection = TextDirection.Ltr),
                            color = colors.ink,
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(Modifier.weight(1f).height(6.dp).background(colors.sunken, RoundedCornerShape(9.dp))) {
                            Box(
                                Modifier
                                    .fillMaxWidth(category.fraction.coerceIn(0.02f, 1f))
                                    .fillMaxHeight()
                                    .background(colors.caption, RoundedCornerShape(9.dp)),
                            )
                        }
                        category.percent?.let {
                            Text(
                                "$it%",
                                style = AppTheme.type.caption.copy(fontSize = 11.sp, fontWeight = FontWeight.SemiBold),
                                color = colors.ink2,
                                modifier = Modifier.width(30.dp),
                            )
                        }
                    }
                }
            }
        }
        if (overview.moreCategories > 0) {
            CalmDivider()
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .clickable(role = Role.Button) { onIntent(HomeIntent.MoreCategoriesClicked) }
                    .testTag("home.where.more"),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    pluralStringResource(Res.plurals.homec_more_categories, overview.moreCategories, overview.moreCategories),
                    style = AppTheme.type.label.copy(fontSize = 13.sp, fontWeight = FontWeight.SemiBold),
                    color = colors.ink2,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    money(currency, overview.moreCategoriesMinor),
                    style = AppTheme.type.label.copy(fontSize = 13.sp, fontWeight = FontWeight.SemiBold, textDirection = TextDirection.Ltr),
                    color = colors.ink2,
                )
            }
        }
        if (showSuggestion) BudgetSuggestion(currency, typicalSpendMinor, onIntent, Modifier.padding(top = 8.dp))
    }
}

@Composable
private fun BudgetSuggestion(currency: String, typicalMinor: Long?, onIntent: (HomeIntent) -> Unit, modifier: Modifier = Modifier) {
    val colors = AppTheme.colors
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier.fillMaxWidth().background(colors.primaryContainer, RoundedCornerShape(18.dp)).padding(14.dp).testTag("home.suggestion"),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top) {
            Icon(AppIcons.Info, contentDescription = null, tint = colors.primary)
            Text(
                if (typicalMinor != null && typicalMinor > 0) {
                    stringResource(Res.string.homec_suggestion, money(currency, typicalMinor))
                } else {
                    stringResource(Res.string.homec_suggestion_plain)
                },
                style = AppTheme.type.label,
                color = colors.ink,
                modifier = Modifier.weight(1f),
            )
            AppIconButton(AppIcons.Close, stringResource(Res.string.homec_dismiss), { onIntent(HomeIntent.DismissSuggestion) })
        }
        PrimaryButton(stringResource(Res.string.homec_set_budget), { onIntent(HomeIntent.SetBudgetClicked) }, modifier = Modifier.testTag("home.suggestion.set"))
    }
}

/** The three newest transactions across the wallets. */
@Composable
internal fun RecentCard(overview: HomeOverview, onIntent: (HomeIntent) -> Unit) {
    val colors = AppTheme.colors
    if (overview.recent.isEmpty()) return
    CalmCard(padding = PaddingValues(start = 18.dp, end = 18.dp, top = 4.dp, bottom = 6.dp), modifier = Modifier.testTag("home.recent")) {
        Text(
            stringResource(Res.string.homec_recent),
            style = AppTheme.type.itemTitle.copy(fontSize = 15.sp, fontWeight = FontWeight.Bold),
            color = colors.ink,
            modifier = Modifier.padding(top = 14.dp, bottom = 4.dp).semantics { heading() },
        )
        overview.recent.forEachIndexed { index, item ->
            if (index > 0) CalmDivider()
            RecentRow(item, overview.today, onIntent, Modifier.testTag("home.recent.$index"))
        }
    }
}

@Composable
private fun RecentRow(item: HomeRecent, today: LocalDate?, onIntent: (HomeIntent) -> Unit, modifier: Modifier) {
    val colors = AppTheme.colors
    val income = item.kind == HomeRecentKind.Income
    val transfer = item.kind == HomeRecentKind.Transfer
    val title = item.title ?: if (transfer) stringResource(Res.string.homec_transfer) else ""
    val subtitle = listOfNotNull(
        item.categoryName?.takeIf { it != title },
        when (item.date) {
            today -> stringResource(Res.string.homec_today)
            today?.minus(DatePeriod(days = 1)) -> stringResource(Res.string.homec_yesterday)
            else -> formatDayMonth(item.date)
        },
    ).joinToString(" · ")
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier
            .fillMaxWidth()
            .clickable(role = Role.Button) { onIntent(HomeIntent.TransactionClicked(item.id, item.walletId)) }
            .padding(vertical = 10.dp),
    ) {
        CalmIconTile(
            CategoryIcons.of(item.iconKey),
            tint = if (income) AppTheme.money.positive else colors.ink2,
            container = if (income) AppTheme.money.positiveContainer else colors.sunken,
            size = 34.dp, iconSize = 18.dp, radius = 11.dp,
        )
        Column(Modifier.weight(1f)) {
            Text(title, style = AppTheme.type.label.copy(fontWeight = FontWeight.SemiBold), color = colors.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subtitle, style = AppTheme.type.caption, color = colors.ink2, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp))
        }
        val sign = when {
            transfer -> ""
            income || item.incoming -> "+"
            else -> "−"
        }
        Text(
            "$sign${money(item.currency, item.amountMinor)}",
            style = AppTheme.type.label.copy(fontWeight = FontWeight.Bold, textDirection = TextDirection.Ltr),
            color = if (income) AppTheme.money.positive else colors.ink,
        )
    }
}
