package com.fares.saveme.feature.recurring.ui.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.fares.saveme.designsystem.component.AppDatePickerDialog
import com.fares.saveme.designsystem.component.AmountField
import com.fares.saveme.designsystem.component.AppScreen
import com.fares.saveme.designsystem.component.AppSelectField
import com.fares.saveme.designsystem.component.AppTextField
import com.fares.saveme.designsystem.component.DateField
import com.fares.saveme.designsystem.component.EmptyState
import com.fares.saveme.designsystem.component.MoneyKind
import com.fares.saveme.designsystem.component.PreviewDateItem
import com.fares.saveme.designsystem.component.PreviewDateList
import com.fares.saveme.designsystem.component.PrimaryButton
import com.fares.saveme.designsystem.component.SecondaryButton
import com.fares.saveme.designsystem.component.SegmentedControl
import com.fares.saveme.designsystem.component.SelectionSheet
import com.fares.saveme.designsystem.component.SettingsSwitchRow
import com.fares.saveme.designsystem.component.Skeleton
import com.fares.saveme.designsystem.format.formatDate
import com.fares.saveme.designsystem.theme.AppTheme
import com.fares.saveme.feature.recurring.domain.model.AmountBasis
import com.fares.saveme.feature.recurring.domain.model.Frequency
import com.fares.saveme.feature.recurring.domain.model.Preset
import com.fares.saveme.feature.recurring.domain.model.RuleField
import com.fares.saveme.feature.recurring.ui.CategoryPickField
import com.fares.saveme.feature.recurring.ui.ChoiceChips
import com.fares.saveme.feature.recurring.ui.RecurringBackBar
import com.fares.saveme.feature.recurring.ui.RecurringCategorySheet
import com.fares.saveme.feature.recurring.ui.kindLabelRes
import com.fares.saveme.feature.recurring.ui.labelRes
import com.fares.saveme.feature.recurring.ui.moneyKind
import com.fares.saveme.feature.recurring.ui.text
import com.fares.saveme.feature.transactions.domain.model.TransactionKind
import kotlinx.datetime.LocalDate
import org.jetbrains.compose.resources.stringResource
import saveme.shared.generated.resources.Res
import saveme.shared.generated.resources.filter_kind
import saveme.shared.generated.resources.recurring_auto_confirm
import saveme.shared.generated.resources.recurring_auto_help
import saveme.shared.generated.resources.recurring_basis_each
import saveme.shared.generated.resources.recurring_basis_total
import saveme.shared.generated.resources.recurring_change_from_title
import saveme.shared.generated.resources.recurring_action_change_from
import saveme.shared.generated.resources.recurring_edit_title
import saveme.shared.generated.resources.recurring_end_count
import saveme.shared.generated.resources.recurring_end_date
import saveme.shared.generated.resources.recurring_end_never
import saveme.shared.generated.resources.recurring_field_amount_round
import saveme.shared.generated.resources.recurring_field_basis
import saveme.shared.generated.resources.recurring_field_count
import saveme.shared.generated.resources.recurring_field_day
import saveme.shared.generated.resources.recurring_field_day_help
import saveme.shared.generated.resources.recurring_field_end
import saveme.shared.generated.resources.recurring_field_end_date
import saveme.shared.generated.resources.recurring_field_frequency
import saveme.shared.generated.resources.recurring_field_from
import saveme.shared.generated.resources.recurring_field_interval
import saveme.shared.generated.resources.recurring_field_payout_category
import saveme.shared.generated.resources.recurring_field_payout_round
import saveme.shared.generated.resources.recurring_field_preset
import saveme.shared.generated.resources.recurring_field_rounds
import saveme.shared.generated.resources.recurring_field_start
import saveme.shared.generated.resources.recurring_frozen_reason
import saveme.shared.generated.resources.recurring_from_note
import saveme.shared.generated.resources.recurring_locked_note
import saveme.shared.generated.resources.recurring_new_title
import saveme.shared.generated.resources.recurring_payout_amount
import saveme.shared.generated.resources.recurring_payout
import saveme.shared.generated.resources.recurring_preset_custom
import saveme.shared.generated.resources.recurring_preview_label
import saveme.shared.generated.resources.recurring_preview_none
import saveme.shared.generated.resources.recurring_readonly_ended
import saveme.shared.generated.resources.recurring_readonly_unknown
import saveme.shared.generated.resources.recurring_readonly_viewer
import saveme.shared.generated.resources.recurring_save_rule
import saveme.shared.generated.resources.recurring_unit_daily
import saveme.shared.generated.resources.recurring_unit_monthly
import saveme.shared.generated.resources.recurring_unit_weekly
import saveme.shared.generated.resources.recurring_unit_yearly
import saveme.shared.generated.resources.recurring_unsynced_hint
import saveme.shared.generated.resources.recurring_dates_after_sync
import saveme.shared.generated.resources.recurring_update_after_sync
import saveme.shared.generated.resources.tx_choose_destination
import saveme.shared.generated.resources.tx_create_wallet
import saveme.shared.generated.resources.tx_field_amount
import saveme.shared.generated.resources.tx_field_category
import saveme.shared.generated.resources.tx_field_destination
import saveme.shared.generated.resources.tx_field_details
import saveme.shared.generated.resources.tx_field_note
import saveme.shared.generated.resources.tx_field_person
import saveme.shared.generated.resources.tx_field_wallet
import saveme.shared.generated.resources.tx_no_wallet
import saveme.shared.generated.resources.tx_destinations_empty
import saveme.shared.generated.resources.category_choice_empty

private fun Preset.tag(): String = if (this == Preset.Unknown) "unknown" else wire

@Composable
fun RuleEditorScreen(
    state: RuleEditorState,
    onIntent: (RuleEditorIntent) -> Unit,
    snackbar: SnackbarHostState = remember { SnackbarHostState() },
) {
    AppScreen(
        snackbarHostState = snackbar,
        topBar = {
            val title = when (state.mode) {
                RuleEditorMode.Create -> Res.string.recurring_new_title
                RuleEditorMode.FromDate -> Res.string.recurring_change_from_title
                else -> Res.string.recurring_edit_title
            }
            RecurringBackBar(stringResource(title)) { onIntent(RuleEditorIntent.Back) }
        },
    ) {
        if (!state.loaded) {
            repeat(4) { Skeleton(Modifier.fillMaxWidth().height(52.dp)) }
            return@AppScreen
        }
        if (state.noWallet) {
            EmptyState(
                title = stringResource(Res.string.tx_no_wallet),
                body = "",
                actionLabel = stringResource(Res.string.tx_create_wallet),
                onAction = { onIntent(RuleEditorIntent.CreateWalletClicked) },
                modifier = Modifier.testTag("ruleEditor.noWallet"),
                actionModifier = Modifier.testTag("ruleEditor.createWallet"),
            )
            return@AppScreen
        }
        ReadOnlyNote(state)
        FromSection(state, onIntent)
        PresetSection(state, onIntent)
        WalletSection(state, onIntent)
        KindSection(state, onIntent)
        AmountSection(state, onIntent)
        CategoryOrDestination(state, onIntent)
        GameyaSection(state, onIntent)
        RoundsSection(state, onIntent)
        ScheduleSection(state, onIntent)
        TextsSection(state, onIntent)
        SettingsSwitchRow(
            title = stringResource(Res.string.recurring_auto_confirm),
            checked = state.autoConfirm,
            onCheckedChange = { onIntent(RuleEditorIntent.AutoConfirmToggled(it)) },
            modifier = Modifier.testTag("ruleEditor.autoConfirm"),
            supportingText = stringResource(Res.string.recurring_auto_help),
        )
        PreviewSection(state)
        if (state.canEdit) {
            PrimaryButton(
                stringResource(Res.string.recurring_save_rule),
                { onIntent(RuleEditorIntent.SaveClicked) },
                modifier = Modifier.testTag("ruleEditor.save"),
                enabled = state.canSave,
                loading = state.saving,
            )
        }
    }
    Dialogs(state, onIntent)
}

private fun RuleEditorState.problem(field: RuleField) = errors[field]

@Composable
private fun RuleEditorState.errorText(field: RuleField): String? = errors[field]?.text(field)

@Composable
private fun Note(text: String, tag: String? = null) {
    Text(text, style = AppTheme.type.body, color = AppTheme.colors.ink2, modifier = if (tag != null) Modifier.testTag(tag) else Modifier)
}

@Composable
private fun ReadOnlyNote(state: RuleEditorState) {
    val reason = state.readOnly ?: return
    Note(
        stringResource(
            when (reason) {
                ReadOnlyReason.Unknown -> Res.string.recurring_readonly_unknown
                ReadOnlyReason.Ended -> Res.string.recurring_readonly_ended
                ReadOnlyReason.Viewer -> Res.string.recurring_readonly_viewer
            },
        ),
        "ruleEditor.readOnly",
    )
}

@Composable
private fun FromSection(state: RuleEditorState, onIntent: (RuleEditorIntent) -> Unit) {
    if (!state.fromDateMode) return
    DateField(
        label = stringResource(Res.string.recurring_field_from),
        value = state.from?.let { formatDate(it) }.orEmpty(),
        onClick = { onIntent(RuleEditorIntent.DateClicked(DateTarget.From)) },
        modifier = Modifier.testTag("ruleEditor.from"),
        errorText = state.errorText(RuleField.From),
        enabled = state.canEdit,
    )
    Note(stringResource(Res.string.recurring_from_note))
}

@Composable
private fun PresetSection(state: RuleEditorState, onIntent: (RuleEditorIntent) -> Unit) {
    if (state.presetEditable) {
        ChoiceChips(
            options = listOf(Preset.Custom, Preset.Gameya, Preset.Installment),
            label = { stringResource(it.labelRes() ?: Res.string.recurring_preset_custom) },
            selected = state.preset,
            onSelect = { onIntent(RuleEditorIntent.PresetChosen(it)) },
            optionModifier = { Modifier.testTag("ruleEditor.preset.${it.tag()}") },
        )
        return
    }
    AppTextField(
        value = stringResource(state.preset.labelRes() ?: Res.string.recurring_preset_custom),
        onValueChange = {},
        label = stringResource(Res.string.recurring_field_preset),
        modifier = Modifier.testTag("ruleEditor.preset"),
        enabled = false,
        supportingText = stringResource(Res.string.recurring_locked_note),
    )
}

@Composable
private fun WalletSection(state: RuleEditorState, onIntent: (RuleEditorIntent) -> Unit) {
    if (!state.walletEditable) return
    AppSelectField(
        label = stringResource(Res.string.tx_field_wallet),
        value = state.walletChoices.firstOrNull { it.id == state.walletId }?.name.orEmpty(),
        onClick = { onIntent(RuleEditorIntent.WalletClicked) },
        modifier = Modifier.testTag("ruleEditor.wallet"),
        enabled = state.walletChoices.size > 1,
    )
}

@Composable
private fun KindSection(state: RuleEditorState, onIntent: (RuleEditorIntent) -> Unit) {
    if (state.kindEditable) {
        SegmentedControl(
            options = state.kindOptions,
            label = { stringResource(it.kindLabelRes()) },
            selected = state.kind,
            onSelect = { onIntent(RuleEditorIntent.KindChosen(it)) },
            optionModifier = { Modifier.testTag("ruleEditor.kind.${it.wire}") },
        )
        return
    }
    if (state.preset == Preset.Gameya && state.isCreate) return
    AppTextField(
        value = stringResource(state.kind.kindLabelRes()),
        onValueChange = {},
        label = stringResource(Res.string.filter_kind),
        modifier = Modifier.testTag("ruleEditor.kind"),
        enabled = false,
    )
}

@Composable
private fun AmountSection(state: RuleEditorState, onIntent: (RuleEditorIntent) -> Unit) {
    val total = state.preset == Preset.Installment && state.basis == AmountBasis.Total
    if (state.preset == Preset.Installment && (state.isCreate || state.fromDateMode || state.readOnly == null)) {
        ChoiceChips(
            options = listOf(AmountBasis.PerOccurrence, AmountBasis.Total),
            label = { stringResource(if (it == AmountBasis.Total) Res.string.recurring_basis_total else Res.string.recurring_basis_each) },
            selected = if (state.basis == AmountBasis.Total) AmountBasis.Total else AmountBasis.PerOccurrence,
            onSelect = { if (state.basisEditable) onIntent(RuleEditorIntent.BasisChosen(it)) },
            optionModifier = { Modifier.testTag("ruleEditor.basis.${if (it == AmountBasis.Total) "total" else "per"}") },
        )
    }
    AmountField(
        value = state.amountText,
        onValueChange = { onIntent(RuleEditorIntent.AmountChanged(it)) },
        currency = state.currency,
        label = stringResource(
            when {
                total -> Res.string.recurring_basis_total
                state.presetShaped -> Res.string.recurring_field_amount_round
                else -> Res.string.tx_field_amount
            },
        ),
        modifier = Modifier.testTag("ruleEditor.amount"),
        errorText = state.errorText(RuleField.Amount) ?: state.errorText(RuleField.Basis),
        autoFocus = state.isCreate,
        enabled = state.amountEditable,
    )
}

@Composable
private fun CategoryOrDestination(state: RuleEditorState, onIntent: (RuleEditorIntent) -> Unit) {
    if (state.isTransfer) {
        AppSelectField(
            label = stringResource(Res.string.tx_field_destination),
            value = state.destinationName.orEmpty(),
            onClick = { onIntent(RuleEditorIntent.DestinationClicked) },
            modifier = Modifier.testTag("ruleEditor.destination"),
            errorText = state.errorText(RuleField.Destination),
            enabled = state.destinationEditable && state.canEdit && state.destinationChoices.isNotEmpty(),
        )
        if (state.destinationsEmpty) {
            Note(stringResource(Res.string.tx_destinations_empty, state.currency), "ruleEditor.destinationsEmpty")
        }
        return
    }
    if (state.kind == TransactionKind.Unknown) return
    CategoryPickField(
        label = stringResource(Res.string.tx_field_category),
        categoryId = state.categoryId,
        display = state.category,
        onClick = { onIntent(RuleEditorIntent.CategoryClicked) },
        modifier = Modifier.testTag("ruleEditor.category"),
        errorText = state.errorText(RuleField.Category),
        enabled = state.canEdit,
    )
    if (state.categoriesEmpty) Note(stringResource(Res.string.category_choice_empty))
}

@Composable
private fun GameyaSection(state: RuleEditorState, onIntent: (RuleEditorIntent) -> Unit) {
    if (state.preset != Preset.Gameya) return
    AppTextField(
        value = state.payoutRoundText,
        onValueChange = { onIntent(RuleEditorIntent.PayoutRoundChanged(it)) },
        label = stringResource(Res.string.recurring_field_payout_round),
        modifier = Modifier.testTag("ruleEditor.payoutRound"),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        errorText = state.errorText(RuleField.PayoutRound),
        enabled = state.scheduleEditable,
    )
    CategoryPickField(
        label = stringResource(Res.string.recurring_field_payout_category),
        categoryId = state.payoutCategoryId,
        display = state.payoutCategory,
        onClick = { onIntent(RuleEditorIntent.PayoutCategoryClicked) },
        modifier = Modifier.testTag("ruleEditor.payoutCategory"),
        errorText = state.errorText(RuleField.PayoutCategory),
        enabled = state.canEdit,
    )
}

@Composable
private fun RoundsSection(state: RuleEditorState, onIntent: (RuleEditorIntent) -> Unit) {
    if (!state.presetShaped) return
    AppTextField(
        value = state.countText,
        onValueChange = { onIntent(RuleEditorIntent.CountChanged(it)) },
        label = stringResource(Res.string.recurring_field_rounds),
        modifier = Modifier.testTag("ruleEditor.rounds"),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        errorText = state.errorText(RuleField.Count),
        enabled = state.countEditable,
    )
}

@Composable
private fun ScheduleSection(state: RuleEditorState, onIntent: (RuleEditorIntent) -> Unit) {
    if (state.anyFrozen) {
        Note(stringResource(Res.string.recurring_frozen_reason), "ruleEditor.frozen")
        if (state.canSplit) {
            SecondaryButton(
                stringResource(Res.string.recurring_action_change_from),
                { onIntent(RuleEditorIntent.ChangeFromDateClicked) },
                Modifier.testTag("ruleEditor.changeFrom"),
            )
        }
    }
    if (!state.presetShaped) {
        AppSelectField(
            label = stringResource(Res.string.recurring_field_frequency),
            value = stringResource(state.frequency.labelRes()),
            onClick = { onIntent(RuleEditorIntent.FrequencyClicked) },
            modifier = Modifier.testTag("ruleEditor.frequency"),
            errorText = state.errorText(RuleField.Frequency),
            enabled = state.scheduleEditable,
        )
        AppTextField(
            value = state.intervalText,
            onValueChange = { onIntent(RuleEditorIntent.IntervalChanged(it)) },
            label = stringResource(Res.string.recurring_field_interval),
            modifier = Modifier.testTag("ruleEditor.interval"),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            supportingText = stringResource(
                when (state.frequency) {
                    Frequency.Weekly -> Res.string.recurring_unit_weekly
                    Frequency.Monthly -> Res.string.recurring_unit_monthly
                    Frequency.Yearly -> Res.string.recurring_unit_yearly
                    else -> Res.string.recurring_unit_daily
                },
            ),
            errorText = state.errorText(RuleField.Interval),
            enabled = state.scheduleEditable,
        )
        if (state.showsDay) {
            AppTextField(
                value = state.dayText,
                onValueChange = { onIntent(RuleEditorIntent.DayChanged(it)) },
                label = stringResource(Res.string.recurring_field_day),
                modifier = Modifier.testTag("ruleEditor.day"),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                supportingText = stringResource(Res.string.recurring_field_day_help),
                errorText = state.errorText(RuleField.Day),
                enabled = state.scheduleEditable,
            )
        }
    }
    if (!state.fromDateMode) {
        DateField(
            label = stringResource(Res.string.recurring_field_start),
            value = state.start?.let { formatDate(it) }.orEmpty(),
            onClick = { onIntent(RuleEditorIntent.DateClicked(DateTarget.Start)) },
            modifier = Modifier.testTag("ruleEditor.start"),
            errorText = state.errorText(RuleField.Start),
            enabled = state.scheduleEditable,
        )
    }
    if (!state.presetShaped) EndSection(state, onIntent)
}

@Composable
private fun EndSection(state: RuleEditorState, onIntent: (RuleEditorIntent) -> Unit) {
    Text(stringResource(Res.string.recurring_field_end), style = AppTheme.type.label, color = AppTheme.colors.ink2)
    ChoiceChips(
        options = EndMode.entries,
        label = {
            stringResource(
                when (it) {
                    EndMode.Never -> Res.string.recurring_end_never
                    EndMode.OnDate -> Res.string.recurring_end_date
                    EndMode.After -> Res.string.recurring_end_count
                },
            )
        },
        selected = state.endMode,
        onSelect = { if (state.canEdit) onIntent(RuleEditorIntent.EndModeChosen(it)) },
        optionModifier = { Modifier.testTag("ruleEditor.end.${it.name.let { n -> if (it == EndMode.OnDate) "date" else if (it == EndMode.After) "count" else "never" }}") },
    )
    when (state.endMode) {
        EndMode.Never -> Unit
        EndMode.OnDate -> DateField(
            label = stringResource(Res.string.recurring_field_end_date),
            value = state.endDate?.let { formatDate(it) }.orEmpty(),
            onClick = { onIntent(RuleEditorIntent.DateClicked(DateTarget.End)) },
            modifier = Modifier.testTag("ruleEditor.endDate"),
            errorText = state.errorText(RuleField.End),
            enabled = state.canEdit,
        )
        EndMode.After -> AppTextField(
            value = state.countText,
            onValueChange = { onIntent(RuleEditorIntent.CountChanged(it)) },
            label = stringResource(Res.string.recurring_field_count),
            modifier = Modifier.testTag("ruleEditor.count"),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            errorText = state.errorText(RuleField.Count) ?: state.errorText(RuleField.End),
            enabled = state.countEditable,
        )
    }
}

@Composable
private fun TextsSection(state: RuleEditorState, onIntent: (RuleEditorIntent) -> Unit) {
    AppTextField(
        value = state.note,
        onValueChange = { onIntent(RuleEditorIntent.NoteChanged(it)) },
        label = stringResource(Res.string.tx_field_note),
        modifier = Modifier.testTag("ruleEditor.note"),
        errorText = state.errorText(RuleField.Note),
        enabled = state.canEdit,
    )
    AppTextField(
        value = state.details,
        onValueChange = { onIntent(RuleEditorIntent.DetailsChanged(it)) },
        label = stringResource(Res.string.tx_field_details),
        modifier = Modifier.testTag("ruleEditor.details"),
        singleLine = false,
        minLines = 3,
        errorText = state.errorText(RuleField.Details),
        enabled = state.canEdit,
    )
    AppTextField(
        value = state.person,
        onValueChange = { onIntent(RuleEditorIntent.PersonChanged(it)) },
        label = stringResource(Res.string.tx_field_person),
        modifier = Modifier.testTag("ruleEditor.person"),
        errorText = state.errorText(RuleField.Person),
        enabled = state.canEdit,
    )
}

@Composable
private fun PreviewSection(state: RuleEditorState) {
    val payoutLabel = stringResource(Res.string.recurring_payout)
    val items = state.preview.rounds.map { round ->
        PreviewDateItem(
            dateText = formatDate(round.date),
            amountMinor = round.amountMinor,
            kind = if (round.isPayout) MoneyKind.Income else state.kind.moneyKind(),
            tag = if (round.isPayout) payoutLabel else null,
        )
    }
    val syncNote = when {
        state.isCreate -> stringResource(Res.string.recurring_unsynced_hint)
        state.unsentRule -> stringResource(Res.string.recurring_dates_after_sync)
        else -> stringResource(Res.string.recurring_update_after_sync)
    }
    PreviewDateList(
        items = items,
        currency = state.currency,
        label = stringResource(Res.string.recurring_preview_label),
        modifier = Modifier.testTag("ruleEditor.preview"),
        caption = state.preview.payoutMinor?.let { stringResource(Res.string.recurring_payout_amount, com.fares.saveme.designsystem.component.formatMoney(it, state.currency)) } ?: syncNote,
        emptyText = stringResource(Res.string.recurring_preview_none),
    )
}

@Composable
private fun Dialogs(state: RuleEditorState, onIntent: (RuleEditorIntent) -> Unit) {
    state.datePicker?.let { target ->
        AppDatePickerDialog(
            initial = when (target) {
                DateTarget.Start -> state.start
                DateTarget.End -> state.endDate ?: state.start
                DateTarget.From -> state.from
            } ?: LocalDate(2026, 1, 1),
            onPick = { onIntent(RuleEditorIntent.DateChosen(it)) },
            onDismiss = { onIntent(RuleEditorIntent.DatePickerDismissed) },
        )
    }
    if (state.categorySheetOpen) {
        RecurringCategorySheet(state.categoryChoices, state.categoryId, { onIntent(RuleEditorIntent.CategoryChosen(it)) }, { onIntent(RuleEditorIntent.SheetDismissed) })
    }
    if (state.payoutSheetOpen) {
        RecurringCategorySheet(state.payoutChoices, state.payoutCategoryId, { onIntent(RuleEditorIntent.PayoutCategoryChosen(it)) }, { onIntent(RuleEditorIntent.SheetDismissed) })
    }
    if (state.destinationSheetOpen) {
        SelectionSheet(
            title = stringResource(Res.string.tx_choose_destination),
            options = state.destinationChoices,
            label = { it.name },
            selected = state.destinationChoices.firstOrNull { it.id == state.destinationId },
            onSelect = { onIntent(RuleEditorIntent.DestinationChosen(it.id)) },
            onDismiss = { onIntent(RuleEditorIntent.SheetDismissed) },
            searchable = false,
        )
    }
    if (state.walletSheetOpen) {
        SelectionSheet(
            title = stringResource(Res.string.tx_field_wallet),
            options = state.walletChoices,
            label = { it.name },
            selected = state.walletChoices.firstOrNull { it.id == state.walletId },
            onSelect = { onIntent(RuleEditorIntent.WalletChosen(it.id)) },
            onDismiss = { onIntent(RuleEditorIntent.WalletSheetDismissed) },
            searchable = false,
        )
    }
    if (state.frequencySheetOpen) {
        val frequencies = listOf(Frequency.Daily, Frequency.Weekly, Frequency.Monthly, Frequency.Yearly)
        val names = frequencies.associateWith { stringResource(it.labelRes()) }
        SelectionSheet(
            title = stringResource(Res.string.recurring_field_frequency),
            options = frequencies,
            label = { names[it].orEmpty() },
            selected = state.frequency,
            onSelect = { onIntent(RuleEditorIntent.FrequencyChosen(it)) },
            onDismiss = { onIntent(RuleEditorIntent.SheetDismissed) },
            searchable = false,
        )
    }
}

@Preview
@Composable
private fun RuleEditorScreenPreview() {
    AppTheme {
        RuleEditorScreen(
            RuleEditorState(loaded = true, walletId = "w1", currency = "EGP", amountText = "4500", start = LocalDate(2026, 10, 1), dayText = "1"),
            {},
        )
    }
}
