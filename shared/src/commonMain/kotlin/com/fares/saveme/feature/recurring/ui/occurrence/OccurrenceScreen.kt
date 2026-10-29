package com.fares.saveme.feature.recurring.ui.occurrence

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Scaffold
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.fares.saveme.designsystem.component.AlertTone
import com.fares.saveme.designsystem.component.AppSnackbarHost
import com.fares.saveme.designsystem.component.CalmCircleButton
import com.fares.saveme.designsystem.component.CalmIconTile
import com.fares.saveme.designsystem.format.formatDayMonth
import com.fares.saveme.designsystem.icon.AppIcons
import com.fares.saveme.designsystem.icon.CategoryIcons
import com.fares.saveme.designsystem.icon.RecurringPencil
import com.fares.saveme.designsystem.icon.RecurringSkip
import com.fares.saveme.feature.recurring.domain.model.OccurrenceView
import com.fares.saveme.feature.recurring.ui.CalmAmountInput
import com.fares.saveme.feature.recurring.ui.CalmInfoChip
import com.fares.saveme.feature.recurring.ui.CalmLink
import com.fares.saveme.feature.recurring.ui.CalmSheetButton
import com.fares.saveme.feature.recurring.ui.look
import com.fares.saveme.feature.recurring.ui.rememberToday
import com.fares.saveme.feature.recurring.ui.stateWords
import com.fares.saveme.feature.transactions.domain.TransactionAmount
import com.fares.saveme.designsystem.component.AppDatePickerDialog
import com.fares.saveme.designsystem.component.AppTextField
import com.fares.saveme.designsystem.component.CalmCard
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
import com.fares.saveme.feature.recurring.ui.CalmLabelValue
import com.fares.saveme.feature.recurring.ui.CalmRows
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
import saveme.shared.generated.resources.category_unavailable
import saveme.shared.generated.resources.recurring_calm_amount_this_time
import saveme.shared.generated.resources.recurring_calm_close
import saveme.shared.generated.resources.recurring_rule_title
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

private val SheetBackdrop = Color(0xFF5E6574)

@Composable
fun OccurrenceScreen(
    state: OccurrenceScreenState,
    onIntent: (OccurrenceIntent) -> Unit,
    snackbar: SnackbarHostState = remember { SnackbarHostState() },
) {
    val view = state.view
    val today = rememberToday()
    Scaffold(
        snackbarHost = { AppSnackbarHost(snackbar) },
        containerColor = SheetBackdrop,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
    ) { padding ->
        BoxWithConstraints(Modifier.fillMaxSize().padding(padding)) {
            Box(Modifier.fillMaxSize().pointerInput(Unit) { detectTapGestures { onIntent(OccurrenceIntent.Back) } })
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .heightIn(max = maxHeight * 0.94f)
                    .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                    .background(AppTheme.colors.surface)
                    .imePadding()
                    .verticalScroll(rememberScrollState())
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(start = 20.dp, end = 20.dp, top = 10.dp, bottom = 24.dp),
            ) {
                Box(
                    Modifier
                        .align(Alignment.CenterHorizontally)
                        .size(width = 40.dp, height = 5.dp)
                        .background(AppTheme.colors.outlineVariant, RoundedCornerShape(9.dp)),
                )
                if (!state.loaded || view == null) {
                    Column(verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.s), modifier = Modifier.padding(top = 16.dp)) {
                        repeat(4) { Skeleton(Modifier.fillMaxWidth().height(52.dp)) }
                    }
                } else {
                    SheetContent(state, view, today, onIntent)
                }
            }
        }
    }
    Dialogs(state, onIntent)
}

@Composable
private fun ColumnScope.SheetContent(state: OccurrenceScreenState, view: OccurrenceView, today: LocalDate, onIntent: (OccurrenceIntent) -> Unit) {
    val look = view.state.look()
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.padding(top = 16.dp).fillMaxWidth(),
    ) {
        CalmIconTile(if (view.state == OccurrenceState.Due || view.state == OccurrenceState.Upcoming) AppIcons.Calendar else look.icon, look.color, look.container, size = 48.dp, iconSize = 24.dp, radius = 15.dp)
        Column(Modifier.weight(1f).testTag("occurrence.state")) {
            Text(
                view.ruleDescription?.takeIf { it.isNotBlank() } ?: stringResource(Res.string.recurring_rule_title),
                style = AppTheme.type.title.copy(fontSize = 20.sp, fontWeight = FontWeight.ExtraBold),
                color = AppTheme.colors.ink,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.semantics { heading() },
            )
            Row {
                Text(view.stateWords(today), style = AppTheme.type.caption.copy(fontSize = 13.sp, fontWeight = FontWeight.Bold), color = look.color)
                Text(
                    " · " + formatDayMonth(view.shownDate),
                    style = AppTheme.type.caption.copy(fontSize = 13.sp, fontWeight = FontWeight.Bold),
                    color = look.color,
                )
            }
        }
        CalmCircleButton(
            AppIcons.Close, stringResource(Res.string.recurring_calm_close), { onIntent(OccurrenceIntent.Back) },
            container = AppTheme.colors.sunken, bordered = false,
        )
    }
    Column(verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.xs), modifier = Modifier.padding(top = 12.dp)) {
        Notices(state)
        StatusArea(state, onIntent)
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth().padding(top = 20.dp)) {
        Text(
            stringResource(Res.string.recurring_calm_amount_this_time),
            style = AppTheme.type.caption.copy(fontSize = 13.sp, fontWeight = FontWeight.SemiBold),
            color = AppTheme.colors.ink2,
        )
        val openForm = state.canConfirm || state.canChangeConfirmation
        CalmAmountInput(
            value = if (state.formOpen) state.amountText else TransactionAmount.format(view.shownAmountMinor, view.currency).replace(Regex("[.,]00$"), ""),
            currency = view.currency,
            modifier = Modifier.padding(top = 2.dp),
            onValueChange = if (state.formOpen) ({ onIntent(OccurrenceIntent.AmountChanged(it)) }) else null,
            onClick = if (openForm) ({ onIntent(OccurrenceIntent.ConfirmClicked) }) else null,
            fieldModifier = Modifier.testTag("occurrence.form.amount"),
        )
        state.errors[ConfirmField.Amount]?.let { FieldError(it.text(ConfirmField.Amount.asRuleField())) }
    }
    Chips(state, view, onIntent)
    if (state.formOpen) {
        FormFields(state, onIntent)
        FormActions(state, onIntent)
    } else {
        SheetActions(state, onIntent)
    }
    CalmLink(
        stringResource(Res.string.recurring_open_rule),
        { onIntent(OccurrenceIntent.OpenRuleClicked) },
        Modifier.align(Alignment.CenterHorizontally).testTag("occurrence.openRule"),
    )
    if (!state.formOpen) Details(state)
}

@Composable
private fun FieldError(text: String?) {
    if (text != null) Text(text, style = AppTheme.type.caption, color = AppTheme.colors.error, modifier = Modifier.padding(top = 4.dp))
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Chips(state: OccurrenceScreenState, view: OccurrenceView, onIntent: (OccurrenceIntent) -> Unit) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        itemVerticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(top = 14.dp),
    ) {
        CalmInfoChip(
            AppIcons.Calendar,
            formatDayMonth(if (state.formOpen) state.date ?: view.shownDate else view.shownDate),
            Modifier.testTag("occurrence.form.date"),
            onClick = if (state.formOpen) ({ onIntent(OccurrenceIntent.DateClicked) }) else null,
        )
        state.walletName?.let { CalmInfoChip(AppIcons.Wallet, it) }
        if (state.kind == TransactionKind.Transfer) {
            state.destinationName?.let { CalmInfoChip(AppIcons.Cycle, it) }
        } else if (state.formOpen && state.needsCategory) {
            val display = state.formCategory
            CalmInfoChip(
                CategoryIcons.find(display?.iconKey) ?: AppIcons.More,
                if (state.formCategoryId == null) stringResource(Res.string.tx_field_category) else display?.label() ?: stringResource(Res.string.category_unavailable),
                Modifier.testTag("occurrence.form.category"),
                onClick = { onIntent(OccurrenceIntent.CategoryClicked) },
            )
        } else if (view.shownCategoryId != null) {
            CalmInfoChip(CategoryIcons.find(state.category?.iconKey) ?: AppIcons.More, state.category?.label().orEmpty())
        }
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        FieldError(state.errors[ConfirmField.Date]?.text(ConfirmField.Date.asRuleField()))
        FieldError(state.errors[ConfirmField.Category]?.text(ConfirmField.Category.asRuleField()))
    }
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
        is RecurringSyncState.Clash -> CalmCard {
            Column(verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.xs)) {
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
    val rows = buildList<@Composable () -> Unit> {
        add { Labelled(stringResource(Res.string.recurring_round_number, occurrence.ordinal)) }
        if (occurrence.edited && occurrence.scheduledDate != view.shownDate) {
            add { Labelled(stringResource(Res.string.recurring_originally, formatDate(occurrence.scheduledDate))) }
        }
        add { CalmLabelValue(stringResource(Res.string.filter_kind), stringResource(state.kind.kindLabelRes())) }
        view.shownDescription?.takeIf { it.isNotBlank() }?.let { add { CalmLabelValue(stringResource(Res.string.tx_field_note), it) } }
        view.shownMemo?.takeIf { it.isNotBlank() }?.let { add { CalmLabelValue(stringResource(Res.string.tx_field_details), it) } }
        view.shownCounterparty?.takeIf { it.isNotBlank() }?.let { add { CalmLabelValue(stringResource(Res.string.tx_field_person), it) } }
        if (state.serverConfirmed) add { Labelled(stringResource(Res.string.recurring_confirmed_body)) }
        if (!state.serverConfirmed && !state.confirmationWaiting && view.state == OccurrenceState.Unknown) {
            add { Labelled(stringResource(Res.string.recurring_no_dates_yet)) }
        }
    }
    CalmRows(rows, Modifier.padding(top = 8.dp))
}

@Composable
private fun Labelled(text: String) {
    Text(text, style = AppTheme.type.body, color = AppTheme.colors.ink2, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp))
}

@Composable
private fun SheetActions(state: OccurrenceScreenState, onIntent: (OccurrenceIntent) -> Unit) {
    val colors = AppTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(top = 26.dp)) {
        if (state.canConfirm || state.canChangeConfirmation || state.canSkip) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                if (state.canSkip) {
                    CalmSheetButton(
                        AppIcons.RecurringSkip, stringResource(Res.string.recurring_skip), { onIntent(OccurrenceIntent.SkipClicked) },
                        colors.sunken, colors.ink, Modifier.weight(1f).testTag("occurrence.skip"), enabled = !state.busy,
                    )
                }
                if (state.canConfirm) {
                    CalmSheetButton(
                        AppIcons.Check, stringResource(Res.string.recurring_confirm), { onIntent(OccurrenceIntent.ConfirmClicked) },
                        colors.primary, colors.onPrimary, Modifier.weight(2f).testTag("occurrence.confirm"), enabled = !state.busy,
                    )
                }
                if (state.canChangeConfirmation) {
                    CalmSheetButton(
                        AppIcons.Check, stringResource(Res.string.recurring_change_confirmation), { onIntent(OccurrenceIntent.ConfirmClicked) },
                        colors.primary, colors.onPrimary, Modifier.weight(2f).testTag("occurrence.confirm"), enabled = !state.busy,
                    )
                }
            }
        }
        if (state.canChangeConfirmation) {
            CalmSheetButton(
                AppIcons.Close, stringResource(Res.string.recurring_cancel_confirmation), { onIntent(OccurrenceIntent.CancelConfirmationClicked) },
                colors.sunken, colors.ink, Modifier.fillMaxWidth().testTag("occurrence.cancelConfirm"), enabled = !state.busy,
            )
        }
        if (state.canUnskip) {
            CalmSheetButton(
                AppIcons.Undo, stringResource(Res.string.recurring_unskip), { onIntent(OccurrenceIntent.UnskipClicked) },
                colors.sunken, colors.ink, Modifier.fillMaxWidth().testTag("occurrence.unskip"), enabled = !state.busy,
            )
        }
        if (state.canEdit) {
            CalmSheetButton(
                AppIcons.RecurringPencil, stringResource(Res.string.recurring_edit_one), { onIntent(OccurrenceIntent.EditClicked) },
                colors.sunken, colors.ink, Modifier.fillMaxWidth().testTag("occurrence.edit"), enabled = !state.busy,
            )
        }
        if (state.canOpenTransaction) {
            CalmSheetButton(
                AppIcons.ChevronForward, stringResource(Res.string.recurring_open_transaction), { onIntent(OccurrenceIntent.OpenTransactionClicked) },
                colors.sunken, colors.ink, Modifier.fillMaxWidth().testTag("occurrence.openTransaction"),
            )
        }
    }
}

/** The confirm and edit forms share their fields; kind, currency and destination are fixed (FR-021, FR-023). */
@Composable
private fun FormFields(state: OccurrenceScreenState, onIntent: (OccurrenceIntent) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.s), modifier = Modifier.padding(top = 16.dp)) {
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
    }
}

@Composable
private fun FormActions(state: OccurrenceScreenState, onIntent: (OccurrenceIntent) -> Unit) {
    val confirming = state.form == OccurrenceForm.Confirm
    val colors = AppTheme.colors
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth().padding(top = 26.dp)) {
        CalmSheetButton(
            null, stringResource(Res.string.action_cancel), { onIntent(OccurrenceIntent.FormCancelled) },
            colors.sunken, colors.ink, Modifier.weight(1f).testTag("occurrence.form.cancel"),
        )
        CalmSheetButton(
            AppIcons.Check,
            stringResource(if (confirming) Res.string.recurring_confirm else Res.string.recurring_save),
            { onIntent(OccurrenceIntent.SaveClicked) },
            colors.primary, colors.onPrimary, Modifier.weight(2f).testTag("occurrence.form.save"),
            enabled = state.canSave,
        )
    }
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
