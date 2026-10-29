package com.fares.saveme.feature.recurring.ui.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fares.saveme.designsystem.component.AmountField
import com.fares.saveme.designsystem.component.AppTextButton
import com.fares.saveme.designsystem.component.AppTextField
import com.fares.saveme.designsystem.component.CalmCard
import com.fares.saveme.designsystem.component.CalmDivider
import com.fares.saveme.designsystem.component.formatMoney
import com.fares.saveme.designsystem.format.formatDate
import com.fares.saveme.designsystem.icon.AppIcons
import com.fares.saveme.designsystem.icon.RecurringChevronDown
import com.fares.saveme.designsystem.theme.AppTheme
import com.fares.saveme.feature.recurring.domain.model.AmountBasis
import com.fares.saveme.feature.recurring.domain.model.Frequency
import com.fares.saveme.feature.recurring.domain.model.Preset
import com.fares.saveme.feature.recurring.domain.model.RuleField
import com.fares.saveme.feature.recurring.ui.calmMoney
import com.fares.saveme.feature.recurring.ui.label
import com.fares.saveme.feature.recurring.ui.text
import com.fares.saveme.feature.recurring.ui.RecurringHeading
import com.fares.saveme.feature.transactions.domain.TransactionAmount
import com.fares.saveme.feature.transactions.domain.model.TransactionKind
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringArrayResource
import org.jetbrains.compose.resources.stringResource
import saveme.shared.generated.resources.Res
import saveme.shared.generated.resources.action_done
import saveme.shared.generated.resources.month_short
import saveme.shared.generated.resources.recurring_auto_confirm
import saveme.shared.generated.resources.recurring_calm_auto_sub
import saveme.shared.generated.resources.recurring_calm_more_details
import saveme.shared.generated.resources.recurring_calm_next_dates
import saveme.shared.generated.resources.recurring_calm_optional
import saveme.shared.generated.resources.recurring_calm_p_as_category
import saveme.shared.generated.resources.recurring_calm_p_every
import saveme.shared.generated.resources.recurring_calm_p_for_category
import saveme.shared.generated.resources.recurring_calm_p_for_rounds
import saveme.shared.generated.resources.recurring_calm_p_for_times
import saveme.shared.generated.resources.recurring_calm_p_move
import saveme.shared.generated.resources.recurring_calm_p_on_day
import saveme.shared.generated.resources.recurring_calm_p_over_rounds
import saveme.shared.generated.resources.recurring_calm_p_pay
import saveme.shared.generated.resources.recurring_calm_p_pay_total
import saveme.shared.generated.resources.recurring_calm_p_payout
import saveme.shared.generated.resources.recurring_calm_p_receive
import saveme.shared.generated.resources.recurring_calm_p_until
import saveme.shared.generated.resources.recurring_calm_round_short
import saveme.shared.generated.resources.recurring_calm_starts
import saveme.shared.generated.resources.recurring_calm_t_day
import saveme.shared.generated.resources.recurring_calm_t_every_daily
import saveme.shared.generated.resources.recurring_calm_t_every_monthly
import saveme.shared.generated.resources.recurring_calm_t_every_weekly
import saveme.shared.generated.resources.recurring_calm_t_every_yearly
import saveme.shared.generated.resources.recurring_calm_t_freq_daily
import saveme.shared.generated.resources.recurring_calm_t_freq_monthly
import saveme.shared.generated.resources.recurring_calm_t_freq_weekly
import saveme.shared.generated.resources.recurring_calm_t_freq_yearly
import saveme.shared.generated.resources.recurring_calm_t_round
import saveme.shared.generated.resources.recurring_calm_t_rounds
import saveme.shared.generated.resources.recurring_calm_t_times
import saveme.shared.generated.resources.recurring_calm_tap_part
import saveme.shared.generated.resources.recurring_calm_weekdays_short
import saveme.shared.generated.resources.recurring_cd_preview
import saveme.shared.generated.resources.recurring_field_count
import saveme.shared.generated.resources.recurring_field_day
import saveme.shared.generated.resources.recurring_field_payout_round
import saveme.shared.generated.resources.recurring_field_rounds
import saveme.shared.generated.resources.recurring_payout
import saveme.shared.generated.resources.tx_field_amount
import saveme.shared.generated.resources.tx_field_note

/** The parts of the sentence that open a small editor of their own instead of a sheet or picker. */
enum class SentenceEdit { Amount, Day, Rounds, PayoutRound, Count }

private class Token(val label: String, val onClick: (() -> Unit)?, val green: Boolean = false)

private sealed interface Piece {
    data class Words(val text: String) : Piece
    data class Slot(val key: String) : Piece
}

private fun pieces(template: String): List<Piece> {
    val out = mutableListOf<Piece>()
    var index = 0
    while (index < template.length) {
        val open = template.indexOf('{', index)
        if (open < 0) {
            out += Piece.Words(template.substring(index))
            break
        }
        val close = template.indexOf('}', open)
        if (close < 0) {
            out += Piece.Words(template.substring(index))
            break
        }
        if (open > index) out += Piece.Words(template.substring(index, open))
        out += Piece.Slot(template.substring(open + 1, close))
        index = close + 1
    }
    return out
}

private fun RuleEditorState.dayNumber(): Int? = dayText.toIntOrNull() ?: start?.day

/**
 * The sentence card of the editor: "Pay EGP 2,000 from CIB Checking every month on day 1 for 10 rounds...". Each coloured
 * part is a control that opens what edits it: a sheet, the date picker, or a small box for a number.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SentenceCard(state: RuleEditorState, onIntent: (RuleEditorIntent) -> Unit, onEdit: (SentenceEdit) -> Unit) {
    val dash = "—"
    val tokens = mutableMapOf<String, Token>()
    val parts = mutableListOf<String>()
    val shaped = state.presetShaped
    val installmentTotal = state.preset == Preset.Installment && state.basis == AmountBasis.Total
    val amountLabel = when (val parsed = TransactionAmount.parse(state.amountText, state.currency)) {
        is TransactionAmount.Result.Valid -> calmMoney(parsed.minor, state.currency)
        else -> state.amountText.takeIf { it.isNotBlank() }?.let { "${state.currency} $it" } ?: dash
    }
    tokens["amount"] = Token(amountLabel, if (state.amountEditable) ({ onEdit(SentenceEdit.Amount) }) else null)
    tokens["wallet"] = Token(
        state.walletChoices.firstOrNull { it.id == state.walletId }?.name ?: dash,
        if (state.walletEditable && state.walletChoices.size > 1) ({ onIntent(RuleEditorIntent.WalletClicked) }) else null,
    )
    tokens["destination"] = Token(
        state.destinationName ?: dash,
        if (state.destinationEditable && state.canEdit && state.destinationChoices.isNotEmpty()) ({ onIntent(RuleEditorIntent.DestinationClicked) }) else null,
    )
    tokens["category"] = Token(state.category?.label() ?: dash, if (state.canEdit) ({ onIntent(RuleEditorIntent.CategoryClicked) }) else null)
    val interval = state.intervalText.toIntOrNull()?.coerceAtLeast(1) ?: 1
    val frequencyLabel = when (state.frequency) {
        Frequency.Weekly -> if (interval == 1) stringResource(Res.string.recurring_calm_t_freq_weekly) else pluralStringResource(Res.plurals.recurring_calm_t_every_weekly, interval, interval)
        Frequency.Monthly -> if (interval == 1) stringResource(Res.string.recurring_calm_t_freq_monthly) else pluralStringResource(Res.plurals.recurring_calm_t_every_monthly, interval, interval)
        Frequency.Yearly -> if (interval == 1) stringResource(Res.string.recurring_calm_t_freq_yearly) else pluralStringResource(Res.plurals.recurring_calm_t_every_yearly, interval, interval)
        else -> if (interval == 1) stringResource(Res.string.recurring_calm_t_freq_daily) else pluralStringResource(Res.plurals.recurring_calm_t_every_daily, interval, interval)
    }
    tokens["frequency"] = Token(frequencyLabel, if (state.scheduleEditable && !shaped) ({ onIntent(RuleEditorIntent.FrequencyClicked) }) else null)
    val day = state.dayNumber()
    tokens["day"] = Token(
        day?.let { stringResource(Res.string.recurring_calm_t_day, it) } ?: dash,
        if (state.scheduleEditable) {
            { if (shaped) onIntent(RuleEditorIntent.DateClicked(DateTarget.Start)) else onEdit(SentenceEdit.Day) }
        } else {
            null
        },
    )
    val rounds = state.countText.toIntOrNull()
    tokens["rounds"] = Token(
        rounds?.let { pluralStringResource(Res.plurals.recurring_calm_t_rounds, it, it) } ?: dash,
        if (state.countEditable) ({ onEdit(SentenceEdit.Rounds) }) else null,
    )
    tokens["count"] = Token(
        rounds?.let { pluralStringResource(Res.plurals.recurring_calm_t_times, it, it) } ?: dash,
        if (state.countEditable) ({ onEdit(SentenceEdit.Count) }) else null,
    )
    tokens["endDate"] = Token(state.endDate?.let { formatDate(it) } ?: dash, if (state.canEdit) ({ onIntent(RuleEditorIntent.DateClicked(DateTarget.End)) }) else null)
    val payoutRound = state.payoutRoundText.toIntOrNull()
    tokens["payoutRound"] = Token(
        payoutRound?.let { stringResource(Res.string.recurring_calm_t_round, it) } ?: dash,
        if (state.scheduleEditable) ({ onEdit(SentenceEdit.PayoutRound) }) else null,
        green = true,
    )
    tokens["payoutCategory"] = Token(state.payoutCategory?.label() ?: dash, if (state.canEdit) ({ onIntent(RuleEditorIntent.PayoutCategoryClicked) }) else null)

    parts += stringResource(
        when {
            state.kind == TransactionKind.Income -> Res.string.recurring_calm_p_receive
            state.kind == TransactionKind.Transfer -> Res.string.recurring_calm_p_move
            installmentTotal -> Res.string.recurring_calm_p_pay_total
            else -> Res.string.recurring_calm_p_pay
        },
    )
    if (state.kind != TransactionKind.Transfer && state.kind != TransactionKind.Unknown && state.preset != Preset.Gameya) {
        parts += stringResource(if (state.kind == TransactionKind.Income) Res.string.recurring_calm_p_as_category else Res.string.recurring_calm_p_for_category)
    }
    parts += stringResource(Res.string.recurring_calm_p_every)
    if (state.frequency == Frequency.Monthly || state.frequency == Frequency.Yearly) parts += stringResource(Res.string.recurring_calm_p_on_day)
    when {
        shaped -> parts += stringResource(if (installmentTotal) Res.string.recurring_calm_p_over_rounds else Res.string.recurring_calm_p_for_rounds)
        state.endMode == EndMode.OnDate -> parts += stringResource(Res.string.recurring_calm_p_until)
        state.endMode == EndMode.After -> parts += stringResource(Res.string.recurring_calm_p_for_times)
    }
    var template = parts.joinToString(" ") + "."
    if (state.preset == Preset.Gameya) template += " " + stringResource(Res.string.recurring_calm_p_payout)

    val errors = state.errors.keys.toList().mapNotNull { field -> state.errors[field]?.text(field) }
    CalmCard(padding = PaddingValues(18.dp)) {
        Text(
            stringResource(Res.string.recurring_calm_tap_part),
            style = AppTheme.type.caption.copy(fontSize = 13.sp, fontWeight = FontWeight.Bold),
            color = AppTheme.colors.ink2,
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            itemVerticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        ) {
            val units = mutableListOf<@Composable () -> Unit>()
            pieces(template).forEach { piece ->
                when (piece) {
                    is Piece.Words -> piece.text.split(' ').filter { it.isNotEmpty() }.forEach { word ->
                        if (word.first() in ".,;:" && units.isNotEmpty()) {
                            val previous = units.removeAt(units.lastIndex)
                            units += {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    previous()
                                    SentenceWord(word)
                                }
                            }
                        } else {
                            units += { SentenceWord(word) }
                        }
                    }
                    is Piece.Slot -> tokens[piece.key]?.let { token ->
                        units += { SentenceToken(token, Modifier.testTag("ruleEditor.token.${piece.key}")) }
                    }
                }
            }
            units.forEach { it() }
        }
        errors.forEach { Text(it, style = AppTheme.type.caption, color = AppTheme.colors.error, modifier = Modifier.padding(top = 6.dp)) }
    }
}

@Composable
private fun SentenceWord(text: String) {
    Text(
        text,
        style = AppTheme.type.body.copy(fontSize = 20.sp, fontWeight = FontWeight.SemiBold, lineHeight = 31.sp),
        color = AppTheme.colors.ink,
    )
}

@Composable
private fun SentenceToken(token: Token, modifier: Modifier = Modifier) {
    val container = if (token.green) AppTheme.money.positiveContainer else AppTheme.colors.primaryContainer
    val content = if (token.green) AppTheme.money.positive else AppTheme.colors.primary
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .heightIn(min = 48.dp)
            .then(if (token.onClick != null) Modifier.clickable(role = Role.Button, onClick = token.onClick) else Modifier),
    ) {
        Text(
            token.label,
            style = AppTheme.type.body.copy(fontSize = 20.sp, fontWeight = FontWeight.Bold),
            color = content,
            modifier = Modifier
                .heightIn(min = 36.dp)
                .background(container, RoundedCornerShape(10.dp))
                .padding(horizontal = 10.dp, vertical = 2.dp),
        )
    }
}

/** The small box a number part of the sentence opens. */
@Composable
internal fun SentenceEditDialog(edit: SentenceEdit, state: RuleEditorState, onIntent: (RuleEditorIntent) -> Unit, onDismiss: () -> Unit) {
    val numeric = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number)
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { AppTextButton(stringResource(Res.string.action_done), onDismiss, Modifier.testTag("ruleEditor.token.done")) },
        text = {
            when (edit) {
                SentenceEdit.Amount -> AmountField(
                    value = state.amountText,
                    onValueChange = { onIntent(RuleEditorIntent.AmountChanged(it)) },
                    currency = state.currency,
                    label = stringResource(Res.string.tx_field_amount),
                    autoFocus = true,
                    errorText = state.errors[RuleField.Amount]?.text(RuleField.Amount),
                )
                SentenceEdit.Day -> AppTextField(
                    value = state.dayText,
                    onValueChange = { onIntent(RuleEditorIntent.DayChanged(it)) },
                    label = stringResource(Res.string.recurring_field_day),
                    keyboardOptions = numeric,
                    errorText = state.errors[RuleField.Day]?.text(RuleField.Day),
                )
                SentenceEdit.Rounds -> AppTextField(
                    value = state.countText,
                    onValueChange = { onIntent(RuleEditorIntent.CountChanged(it)) },
                    label = stringResource(Res.string.recurring_field_rounds),
                    keyboardOptions = numeric,
                    errorText = state.errors[RuleField.Count]?.text(RuleField.Count),
                )
                SentenceEdit.Count -> AppTextField(
                    value = state.countText,
                    onValueChange = { onIntent(RuleEditorIntent.CountChanged(it)) },
                    label = stringResource(Res.string.recurring_field_count),
                    keyboardOptions = numeric,
                    errorText = state.errors[RuleField.Count]?.text(RuleField.Count),
                )
                SentenceEdit.PayoutRound -> AppTextField(
                    value = state.payoutRoundText,
                    onValueChange = { onIntent(RuleEditorIntent.PayoutRoundChanged(it)) },
                    label = stringResource(Res.string.recurring_field_payout_round),
                    keyboardOptions = numeric,
                    errorText = state.errors[RuleField.PayoutRound]?.text(RuleField.PayoutRound),
                )
            }
        },
    )
}

/** The next five dates, one small tile each: month, day, and the round (or weekday). */
@Composable
internal fun NextDatesCard(state: RuleEditorState, caption: String?, emptyText: String, modifier: Modifier = Modifier) {
    val months = stringArrayResource(Res.array.month_short)
    val weekdays = stringArrayResource(Res.array.recurring_calm_weekdays_short)
    val payout = stringResource(Res.string.recurring_payout)
    val description = stringResource(Res.string.recurring_cd_preview)
    val rounds = state.preview.rounds.take(5)
    CalmCard(padding = PaddingValues(horizontal = 18.dp, vertical = 16.dp), modifier = modifier.semantics { contentDescription = description }) {
        Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.fillMaxWidth()) {
            RecurringHeading(stringResource(Res.string.recurring_calm_next_dates), Modifier.weight(1f))
            state.start?.let {
                Text(
                    stringResource(Res.string.recurring_calm_starts, formatDate(it)),
                    style = AppTheme.type.caption.copy(fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold),
                    color = AppTheme.colors.ink2,
                )
            }
        }
        if (rounds.isEmpty()) {
            Text(emptyText, style = AppTheme.type.body, color = AppTheme.colors.ink2, modifier = Modifier.padding(top = 8.dp))
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
                rounds.forEach { round ->
                    val green = round.isPayout
                    val ground = if (green) AppTheme.money.positiveContainer else AppTheme.colors.sunken
                    val tone = if (green) AppTheme.money.positive else AppTheme.colors.ink2
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.weight(1f).background(ground, RoundedCornerShape(12.dp)).padding(vertical = 8.dp),
                    ) {
                        Text(months[round.date.month.ordinal], style = AppTheme.type.caption.copy(fontSize = 11.sp, fontWeight = FontWeight.SemiBold), color = tone, maxLines = 1)
                        Text(
                            round.date.day.toString(),
                            style = AppTheme.type.body.copy(fontSize = 14.sp, fontWeight = FontWeight.Bold),
                            color = if (green) AppTheme.money.positive else AppTheme.colors.ink,
                        )
                        Text(
                            when {
                                green -> payout
                                state.presetShaped -> stringResource(Res.string.recurring_calm_round_short, round.ordinal)
                                else -> weekdays[round.date.dayOfWeek.ordinal]
                            },
                            style = AppTheme.type.caption.copy(fontSize = 11.sp, fontWeight = FontWeight.SemiBold),
                            color = tone,
                            maxLines = 1,
                        )
                    }
                }
                repeat(5 - rounds.size) { Spacer(Modifier.weight(1f)) }
            }
        }
        if (caption != null) Text(caption, style = AppTheme.type.caption, color = AppTheme.colors.caption, modifier = Modifier.padding(top = 10.dp))
    }
}

/** Note, automatic confirmation and the "More details" toggle in one card. */
@Composable
internal fun NoteAutoCard(
    state: RuleEditorState,
    onIntent: (RuleEditorIntent) -> Unit,
    expanded: Boolean,
    onToggle: () -> Unit,
) {
    CalmCard(padding = PaddingValues(0.dp)) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 12.dp)) {
            Text(
                stringResource(Res.string.tx_field_note),
                style = AppTheme.type.caption.copy(fontSize = 12.sp, fontWeight = FontWeight.SemiBold),
                color = AppTheme.colors.ink2,
            )
            Box(Modifier.fillMaxWidth().heightIn(min = 28.dp), contentAlignment = Alignment.CenterStart) {
                if (state.note.isEmpty()) {
                    Text(stringResource(Res.string.recurring_calm_optional), style = AppTheme.type.body.copy(fontSize = 15.sp), color = AppTheme.colors.caption)
                }
                BasicTextField(
                    value = state.note,
                    onValueChange = { onIntent(RuleEditorIntent.NoteChanged(it)) },
                    enabled = state.canEdit,
                    singleLine = true,
                    textStyle = AppTheme.type.body.copy(fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = AppTheme.colors.ink),
                    cursorBrush = SolidColor(AppTheme.colors.primary),
                    modifier = Modifier.fillMaxWidth().testTag("ruleEditor.note"),
                )
            }
            state.errors[RuleField.Note]?.let { Text(it.text(RuleField.Note), style = AppTheme.type.caption, color = AppTheme.colors.error) }
        }
        CalmDivider()
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .toggleable(value = state.autoConfirm, enabled = state.canEdit, role = Role.Switch) { onIntent(RuleEditorIntent.AutoConfirmToggled(it)) }
                .heightIn(min = 64.dp)
                .padding(horizontal = 18.dp, vertical = 14.dp)
                .testTag("ruleEditor.autoConfirm"),
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(Res.string.recurring_auto_confirm),
                    style = AppTheme.type.body.copy(fontSize = 15.sp, fontWeight = FontWeight.SemiBold),
                    color = AppTheme.colors.ink,
                )
                Text(
                    stringResource(Res.string.recurring_calm_auto_sub),
                    style = AppTheme.type.caption.copy(fontSize = 12.5.sp),
                    color = AppTheme.colors.ink2,
                )
            }
            CalmSwitch(state.autoConfirm)
        }
        CalmDivider()
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clickable(role = Role.Button, onClick = onToggle)
                .heightIn(min = 52.dp)
                .padding(horizontal = 18.dp)
                .testTag("ruleEditor.moreDetails"),
        ) {
            Text(
                stringResource(Res.string.recurring_calm_more_details),
                style = AppTheme.type.body.copy(fontSize = 15.sp, fontWeight = FontWeight.SemiBold),
                color = AppTheme.colors.ink,
                modifier = Modifier.weight(1f),
            )
            Icon(
                AppIcons.RecurringChevronDown,
                contentDescription = null,
                tint = AppTheme.colors.caption,
                modifier = Modifier.size(22.dp).rotate(if (expanded) 180f else 0f),
            )
        }
    }
}

/** 46 x 28 switch: ruled track off, primary on, a 22dp white thumb. */
@Composable
private fun CalmSwitch(on: Boolean) {
    val track: Color = if (on) AppTheme.colors.primary else AppTheme.colors.outlineVariant
    Box(
        contentAlignment = if (on) Alignment.CenterEnd else Alignment.CenterStart,
        modifier = Modifier.size(width = 46.dp, height = 28.dp).background(track, CircleShape).padding(3.dp),
    ) {
        Box(Modifier.size(22.dp).background(Color.White, CircleShape))
    }
}
