package com.fares.saveme.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.fares.saveme.designsystem.icon.AppIcons
import com.fares.saveme.designsystem.theme.AppTheme

/** One capability line of a [PlanCard]; [included] false draws a dash instead of a check. */
data class PlanCapability(val text: String, val included: Boolean = true)

/**
 * One plan in the catalogue or the person's own: name, optional period line, optional price,
 * capability lines. [currentLabel] non-null marks the person's plan with a text label and an
 * outline (never colour alone, P-08). Announced as one item. Shows no purchase action (P-12.2).
 */
@Composable
fun PlanCard(
    name: String,
    capabilities: List<PlanCapability>,
    modifier: Modifier = Modifier,
    periodLine: String? = null,
    priceMinor: Long? = null,
    priceCurrency: String? = null,
    currentLabel: String? = null,
    nameModifier: Modifier = Modifier,
    priceModifier: Modifier = Modifier,
    currentLabelModifier: Modifier = Modifier,
) {
    val colors = AppTheme.colors
    val border = if (currentLabel != null) 2.dp to colors.primary else Dp.Hairline to colors.outlineVariant
    Column(
        verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.xs),
        modifier = modifier
            .fillMaxWidth()
            .background(colors.surface, AppTheme.shapes.card)
            .border(border.first, border.second, AppTheme.shapes.card)
            .padding(AppTheme.spacing.m)
            .semantics(mergeDescendants = true) {},
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.xs)) {
            Text(name, style = AppTheme.type.itemTitle, color = colors.ink, modifier = nameModifier.weight(1f))
            if (currentLabel != null) {
                Text(
                    currentLabel,
                    style = AppTheme.type.label,
                    color = colors.onPrimaryContainer,
                    modifier = currentLabelModifier
                        .background(colors.primaryContainer, AppTheme.shapes.pill)
                        .padding(horizontal = AppTheme.spacing.s, vertical = AppTheme.spacing.xxs),
                )
            }
        }
        if (periodLine != null) {
            Text(periodLine, style = AppTheme.type.caption, color = colors.caption)
        }
        if (priceMinor != null && priceCurrency != null) {
            MoneyText(priceMinor, priceCurrency, MoneyKind.Balance, modifier = priceModifier)
        }
        capabilities.forEach { line ->
            Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.xs)) {
                Icon(
                    if (line.included) AppIcons.Check else AppIcons.Minus,
                    contentDescription = null,
                    tint = if (line.included) colors.ink2 else colors.caption,
                    modifier = Modifier.size(20.dp),
                )
                Text(line.text, style = AppTheme.type.body, color = colors.ink2, modifier = Modifier.weight(1f))
            }
        }
    }
}
