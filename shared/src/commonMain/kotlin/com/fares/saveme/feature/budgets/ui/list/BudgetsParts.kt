package com.fares.saveme.feature.budgets.ui.list

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import com.fares.saveme.designsystem.component.BudgetState
import com.fares.saveme.designsystem.component.WalletCardStatus
import com.fares.saveme.designsystem.component.WalletCardStatusKind
import com.fares.saveme.designsystem.component.WalletStatusLine
import com.fares.saveme.designsystem.component.budgetPercent
import com.fares.saveme.designsystem.component.budgetState
import com.fares.saveme.designsystem.component.formatMoney
import com.fares.saveme.designsystem.icon.AppIcons
import com.fares.saveme.designsystem.icon.CategoryIcons
import com.fares.saveme.designsystem.money.MoneyFormatter
import com.fares.saveme.designsystem.theme.AppTheme
import com.fares.saveme.feature.budgets.ui.BudgetRowUi
import com.fares.saveme.feature.budgets.ui.BudgetStatusUi
import com.fares.saveme.feature.budgets.ui.RowFigures
import com.fares.saveme.feature.budgets.ui.budgetName
import org.jetbrains.compose.resources.stringResource
import saveme.shared.generated.resources.Res
import saveme.shared.generated.resources.budget_percent
import saveme.shared.generated.resources.budget_spent_of
import saveme.shared.generated.resources.budgets_calm_left_of
import saveme.shared.generated.resources.budgets_calm_on_pace
import saveme.shared.generated.resources.budgets_calm_over_of
import saveme.shared.generated.resources.budgets_calm_tile_left
import saveme.shared.generated.resources.budgets_calm_tile_over
import saveme.shared.generated.resources.budgets_cd_open
import saveme.shared.generated.resources.budgets_includes_waiting
import saveme.shared.generated.resources.budgets_limit_line
import saveme.shared.generated.resources.budgets_newer_version
import saveme.shared.generated.resources.budgets_other_currencies
import saveme.shared.generated.resources.budgets_status_clash
import saveme.shared.generated.resources.budgets_status_deleted
import saveme.shared.generated.resources.budgets_status_not_saved
import saveme.shared.generated.resources.budgets_status_waiting
import saveme.shared.generated.resources.budgets_unknown_progress

/** Calm variant of the budget progress: a ring with the percentage inside, in the colour of its state. */
@Composable
internal fun BudgetRing(
    percent: Int,
    fill: Color,
    track: Color,
    ringSize: Dp,
    stroke: Dp,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(contentAlignment = Alignment.Center, modifier = modifier.size(ringSize)) {
        Canvas(Modifier.fillMaxSize()) {
            val width = stroke.toPx()
            // The mock draws the ring's centre line at 5/6 of the box (r 50 of 120, r 20 of 48).
            val diameter = size.width * 5f / 6f
            val arc = Size(diameter, diameter)
            val topLeft = Offset((size.width - diameter) / 2, (size.height - diameter) / 2)
            drawArc(track, 0f, 360f, false, topLeft, arc, style = Stroke(width))
            val sweep = 360f * percent.coerceIn(0, 100) / 100f
            if (sweep > 0f) drawArc(fill, -90f, sweep, false, topLeft, arc, style = Stroke(width, cap = StrokeCap.Round))
        }
        content()
    }
}

private class Tone(val ring: Color, val chip: Color, val chipContainer: Color)

@Composable
private fun toneOf(state: BudgetState): Tone {
    val money = AppTheme.money
    return when (state) {
        BudgetState.Positive -> Tone(AppTheme.colors.primary, money.positive, money.positiveContainer)
        BudgetState.Pending -> Tone(money.pending, money.pending, money.pendingContainer)
        BudgetState.Over -> Tone(money.over, money.over, money.overContainer)
    }
}

@Composable
private fun BudgetSurface(
    container: Color,
    bordered: Boolean,
    radius: Dp,
    padding: PaddingValues,
    row: BudgetRowUi,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    verticalArrangement: Arrangement.Vertical = Arrangement.Top,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(radius)
    val description = stringResource(Res.string.budgets_cd_open, budgetName(row.categoryName))
    Column(
        verticalArrangement = verticalArrangement,
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(container, shape)
            .then(if (bordered) Modifier.border(1.dp, AppTheme.colors.outlineVariant, shape) else Modifier)
            .clickable(role = Role.Button, onClickLabel = description, onClick = onClick)
            .padding(padding),
        content = content,
    )
}

@Composable
private fun spentOf(row: BudgetRowUi, figures: RowFigures): String =
    stringResource(
        Res.string.budget_spent_of,
        formatMoney(figures.spentMinor, row.currency),
        MoneyFormatter.number(row.limitMinor, MoneyFormatter.digitsFor(row.currency)),
    )

/** A grouped number without the currency and without a zero fraction ("9,700"), as the calm budgets write amounts. */
internal fun compactAmount(minor: Long, currency: String): String {
    val digits = MoneyFormatter.digitsFor(currency)
    val text = MoneyFormatter.number(if (minor < 0) -minor else minor, digits)
    val whole = if (digits > 0 && text.substringAfter('.').all { it == '0' }) text.substringBefore('.') else text
    return if (minor < 0) "-$whole" else whole
}

@Composable
private fun CaptionLine(text: String, modifier: Modifier = Modifier) {
    Text(text, style = AppTheme.type.caption, color = AppTheme.colors.caption, modifier = modifier)
}

@Composable
private fun BudgetStatusLine(status: BudgetStatusUi, modifier: Modifier) {
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
        modifier,
    )
}

@Composable
private fun ExtraCaptions(figures: RowFigures) {
    if (figures.otherCurrencies.isNotEmpty()) {
        CaptionLine(stringResource(Res.string.budgets_other_currencies, figures.otherCurrencies.joinToString(", ")))
    }
    if (figures.includesWaiting) CaptionLine(stringResource(Res.string.budgets_includes_waiting))
}

/** The all-expenses budget: a large ring beside the amount left, "left of" the limit and the pace chip. */
@Composable
internal fun BudgetHero(row: BudgetRowUi, figures: RowFigures, tag: String, onIntent: (BudgetsIntent) -> Unit) {
    val colors = AppTheme.colors
    val state = budgetState(figures.spentMinor, row.limitMinor)
    val percent = budgetPercent(figures.spentMinor, row.limitMinor)
    val tone = toneOf(state)
    val over = state == BudgetState.Over
    val limit = compactAmount(row.limitMinor, row.currency)
    val spentOfText = spentOf(row, figures)
    BudgetSurface(
        container = colors.surface, bordered = true, radius = 24.dp, padding = PaddingValues(18.dp), row = row,
        onClick = { onIntent(BudgetsIntent.BudgetClicked(row.id)) },
        modifier = Modifier.testTag(tag).semantics { stateDescription = spentOfText },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            BudgetRing(percent, tone.ring, colors.sunken, 112.dp, 11.2.dp) {
                Text(
                    stringResource(Res.string.budget_percent, percent),
                    style = AppTheme.type.numeralL.copy(fontSize = 22.sp),
                    color = colors.ink,
                )
            }
            Column(Modifier.weight(1f)) {
                Text(budgetName(row.categoryName), style = AppTheme.type.label.copy(fontSize = 13.sp), color = colors.ink2)
                Text(
                    compactAmount(if (over) figures.spentMinor - row.limitMinor else row.limitMinor - figures.spentMinor, row.currency),
                    style = AppTheme.type.numeralL.copy(fontSize = 28.sp, letterSpacing = (-0.03).em),
                    color = if (over) AppTheme.money.over else colors.ink,
                    maxLines = 1,
                    modifier = Modifier.padding(top = 2.dp),
                )
                Text(
                    if (over) stringResource(Res.string.budgets_calm_over_of, row.currency, limit) else stringResource(Res.string.budgets_calm_left_of, row.currency, limit),
                    style = AppTheme.type.caption.copy(fontSize = 12.5.sp),
                    color = colors.ink2,
                )
                figures.projectedMinor?.let { projected ->
                    Text(
                        stringResource(Res.string.budgets_calm_on_pace, compactAmount(projected, row.currency)),
                        style = AppTheme.type.caption.copy(fontSize = 11.5.sp, fontWeight = FontWeight.Bold),
                        color = tone.chip,
                        modifier = Modifier
                            .padding(top = 8.dp)
                            .background(tone.chipContainer, AppTheme.shapes.pill)
                            .padding(horizontal = 9.dp, vertical = 4.dp),
                    )
                }
                ExtraCaptions(figures)
                row.status?.let { BudgetStatusLine(it, Modifier.testTag("$tag.status")) }
            }
        }
    }
}

/** One budget as a half-width tile: ring around the category icon, the name, and "N left · P%". */
@Composable
internal fun BudgetTile(row: BudgetRowUi, tag: String, onIntent: (BudgetsIntent) -> Unit, modifier: Modifier = Modifier) {
    val colors = AppTheme.colors
    val figures = row.figures
    val state = figures?.let { budgetState(it.spentMinor, row.limitMinor) }
    val over = state == BudgetState.Over
    val name = budgetName(row.categoryName)
    val spentOfText = figures?.let { spentOf(row, it) }
    BudgetSurface(
        container = if (over) AppTheme.money.overContainer else colors.surface,
        bordered = !over, radius = 22.dp, padding = PaddingValues(14.dp), row = row,
        onClick = { onIntent(BudgetsIntent.BudgetClicked(row.id)) },
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = modifier.testTag(tag).semantics { if (spentOfText != null) stateDescription = spentOfText },
    ) {
        if (figures != null && state != null) {
            val tone = toneOf(state)
            val percent = budgetPercent(figures.spentMinor, row.limitMinor)
            BudgetRing(percent, tone.ring, if (over) colors.surface else colors.sunken, 56.dp, 5.8.dp) {
                Icon(
                    if (row.categoryName == null) AppIcons.Budgets else CategoryIcons.of(row.iconKey),
                    contentDescription = null,
                    tint = tone.ring,
                    modifier = Modifier.size(20.dp),
                )
            }
            Column {
                Text(
                    name,
                    style = AppTheme.type.label.copy(fontSize = 14.sp, fontWeight = FontWeight.Bold),
                    color = if (over) AppTheme.money.over else colors.primary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                val percentText = stringResource(Res.string.budget_percent, percent)
                Text(
                    if (over) {
                        stringResource(Res.string.budgets_calm_tile_over, compactAmount(figures.spentMinor - row.limitMinor, row.currency), percentText)
                    } else {
                        stringResource(Res.string.budgets_calm_tile_left, compactAmount(row.limitMinor - figures.spentMinor, row.currency), percentText)
                    },
                    style = AppTheme.type.caption.copy(fontSize = 12.5.sp, fontWeight = if (over) FontWeight.Bold else FontWeight.SemiBold),
                    color = if (over) AppTheme.money.over else colors.ink2,
                    modifier = Modifier.padding(top = 2.dp),
                )
                ExtraCaptions(figures)
            }
        } else {
            Column {
                Text(name, style = AppTheme.type.label.copy(fontSize = 14.sp, fontWeight = FontWeight.Bold), color = colors.primary, maxLines = 2)
                Text(
                    stringResource(Res.string.budgets_limit_line, formatMoney(row.limitMinor, row.currency)),
                    style = AppTheme.type.caption.copy(fontSize = 12.5.sp),
                    color = colors.ink2,
                    modifier = Modifier.padding(top = 2.dp),
                )
                CaptionLine(stringResource(if (row.readOnly) Res.string.budgets_newer_version else Res.string.budgets_unknown_progress))
            }
        }
        row.status?.let { BudgetStatusLine(it, Modifier.testTag("$tag.status")) }
    }
}
