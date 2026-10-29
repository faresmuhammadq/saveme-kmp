package com.fares.saveme.feature.recurring.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.fares.saveme.designsystem.component.CalmCard
import com.fares.saveme.designsystem.component.MoneyKind
import com.fares.saveme.designsystem.component.WalletStatusLine
import com.fares.saveme.designsystem.component.formatMoney
import com.fares.saveme.designsystem.format.formatDayMonth
import com.fares.saveme.designsystem.icon.AppIcons
import com.fares.saveme.designsystem.icon.RecurringGroups
import com.fares.saveme.designsystem.icon.RecurringSplit
import com.fares.saveme.designsystem.theme.AppTheme
import com.fares.saveme.feature.recurring.domain.model.OccurrenceState
import com.fares.saveme.feature.recurring.domain.model.OccurrenceView
import com.fares.saveme.feature.recurring.domain.model.Preset
import com.fares.saveme.feature.recurring.domain.model.RuleStatus
import com.fares.saveme.feature.recurring.domain.model.RuleView
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.todayIn
import org.jetbrains.compose.resources.stringArrayResource
import org.jetbrains.compose.resources.stringResource
import kotlin.time.Clock
import saveme.shared.generated.resources.Res
import saveme.shared.generated.resources.recurring_calm_change_it
import saveme.shared.generated.resources.recurring_calm_due_today
import saveme.shared.generated.resources.recurring_calm_due_tomorrow
import saveme.shared.generated.resources.recurring_calm_paid_of
import saveme.shared.generated.resources.recurring_calm_round_of
import saveme.shared.generated.resources.recurring_calm_this_week
import saveme.shared.generated.resources.recurring_calm_weekdays_short
import saveme.shared.generated.resources.recurring_confirm
import saveme.shared.generated.resources.recurring_rule_title
import saveme.shared.generated.resources.recurring_skip

/** The device's today; the calm list and sheet word dates relative to it. */
@Composable
internal fun rememberToday(): LocalDate = remember { Clock.System.todayIn(TimeZone.currentSystemDefault()) }

/** 15sp bold section heading of the calm recurring screens. */
@Composable
internal fun RecurringHeading(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = AppTheme.type.body.copy(fontSize = 15.sp, fontWeight = FontWeight.Bold),
        color = AppTheme.colors.ink,
        modifier = modifier.semantics { heading() },
    )
}

/** The state of a scheduled occurrence in words: "Due today" and "Due tomorrow" for the nearest due dates. */
@Composable
internal fun OccurrenceView.stateWords(today: LocalDate): String = when {
    state == OccurrenceState.Due && shownDate == today -> stringResource(Res.string.recurring_calm_due_today)
    state == OccurrenceState.Due && shownDate == today.plus(1, DateTimeUnit.DAY) -> stringResource(Res.string.recurring_calm_due_tomorrow)
    else -> stringResource(state.labelRes())
}

/** Money without a ".00" tail on whole amounts: "EGP 450", "EGP 2,000". */
@Composable
internal fun calmMoney(amountMinor: Long, currency: String, showPlus: Boolean = false): String =
    formatMoney(amountMinor, currency, showPlus).replace(Regex("[.,\u066B]00(?!\\d)"), "")

/** An amount as the calm cards show it: no minus on an expense, a plus and the positive colour on income. */
@Composable
internal fun CalmAmount(amountMinor: Long, currency: String, kind: MoneyKind, modifier: Modifier = Modifier, fontSize: Int = 16) {
    val income = kind == MoneyKind.Income
    Text(
        calmMoney(kotlin.math.abs(amountMinor), currency, showPlus = income),
        style = AppTheme.type.body.copy(fontSize = fontSize.sp, fontWeight = FontWeight.ExtraBold, textDirection = androidx.compose.ui.text.style.TextDirection.Ltr),
        color = if (income) AppTheme.money.positive else AppTheme.colors.ink,
        maxLines = 1,
        modifier = modifier,
    )
}

/** The seven days from two days ago, today marked, a dot under the days with something to do. */
@Composable
internal fun CalmWeekStrip(today: LocalDate, dots: Map<LocalDate, Color>, modifier: Modifier = Modifier) {
    val names = stringArrayResource(Res.array.recurring_calm_weekdays_short)
    val description = stringResource(Res.string.recurring_calm_this_week)
    val shape = RoundedCornerShape(20.dp)
    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = modifier
            .fillMaxWidth()
            .background(AppTheme.colors.surface, shape)
            .border(1.dp, AppTheme.colors.outlineVariant, shape)
            .padding(6.dp)
            .semantics { contentDescription = description },
    ) {
        for (offset in -2..4) {
            val day = today.plus(offset, DateTimeUnit.DAY)
            val selected = offset == 0
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (selected) AppTheme.colors.primary else Color.Transparent)
                    .padding(vertical = 8.dp),
            ) {
                Text(
                    names[day.dayOfWeek.ordinal],
                    style = AppTheme.type.caption.copy(fontSize = 11.sp, fontWeight = FontWeight.SemiBold),
                    color = if (selected) AppTheme.colors.onPrimary else AppTheme.colors.ink2,
                    maxLines = 1,
                )
                Text(
                    day.day.toString(),
                    style = AppTheme.type.body.copy(fontSize = 15.sp, fontWeight = FontWeight.Bold),
                    color = if (selected) AppTheme.colors.onPrimary else AppTheme.colors.ink,
                )
                Box(Modifier.size(5.dp).background(dots[day] ?: Color.Transparent, CircleShape))
            }
        }
    }
}

/** 36dp pill button of a card; the touch area stays 48dp. */
@Composable
internal fun CalmSmallButton(
    text: String,
    onClick: () -> Unit,
    container: Color,
    content: Color,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .heightIn(min = 48.dp)
            .semantics { if (!enabled) disabled() }
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .heightIn(min = 36.dp)
                .background(if (enabled) container else AppTheme.colors.sunken2, AppTheme.shapes.pill)
                .padding(horizontal = 14.dp),
        ) {
            Text(
                text,
                style = AppTheme.type.caption.copy(fontSize = 13.sp, fontWeight = FontWeight.Bold),
                color = if (enabled) content else AppTheme.colors.ink2,
                maxLines = 1,
            )
        }
    }
}

/**
 * A due or missed occurrence in "Needs you": title and amount, the state in words with the wallet or date, and
 * Confirm / Skip / Change it. Buttons are left out when [actions] is false (read-only, waiting, clashing).
 */
@Composable
internal fun CalmDueCard(
    view: OccurrenceView,
    walletName: String?,
    today: LocalDate,
    actions: Boolean,
    busy: Boolean,
    onOpen: () -> Unit,
    onConfirm: () -> Unit,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier,
    tag: (String) -> Modifier = { Modifier },
) {
    val look = view.state.look()
    val shape = RoundedCornerShape(22.dp)
    CalmCard(
        modifier = modifier.border(1.dp, look.color, shape),
        padding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
        bordered = false,
        radius = 22.dp,
        onClick = onOpen,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                view.ruleDescription?.takeIf { it.isNotBlank() } ?: stringResource(Res.string.recurring_rule_title),
                style = AppTheme.type.body.copy(fontSize = 15.sp, fontWeight = FontWeight.Bold),
                color = AppTheme.colors.ink,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            CalmAmount(view.shownAmountMinor, view.currency, view.occurrence.kind.moneyKind())
        }
        Row(Modifier.padding(top = 2.dp)) {
            val detail = if (view.state == OccurrenceState.Missed) null else walletName
            Text(view.stateWords(today), style = AppTheme.type.caption.copy(fontSize = 12.5.sp, fontWeight = FontWeight.Bold), color = look.color)
            Text(
                " · " + (detail ?: formatDayMonth(view.shownDate)),
                style = AppTheme.type.caption.copy(fontSize = 12.5.sp, fontWeight = FontWeight.Bold),
                color = look.color,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        view.syncState.statusLine()?.let { WalletStatusLine(it, Modifier.padding(top = 2.dp)) }
        if (actions) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                CalmSmallButton(
                    stringResource(Res.string.recurring_confirm), onConfirm, AppTheme.colors.primary, AppTheme.colors.onPrimary,
                    tag("confirm"), enabled = !busy,
                )
                CalmSmallButton(
                    stringResource(Res.string.recurring_skip), onSkip, AppTheme.colors.sunken, AppTheme.colors.ink,
                    tag("skip"), enabled = !busy,
                )
                if (view.state == OccurrenceState.Due) {
                    CalmSmallButton(
                        stringResource(Res.string.recurring_calm_change_it), onOpen, Color.Transparent, AppTheme.colors.primary,
                        tag("change"), enabled = !busy,
                    )
                }
            }
        }
    }
}

/** A rule as a half-width tile: preset icon, title, progress in words and a thin progress bar. */
@Composable
internal fun CalmRuleTile(
    view: RuleView,
    paid: Int,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    val draft = view.shown
    val count = draft.occurrenceCount
    val gameya = draft.preset == Preset.Gameya
    val progress = if (count != null && count > 0) (paid.toFloat() / count).coerceIn(0f, 1f) else null
    val icon = when (draft.preset) {
        Preset.Gameya -> AppIcons.RecurringGroups
        Preset.Installment -> AppIcons.RecurringSplit
        else -> AppIcons.Cycle
    }
    CalmCard(modifier = modifier, padding = PaddingValues(horizontal = 16.dp, vertical = 14.dp), radius = 22.dp, onClick = onClick) {
        Icon(icon, contentDescription = null, tint = AppTheme.colors.primary, modifier = Modifier.size(22.dp))
        Text(
            draft.description?.takeIf { it.isNotBlank() } ?: stringResource(Res.string.recurring_rule_title),
            style = AppTheme.type.body.copy(fontSize = 14.sp, fontWeight = FontWeight.Bold),
            color = AppTheme.colors.ink,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 8.dp),
        )
        val subtitle = when {
            count != null && gameya -> stringResource(Res.string.recurring_calm_round_of, paid, count)
            count != null -> stringResource(Res.string.recurring_calm_paid_of, paid, count)
            else -> cadenceText(draft.frequency, draft.interval, draft.anchorDay, draft.startDate)
        }
        Text(subtitle, style = AppTheme.type.caption.copy(fontWeight = FontWeight.SemiBold), color = AppTheme.colors.ink2)
        if (progress != null) {
            Box(Modifier.padding(top = 8.dp).fillMaxWidth().height(5.dp).clip(RoundedCornerShape(9.dp)).background(AppTheme.colors.sunken)) {
                Box(
                    Modifier
                        .fillMaxWidth(progress)
                        .fillMaxHeight()
                        .background(if (gameya) AppTheme.colors.primary else AppTheme.money.positive),
                )
            }
        }
        if (view.rule.status != RuleStatus.Active && view.rule.status != RuleStatus.Unknown) {
            view.rule.status.calmPill(stringResource(view.rule.status.labelRes()), Modifier.padding(top = 8.dp))
        }
        view.syncState.statusLine()?.let { WalletStatusLine(it, Modifier.padding(top = 6.dp)) }
    }
}

/** Two tiles per row, equal height; an odd last one leaves its neighbour's place empty. */
@Composable
internal fun CalmTileRow(first: @Composable (Modifier) -> Unit, second: (@Composable (Modifier) -> Unit)?) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Max)) {
        first(Modifier.weight(1f).fillMaxHeight())
        if (second != null) second(Modifier.weight(1f).fillMaxHeight()) else Spacer(Modifier.weight(1f))
    }
}

/** One round of the rounds grid. */
internal class CalmRound(val ordinal: Int, val paid: Boolean, val next: Boolean, val payout: Boolean)

/** Rounds of a gam'eya or an installment: filled when paid, ringed when next, dashed when it is the payout. */
@Composable
internal fun CalmRoundsGrid(rounds: List<CalmRound>, description: String, modifier: Modifier = Modifier) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = modifier.fillMaxWidth().semantics { contentDescription = description }) {
        rounds.chunked(5).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                row.forEach { round -> CalmRoundCell(round, Modifier.weight(1f)) }
                repeat(5 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun CalmRoundCell(round: CalmRound, modifier: Modifier = Modifier) {
    val colors = AppTheme.colors
    val ok = AppTheme.money.positive
    val base = modifier.aspectRatio(1f).clip(CircleShape)
    when {
        round.payout -> Box(
            contentAlignment = Alignment.Center,
            modifier = base
                .background(AppTheme.money.positiveContainer)
                .drawBehind {
                    drawCircle(
                        ok,
                        radius = size.minDimension / 2 - 1.dp.toPx(),
                        style = Stroke(width = 2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(7.dp.toPx(), 5.dp.toPx()))),
                    )
                },
        ) { Icon(AppIcons.Savings, contentDescription = null, tint = ok, modifier = Modifier.size(20.dp)) }
        round.paid -> Box(contentAlignment = Alignment.Center, modifier = base.background(colors.primary)) {
            Text(round.ordinal.toString(), style = AppTheme.type.caption.copy(fontSize = 13.sp, fontWeight = FontWeight.Bold), color = colors.onPrimary)
        }
        round.next -> Box(contentAlignment = Alignment.Center, modifier = base.border(2.5.dp, colors.primary, CircleShape)) {
            Text(round.ordinal.toString(), style = AppTheme.type.caption.copy(fontSize = 13.sp, fontWeight = FontWeight.Bold), color = colors.primary)
        }
        else -> Box(contentAlignment = Alignment.Center, modifier = base.background(colors.sunken)) {
            Text(round.ordinal.toString(), style = AppTheme.type.caption.copy(fontSize = 13.sp, fontWeight = FontWeight.Bold), color = colors.ink2)
        }
    }
}

/** 38dp info chip of the occurrence sheet: icon and words; tappable when [onClick] is given. */
@Composable
internal fun CalmInfoChip(icon: ImageVector, label: String, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null) {
    val shape = RoundedCornerShape(12.dp)
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .heightIn(min = if (onClick != null) 48.dp else 38.dp)
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier
                .height(38.dp)
                .border(1.dp, AppTheme.colors.outlineVariant, shape)
                .padding(horizontal = 12.dp),
        ) {
            Icon(icon, contentDescription = null, tint = AppTheme.colors.ink2, modifier = Modifier.size(18.dp))
            Text(
                label,
                style = AppTheme.type.caption.copy(fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold),
                color = AppTheme.colors.ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** 56dp / 18dp-radius button of the sheet, with an icon. */
@Composable
internal fun CalmSheetButton(
    icon: ImageVector?,
    label: String,
    onClick: () -> Unit,
    container: Color,
    content: Color,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val shape = RoundedCornerShape(18.dp)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
        modifier = modifier
            .heightIn(min = 56.dp)
            .clip(shape)
            .background(if (enabled) container else AppTheme.colors.sunken2, shape)
            .semantics { if (!enabled) disabled() }
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp),
    ) {
        val tint = if (enabled) content else AppTheme.colors.ink2
        if (icon != null) Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
        Text(label, style = AppTheme.type.body.copy(fontSize = 16.sp, fontWeight = FontWeight.Bold), color = tint, maxLines = 1)
    }
}

/**
 * The big amount of the occurrence sheet: the currency, then 52sp digits over a dashed line. Editable when
 * [onValueChange] is given (the confirm form); otherwise a display that opens the form when [onClick] is given.
 */
@Composable
internal fun CalmAmountInput(
    value: String,
    currency: String,
    modifier: Modifier = Modifier,
    onValueChange: ((String) -> Unit)? = null,
    onClick: (() -> Unit)? = null,
    fieldModifier: Modifier = Modifier,
) {
    val digits = TextStyleLtr52()
    val line = AppTheme.colors.outlineVariant
    val width: Dp = (value.length.coerceAtLeast(3) * 31).dp.coerceAtLeast(120.dp)
    Row(
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = modifier.then(if (onClick != null && onValueChange == null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier),
    ) {
        Text(
            currency,
            style = AppTheme.type.body.copy(fontSize = 20.sp, fontWeight = FontWeight.Bold, textDirection = androidx.compose.ui.text.style.TextDirection.Ltr),
            color = AppTheme.colors.ink2,
            modifier = Modifier.padding(bottom = 10.dp),
        )
        val underline = Modifier.width(width).drawBehind {
            drawLine(
                line,
                Offset(0f, size.height),
                Offset(size.width, size.height),
                strokeWidth = 2.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx())),
            )
        }.padding(bottom = 2.dp)
        if (onValueChange != null) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = digits.copy(color = AppTheme.colors.ink, textAlign = TextAlign.Center),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                cursorBrush = SolidColor(AppTheme.colors.primary),
                modifier = underline.then(fieldModifier),
            )
        } else {
            Text(value, style = digits.copy(textAlign = TextAlign.Center), color = AppTheme.colors.ink, maxLines = 1, modifier = underline)
        }
    }
}

@Composable
private fun TextStyleLtr52() = AppTheme.type.headline.copy(
    fontSize = 52.sp,
    fontWeight = FontWeight.ExtraBold,
    letterSpacing = (-0.04).em,
    lineHeight = 60.sp,
    textDirection = androidx.compose.ui.text.style.TextDirection.Ltr,
)

/** The "Open the rule" style link: 13.5sp bold primary on a 48dp target. */
@Composable
internal fun CalmLink(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier.heightIn(min = 48.dp).clickable(role = Role.Button, onClick = onClick).padding(horizontal = 8.dp),
    ) {
        Text(text, style = AppTheme.type.caption.copy(fontSize = 13.5.sp, fontWeight = FontWeight.Bold), color = AppTheme.colors.primary)
    }
}
