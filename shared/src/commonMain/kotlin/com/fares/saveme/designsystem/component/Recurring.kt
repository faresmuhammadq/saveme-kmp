@file:OptIn(ExperimentalLayoutApi::class)

package com.fares.saveme.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.fares.saveme.designsystem.icon.AppIcons
import com.fares.saveme.designsystem.theme.AppTheme
import org.jetbrains.compose.resources.stringResource
import saveme.shared.generated.resources.Res
import saveme.shared.generated.resources.recurring_cd_preview

/** How an occurrence stands on its date. Shown as an icon and words, never colour alone (P-08.3). */
enum class OccurrenceVisualState { Upcoming, Due, Missed, Confirmed, Skipped }

/** A rule's status pill. */
enum class RuleStatePill { Active, Paused, Ended }

@Composable
private fun OccurrenceVisualState.visual(): Pair<ImageVector, Color> = when (this) {
    OccurrenceVisualState.Upcoming -> AppIcons.Calendar to AppTheme.colors.ink2
    OccurrenceVisualState.Due -> AppIcons.Info to AppTheme.money.pending
    OccurrenceVisualState.Missed -> AppIcons.Warning to AppTheme.money.over
    OccurrenceVisualState.Confirmed -> AppIcons.Check to AppTheme.money.positive
    OccurrenceVisualState.Skipped -> AppIcons.Minus to AppTheme.colors.ink2
}

/** A small tag such as "Payout". */
@Composable
private fun TagPill(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = AppTheme.type.caption,
        color = AppTheme.colors.onPrimaryContainer,
        maxLines = 1,
        softWrap = false,
        modifier = modifier
            .background(AppTheme.colors.primaryContainer, AppTheme.shapes.pill)
            .padding(horizontal = AppTheme.spacing.s, vertical = AppTheme.spacing.xxs),
    )
}

/**
 * One occurrence of a recurring rule: its date and title, its state as icon + [stateLabel], an optional [payoutLabel]
 * tag, an optional sync [status], and the amount. An occurrence is not a transaction: a calendar tile leads the row and
 * its state is spelled out, so it never reads as one (FR-020).
 */
@Composable
fun OccurrenceRow(
    dateText: String,
    title: String,
    amountMinor: Long,
    currency: String,
    kind: MoneyKind,
    state: OccurrenceVisualState,
    stateLabel: String,
    modifier: Modifier = Modifier,
    payoutLabel: String? = null,
    status: WalletCardStatus? = null,
    onClick: (() -> Unit)? = null,
    directionLabel: String? = null,
    amountModifier: Modifier = Modifier,
    statusModifier: Modifier = Modifier,
) {
    val (icon, color) = state.visual()
    val amountText = formatMoney(
        if (kind == MoneyKind.Expense) -kotlin.math.abs(amountMinor) else kotlin.math.abs(amountMinor),
        currency,
        showPlus = kind == MoneyKind.Income,
    )
    val showTile = LocalDensity.current.fontScale <= 1.3f
    Row(
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.s),
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = AppTheme.sizes.rowTwoLine)
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
            .padding(horizontal = AppTheme.spacing.m, vertical = AppTheme.spacing.s),
    ) {
        if (showTile) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(AppTheme.sizes.iconTile).background(AppTheme.colors.sunken, AppTheme.shapes.tile),
            ) {
                Icon(AppIcons.Calendar, contentDescription = null, tint = AppTheme.colors.ink2)
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.xxs)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.s)) {
                Text(
                    title, style = AppTheme.type.itemTitle, color = AppTheme.colors.ink, maxLines = 2, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                MoneyText(
                    amountMinor, currency, kind,
                    modifier = amountModifier.then(
                        if (directionLabel != null) Modifier.semantics { contentDescription = "$directionLabel $amountText" } else Modifier,
                    ),
                )
            }
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.xs),
                verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.xxs),
                itemVerticalAlignment = Alignment.CenterVertically,
            ) {
                Text(dateText, style = AppTheme.type.caption, color = AppTheme.colors.caption)
                if (payoutLabel != null) TagPill(payoutLabel)
            }
            Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.xxs)) {
                Icon(icon, contentDescription = null, tint = color, modifier = Modifier.padding(top = 2.dp).size(14.dp))
                Text(stateLabel, style = AppTheme.type.caption, color = color)
            }
            if (status != null) WalletStatusLine(status, statusModifier)
        }
    }
}

/**
 * One recurring rule: title, its cadence in words, the next date, the amount (or [amountNote] such as "833.33 of
 * 10,000.00") and a status pill. [pillLabel] is the words of [pill]; sync [status] is icon + words.
 */
@Composable
fun RuleRow(
    title: String,
    cadence: String,
    nextText: String?,
    amountMinor: Long,
    currency: String,
    kind: MoneyKind,
    pill: RuleStatePill,
    pillLabel: String,
    modifier: Modifier = Modifier,
    amountNote: String? = null,
    status: WalletCardStatus? = null,
    onClick: (() -> Unit)? = null,
    amountModifier: Modifier = Modifier,
    statusModifier: Modifier = Modifier,
) {
    val showTile = LocalDensity.current.fontScale <= 1.3f
    Row(
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.s),
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = AppTheme.sizes.rowTwoLine)
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
            .padding(horizontal = AppTheme.spacing.m, vertical = AppTheme.spacing.s),
    ) {
        if (showTile) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(AppTheme.sizes.iconTile).background(AppTheme.colors.primaryContainer, AppTheme.shapes.tile),
            ) {
                Icon(AppIcons.Cycle, contentDescription = null, tint = AppTheme.colors.onPrimaryContainer)
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.xxs)) {
            Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.s)) {
                Text(
                    title, style = AppTheme.type.itemTitle, color = AppTheme.colors.ink, maxLines = 2, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Column(horizontalAlignment = Alignment.End) {
                    MoneyText(amountMinor, currency, kind, modifier = amountModifier)
                    if (amountNote != null) Text(amountNote, style = AppTheme.type.caption.ltr(), color = AppTheme.colors.caption, maxLines = 1)
                }
            }
            Text(cadence, style = AppTheme.type.caption, color = AppTheme.colors.caption)
            if (nextText != null) Text(nextText, style = AppTheme.type.caption, color = AppTheme.colors.caption)
            StatePill(pill, pillLabel)
            if (status != null) WalletStatusLine(status, statusModifier)
        }
    }
}

@Composable
private fun StatePill(pill: RuleStatePill, label: String) {
    val (icon, color, container) = when (pill) {
        RuleStatePill.Active -> Triple(AppIcons.Check, AppTheme.money.positive, AppTheme.money.positiveContainer)
        RuleStatePill.Paused -> Triple(AppIcons.Minus, AppTheme.money.pending, AppTheme.money.pendingContainer)
        RuleStatePill.Ended -> Triple(AppIcons.Close, AppTheme.colors.ink2, AppTheme.colors.sunken)
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.xxs),
        modifier = Modifier
            .background(container, AppTheme.shapes.pill)
            .padding(horizontal = AppTheme.spacing.s, vertical = AppTheme.spacing.xxs),
    ) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(12.dp))
        Text(label, style = AppTheme.type.caption, color = color)
    }
}

/**
 * Home's card for what is due: [countText] ("3 due"), the first item's title and date, and a chevron. The whole card is
 * the 48 dp+ target; [contentDescription] names it for screen readers.
 */
@Composable
fun DueCard(
    countText: String,
    firstTitle: String,
    firstDate: String,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    onClick: (() -> Unit)? = null,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.s),
        modifier = modifier
            .fillMaxWidth()
            .background(AppTheme.money.pendingContainer, AppTheme.shapes.card)
            .border(androidx.compose.ui.unit.Dp.Hairline, AppTheme.colors.outlineVariant, AppTheme.shapes.card)
            .then(if (onClick != null) Modifier.clip(AppTheme.shapes.card).clickable(role = Role.Button, onClick = onClick) else Modifier)
            .then(if (contentDescription != null) Modifier.semantics { this.contentDescription = contentDescription } else Modifier)
            .heightIn(min = AppTheme.sizes.rowTwoLine)
            .padding(AppTheme.spacing.m),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(AppTheme.sizes.iconTile).background(AppTheme.colors.surface, AppTheme.shapes.tile),
        ) {
            Icon(AppIcons.Info, contentDescription = null, tint = AppTheme.money.pending)
        }
        Column(Modifier.weight(1f)) {
            Text(countText, style = AppTheme.type.itemTitle, color = AppTheme.colors.ink, maxLines = 1)
            Text("$firstTitle · $firstDate", style = AppTheme.type.caption, color = AppTheme.colors.ink2, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Icon(AppIcons.ChevronForward, contentDescription = null, tint = AppTheme.colors.ink2)
    }
}

/** One dated line of [PreviewDateList]; [tag] marks a payout. */
data class PreviewDateItem(
    val dateText: String,
    val amountMinor: Long,
    val kind: MoneyKind,
    val tag: String? = null,
)

/**
 * The editor's and rule detail's compact list of upcoming dates and amounts, headed by [label] ("Preview", "Dates appear
 * after sync"). It is a calculation on this phone, not the server's answer, so it sits on the sunken surface.
 */
@Composable
fun PreviewDateList(
    items: List<PreviewDateItem>,
    currency: String,
    label: String,
    modifier: Modifier = Modifier,
    caption: String? = null,
    emptyText: String? = null,
) {
    val description = stringResource(Res.string.recurring_cd_preview)
    Column(
        verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.xs),
        modifier = modifier
            .fillMaxWidth()
            .background(AppTheme.colors.sunken, AppTheme.shapes.card)
            .padding(AppTheme.spacing.m)
            .semantics { contentDescription = description },
    ) {
        Text(label, style = AppTheme.type.label, color = AppTheme.colors.ink2)
        if (items.isEmpty() && emptyText != null) Text(emptyText, style = AppTheme.type.body, color = AppTheme.colors.ink2)
        items.forEach { item ->
            FlowRow(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.xxs),
                itemVerticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().heightIn(min = 32.dp),
            ) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.xs),
                    verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.xxs),
                    itemVerticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(item.dateText, style = AppTheme.type.body, color = AppTheme.colors.ink)
                    if (item.tag != null) TagPill(item.tag)
                }
                MoneyText(item.amountMinor, currency, item.kind)
            }
        }
        if (caption != null) Text(caption, style = AppTheme.type.caption, color = AppTheme.colors.caption)
    }
}
