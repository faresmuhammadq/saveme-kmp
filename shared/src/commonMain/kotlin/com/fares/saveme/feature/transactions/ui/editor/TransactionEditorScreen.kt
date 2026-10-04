package com.fares.saveme.feature.transactions.ui.editor

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
import com.fares.saveme.designsystem.component.AppAlertDialog
import com.fares.saveme.designsystem.component.AppDatePickerDialog
import com.fares.saveme.designsystem.component.AppListItem
import com.fares.saveme.designsystem.component.AppScreen
import com.fares.saveme.designsystem.component.AppSelectField
import com.fares.saveme.designsystem.component.AppTextField
import com.fares.saveme.designsystem.component.AppTextButton
import com.fares.saveme.designsystem.component.CategoryChoiceGroup
import com.fares.saveme.designsystem.component.CategoryChoiceItem
import com.fares.saveme.designsystem.component.CategoryChoiceSheet
import com.fares.saveme.designsystem.component.CategoryRow
import com.fares.saveme.designsystem.component.DateField
import com.fares.saveme.designsystem.component.DestructiveButton
import com.fares.saveme.designsystem.component.EmptyState
import com.fares.saveme.designsystem.component.InlineAlert
import com.fares.saveme.designsystem.component.ListTrailing
import com.fares.saveme.designsystem.component.PrimaryButton
import com.fares.saveme.designsystem.component.SecondaryButton
import com.fares.saveme.designsystem.component.SegmentedControl
import com.fares.saveme.designsystem.component.SelectionSheet
import com.fares.saveme.designsystem.component.SettingsSwitchRow
import com.fares.saveme.designsystem.component.Skeleton
import com.fares.saveme.designsystem.component.formatMoney
import com.fares.saveme.designsystem.format.formatDate
import com.fares.saveme.designsystem.theme.AppTheme
import com.fares.saveme.feature.categories.domain.model.CategoryChoices
import com.fares.saveme.feature.categories.domain.model.CategoryDisplay
import com.fares.saveme.feature.transactions.domain.model.AddedBy
import com.fares.saveme.feature.transactions.domain.model.TransactionField
import com.fares.saveme.feature.transactions.domain.model.TransactionKind
import com.fares.saveme.feature.transactions.domain.model.WalletChoice
import com.fares.saveme.feature.transactions.ui.TransactionsBackBar
import com.fares.saveme.feature.transactions.ui.label
import com.fares.saveme.feature.transactions.ui.labelRes
import com.fares.saveme.feature.transactions.ui.text
import kotlinx.datetime.LocalDate
import org.jetbrains.compose.resources.stringResource
import saveme.shared.generated.resources.Res
import saveme.shared.generated.resources.action_accept_deletion
import saveme.shared.generated.resources.action_cancel
import saveme.shared.generated.resources.action_delete_anyway
import saveme.shared.generated.resources.action_discard
import saveme.shared.generated.resources.action_keep_transaction
import saveme.shared.generated.resources.action_restore_with_mine
import saveme.shared.generated.resources.action_retry
import saveme.shared.generated.resources.category_choice_current
import saveme.shared.generated.resources.category_choice_defaults
import saveme.shared.generated.resources.category_choice_empty
import saveme.shared.generated.resources.category_choice_own
import saveme.shared.generated.resources.category_choice_title
import saveme.shared.generated.resources.category_keep_mine
import saveme.shared.generated.resources.category_none
import saveme.shared.generated.resources.category_unavailable
import saveme.shared.generated.resources.category_use_theirs
import saveme.shared.generated.resources.filter_kind
import saveme.shared.generated.resources.tx_added_by
import saveme.shared.generated.resources.tx_added_by_other
import saveme.shared.generated.resources.tx_choose_destination
import saveme.shared.generated.resources.tx_clash_body
import saveme.shared.generated.resources.tx_clash_delete_body
import saveme.shared.generated.resources.tx_cleared_help
import saveme.shared.generated.resources.tx_cleared_no
import saveme.shared.generated.resources.tx_cleared_yes
import saveme.shared.generated.resources.tx_create_wallet
import saveme.shared.generated.resources.tx_deleted_body
import saveme.shared.generated.resources.tx_delete
import saveme.shared.generated.resources.tx_delete_body
import saveme.shared.generated.resources.tx_delete_title
import saveme.shared.generated.resources.tx_destinations_empty
import saveme.shared.generated.resources.tx_field_amount
import saveme.shared.generated.resources.tx_field_category
import saveme.shared.generated.resources.tx_field_cleared
import saveme.shared.generated.resources.tx_field_date
import saveme.shared.generated.resources.tx_field_destination
import saveme.shared.generated.resources.tx_field_details
import saveme.shared.generated.resources.tx_field_note
import saveme.shared.generated.resources.tx_field_person
import saveme.shared.generated.resources.recurring_make_recurring
import saveme.shared.generated.resources.recurring_match_offer
import saveme.shared.generated.resources.recurring_match_title
import saveme.shared.generated.resources.recurring_open_rule
import saveme.shared.generated.resources.recurring_rule_title
import saveme.shared.generated.resources.tx_field_wallet
import saveme.shared.generated.resources.tx_from_recurring
import saveme.shared.generated.resources.tx_kind_locked_note
import saveme.shared.generated.resources.tx_more
import saveme.shared.generated.resources.tx_no_wallet
import saveme.shared.generated.resources.tx_other_device
import saveme.shared.generated.resources.tx_save
import saveme.shared.generated.resources.tx_status_clash
import saveme.shared.generated.resources.tx_status_deleted
import saveme.shared.generated.resources.tx_this_phone
import saveme.shared.generated.resources.tx_title_edit
import saveme.shared.generated.resources.tx_title_new
import saveme.shared.generated.resources.tx_unknown_note
import saveme.shared.generated.resources.tx_viewer_note
import saveme.shared.generated.resources.tx_wallets_locked_note
import saveme.shared.generated.resources.wallet_status_waiting

@Composable
fun TransactionEditorScreen(
    state: TransactionEditorState,
    onIntent: (TransactionEditorIntent) -> Unit,
    snackbar: SnackbarHostState = remember { SnackbarHostState() },
) {
    AppScreen(
        snackbarHostState = snackbar,
        topBar = {
            TransactionsBackBar(stringResource(if (state.isCreate) Res.string.tx_title_new else Res.string.tx_title_edit)) {
                onIntent(TransactionEditorIntent.Back)
            }
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
                onAction = { onIntent(TransactionEditorIntent.CreateWalletClicked) },
                modifier = Modifier.testTag("txEditor.noWallet"),
                actionModifier = Modifier.testTag("txEditor.createWallet"),
            )
            return@AppScreen
        }
        StatusArea(state, onIntent)
        Notes(state)
        WalletSection(state, onIntent)
        KindSection(state, onIntent)
        AmountField(
            value = state.amountText,
            onValueChange = { onIntent(TransactionEditorIntent.AmountChanged(it)) },
            currency = state.currency,
            label = stringResource(Res.string.tx_field_amount),
            modifier = Modifier.testTag("txEditor.amount"),
            errorText = state.errors[TransactionField.Amount]?.text(TransactionField.Amount),
            autoFocus = state.isCreate,
            enabled = state.canEdit,
        )
        CategoryOrDestination(state, onIntent)
        DateField(
            label = stringResource(Res.string.tx_field_date),
            value = state.date?.let { formatDate(it) }.orEmpty(),
            onClick = { onIntent(TransactionEditorIntent.DateClicked) },
            modifier = Modifier.testTag("txEditor.date"),
            errorText = state.errors[TransactionField.Date]?.text(TransactionField.Date),
            enabled = state.canEdit,
        )
        AppTextField(
            value = state.note,
            onValueChange = { onIntent(TransactionEditorIntent.NoteChanged(it)) },
            label = stringResource(Res.string.tx_field_note),
            modifier = Modifier.testTag("txEditor.note"),
            errorText = state.errors[TransactionField.Note]?.text(TransactionField.Note),
            enabled = state.canEdit,
        )
        MoreDetails(state, onIntent)
        MatchOffers(state, onIntent)
        Captions(state, onIntent)
        if (state.canMakeRecurring) {
            SecondaryButton(
                stringResource(Res.string.recurring_make_recurring),
                { onIntent(TransactionEditorIntent.MakeRecurringClicked) },
                modifier = Modifier.testTag("txEditor.makeRecurring"),
                enabled = !state.saving && !state.busy,
            )
        }
        if (state.canEdit) {
            PrimaryButton(
                stringResource(Res.string.tx_save),
                { onIntent(TransactionEditorIntent.SaveClicked) },
                modifier = Modifier.testTag("txEditor.save"),
                enabled = state.canSave,
                loading = state.saving,
            )
        }
        if (state.canDelete) {
            DestructiveButton(
                stringResource(Res.string.tx_delete),
                { onIntent(TransactionEditorIntent.DeleteClicked) },
                modifier = Modifier.testTag("txEditor.delete"),
            )
        }
    }
    Dialogs(state, onIntent)
}

@Composable
private fun Notes(state: TransactionEditorState) {
    if (state.isCreate) return
    when {
        state.kind == TransactionKind.Unknown -> Note(stringResource(Res.string.tx_unknown_note))
        !state.canEdit -> Note(stringResource(Res.string.tx_viewer_note))
    }
}

@Composable
private fun Note(text: String) {
    Text(text, style = AppTheme.type.body, color = AppTheme.colors.ink2, modifier = Modifier.testTag("txEditor.readOnly"))
}

@Composable
private fun WalletSection(state: TransactionEditorState, onIntent: (TransactionEditorIntent) -> Unit) {
    if (!state.walletEditable) return
    AppSelectField(
        label = stringResource(Res.string.tx_field_wallet),
        value = state.walletChoices.firstOrNull { it.id == state.walletId }?.name.orEmpty(),
        onClick = { onIntent(TransactionEditorIntent.WalletClicked) },
        modifier = Modifier.testTag("txEditor.wallet"),
        enabled = state.walletChoices.size > 1,
    )
}

@Composable
private fun KindSection(state: TransactionEditorState, onIntent: (TransactionEditorIntent) -> Unit) {
    if (state.kindEditable) {
        SegmentedControl(
            options = TransactionKind.selectable,
            label = { stringResource(it.labelRes()) },
            selected = state.kind,
            onSelect = { onIntent(TransactionEditorIntent.KindChosen(it)) },
            optionModifier = { Modifier.testTag("txEditor.kind.${it.wire}") },
        )
        return
    }
    AppTextField(
        value = stringResource(state.kind.labelRes()),
        onValueChange = {},
        label = stringResource(Res.string.filter_kind),
        modifier = Modifier.testTag("txEditor.kind"),
        enabled = false,
        supportingText = stringResource(Res.string.tx_kind_locked_note),
        errorText = state.errors[TransactionField.Kind]?.text(TransactionField.Kind),
    )
}

@Composable
private fun CategoryOrDestination(state: TransactionEditorState, onIntent: (TransactionEditorIntent) -> Unit) {
    if (state.kind == TransactionKind.Transfer) {
        AppSelectField(
            label = stringResource(Res.string.tx_field_destination),
            value = state.destinationName.orEmpty(),
            onClick = { onIntent(TransactionEditorIntent.DestinationClicked) },
            modifier = Modifier.testTag("txEditor.destination"),
            supportingText = if (state.isCreate) null else stringResource(Res.string.tx_wallets_locked_note),
            errorText = state.errors[TransactionField.Destination]?.text(TransactionField.Destination),
            enabled = state.isCreate && state.canEdit && state.destinationChoices.isNotEmpty(),
        )
        if (state.destinationsEmpty) {
            Text(
                stringResource(Res.string.tx_destinations_empty, state.currency),
                style = AppTheme.type.body,
                color = AppTheme.colors.ink2,
                modifier = Modifier.testTag("txEditor.destinationsEmpty"),
            )
        }
        return
    }
    if (state.kind == TransactionKind.Unknown) return
    val category = state.category
    if (state.categoryId == null) {
        AppSelectField(
            label = stringResource(Res.string.tx_field_category),
            value = "",
            onClick = { onIntent(TransactionEditorIntent.CategoryClicked) },
            modifier = Modifier.testTag("txEditor.category"),
            errorText = state.errors[TransactionField.Category]?.text(TransactionField.Category),
            enabled = state.canEdit,
        )
        if (state.categoriesEmpty) {
            Text(stringResource(Res.string.category_choice_empty), style = AppTheme.type.body, color = AppTheme.colors.ink2)
        }
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.xxs)) {
        Text(stringResource(Res.string.tx_field_category), style = AppTheme.type.label, color = AppTheme.colors.ink2)
        CategoryRow(
            name = category?.label() ?: stringResource(Res.string.category_unavailable),
            iconKey = category?.iconKey,
            colorHex = category?.colorHex,
            modifier = Modifier
                .background(AppTheme.colors.surface, AppTheme.shapes.control)
                .testTag("txEditor.category"),
            onClick = if (state.canEdit) ({ onIntent(TransactionEditorIntent.CategoryClicked) }) else null,
        )
        state.errors[TransactionField.Category]?.let {
            Text(it.text(TransactionField.Category), style = AppTheme.type.caption, color = AppTheme.colors.error)
        }
    }
}

@Composable
private fun MoreDetails(state: TransactionEditorState, onIntent: (TransactionEditorIntent) -> Unit) {
    AppListItem(
        stringResource(Res.string.tx_more),
        modifier = Modifier.testTag("txEditor.more"),
        trailing = ListTrailing.Chevron,
        onClick = { onIntent(TransactionEditorIntent.MoreToggled) },
    )
    if (!state.moreOpen) return
    AppTextField(
        value = state.details,
        onValueChange = { onIntent(TransactionEditorIntent.DetailsChanged(it)) },
        label = stringResource(Res.string.tx_field_details),
        modifier = Modifier.testTag("txEditor.details"),
        singleLine = false,
        minLines = 3,
        errorText = state.errors[TransactionField.Details]?.text(TransactionField.Details),
        enabled = state.canEdit,
    )
    AppTextField(
        value = state.person,
        onValueChange = { onIntent(TransactionEditorIntent.PersonChanged(it)) },
        label = stringResource(Res.string.tx_field_person),
        modifier = Modifier.testTag("txEditor.person"),
        errorText = state.errors[TransactionField.Person]?.text(TransactionField.Person),
        enabled = state.canEdit,
    )
    SettingsSwitchRow(
        title = stringResource(Res.string.tx_field_cleared),
        checked = state.cleared,
        onCheckedChange = { if (state.canEdit) onIntent(TransactionEditorIntent.ClearedToggled(it)) },
        modifier = Modifier.testTag("txEditor.cleared"),
        supportingText = stringResource(Res.string.tx_cleared_help),
    )
}

/** "Confirm … instead": a received occurrence this entry may duplicate, so one payment is not recorded twice (spec 010 FR-031). */
@Composable
private fun MatchOffers(state: TransactionEditorState, onIntent: (TransactionEditorIntent) -> Unit) {
    if (!state.isCreate || state.matches.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.xs)) {
        InlineAlert(AlertTone.Info, stringResource(Res.string.recurring_match_title), Modifier.testTag("txEditor.matches"))
        state.matches.forEachIndexed { index, item ->
            val title = item.description?.takeIf { it.isNotBlank() } ?: stringResource(Res.string.recurring_rule_title)
            SecondaryButton(
                stringResource(Res.string.recurring_match_offer, title, formatMoney(item.amountMinor, item.currency)),
                { onIntent(TransactionEditorIntent.MatchClicked(item.id)) },
                modifier = Modifier.testTag("txEditor.match.$index"),
                enabled = state.canSave,
            )
        }
    }
}

@Composable
private fun Captions(state: TransactionEditorState, onIntent: (TransactionEditorIntent) -> Unit) {
    when (val by = state.addedBy) {
        is AddedBy.Member -> Caption(stringResource(Res.string.tx_added_by, by.name), "txEditor.addedBy")
        AddedBy.AnotherMember -> Caption(stringResource(Res.string.tx_added_by_other), "txEditor.addedBy")
        AddedBy.You, null -> Unit
    }
    if (state.fromRecurringRule) Caption(stringResource(Res.string.tx_from_recurring), "txEditor.recurring")
    if (state.canOpenRule) {
        AppTextButton(
            stringResource(Res.string.recurring_open_rule),
            { onIntent(TransactionEditorIntent.OpenRuleClicked) },
            Modifier.testTag("txEditor.openRule"),
        )
    }
}

@Composable
private fun Caption(text: String, tag: String) {
    Text(text, style = AppTheme.type.caption, color = AppTheme.colors.caption, modifier = Modifier.testTag(tag))
}

@Composable
private fun StatusArea(state: TransactionEditorState, onIntent: (TransactionEditorIntent) -> Unit) {
    when (val sync = state.sync) {
        EditorSync.None -> Unit
        EditorSync.Waiting -> InlineAlert(AlertTone.Info, stringResource(Res.string.wallet_status_waiting), Modifier.testTag("txEditor.status"))
        is EditorSync.Clash -> ClashCard(sync, state, onIntent)
        is EditorSync.Refused -> RefusedCard(sync, state, onIntent)
    }
}

@Composable
private fun RefusedCard(refused: EditorSync.Refused, state: TransactionEditorState, onIntent: (TransactionEditorIntent) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.xs)) {
        InlineAlert(AlertTone.Error, refused.reason.text(state.currency), Modifier.testTag("txEditor.status"))
        if (refused.reason.canRetryUnchanged) {
            PrimaryButton(
                stringResource(Res.string.action_retry), { onIntent(TransactionEditorIntent.TryAgain) },
                Modifier.testTag("txEditor.tryAgain"), enabled = !state.busy,
            )
        }
        SecondaryButton(
            stringResource(Res.string.action_discard), { onIntent(TransactionEditorIntent.Discard) },
            Modifier.testTag("txEditor.discard"), enabled = !state.busy,
        )
    }
}

/** Both versions side by side, and the person chooses; nothing is merged (FR-023, FR-024). */
@Composable
private fun ClashCard(clash: EditorSync.Clash, state: TransactionEditorState, onIntent: (TransactionEditorIntent) -> Unit) {
    Column(
        verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.xs),
        modifier = Modifier.fillMaxWidth().background(AppTheme.colors.surface, AppTheme.shapes.card).padding(AppTheme.spacing.m),
    ) {
        if (clash.deletedElsewhere) {
            InlineAlert(AlertTone.Warning, stringResource(Res.string.tx_status_deleted), Modifier.testTag("txEditor.status"))
            if (clash.isDelete) {
                // Both sides deleted it: nothing to restore, only the person's own deletion to settle.
                PrimaryButton(
                    stringResource(Res.string.action_accept_deletion), { onIntent(TransactionEditorIntent.AcceptDeletion) },
                    Modifier.testTag("txEditor.acceptDeletion"), loading = state.busy,
                )
                return@Column
            }
            Text(stringResource(Res.string.tx_deleted_body), style = AppTheme.type.body, color = AppTheme.colors.ink2)
            PrimaryButton(
                stringResource(Res.string.action_restore_with_mine), { onIntent(TransactionEditorIntent.RestoreWithMine) },
                Modifier.testTag("txEditor.restoreWithMine"), loading = state.busy,
            )
            SecondaryButton(
                stringResource(Res.string.action_accept_deletion), { onIntent(TransactionEditorIntent.AcceptDeletion) },
                Modifier.testTag("txEditor.acceptDeletion"), enabled = !state.busy,
            )
            return@Column
        }
        InlineAlert(AlertTone.Warning, stringResource(Res.string.tx_status_clash), Modifier.testTag("txEditor.status"))
        Text(
            stringResource(if (clash.isDelete) Res.string.tx_clash_delete_body else Res.string.tx_clash_body),
            style = AppTheme.type.body,
            color = AppTheme.colors.ink2,
        )
        clash.lines.forEach { line -> ClashLineView(line) }
        if (clash.isDelete) {
            PrimaryButton(
                stringResource(Res.string.action_delete_anyway), { onIntent(TransactionEditorIntent.DeleteAnyway) },
                Modifier.testTag("txEditor.deleteAnyway"), loading = state.busy,
            )
            SecondaryButton(
                stringResource(Res.string.action_keep_transaction), { onIntent(TransactionEditorIntent.KeepTransaction) },
                Modifier.testTag("txEditor.keepTransaction"), enabled = !state.busy,
            )
        } else {
            PrimaryButton(
                stringResource(Res.string.category_keep_mine), { onIntent(TransactionEditorIntent.KeepMine) },
                Modifier.testTag("txEditor.keepMine"), loading = state.busy,
            )
            SecondaryButton(
                stringResource(Res.string.category_use_theirs), { onIntent(TransactionEditorIntent.UseTheirs) },
                Modifier.testTag("txEditor.useTheirs"), enabled = !state.busy,
            )
        }
    }
}

@Composable
private fun ClashLineView(line: ClashLine) {
    val label = when (line.field) {
        ClashField.Amount -> Res.string.tx_field_amount
        ClashField.Date -> Res.string.tx_field_date
        ClashField.Category -> Res.string.tx_field_category
        ClashField.Note -> Res.string.tx_field_note
        ClashField.Details -> Res.string.tx_field_details
        ClashField.Person -> Res.string.tx_field_person
        ClashField.Cleared -> Res.string.tx_field_cleared
    }
    Text(stringResource(label), style = AppTheme.type.label, color = AppTheme.colors.ink)
    Text(stringResource(Res.string.tx_this_phone, line.mine.text()), style = AppTheme.type.body, color = AppTheme.colors.ink2)
    Text(stringResource(Res.string.tx_other_device, line.theirs.text()), style = AppTheme.type.body, color = AppTheme.colors.ink2)
}

@Composable
private fun ClashValue.text(): String = when (this) {
    is ClashValue.Text -> value?.takeIf { it.isNotBlank() } ?: stringResource(Res.string.category_none)
    is ClashValue.Money -> formatMoney(minor, currency)
    is ClashValue.Day -> formatDate(date)
    is ClashValue.Category -> display?.label() ?: stringResource(Res.string.category_none)
    is ClashValue.Cleared -> stringResource(if (cleared) Res.string.tx_cleared_yes else Res.string.tx_cleared_no)
}

@Composable
private fun Dialogs(state: TransactionEditorState, onIntent: (TransactionEditorIntent) -> Unit) {
    if (state.datePickerOpen) {
        AppDatePickerDialog(
            initial = state.date ?: LocalDate(2026, 1, 1),
            onPick = { onIntent(TransactionEditorIntent.DateChosen(it)) },
            onDismiss = { onIntent(TransactionEditorIntent.DatePickerDismissed) },
        )
    }
    if (state.categorySheetOpen) {
        val choices = state.categoryChoices ?: CategoryChoices(null, emptyList(), emptyList())
        val current = stringResource(Res.string.category_choice_current)
        val own = stringResource(Res.string.category_choice_own)
        val defaults = stringResource(Res.string.category_choice_defaults)
        val unavailable = stringResource(Res.string.category_unavailable)
        fun CategoryDisplay.item() = CategoryChoiceItem(id, name.ifEmpty { unavailable }, iconKey, colorHex)
        CategoryChoiceSheet(
            title = stringResource(Res.string.category_choice_title),
            groups = listOf(
                CategoryChoiceGroup(current, listOfNotNull(choices.current?.item()), "categoryChoice.current", indexed = false),
                CategoryChoiceGroup(own, choices.own.map { it.item() }, "categoryChoice.own"),
                CategoryChoiceGroup(defaults, choices.defaults.map { it.item() }, "categoryChoice.default"),
            ),
            selectedId = state.categoryId,
            onSelect = { onIntent(TransactionEditorIntent.CategoryChosen(it)) },
            onDismiss = { onIntent(TransactionEditorIntent.SheetDismissed) },
            emptyMessage = stringResource(Res.string.category_choice_empty),
        )
    }
    if (state.destinationSheetOpen) {
        SelectionSheet(
            title = stringResource(Res.string.tx_choose_destination),
            options = state.destinationChoices,
            label = { it.name },
            selected = state.destinationChoices.firstOrNull { it.id == state.destinationId },
            onSelect = { onIntent(TransactionEditorIntent.DestinationChosen(it.id)) },
            onDismiss = { onIntent(TransactionEditorIntent.SheetDismissed) },
            searchable = false,
        )
    }
    if (state.walletSheetOpen) {
        SelectionSheet(
            title = stringResource(Res.string.tx_choose_destination),
            options = state.walletChoices,
            label = { it.name },
            selected = state.walletChoices.firstOrNull { it.id == state.walletId },
            onSelect = { onIntent(TransactionEditorIntent.WalletChosen(it.id)) },
            onDismiss = { onIntent(TransactionEditorIntent.WalletSheetDismissed) },
            searchable = false,
        )
    }
    if (state.confirmDelete) {
        val date = state.shownDate ?: state.date
        AppAlertDialog(
            title = stringResource(Res.string.tx_delete_title),
            text = stringResource(
                Res.string.tx_delete_body,
                formatMoney(state.shownAmountMinor, state.currency),
                date?.let { formatDate(it) }.orEmpty(),
            ),
            confirmLabel = stringResource(Res.string.tx_delete),
            onConfirm = { onIntent(TransactionEditorIntent.DeleteConfirmed) },
            dismissLabel = stringResource(Res.string.action_cancel),
            onDismiss = { onIntent(TransactionEditorIntent.DeleteCancelled) },
            destructive = true,
            confirmLoading = state.busy,
        )
    }
}

@Preview
@Composable
private fun TransactionEditorScreenPreview() {
    AppTheme {
        TransactionEditorScreen(
            TransactionEditorState(
                loaded = true, walletId = "w1", currency = "EGP", amountText = "45.50", date = LocalDate(2026, 10, 2),
                walletChoices = listOf(WalletChoice("w1", "Cash", "EGP")),
            ),
            {},
        )
    }
}
