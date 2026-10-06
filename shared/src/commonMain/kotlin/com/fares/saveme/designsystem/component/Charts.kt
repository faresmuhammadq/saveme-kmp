package com.fares.saveme.designsystem.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fares.saveme.designsystem.icon.AppIcons
import com.fares.saveme.designsystem.money.MoneyFormatter
import com.fares.saveme.designsystem.theme.AppTheme
import com.fares.saveme.designsystem.tokens.CategoryColors

/*
 * Charts for reports (spec 014, research R-10), drawn with Compose's Canvas and no library. Every bar has its value written
 * beside it, income and expenses differ by position, legend words and the table (never colour alone, P-08.3), and the
 * oldest period and the start of a bar sit at the start edge, so everything mirrors in right-to-left layouts.
 */

/** Neutral ink for expenses, never the error colour (P-03.5). */
private val expenseInk: Color @Composable get() = AppTheme.colors.ink2


/**
 * A row with [start] at the start edge and [end] at the end edge. When the end block leaves the start block less than 40% of
 * the width (large text, long amounts), the end block drops below it instead of squeezing it: nothing is ever clipped or broken
 * letter by letter at 200% text. Both blocks mirror in right-to-left layouts.
 */
@Composable
private fun AdaptiveEndRow(modifier: Modifier, gap: Dp, start: @Composable () -> Unit, end: @Composable () -> Unit) {
    Layout(content = { Box { start() }; Box { end() } }, modifier = modifier) { measurables, constraints ->
        val gapPx = gap.roundToPx()
        val maxWidth = constraints.maxWidth
        val endIntrinsic = measurables[1].maxIntrinsicWidth(Int.MAX_VALUE).coerceAtMost(maxWidth)
        val startWanted = measurables[0].maxIntrinsicWidth(Int.MAX_VALUE).coerceAtMost(maxWidth)
        val room = maxWidth - endIntrinsic - gapPx
        val sideBySide = room >= minOf(startWanted, (maxWidth * 0.4f).toInt())
        if (sideBySide) {
            val endPlaceable = measurables[1].measure(Constraints(maxWidth = endIntrinsic))
            val startPlaceable = measurables[0].measure(Constraints(maxWidth = room.coerceAtLeast(0)))
            val height = maxOf(startPlaceable.height, endPlaceable.height)
            layout(maxWidth, height) {
                startPlaceable.placeRelative(0, (height - startPlaceable.height) / 2)
                endPlaceable.placeRelative(maxWidth - endPlaceable.width, (height - endPlaceable.height) / 2)
            }
        } else {
            val startPlaceable = measurables[0].measure(Constraints(maxWidth = maxWidth))
            val endPlaceable = measurables[1].measure(Constraints(maxWidth = maxWidth))
            layout(maxWidth, startPlaceable.height + endPlaceable.height) {
                startPlaceable.placeRelative(0, 0)
                endPlaceable.placeRelative(maxWidth - endPlaceable.width, startPlaceable.height)
            }
        }
    }
}

/**
 * One category's share of a total: icon tile, name, a bar filled from the start edge to [fraction], the written amount and
 * share. The bar is decorative: the row is announced once as "name, share, amount".
 */
@Composable
fun ShareBarRow(
    label: String,
    iconKey: String?,
    colorHex: String?,
    amountText: String,
    shareText: String,
    fraction: Float,
    modifier: Modifier = Modifier,
    scheduledText: String? = null,
) {
    val color = CategoryColors.of(colorHex)
    val description = listOfNotNull(label, shareText, amountText, scheduledText).joinToString(", ")
    AdaptiveEndRow(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = AppTheme.sizes.rowTwoLine)
            .padding(vertical = AppTheme.spacing.xs)
            .clearAndSetSemantics { contentDescription = description },
        gap = AppTheme.spacing.s,
        start = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.s)) {
                CategoryTile(iconKey, colorHex)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.xxs)) {
                    Text(label, style = AppTheme.type.itemTitle, color = AppTheme.colors.ink, maxLines = 3, overflow = TextOverflow.Ellipsis)
                    // The fill starts at the start edge: Box lays out in the layout direction, so it grows from the right in RTL.
                    Box(Modifier.fillMaxWidth().height(8.dp).background(color.container, AppTheme.shapes.pill)) {
                        Box(Modifier.fillMaxWidth(fraction.coerceIn(0f, 1f)).fillMaxHeight().background(color.tint, AppTheme.shapes.pill))
                    }
                    if (scheduledText != null) Text(scheduledText, style = AppTheme.type.caption, color = AppTheme.colors.caption)
                }
            }
        },
        end = {
            Column(horizontalAlignment = Alignment.End) {
                Text(amountText, style = AppTheme.type.itemTitle.ltr(), color = AppTheme.colors.ink, textAlign = TextAlign.End)
                Text(shareText, style = AppTheme.type.caption, color = AppTheme.colors.caption)
            }
        },
    )
}

/**
 * One period of a trend: the income and expense sums to draw and to write, and the scheduled parts (outlined, never added
 * to the actual bar's value). Heights are scaled for drawing only; no figure shown is derived from them.
 */
@Immutable
data class TrendPointUi(
    val periodLabel: String,
    val incomeMinor: Long,
    val expensesMinor: Long,
    val plannedIncomeMinor: Long? = null,
    val plannedExpensesMinor: Long? = null,
)

/** The words of a [TrendBarChart]; the legend is always written. */
@Immutable
data class TrendChartLabels(
    val income: String,
    val expenses: String,
    val scheduled: String,
    /** The chart's one announcement; the table below lists each period. */
    val summary: String,
)

/**
 * Grouped income and expense bars per period, oldest at the start edge, a zero baseline, a written value under every bar
 * and an outlined scheduled segment when a period has scheduled money. Wider than the screen when there are many periods:
 * it scrolls from the start edge. The whole chart is one node for a screen reader, which hears [TrendChartLabels.summary].
 */
@Composable
fun TrendBarChart(
    points: List<TrendPointUi>,
    currency: String,
    labels: TrendChartLabels,
    modifier: Modifier = Modifier,
) {
    val digits = MoneyFormatter.digitsFor(currency)
    val income = AppTheme.money.positive
    val expense = expenseInk
    val baseline = AppTheme.colors.outline
    val scale = points.maxOfOrNull { maxOf(it.incomeMinor + (it.plannedIncomeMinor ?: 0), it.expensesMinor + (it.plannedExpensesMinor ?: 0)) }
        ?.coerceAtLeast(1L) ?: 1L
    // A column is at least this wide and as wide as its longest written value, so no number ever breaks across lines.
    val minColumn = with(LocalDensity.current) { 92.sp.toDp() }
    Column(
        verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.xs),
        modifier = modifier.fillMaxWidth().clearAndSetSemantics { contentDescription = labels.summary },
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.m), verticalAlignment = Alignment.CenterVertically) {
            LegendItem(labels.income, income, outlined = false)
            LegendItem(labels.expenses, expense, outlined = false)
            if (points.any { it.plannedIncomeMinor != null || it.plannedExpensesMinor != null }) LegendItem(labels.scheduled, AppTheme.colors.ink2, outlined = true)
        }
        Row(Modifier.horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.Top) {
            points.forEach { point ->
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                    modifier = Modifier.widthIn(min = minColumn).width(IntrinsicSize.Max).padding(horizontal = AppTheme.spacing.xxs),
                ) {
                    Canvas(Modifier.fillMaxWidth().height(CHART_HEIGHT)) {
                        drawBars(point, scale, income, expense, baseline)
                    }
                    Text(point.periodLabel, style = AppTheme.type.caption, color = AppTheme.colors.ink2, maxLines = 1, textAlign = TextAlign.Center)
                    ValueText(MoneyFormatter.number(point.incomeMinor, digits), income)
                    ValueText(MoneyFormatter.number(point.expensesMinor, digits), AppTheme.colors.ink)
                    val plannedIncome = point.plannedIncomeMinor
                    val plannedExpenses = point.plannedExpensesMinor
                    if (plannedIncome != null && plannedIncome != 0L) ValueText("${labels.scheduled} +" + MoneyFormatter.number(plannedIncome, digits), AppTheme.colors.caption)
                    if (plannedExpenses != null && plannedExpenses != 0L) ValueText("${labels.scheduled} −" + MoneyFormatter.number(plannedExpenses, digits), AppTheme.colors.caption)
                }
            }
        }
    }
}

private val CHART_HEIGHT = 120.dp

@Composable
private fun ValueText(text: String, color: Color) {
    Text(text, style = AppTheme.type.caption.ltr(), color = color, softWrap = false, maxLines = 1, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
}

@Composable
private fun LegendItem(text: String, color: Color, outlined: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.xxs)) {
        Canvas(Modifier.size(12.dp)) {
            if (outlined) {
                drawRect(color, style = Stroke(width = 2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx()))))
            } else {
                drawRect(color)
            }
        }
        Text(text, style = AppTheme.type.caption, color = AppTheme.colors.ink2)
    }
}

/** Two bars side by side (income, then expenses) from the zero baseline; mirrored in a right-to-left layout. */
private fun DrawScope.drawBars(point: TrendPointUi, scale: Long, income: Color, expense: Color, baseline: Color) {
    val rtl = layoutDirection == LayoutDirection.Rtl
    val gap = 8.dp.toPx()
    val barWidth = (size.width - gap * 3) / 2f
    val floorY = size.height - 2.dp.toPx()
    val usable = floorY - 2.dp.toPx()
    fun x(slot: Int): Float {
        val left = gap + slot * (barWidth + gap)
        return if (rtl) size.width - left - barWidth else left
    }
    fun height(value: Long): Float = if (value <= 0L) 0f else usable * (value.toDouble() / scale.toDouble()).toFloat()
    val dash = Stroke(width = 2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx())))
    listOf(
        Triple(0, point.incomeMinor to point.plannedIncomeMinor, income),
        Triple(1, point.expensesMinor to point.plannedExpensesMinor, expense),
    ).forEach { (slot, sums, color) ->
        val actual = height(sums.first)
        if (actual > 0f) drawRect(color, Offset(x(slot), floorY - actual), Size(barWidth, actual))
        val planned = sums.second?.let { height(it) } ?: 0f
        if (planned > 0f) drawRect(color, Offset(x(slot) + 1.dp.toPx(), floorY - actual - planned + 1.dp.toPx()), Size(barWidth - 2.dp.toPx(), planned - 2.dp.toPx()), style = dash)
    }
    drawLine(baseline, Offset(0f, floorY), Offset(size.width, floorY), strokeWidth = 2.dp.toPx())
}

/** One row of the trend table: the period and its sums, with the scheduled ones when asked for. */
@Immutable
data class TrendRowUi(
    val periodLabel: String,
    val incomeMinor: Long,
    val expensesMinor: Long,
    val netMinor: Long,
    val plannedIncomeMinor: Long? = null,
    val plannedExpensesMinor: Long? = null,
)

@Immutable
data class TrendTableLabels(
    val income: String,
    val expenses: String,
    val net: String,
    val scheduled: String,
)

/**
 * The text alternative of a trend chart: every period's dates, income, expenses and net as readable rows (each row one node
 * for a screen reader). Stacked rather than columned, so no amount is ever squeezed or clipped at 200% text.
 */
@Composable
fun TrendTable(
    rows: List<TrendRowUi>,
    currency: String,
    labels: TrendTableLabels,
    modifier: Modifier = Modifier,
    rowModifier: (Int) -> Modifier = { Modifier },
) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.s)) {
        rows.forEachIndexed { index, row ->
            Column(
                verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.xxs),
                modifier = rowModifier(index)
                    .fillMaxWidth()
                    .background(AppTheme.colors.sunken, AppTheme.shapes.tile)
                    .padding(AppTheme.spacing.s)
                    .semantics(mergeDescendants = true) {},
            ) {
                Text(row.periodLabel, style = AppTheme.type.label, color = AppTheme.colors.ink)
                ReportFigureRow(labels.income, formatMoney(row.incomeMinor, currency), FigureTone.Positive)
                ReportFigureRow(labels.expenses, formatMoney(row.expensesMinor, currency))
                ReportFigureRow(labels.net, formatMoney(row.netMinor, currency), emphasis = true)
                row.plannedIncomeMinor?.takeIf { it != 0L }?.let { ReportFigureRow(labels.scheduled + " · " + labels.income, formatMoney(it, currency), FigureTone.Muted) }
                row.plannedExpensesMinor?.takeIf { it != 0L }?.let { ReportFigureRow(labels.scheduled + " · " + labels.expenses, formatMoney(it, currency), FigureTone.Muted) }
            }
        }
    }
}

enum class FigureTone { Neutral, Positive, Muted }

/** A label at the start and an amount at the end, the amount always left to right (P-06.3). [amountText] is already formatted. */
@Composable
fun ReportFigureRow(
    label: String,
    amountText: String,
    tone: FigureTone = FigureTone.Neutral,
    modifier: Modifier = Modifier,
    emphasis: Boolean = false,
) {
    val color = when (tone) {
        FigureTone.Positive -> AppTheme.money.positive
        FigureTone.Muted -> AppTheme.colors.caption
        FigureTone.Neutral -> AppTheme.colors.ink
    }
    AdaptiveEndRow(
        modifier = modifier.fillMaxWidth().heightIn(min = 32.dp),
        gap = AppTheme.spacing.s,
        start = { Text(label, style = if (emphasis) AppTheme.type.itemTitle else AppTheme.type.body, color = AppTheme.colors.ink2) },
        end = {
            Text(
                amountText,
                style = (if (emphasis) AppTheme.type.numeralL else AppTheme.type.itemTitle).ltr(),
                color = color,
                textAlign = TextAlign.End,
            )
        },
    )
}

/** The summary of a report: a titled card holding [ReportFigureRow]s. */
@Composable
fun SummaryCard(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.xxs),
        modifier = modifier.fillMaxWidth().background(AppTheme.colors.surface, AppTheme.shapes.card).padding(AppTheme.spacing.m),
    ) {
        Text(title, style = AppTheme.type.title, color = AppTheme.colors.ink, modifier = Modifier.semantics { heading() })
        content()
    }
}

/** A thin separator between groups of rows in a [SummaryCard]. */
@Composable
fun SummaryDivider(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(1.dp).background(AppTheme.colors.outlineVariant))
}

/** One action of an [ExcludedWalletsNotice]. */
@Immutable
data class NoticeAction(val label: String, val tag: String)

/**
 * "Not included: Savings (USD)" with one action per other currency. Informational, never an error: wallets in another
 * currency are named, never silently dropped and never converted (FR-014).
 */
@Composable
fun ExcludedWalletsNotice(
    text: String,
    actions: List<NoticeAction>,
    onAction: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = AppTheme.colors
    Column(
        modifier = modifier.fillMaxWidth().background(colors.primaryContainer, AppTheme.shapes.tile).padding(AppTheme.spacing.s),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.xs)) {
            Icon(AppIcons.Info, contentDescription = null, tint = colors.onPrimaryContainer, modifier = Modifier.size(20.dp))
            Text(text, style = AppTheme.type.body, color = colors.ink, modifier = Modifier.weight(1f))
        }
        actions.forEachIndexed { index, action ->
            TextButton(
                onClick = { onAction(index) },
                modifier = Modifier.align(Alignment.End).heightIn(min = AppTheme.sizes.touchTarget).testTag(action.tag),
            ) {
                Text(action.label, style = AppTheme.type.label, color = colors.onPrimaryContainer)
            }
        }
    }
}
