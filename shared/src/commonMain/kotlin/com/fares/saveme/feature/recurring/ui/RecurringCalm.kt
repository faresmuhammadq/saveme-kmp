package com.fares.saveme.feature.recurring.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.fares.saveme.designsystem.component.CalmCard
import com.fares.saveme.designsystem.component.CalmDivider
import com.fares.saveme.designsystem.component.CalmIconTile
import com.fares.saveme.designsystem.component.MoneyText
import com.fares.saveme.designsystem.component.PreviewDateItem
import com.fares.saveme.designsystem.component.WalletStatusLine
import com.fares.saveme.designsystem.component.formatMoney
import com.fares.saveme.designsystem.format.formatDate
import com.fares.saveme.designsystem.icon.AppIcons
import com.fares.saveme.designsystem.theme.AppTheme
import com.fares.saveme.feature.recurring.domain.model.AmountBasis
import com.fares.saveme.feature.recurring.domain.model.OccurrenceState
import com.fares.saveme.feature.recurring.domain.model.OccurrenceView
import com.fares.saveme.feature.recurring.domain.model.Preset
import com.fares.saveme.feature.recurring.domain.model.RuleStatus
import com.fares.saveme.feature.recurring.domain.model.RuleView
import org.jetbrains.compose.resources.stringResource
import saveme.shared.generated.resources.Res
import saveme.shared.generated.resources.recurring_amount_of_total
import saveme.shared.generated.resources.recurring_cd_preview
import saveme.shared.generated.resources.recurring_next_date
import saveme.shared.generated.resources.recurring_next_none
import saveme.shared.generated.resources.recurring_payout
import saveme.shared.generated.resources.recurring_payout_amount
import saveme.shared.generated.resources.recurring_rule_title

internal class OccurrenceLook(val icon: ImageVector, val color: Color, val container: Color, val accented: Boolean)

@Composable
internal fun OccurrenceState.look(): OccurrenceLook = when (this) {
    OccurrenceState.Due -> OccurrenceLook(AppIcons.Info, AppTheme.money.pending, AppTheme.money.pendingContainer, true)
    OccurrenceState.Missed -> OccurrenceLook(AppIcons.Warning, AppTheme.money.over, AppTheme.money.overContainer, true)
    OccurrenceState.Confirmed -> OccurrenceLook(AppIcons.Check, AppTheme.money.positive, AppTheme.money.positiveContainer, false)
    OccurrenceState.Skipped -> OccurrenceLook(AppIcons.Minus, AppTheme.colors.ink2, AppTheme.colors.sunken, false)
    else -> OccurrenceLook(AppIcons.Calendar, AppTheme.colors.ink2, AppTheme.colors.sunken, false)
}

/** Small pill with an icon and words: a rule's status, a payout tag. */
@Composable
internal fun CalmStatePill(label: String, icon: ImageVector?, color: Color, container: Color, modifier: Modifier = Modifier) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = modifier
            .background(container, AppTheme.shapes.pill)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        if (icon != null) Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(14.dp))
        Text(label, style = AppTheme.type.caption.copy(fontWeight = FontWeight.Bold), color = color, maxLines = 1)
    }
}

@Composable
internal fun RuleStatus.calmPill(label: String, modifier: Modifier = Modifier) {
    when (this) {
        RuleStatus.Paused -> CalmStatePill(label, AppIcons.Minus, AppTheme.money.pending, AppTheme.money.pendingContainer, modifier)
        RuleStatus.Ended -> CalmStatePill(label, AppIcons.Close, AppTheme.colors.ink2, AppTheme.colors.sunken, modifier)
        else -> CalmStatePill(label, AppIcons.Check, AppTheme.money.positive, AppTheme.money.positiveContainer, modifier)
    }
}

/**
 * One occurrence as a calm card: state tile, rule title, shown amount, the state in words and the date, a payout tag and
 * the sync status. Due and missed ones get an accent border.
 */
@Composable
internal fun CalmOccurrenceCard(
    view: OccurrenceView,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    statusModifier: Modifier = Modifier,
) {
    val look = view.state.look()
    val kind = view.occurrence.kind.moneyKind()
    val shape = RoundedCornerShape(24.dp)
    CalmCard(
        modifier = modifier.then(if (look.accented) Modifier.border(1.dp, look.color, shape) else Modifier),
        padding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
        bordered = !look.accented,
        onClick = onClick,
    ) {
        Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            CalmIconTile(look.icon, look.color, look.container, size = 40.dp, iconSize = 20.dp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        view.ruleDescription?.takeIf { it.isNotBlank() } ?: stringResource(Res.string.recurring_rule_title),
                        style = AppTheme.type.itemTitle,
                        color = AppTheme.colors.ink,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    MoneyText(view.shownAmountMinor, view.currency, kind)
                }
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                    itemVerticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        stringResource(view.state.labelRes()),
                        style = AppTheme.type.caption.copy(fontWeight = FontWeight.Bold),
                        color = look.color,
                    )
                    Text(formatDate(view.shownDate), style = AppTheme.type.caption, color = AppTheme.colors.ink2)
                    if (view.isPayout) {
                        CalmStatePill(
                            stringResource(Res.string.recurring_payout), null,
                            AppTheme.colors.onPrimaryContainer, AppTheme.colors.primaryContainer,
                        )
                    }
                }
                view.syncState.statusLine()?.let { WalletStatusLine(it, statusModifier) }
            }
        }
    }
}

/** One rule as a calm card: icon tile, title, cadence, next date, amount (or the share of a total) and status. */
@Composable
internal fun CalmRuleCard(
    view: RuleView,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    val draft = view.shown
    val rule = view.rule
    val noNext = stringResource(Res.string.recurring_next_none)
    val next = view.nextDate?.let { stringResource(Res.string.recurring_next_date, formatDate(it)) } ?: noNext
    val note = when {
        draft.amountBasis == AmountBasis.Total && draft.occurrenceCount != null ->
            stringResource(Res.string.recurring_amount_of_total, formatMoney(draft.amountMinor / draft.occurrenceCount, rule.currency), formatMoney(draft.amountMinor, rule.currency))
        draft.preset == Preset.Gameya && draft.occurrenceCount != null ->
            stringResource(Res.string.recurring_payout_amount, formatMoney(draft.amountMinor * draft.occurrenceCount, rule.currency))
        else -> null
    }
    CalmCard(modifier = modifier, padding = PaddingValues(horizontal = 16.dp, vertical = 14.dp), onClick = onClick) {
        Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            CalmIconTile(AppIcons.Cycle, AppTheme.colors.primary, AppTheme.colors.primaryContainer, size = 40.dp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        draft.description?.takeIf { it.isNotBlank() } ?: stringResource(Res.string.recurring_rule_title),
                        style = AppTheme.type.itemTitle,
                        color = AppTheme.colors.ink,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Column(horizontalAlignment = Alignment.End) {
                        MoneyText(
                            if (draft.amountBasis == AmountBasis.Total && draft.occurrenceCount != null) draft.amountMinor / draft.occurrenceCount else draft.amountMinor,
                            rule.currency,
                            draft.kind.moneyKind(),
                        )
                        if (note != null) Text(note, style = AppTheme.type.caption, color = AppTheme.colors.caption, maxLines = 1)
                    }
                }
                Text(
                    cadenceText(draft.frequency, draft.interval, draft.anchorDay, draft.startDate),
                    style = AppTheme.type.caption,
                    color = AppTheme.colors.ink2,
                )
                Text(next, style = AppTheme.type.caption, color = AppTheme.colors.ink2)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                    rule.status.calmPill(stringResource(rule.status.labelRes()))
                }
                view.syncState.statusLine()?.let { WalletStatusLine(it) }
            }
        }
    }
}

/** 40dp outlined pill action (edit, pause, ...); the touch area stays 48dp. */
@Composable
internal fun CalmPillAction(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    tint: Color = AppTheme.colors.ink,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .heightIn(min = 48.dp)
            .semantics { if (!enabled) disabled() }
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier
                .heightIn(min = 40.dp)
                .background(AppTheme.colors.surface, AppTheme.shapes.pill)
                .border(1.dp, AppTheme.colors.outlineVariant, AppTheme.shapes.pill)
                .padding(horizontal = 16.dp),
        ) {
            Icon(icon, contentDescription = null, tint = if (enabled) tint else AppTheme.colors.outline, modifier = Modifier.size(18.dp))
            Text(
                label,
                style = AppTheme.type.caption.copy(fontWeight = FontWeight.Bold),
                color = if (enabled) tint else AppTheme.colors.outline,
            )
        }
    }
}

/** A row of [CalmPillAction]s that wraps. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun CalmPillRow(content: @Composable () -> Unit) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        itemVerticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(),
    ) { content() }
}

/** A label on the start and its value on the end, for the detail cards. */
@Composable
internal fun CalmLabelValue(label: String, value: String, modifier: Modifier = Modifier) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier.fillMaxWidth().padding(vertical = 8.dp),
    ) {
        Text(label, style = AppTheme.type.body, color = AppTheme.colors.ink2, modifier = Modifier.weight(1f))
        Text(value, style = AppTheme.type.body.copy(fontWeight = FontWeight.Bold), color = AppTheme.colors.ink)
    }
}

/** Card of rows separated by hairlines. */
@Composable
internal fun CalmRows(rows: List<@Composable () -> Unit>, modifier: Modifier = Modifier) {
    CalmCard(modifier = modifier, padding = PaddingValues(horizontal = 18.dp, vertical = 6.dp)) {
        rows.forEachIndexed { index, row ->
            if (index > 0) CalmDivider()
            row()
        }
    }
}

/** The phone's own preview of the next dates, in a calm card. */
@Composable
internal fun CalmPreviewCard(
    items: List<PreviewDateItem>,
    currency: String,
    label: String,
    modifier: Modifier = Modifier,
    caption: String? = null,
    emptyText: String? = null,
) {
    val description = stringResource(Res.string.recurring_cd_preview)
    CalmCard(modifier = modifier.semantics { contentDescription = description }) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(label, style = AppTheme.type.label, color = AppTheme.colors.ink2)
            if (items.isEmpty() && emptyText != null) Text(emptyText, style = AppTheme.type.body, color = AppTheme.colors.ink2)
            items.forEach { item ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 32.dp),
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(item.dateText, style = AppTheme.type.body, color = AppTheme.colors.ink)
                        if (item.tag != null) {
                            CalmStatePill(item.tag, null, AppTheme.money.positive, AppTheme.money.positiveContainer, Modifier.padding(top = 2.dp))
                        }
                    }
                    MoneyText(item.amountMinor, currency, item.kind)
                }
            }
            if (caption != null) Text(caption, style = AppTheme.type.caption, color = AppTheme.colors.caption)
        }
    }
}
