package com.fares.saveme.feature.recurring.ui.occurrence

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.fares.saveme.designsystem.component.AlertTone
import com.fares.saveme.designsystem.component.AmountField
import com.fares.saveme.designsystem.component.AppDatePickerDialog
import com.fares.saveme.designsystem.component.AppScreen
import com.fares.saveme.designsystem.component.AppTextField
import com.fares.saveme.designsystem.component.DateField
import com.fares.saveme.designsystem.component.InlineAlert
import com.fares.saveme.designsystem.component.PrimaryButton
import com.fares.saveme.designsystem.component.SecondaryButton
import com.fares.saveme.designsystem.component.Skeleton
import com.fares.saveme.designsystem.format.formatDate
import com.fares.saveme.designsystem.theme.AppTheme
import com.fares.saveme.feature.recurring.domain.model.AutoConfirmRefusal
import com.fares.saveme.feature.recurring.domain.model.ConfirmField
import com.fares.saveme.feature.recurring.domain.model.OccurrenceState
import com.fares.saveme.feature.recurring.domain.model.RecurringSyncState
import com.fares.saveme.feature.recurring.ui.CategoryPickField
import com.fares.saveme.feature.recurring.ui.OccurrenceListRow
import com.fares.saveme.feature.recurring.ui.RecurringBackBar
import com.fares.saveme.feature.recurring.ui.RecurringCategorySheet
import com.fares.saveme.feature.recurring.ui.asRuleField
import com.fares.saveme.feature.recurring.ui.kindLabelRes
import com.fares.saveme.feature.recurring.ui.label
import com.fares.saveme.feature.recurring.ui.messageRes
import com.fares.saveme.feature.recurring.ui.text
import com.fares.saveme.feature.transactions.domain.model.TransactionKind
import org.jetbrains.compose.resources.stringResource
import saveme.shared.generated.resources.Res
import saveme.shared.generated.resources.action_accept_deletion
import saveme.shared.generated.resources.action_cancel
import saveme.shared.generated.resources.action_discard
import saveme.shared.generated.resources.action_retry
import saveme.shared.generated.resources.filter_kind
import saveme.shared.generated.resources.recurring_cancel_confirmation
import saveme.shared.generated.resources.recurring_change_confirmation
import saveme.shared.generated.resources.recurring_clash_body
import saveme.shared.generated.resources.recurring_confirm
import saveme.shared.generated.resources.recurring_confirmation_waiting
import saveme.shared.generated.resources.recurring_confirmed_body
import saveme.shared.generated.resources.recurring_deleted_body
import saveme.shared.generated.resources.recurring_edit_one
import saveme.shared.generated.resources.recurring_form_confirm_title
import saveme.shared.generated.resources.recurring_form_edit_title
import saveme.shared.generated.resources.recurring_no_dates_yet
import saveme.shared.generated.resources.recurring_notice_already
import saveme.shared.generated.resources.recurring_notice_already_body
import saveme.shared.generated.resources.recurring_occurrence_title
import saveme.shared.generated.resources.recurring_open_rule
import saveme.shared.generated.resources.recurring_open_transaction
import saveme.shared.generated.resources.recurring_originally
import saveme.shared.generated.resources.recurring_round_number
import saveme.shared.generated.resources.recurring_save
import saveme.shared.generated.resources.recurring_skip
import saveme.shared.generated.resources.recurring_status_clash
import saveme.shared.generated.resources.recurring_status_deleted
import saveme.shared.generated.resources.recurring_status_waiting
import saveme.shared.generated.resources.recurring_unskip
import saveme.shared.generated.resources.tx_field_amount
import saveme.shared.generated.resources.tx_field_category
import saveme.shared.generated.resources.tx_field_date
import saveme.shared.generated.resources.tx_field_destination
import saveme.shared.generated.resources.tx_field_details
import saveme.shared.generated.resources.tx_field_note
import saveme.shared.generated.resources.tx_field_person
import saveme.shared.generated.resources.wallet_keep_mine
import saveme.shared.generated.resources.wallet_use_theirs
import kotlinx.datetime.LocalDate

@Composable
fun OccurrenceScreen(
    state: OccurrenceScreenState,
    onIntent: (OccurrenceIntent) -> Unit,
    snackbar: SnackbarHostState = remember { SnackbarHostState() },
) {
    AppScreen(
        snackbarHostState = snackbar,
        topBar = { RecurringBackBar(stringResource(Res.string.recurring_occurrence_title)) { onIntent(OccurrenceIntent.Back) } },
    ) {
        val view = state.view
        if (!state.loaded || view == null) {
            repeat(4) { Skeleton(Modifier.fillMaxWidth().height(52.dp)) }
            return@AppScreen
        }
        OccurrenceListRow(view, modifier = Modifier.testTag("occurrence.state"))
        Notices(state)
        StatusArea(state, onIntent)
        if (state.formOpen) {
            Form(state, onIntent)
        } else {
            Details(state)
            Actions(state, onIntent)
        }
    }
    Dialogs(state, onIntent)
}

@Composable
private fun Notices(state: OccurrenceScreenState) {
    if (state.notice != null) {
        Column(verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.xxs), modifier = Modifier.testTag("occurrence.notice")) {
            InlineAlert(AlertTone.Info, stringResource(Res.string.recurring_notice_already))
            Text(stringResource(Res.string.recurring_notice_already_body), style = AppTheme.type.body, color = AppTheme.colors.ink2)
        }
    }
    val refusal = state.view?.occurrence?.autoConfirmRefusal
    // Any manual save clears the reason on the server (FR-027), so none is shown while a save of it waits.
    if (refusal != null && !state.serverConfirmed && state.view?.pending == null) {
        InlineAlert(AlertTone.Warning, stringResource(refusal.messageRes()), Modifier.testTag("occurrence.refusal"))
    }
    if (state.confirmationWaiting) {
        InlineAlert(AlertTone.Info, stringResource(Res.string.recurring_confirmation_waiting), Modifier.testTag("occurrence.waiting"))
    }
}

@Composable
private fun StatusArea(state: OccurrenceScreenState, onIntent: (OccurrenceIntent) -> Unit) {
    when (val sync = state.view?.syncState ?: RecurringSyncState.None) {
        RecurringSyncState.None, is RecurringSyncState.Held -> Unit
        RecurringSyncState.Waiting ->
            if (!state.confirmationWaiting) InlineAlert(AlertTone.Info, stringResource(Res.string.recurring_status_waiting), Modifier.testTag("occurrence.status"))
        is RecurringSyncState.Clash -> Column(
            verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.xs),
            modifier = Modifier.fillMaxWidth().background(AppTheme.colors.surface, AppTheme.shapes.card).padding(AppTheme.spacing.m),
        ) {
            if (sync.deletedElsewhere) {
                InlineAlert(AlertTone.Warning, stringResource(Res.string.recurring_status_deleted), Modifier.testTag("occurrence.status"))
                Text(stringResource(Res.string.recurring_deleted_body), style = AppTheme.type.body, color = AppTheme.colors.ink2)
                PrimaryButton(
                    stringResource(Res.string.action_accept_deletion), { onIntent(OccurrenceIntent.AcceptDeletion) },
                    Modifier.testTag("occurrence.acceptDeletion"), loading = state.busy,
                )
            } else {
                InlineAlert(AlertTone.Warning, stringResource(Res.string.recurring_status_clash), Modifier.testTag("occurrence.status"))
                Text(stringResource(Res.string.recurring_clash_body), style = AppTheme.type.body, color = AppTheme.colors.ink2)
                PrimaryButton(
                    stringResource(Res.string.wallet_keep_mine), { onIntent(OccurrenceIntent.KeepMine) },
                    Modifier.testTag("occurrence.keepMine"), loading = state.busy,
                )
                SecondaryButton(
                    stringResource(Res.string.wallet_use_theirs), { onIntent(OccurrenceIntent.UseTheirs) },
                    Modifier.testTag("occurrence.useTheirs"), enabled = !state.busy,
                )
            }
        }
        is RecurringSyncState.Refused -> Column(verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.xs)) {
            InlineAlert(
                AlertTone.Error,
                sync.text(isOccurrence = true, currency = state.currency, isTransfer = state.kind == TransactionKind.Transfer),
                Modifier.testTag("occurrence.status"),
            )
            PrimaryButton(
                stringResource(Res.string.action_retry), { onIntent(OccurrenceIntent.TryAgain) },
                Modifier.testTag("occurrence.tryAgain"), enabled = !state.busy,
            )
            SecondaryButton(
                stringResource(Res.string.action_discard), { onIntent(OccurrenceIntent.Discard) },
                Modifier.testTag("occurrence.discard"), enabled = !state.busy,
            )
        }
    }
}

@Composable
private fun Details(state: OccurrenceScreenState) {
    val view = state.view ?: return
    val occurrence = view.occurrence
    Column(verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.xs)) {
        Line(stringResource(Res.string.recurring_round_number, occurrence.ordinal))
        if (occurrence.edited && occurrence.scheduledDate != view.shownDate) {
            Line(stringResource(Res.string.recurring_originally, formatDate(occurrence.scheduledDate)))
        }
        Labelled(stringResource(Res.string.filter_kind), stringResource(state.kind.kindLabelRes()))
        if (state.kind == TransactionKind.Transfer) {
            state.destinationName?.let { Labelled(stringResource(Res.string.tx_field_destination), it) }
        } else if (view.shownCategoryId != null) {
            Labelled(stringResource(Res.string.tx_field_category), state.category?.label().orEmpty())
        }
        view.shownDescription?.takeIf { it.isNotBlank() }?.let { Labelled(stringResource(Res.string.tx_field_note), it) }
        view.shownMemo?.takeIf { it.isNotBlank() }?.let { Labelled(stringResource(Res.string.tx_field_details), it) }
        view.shownCounterparty?.takeIf { it.isNotBlank() }?.let { Labelled(stringResource(Res.string.tx_field_person), it) }
        if (state.serverConfirmed) Line(stringResource(Res.string.recurring_confirmed_body))
        if (!state.serverConfirmed && !state.confirmationWaiting && view.state == OccurrenceState.Unknown) {
            Line(stringResource(Res.string.recurring_no_dates_yet))
        }
    }
}

@Composable
private fun Line(text: String) {
    Text(text, style = AppTheme.type.body, color = AppTheme.colors.ink2)
}

@Composable
private fun Labelled(label: String, value: String) {
    Column {
        Text(label, style = AppTheme.type.caption, color = AppTheme.colors.caption)
        Text(value, style = AppTheme.type.body, color = AppTheme.colors.ink)
    }
}

@Composable
private fun Actions(state: OccurrenceScreenState, onIntent: (OccurrenceIntent) -> Unit) {
    if (state.canConfirm) {
        PrimaryButton(
            stringResource(Res.string.recurring_confirm), { onIntent(OccurrenceIntent.ConfirmClicked) },
            Modifier.testTag("occurrence.confirm"), enabled = !state.busy,
        )
    }
    if (state.canChangeConfirmation) {
        PrimaryButton(
            stringResource(Res.string.recurring_change_confirmation), { onIntent(OccurrenceIntent.ConfirmClicked) },
            Modifier.testTag("occurrence.confirm"), enabled = !state.busy,
        )
        SecondaryButton(
            stringResource(Res.string.recurring_cancel_confirmation), { onIntent(OccurrenceIntent.CancelConfirmationClicked) },
            Modifier.testTag("occurrence.cancelConfirm"), enabled = !state.busy,
        )
    }
    if (state.canSkip) {
        SecondaryButton(stringResource(Res.string.recurring_skip), { onIntent(OccurrenceIntent.SkipClicked) }, Modifier.testTag("occurrence.skip"), enabled = !state.busy)
    }
    if (state.canUnskip) {
        SecondaryButton(stringResource(Res.string.recurring_unskip), { onIntent(OccurrenceIntent.UnskipClicked) }, Modifier.testTag("occurrence.unskip"), enabled = !state.busy)
    }
    if (state.canEdit) {
        SecondaryButton(stringResource(Res.string.recurring_edit_one), { onIntent(OccurrenceIntent.EditClicked) }, Modifier.testTag("occurrence.edit"), enabled = !state.busy)
    }
    if (state.canOpenTransaction) {
        SecondaryButton(
            stringResource(Res.string.recurring_open_transaction), { onIntent(OccurrenceIntent.OpenTransactionClicked) },
            Modifier.testTag("occurrence.openTransaction"),
        )
    }
    SecondaryButton(stringResource(Res.string.recurring_open_rule), { onIntent(OccurrenceIntent.OpenRuleClicked) }, Modifier.testTag("occurrence.openRule"))
}

/** The confirm and edit forms share their fields; kind, currency and destination are fixed (FR-021, FR-023). */
@Composable
private fun Form(state: OccurrenceScreenState, onIntent: (OccurrenceIntent) -> Unit) {
    val confirming = state.form == OccurrenceForm.Confirm
    Text(
        stringResource(if (confirming) Res.string.recurring_form_confirm_title else Res.string.recurring_form_edit_title),
        style = AppTheme.type.title,
        color = AppTheme.colors.ink,
    )
    DateField(
        label = stringResource(Res.string.tx_field_date),
        value = state.date?.let { formatDate(it) }.orEmpty(),
        onClick = { onIntent(OccurrenceIntent.DateClicked) },
        modifier = Modifier.testTag("occurrence.form.date"),
        errorText = state.errors[ConfirmField.Date]?.text(ConfirmField.Date.asRuleField()),
    )
    AmountField(
        value = state.amountText,
        onValueChange = { onIntent(OccurrenceIntent.AmountChanged(it)) },
        currency = state.currency,
        label = stringResource(Res.string.tx_field_amount),
        modifier = Modifier.testTag("occurrence.form.amount"),
        errorText = state.errors[ConfirmField.Amount]?.text(ConfirmField.Amount.asRuleField()),
    )
    if (state.needsCategory) {
        CategoryPickField(
            label = stringResource(Res.string.tx_field_category),
            categoryId = state.formCategoryId,
            display = state.formCategory,
            onClick = { onIntent(OccurrenceIntent.CategoryClicked) },
            modifier = Modifier.testTag("occurrence.form.category"),
            errorText = state.errors[ConfirmField.Category]?.text(ConfirmField.Category.asRuleField()),
        )
    } else if (state.kind == TransactionKind.Transfer) {
        state.destinationName?.let { Labelled(stringResource(Res.string.tx_field_destination), it) }
    }
    AppTextField(
        value = state.note,
        onValueChange = { onIntent(OccurrenceIntent.NoteChanged(it)) },
        label = stringResource(Res.string.tx_field_note),
        modifier = Modifier.testTag("occurrence.form.note"),
        errorText = state.errors[ConfirmField.Note]?.text(ConfirmField.Note.asRuleField()),
    )
    AppTextField(
        value = state.details,
        onValueChange = { onIntent(OccurrenceIntent.DetailsChanged(it)) },
        label = stringResource(Res.string.tx_field_details),
        modifier = Modifier.testTag("occurrence.form.details"),
        singleLine = false,
        minLines = 3,
        errorText = state.errors[ConfirmField.Details]?.text(ConfirmField.Details.asRuleField()),
    )
    AppTextField(
        value = state.person,
        onValueChange = { onIntent(OccurrenceIntent.PersonChanged(it)) },
        label = stringResource(Res.string.tx_field_person),
        modifier = Modifier.testTag("occurrence.form.person"),
        errorText = state.errors[ConfirmField.Person]?.text(ConfirmField.Person.asRuleField()),
    )
    PrimaryButton(
        stringResource(if (confirming) Res.string.recurring_confirm else Res.string.recurring_save),
        { onIntent(OccurrenceIntent.SaveClicked) },
        Modifier.testTag("occurrence.form.save"),
        enabled = state.canSave,
        loading = state.saving,
    )
    SecondaryButton(stringResource(Res.string.action_cancel), { onIntent(OccurrenceIntent.FormCancelled) }, Modifier.testTag("occurrence.form.cancel"))
}

@Composable
private fun Dialogs(state: OccurrenceScreenState, onIntent: (OccurrenceIntent) -> Unit) {
    if (state.datePickerOpen) {
        AppDatePickerDialog(
            initial = state.date ?: LocalDate(2026, 1, 1),
            onPick = { onIntent(OccurrenceIntent.DateChosen(it)) },
            onDismiss = { onIntent(OccurrenceIntent.DatePickerDismissed) },
        )
    }
    if (state.categorySheetOpen) {
        RecurringCategorySheet(
            choices = state.categoryChoices,
            selectedId = state.formCategoryId,
            onSelect = { onIntent(OccurrenceIntent.CategoryChosen(it)) },
            onDismiss = { onIntent(OccurrenceIntent.SheetDismissed) },
        )
    }
}

@Preview
@Composable
private fun OccurrenceScreenPreview() {
    AppTheme { OccurrenceScreen(OccurrenceScreenState(), {}) }
}
