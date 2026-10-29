package com.fares.saveme.feature.reports.ui.reports

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fares.saveme.designsystem.component.TrendPointUi
import com.fares.saveme.designsystem.component.formatMoney
import com.fares.saveme.designsystem.money.MoneyFormatter
import com.fares.saveme.designsystem.theme.AppTheme

/** Ring of category shares; whatever the slices leave over shows as the track. [slices] are (fraction of the whole, colour). */
@Composable
internal fun CalmDonut(slices: List<Pair<Float, Color>>, modifier: Modifier = Modifier, content: @Composable () -> Unit = {}) {
    val track = AppTheme.colors.sunken2
    Box(contentAlignment = Alignment.Center, modifier = modifier) {
        Canvas(Modifier.matchParentSize()) {
            val stroke = size.minDimension * 0.14f
            val topLeft = Offset(stroke / 2, stroke / 2)
            val arc = Size(size.width - stroke, size.height - stroke)
            drawArc(track, 0f, 360f, false, topLeft, arc, style = Stroke(stroke))
            var start = -90f
            slices.forEach { (fraction, color) ->
                val sweep = 360f * fraction
                drawArc(color, start + 1.5f, (sweep - 3f).coerceAtLeast(0.5f), false, topLeft, arc, style = Stroke(stroke))
                start += sweep
            }
        }
        content()
    }
}

/** One bar per period, the newest in the primary colour; scheduled money stacks on top in the tinted container. Up to six periods share the width; more scroll. */
@Composable
internal fun CalmTrendBars(
    points: List<TrendPointUi>,
    summary: String,
    modifier: Modifier = Modifier,
    currency: String = "",
    incomeLabel: String = "",
    expensesLabel: String = "",
) {
    val colors = AppTheme.colors
    val figures = if (currency.isEmpty()) emptyList() else points.map { formatMoney(it.incomeMinor, currency) to formatMoney(it.expensesMinor, currency) }
    val description = summary + figures.mapIndexed { i, (income, expenses) ->
        ". ${points[i].periodLabel}: $incomeLabel $income, $expensesLabel $expenses"
    }.joinToString(separator = "")
    val scale = points.maxOfOrNull { it.expensesMinor + (it.plannedExpensesMinor ?: 0L) }?.coerceAtLeast(1L) ?: 1L
    val spread = points.size <= 6
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier
            .fillMaxWidth()
            .height(142.dp)
            .then(if (spread) Modifier else Modifier.horizontalScroll(rememberScrollState()))
            .clearAndSetSemantics { contentDescription = description },
    ) {
        points.forEachIndexed { index, point ->
            val newest = index == points.lastIndex
            val actual = (point.expensesMinor / scale.toFloat()).coerceIn(0f, 1f)
            val planned = ((point.plannedExpensesMinor ?: 0L) / scale.toFloat()).coerceIn(0f, 1f)
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = (if (spread) Modifier.weight(1f) else Modifier.width(44.dp)).fillMaxHeight(),
            ) {
                Box(contentAlignment = Alignment.BottomCenter, modifier = Modifier.weight(1f).fillMaxWidth()) {
                    Column(Modifier.fillMaxWidth().fillMaxHeight((actual + planned).coerceIn(0.03f, 1f))) {
                        if (planned > 0f) {
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .weight(planned)
                                    .background(colors.primaryContainer, RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp)),
                            )
                        }
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .weight(actual.coerceAtLeast(0.001f))
                                .background(if (newest) colors.primary else colors.sunken, RoundedCornerShape(8.dp)),
                        )
                    }
                }
                Text(
                    point.periodLabel,
                    style = AppTheme.type.caption.copy(fontSize = 11.sp, fontWeight = if (newest) FontWeight.ExtraBold else FontWeight.SemiBold),
                    color = if (newest) colors.primary else colors.ink2,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
