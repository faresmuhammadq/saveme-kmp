package com.fares.saveme.feature.reports.ui.categories

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fares.saveme.designsystem.component.CalmCard
import com.fares.saveme.designsystem.component.CalmChip
import com.fares.saveme.designsystem.component.CalmScreen
import com.fares.saveme.designsystem.component.CalmTitle
import com.fares.saveme.designsystem.component.Skeleton
import com.fares.saveme.designsystem.component.formatMoney
import com.fares.saveme.designsystem.icon.AppIcons
import com.fares.saveme.designsystem.icon.CategoryIcons
import com.fares.saveme.designsystem.money.MoneyFormatter
import com.fares.saveme.designsystem.theme.AppTheme
import com.fares.saveme.designsystem.tokens.CategoryColors
import com.fares.saveme.feature.reports.ui.CategoryRowUi
import com.fares.saveme.feature.reports.ui.rangeText
import com.fares.saveme.feature.reports.ui.reports.CategoryGroupKind
import com.fares.saveme.feature.reports.ui.reports.CategoryRow
import com.fares.saveme.feature.reports.ui.reports.RangeUi
import com.fares.saveme.feature.reports.ui.shareText
import kotlinx.datetime.LocalDate
import org.jetbrains.compose.resources.stringResource
import saveme.shared.generated.resources.Res
import saveme.shared.generated.resources.calm_rep_cats_earned
import saveme.shared.generated.resources.calm_rep_cats_spent
import saveme.shared.generated.resources.calm_rep_under_one
import saveme.shared.generated.resources.cd_back
import saveme.shared.generated.resources.category_unavailable
import saveme.shared.generated.resources.report_categories_expenses
import saveme.shared.generated.resources.report_categories_income
import saveme.shared.generated.resources.report_categories_title
import saveme.shared.generated.resources.reports_no_income
import saveme.shared.generated.resources.reports_no_spending
import saveme.shared.generated.resources.reports_scheduled_amount
import saveme.shared.generated.resources.reports_too_large
import saveme.shared.generated.resources.reports_waiting

@Composable
fun ReportCategoriesScreen(state: ReportCategoriesState, onIntent: (ReportCategoriesIntent) -> Unit) {
    val ready = state.status == ReportCategoriesStatus.Ready
    CalmScreen(
        title = null,
        onBack = { onIntent(ReportCategoriesIntent.BackClicked) },
        backDescription = stringResource(Res.string.cd_back),
        actions = {
            state.range?.let { range: RangeUi ->
                Text(
                    rangeText(range.from, range.to, state.thisYear),
                    style = AppTheme.type.label,
                    color = AppTheme.colors.ink2,
                )
            }
        },
        titleSlot = {
            Column {
                state.title?.let { Text(it, style = AppTheme.type.label, color = AppTheme.colors.ink2, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                CalmTitle(stringResource(Res.string.report_categories_title))
                if (ready && !state.tooLarge) {
                    val total = formatMoney(state.totalMinor, state.currency)
                    Text(
                        stringResource(
                            if (state.kind == CategoryGroupKind.Expenses) Res.string.calm_rep_cats_spent else Res.string.calm_rep_cats_earned,
                            total, state.rows.size,
                        ),
                        style = AppTheme.type.body,
                        color = AppTheme.colors.ink2,
                        modifier = Modifier.padding(top = 2.dp).testTag("report_categories.total"),
                    )
                }
            }
        },
    ) {
        if (!ready) {
            Column(verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.s), modifier = Modifier.fillMaxSize().testTag("report_categories.screen")) {
                Skeleton(Modifier.fillMaxWidth().height(96.dp), RoundedCornerShape(24.dp))
            }
        } else {
            Body(state, onIntent)
        }
    }
}

@Composable
private fun Body(state: ReportCategoriesState, onIntent: (ReportCategoriesIntent) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth().testTag("report_categories.screen")) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.testTag("report_categories.kind")) {
            CalmChip(
                stringResource(Res.string.report_categories_expenses),
                selected = state.kind == CategoryGroupKind.Expenses,
                onClick = { onIntent(ReportCategoriesIntent.KindPicked(CategoryGroupKind.Expenses)) },
                modifier = Modifier.testTag("report_categories.kind.${CategoryGroupKind.Expenses.wire}"),
            )
            CalmChip(
                stringResource(Res.string.report_categories_income),
                selected = state.kind == CategoryGroupKind.Income,
                onClick = { onIntent(ReportCategoriesIntent.KindPicked(CategoryGroupKind.Income)) },
                modifier = Modifier.testTag("report_categories.kind.${CategoryGroupKind.Income.wire}"),
            )
        }
        if (state.tooLarge) {
            Text(stringResource(Res.string.reports_too_large), style = AppTheme.type.body, color = AppTheme.colors.ink2)
        } else {
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
            } else {
                CategoryTiles(state.rows)
            }
        }
        if (state.includesWaiting) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.testTag("report_categories.waiting"),
            ) {
                Icon(AppIcons.Sync, contentDescription = null, tint = AppTheme.money.pending, modifier = Modifier.size(16.dp))
                Text(stringResource(Res.string.reports_waiting), style = AppTheme.type.caption, color = AppTheme.money.pending)
            }
        }
    }
}

/** Two large tiles, up to three medium ones and the rest as a list; tile size follows the order, which is the server's (largest first). */
@Composable
private fun CategoryTiles(rows: List<CategoryRowUi>) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth().height(174.dp)) {
            rows.take(LARGE_TILES).forEachIndexed { index, row ->
                CategoryTile(row, index, large = true, wide = false, modifier = Modifier.weight(1f).fillMaxHeight())
            }
        }
        val medium = rows.drop(LARGE_TILES).take(MEDIUM_TILES)
        if (medium.isNotEmpty()) {
            val weights = when (medium.size) {
                3 -> listOf(2f, 1f, 1f)
                2 -> listOf(1f, 1f)
                else -> listOf(1f)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth().height(84.dp)) {
                medium.forEachIndexed { index, row ->
                    CategoryTile(row, LARGE_TILES + index, large = false, wide = weights[index] > 1f, modifier = Modifier.weight(weights[index]).fillMaxHeight())
                }
            }
        }
    val rest = rows.drop(LARGE_TILES + MEDIUM_TILES)
    if (rest.isNotEmpty()) {
        var expanded by remember { mutableStateOf(false) }
        TailTile(rest, expanded, onToggle = { expanded = !expanded })
        if (expanded) {
            CalmCard(padding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 6.dp)) {
                rest.forEachIndexed { index, row -> CategoryRow(row, Modifier.testTag("report_categories.row.${LARGE_TILES + MEDIUM_TILES + index}")) }
            }
        }
    }
    }
}

/** The small categories as one line, "Coffee, Pets   45 · under 1%"; a tap lists them one by one. */
@Composable
private fun TailTile(rest: List<CategoryRowUi>, expanded: Boolean, onToggle: () -> Unit) {
    val unavailable = stringResource(Res.string.category_unavailable)
    val names = rest.joinToString(", ") { it.name.ifEmpty { unavailable } }
    val currency = rest.first().currency
    val sum = rest.sumOf { it.valueMinor }
    val fraction = rest.sumOf { it.fraction.toDouble() }
    val share = if (fraction < 0.01) stringResource(Res.string.calm_rep_under_one) else "${kotlin.math.round(fraction * 100).toInt()}%"
    val number = tileNumber(sum, currency)
    val lines = rest.map { row -> Triple(row.name.ifEmpty { unavailable }, shareText(row.share), formatMoney(row.valueMinor, row.currency)) }
    val description = lines.joinToString("; ") { (name, share, amount) -> listOf(name, share, amount).filter { it.isNotEmpty() }.joinToString(", ") }
    val shape = RoundedCornerShape(18.dp)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .height(84.dp)
            .testTag("report_categories.tail")
            .background(AppTheme.colors.surface, shape)
            .border(1.dp, AppTheme.colors.outlineVariant, shape)
            .clip(shape)
            .clickable(role = Role.Button, onClick = onToggle)
            .padding(horizontal = 12.dp)
            .semantics(mergeDescendants = true) { contentDescription = description },
    ) {
        Text(names, style = AppTheme.type.label, color = AppTheme.colors.ink, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        Text("$number · $share", style = AppTheme.type.caption.copy(fontWeight = FontWeight.SemiBold, textDirection = TextDirection.Ltr), color = AppTheme.colors.ink2, maxLines = 1)
    }
}

private const val LARGE_TILES = 2
private const val MEDIUM_TILES = 3

@Composable
private fun CategoryTile(row: CategoryRowUi, index: Int, large: Boolean, wide: Boolean, modifier: Modifier = Modifier) {
    val name = row.name.ifEmpty { stringResource(Res.string.category_unavailable) }
    val share = shareText(row.share)
    val amount = tileNumber(row.valueMinor, row.currency)
    val description = listOf(name, share, formatMoney(row.valueMinor, row.currency)).filter { it.isNotEmpty() }.joinToString(", ")
    Column(
        verticalArrangement = Arrangement.SpaceBetween,
        modifier = modifier
            .background(tileColor(row.colorHex), RoundedCornerShape(18.dp))
            .padding(horizontal = if (large || wide) 12.dp else 10.dp, vertical = 12.dp)
            .testTag("report_categories.row.$index")
            .clearAndSetSemantics { contentDescription = description },
    ) {
        if (large) {
            Icon(CategoryIcons.of(row.iconKey), contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
            Column {
                Text(name, style = AppTheme.type.label, color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis)
                BasicText(
                    text = amount,
                    style = AppTheme.type.numeralL.copy(color = Color.White, textDirection = TextDirection.Ltr),
                    maxLines = 1,
                    autoSize = TextAutoSize.StepBased(minFontSize = 12.sp, maxFontSize = 24.sp, stepSize = 1.sp),
                )
                Text(share, style = AppTheme.type.caption.copy(fontWeight = FontWeight.SemiBold), color = Color.White.copy(alpha = 0.9f))
            }
        } else {
            Text(name, style = AppTheme.type.label.copy(fontSize = 13.sp), color = Color.White, maxLines = 1, overflow = TextOverflow.Clip)
            Text(
                if (wide && share.isNotEmpty()) "$amount · $share" else amount,
                style = AppTheme.type.caption.copy(fontWeight = FontWeight.SemiBold, textDirection = TextDirection.Ltr),
                color = Color.White.copy(alpha = 0.9f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** The amount without its currency, and without ".00" when the amount is whole. */
private fun tileNumber(minor: Long, currency: String): String {
    val digits = MoneyFormatter.digitsFor(currency)
    var unit = 1L
    repeat(digits) { unit *= 10 }
    return if (minor % unit == 0L) MoneyFormatter.number(minor / unit * unit, digits).substringBefore('.') else MoneyFormatter.number(minor, digits)
}

/** The category colour darkened until white text on it reads at 4.5:1. */
private fun tileColor(hex: String?): Color {
    val swatch = CategoryColors.of(hex).swatch
    var color = swatch
    var step = 0
    while (CategoryColors.contrast(color, Color.White) < 4.5 && step < 20) {
        step++
        color = lerp(swatch, Color.Black, step * 0.05f)
    }
    return color
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
