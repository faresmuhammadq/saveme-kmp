package com.fares.saveme.feature.budgets.ui.editor

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
import androidx.compose.ui.window.Dialog
import com.fares.saveme.designsystem.component.AlertTone
import com.fares.saveme.designsystem.component.AmountField
import com.fares.saveme.designsystem.component.AppAlertDialogCard
import com.fares.saveme.designsystem.component.AppListItem
import com.fares.saveme.designsystem.component.AppScreen
import com.fares.saveme.designsystem.component.AppSelectField
import com.fares.saveme.designsystem.component.AppTextField
import com.fares.saveme.designsystem.component.CategoryChoiceGroup
import com.fares.saveme.designsystem.component.CategoryChoiceItem
import com.fares.saveme.designsystem.component.CategoryChoiceSheet
import com.fares.saveme.designsystem.component.DestructiveButton
import com.fares.saveme.designsystem.component.InlineAlert
import com.fares.saveme.designsystem.component.ListTrailing
import com.fares.saveme.designsystem.component.PrimaryButton
import com.fares.saveme.designsystem.component.SecondaryButton
import com.fares.saveme.designsystem.component.SelectionSheet
import com.fares.saveme.designsystem.component.Skeleton
import com.fares.saveme.designsystem.component.formatMoney
import com.fares.saveme.designsystem.theme.AppTheme
import com.fares.saveme.feature.budgets.domain.model.BudgetChoices
import com.fares.saveme.feature.budgets.domain.model.BudgetField
import com.fares.saveme.feature.budgets.domain.model.RefusalReason
import com.fares.saveme.feature.budgets.ui.BudgetRowContent
import com.fares.saveme.feature.budgets.ui.BudgetsBackBar
import com.fares.saveme.feature.budgets.ui.budgetName
import com.fares.saveme.feature.budgets.ui.text
import com.fares.saveme.feature.categories.domain.model.CategoryDisplay
import org.jetbrains.compose.resources.stringResource
import saveme.shared.generated.resources.Res
import saveme.shared.generated.resources.budgets_accept_deletion
import saveme.shared.generated.resources.budgets_all_expenses
import saveme.shared.generated.resources.budgets_already_budgeted
import saveme.shared.generated.resources.budgets_archive
import saveme.shared.generated.resources.budgets_archive_body
import saveme.shared.generated.resources.budgets_archive_confirm
import saveme.shared.generated.resources.budgets_archive_title
import saveme.shared.generated.resources.budgets_cancel
import saveme.shared.generated.resources.budgets_category_unavailable
import saveme.shared.generated.resources.budgets_choice_defaults
import saveme.shared.generated.resources.budgets_choice_empty
import saveme.shared.generated.resources.budgets_choice_own
import saveme.shared.generated.resources.budgets_choose_category
import saveme.shared.generated.resources.budgets_choose_currency
import saveme.shared.generated.resources.budgets_clash_body
import saveme.shared.generated.resources.budgets_delete
import saveme.shared.generated.resources.budgets_delete_body
import saveme.shared.generated.resources.budgets_delete_anyway
import saveme.shared.generated.resources.budgets_delete_confirm
import saveme.shared.generated.resources.budgets_keep_budget
import saveme.shared.generated.resources.budgets_delete_title
import saveme.shared.generated.resources.budgets_discard
import saveme.shared.generated.resources.budgets_edit_title
import saveme.shared.generated.resources.budgets_field_category
import saveme.shared.generated.resources.budgets_field_currency
import saveme.shared.generated.resources.budgets_field_limit
import saveme.shared.generated.resources.budgets_field_period
import saveme.shared.generated.resources.budgets_immutable
import saveme.shared.generated.resources.budgets_keep_mine
import saveme.shared.generated.resources.budgets_new_title
import saveme.shared.generated.resources.budgets_newer_version
import saveme.shared.generated.resources.budgets_other_device
import saveme.shared.generated.resources.budgets_period_hint
import saveme.shared.generated.resources.budgets_period_monthly
import saveme.shared.generated.resources.budgets_save
import saveme.shared.generated.resources.budgets_status_clash
import saveme.shared.generated.resources.budgets_status_deleted
import saveme.shared.generated.resources.budgets_this_phone
import saveme.shared.generated.resources.budgets_try_again
import saveme.shared.generated.resources.budgets_use_theirs
import saveme.shared.generated.resources.budgets_waiting_note
import saveme.shared.generated.resources.budgets_section_active
import saveme.shared.generated.resources.budgets_section_archived

/** Stands for "All expenses" in the category chooser; a category id is a UUID, so it never collides. */
private const val ALL_ID = "all"

@Composable
fun BudgetEditorScreen(
    state: BudgetEditorState,
    onIntent: (BudgetEditorIntent) -> Unit,
    snackbar: SnackbarHostState = remember { SnackbarHostState() },
) {
    AppScreen(
        snackbarHostState = snackbar,
        topBar = {
            BudgetsBackBar(
                stringResource(if (state.isCreate) Res.string.budgets_new_title else Res.string.budgets_edit_title),
                onBack = { onIntent(BudgetEditorIntent.Back) },
            )
        },
    ) {
        if (!state.loaded) {
            repeat(4) { Skeleton(Modifier.fillMaxWidth().height(52.dp)) }
            return@AppScreen
        }
        if (!state.isCreate) state.row?.let { BudgetRowContent(it.copy(status = null), Modifier.testTag("budgetEditor.progress")) }
        StatusArea(state, onIntent)
        CategoryField(state, onIntent)
        CurrencyField(state, onIntent)
        AmountField(
            value = state.limitText,
            onValueChange = { onIntent(BudgetEditorIntent.LimitChanged(it)) },
            currency = state.currency,
            label = stringResource(Res.string.budgets_field_limit),
            modifier = Modifier.fillMaxWidth().testTag("budgetEditor.limit"),
            errorText = state.errors[BudgetField.Limit]?.text(BudgetField.Limit),
            enabled = !state.readOnly,
        )
        AppTextField(
            value = stringResource(Res.string.budgets_period_monthly, state.startDay),
            onValueChange = {},
            label = stringResource(Res.string.budgets_field_period),
            modifier = Modifier.fillMaxWidth().testTag("budgetEditor.period"),
            enabled = false,
            supportingText = stringResource(if (state.isCreate) Res.string.budgets_period_hint else Res.string.budgets_immutable),
        )
        if (state.readOnly) {
            Text(stringResource(Res.string.budgets_newer_version), style = AppTheme.type.body, color = AppTheme.colors.ink2, modifier = Modifier.testTag("budgetEditor.readOnly"))
        }
        PrimaryButton(
            stringResource(Res.string.budgets_save),
            { onIntent(BudgetEditorIntent.SaveClicked) },
            modifier = Modifier.testTag("budgetEditor.save"),
            enabled = state.canSave,
            loading = state.saving,
        )
        if (!state.isCreate) EditActions(state, onIntent)
    }
    Sheets(state, onIntent)
    Confirmations(state, onIntent)
}

@Composable
private fun categoryLabel(state: BudgetEditorState): String {
    if (!state.categoryChosen) return ""
    val id = state.categoryId ?: return stringResource(Res.string.budgets_all_expenses)
    val choices = state.choices
    val display = listOfNotNull(choices?.current).plus(choices?.own.orEmpty()).plus(choices?.defaults.orEmpty()).firstOrNull { it.id == id }
    return budgetName(display?.name ?: state.row?.categoryName ?: "")
}

@Composable
private fun CategoryField(state: BudgetEditorState, onIntent: (BudgetEditorIntent) -> Unit) {
    if (state.categoryEditable) {
        AppSelectField(
            label = stringResource(Res.string.budgets_field_category),
            value = categoryLabel(state),
            onClick = { onIntent(BudgetEditorIntent.CategoryClicked) },
            modifier = Modifier.testTag("budgetEditor.category"),
            errorText = state.errors[BudgetField.Category]?.text(BudgetField.Category),
        )
        return
    }
    AppTextField(
        value = budgetName(state.row?.categoryName),
        onValueChange = {},
        label = stringResource(Res.string.budgets_field_category),
        modifier = Modifier.fillMaxWidth().testTag("budgetEditor.category"),
        enabled = false,
        supportingText = stringResource(Res.string.budgets_immutable),
    )
}

@Composable
private fun CurrencyField(state: BudgetEditorState, onIntent: (BudgetEditorIntent) -> Unit) {
    if (state.isCreate) {
        AppSelectField(
            label = stringResource(Res.string.budgets_field_currency),
            value = state.currency,
            onClick = { onIntent(BudgetEditorIntent.CurrencyClicked) },
            modifier = Modifier.testTag("budgetEditor.currency"),
            errorText = state.errors[BudgetField.Currency]?.text(BudgetField.Currency),
            enabled = (state.choices?.currencies?.size ?: 0) > 1,
        )
        return
    }
    AppTextField(
        value = state.currency,
        onValueChange = {},
        label = stringResource(Res.string.budgets_field_currency),
        modifier = Modifier.fillMaxWidth().testTag("budgetEditor.currency"),
        enabled = false,
        supportingText = stringResource(Res.string.budgets_immutable),
    )
}

@Composable
private fun StatusArea(state: BudgetEditorState, onIntent: (BudgetEditorIntent) -> Unit) {
    when (val sync = state.sync) {
        EditorSync.None -> Unit
        EditorSync.Waiting -> InlineAlert(AlertTone.Info, stringResource(Res.string.budgets_waiting_note), Modifier.testTag("budgetEditor.status"))
        is EditorSync.Clash -> ClashCard(sync, state.busy, onIntent)
        is EditorSync.Refused -> RefusedCard(sync.reason, state.busy, onIntent)
    }
}

@Composable
private fun RefusedCard(reason: RefusalReason, busy: Boolean, onIntent: (BudgetEditorIntent) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.xs)) {
        InlineAlert(AlertTone.Error, reason.text(), Modifier.testTag("budgetEditor.status"))
        if (reason.canRetryUnchanged) {
            PrimaryButton(stringResource(Res.string.budgets_try_again), { onIntent(BudgetEditorIntent.TryAgain) }, Modifier.testTag("budgetEditor.tryAgain"), enabled = !busy)
        }
        SecondaryButton(stringResource(Res.string.budgets_discard), { onIntent(BudgetEditorIntent.Discard) }, Modifier.testTag("budgetEditor.discard"), enabled = !busy)
    }
}

/** Both versions side by side, and the person chooses; nothing is merged (P-04.4). */
@Composable
private fun ClashCard(clash: EditorSync.Clash, busy: Boolean, onIntent: (BudgetEditorIntent) -> Unit) {
    Column(
        verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.xs),
        modifier = Modifier.fillMaxWidth().background(AppTheme.colors.surface, AppTheme.shapes.card).padding(AppTheme.spacing.m),
    ) {
        if (clash.deletedElsewhere) {
            InlineAlert(AlertTone.Warning, stringResource(Res.string.budgets_status_deleted), Modifier.testTag("budgetEditor.status"))
            PrimaryButton(stringResource(Res.string.budgets_accept_deletion), { onIntent(BudgetEditorIntent.AcceptDeletion) }, Modifier.testTag("budgetEditor.accept"), loading = busy)
            return@Column
        }
        InlineAlert(AlertTone.Warning, stringResource(Res.string.budgets_status_clash), Modifier.testTag("budgetEditor.status"))
        Text(stringResource(Res.string.budgets_clash_body), style = AppTheme.type.body, color = AppTheme.colors.ink2)
        clash.lines.forEach { line -> ClashLineView(line) }
        // A deletion that met a newer copy: send it again, or keep the budget as the other device left it.
        PrimaryButton(
            stringResource(if (clash.isDelete) Res.string.budgets_delete_anyway else Res.string.budgets_keep_mine),
            { onIntent(BudgetEditorIntent.KeepMine) }, Modifier.testTag("budgetEditor.keepMine"), loading = busy,
        )
        SecondaryButton(
            stringResource(if (clash.isDelete) Res.string.budgets_keep_budget else Res.string.budgets_use_theirs),
            { onIntent(BudgetEditorIntent.UseTheirs) }, Modifier.testTag("budgetEditor.useTheirs"), enabled = !busy,
        )
    }
}

@Composable
private fun ClashLineView(line: ClashLine) {
    val label = when (line.field) {
        ClashField.Limit -> stringResource(Res.string.budgets_field_limit)
        ClashField.Archived -> stringResource(Res.string.budgets_section_archived)
    }
    Text(label, style = AppTheme.type.label, color = AppTheme.colors.ink)
    Text(stringResource(Res.string.budgets_this_phone, line.mine.text()), style = AppTheme.type.body, color = AppTheme.colors.ink2)
    Text(stringResource(Res.string.budgets_other_device, line.theirs.text()), style = AppTheme.type.body, color = AppTheme.colors.ink2)
}

@Composable
private fun ClashValue.text(): String = when (this) {
    is ClashValue.Limit -> formatMoney(minor, currency)
    is ClashValue.Archived -> stringResource(if (archived) Res.string.budgets_section_archived else Res.string.budgets_section_active)
}

@Composable
private fun EditActions(state: BudgetEditorState, onIntent: (BudgetEditorIntent) -> Unit) {
    if (state.canArchive) {
        Column(Modifier.fillMaxWidth().background(AppTheme.colors.surface, AppTheme.shapes.card)) {
            AppListItem(
                stringResource(Res.string.budgets_archive),
                modifier = Modifier.testTag("budgetEditor.archive"),
                trailing = ListTrailing.Chevron,
                onClick = { onIntent(BudgetEditorIntent.ArchiveClicked) },
            )
        }
    }
    if (state.canDelete) {
        DestructiveButton(stringResource(Res.string.budgets_delete), { onIntent(BudgetEditorIntent.DeleteClicked) }, modifier = Modifier.testTag("budgetEditor.delete"))
    }
}

@Composable
private fun Sheets(state: BudgetEditorState, onIntent: (BudgetEditorIntent) -> Unit) {
    val choices = state.choices
    if (state.categorySheetOpen && choices != null) {
        CategoryChoiceSheet(
            title = stringResource(Res.string.budgets_choose_category),
            groups = budgetCategoryGroups(choices),
            selectedId = budgetSelectedChoice(state),
            onSelect = { onIntent(BudgetEditorIntent.CategoryChosen(it.takeUnless { id -> id == ALL_ID })) },
            onDismiss = { onIntent(BudgetEditorIntent.SheetDismissed) },
            emptyMessage = stringResource(Res.string.budgets_choice_empty),
        )
    }
    if (state.currencySheetOpen && choices != null) {
        SelectionSheet(
            title = stringResource(Res.string.budgets_choose_currency),
            options = choices.currencies,
            label = { it },
            selected = state.currency,
            onSelect = { onIntent(BudgetEditorIntent.CurrencyChosen(it)) },
            onDismiss = { onIntent(BudgetEditorIntent.SheetDismissed) },
            searchable = false,
        )
    }
}

/** The id the chooser marks as selected: "All expenses", the chosen category, or nothing yet. */
internal fun budgetSelectedChoice(state: BudgetEditorState): String? = if (state.categoryChosen) state.categoryId ?: ALL_ID else null

/**
 * The category chooser's groups (FR-003, FR-004): "All expenses" first, the current category when it is no longer offered, then
 * the person's own and the defaults. A category that already has an active budget in the chosen currency is a disabled row
 * whose caption says why. Shared with the screenshot tests, which cannot see a sheet window.
 */
@Composable
internal fun budgetCategoryGroups(choices: BudgetChoices): List<CategoryChoiceGroup> {
    val own = stringResource(Res.string.budgets_choice_own)
    val defaults = stringResource(Res.string.budgets_choice_defaults)
    val already = stringResource(Res.string.budgets_already_budgeted)
    val unavailable = stringResource(Res.string.budgets_category_unavailable)
    fun CategoryDisplay.item() = CategoryChoiceItem(
        id, name.ifEmpty { unavailable }, iconKey, colorHex,
        caption = already.takeIf { choices.isAlreadyBudgeted(id) }, enabled = !choices.isAlreadyBudgeted(id),
    )
    return listOf(
        CategoryChoiceGroup(
            null,
            listOf(
                CategoryChoiceItem(
                    ALL_ID, stringResource(Res.string.budgets_all_expenses), "chart", null,
                    caption = already.takeIf { choices.isAlreadyBudgeted(null) }, enabled = !choices.isAlreadyBudgeted(null),
                ),
            ),
            "budgetChoice.all", indexed = false,
        ),
        CategoryChoiceGroup(null, listOfNotNull(choices.current?.item()), "budgetChoice.current", indexed = false),
        CategoryChoiceGroup(own, choices.own.map { it.item() }, "budgetChoice.own"),
        CategoryChoiceGroup(defaults, choices.defaults.map { it.item() }, "budgetChoice.default"),
    )
}

@Composable
private fun Confirmations(state: BudgetEditorState, onIntent: (BudgetEditorIntent) -> Unit) {
    if (state.confirm == EditorConfirm.None) return
    Dialog(onDismissRequest = { if (!state.busy) onIntent(BudgetEditorIntent.DismissConfirm) }) {
        ConfirmCard(state, onIntent)
    }
}

/** The confirmation dialog's surface (also shown by the screenshot tests, which cannot see a dialog window). */
@Composable
internal fun ConfirmCard(state: BudgetEditorState, onIntent: (BudgetEditorIntent) -> Unit) {
    val dismiss = { onIntent(BudgetEditorIntent.DismissConfirm) }
    val cancelLabel = stringResource(Res.string.budgets_cancel)
    val confirmTag = Modifier.testTag("budgetEditor.confirm")
    val cancelTag = Modifier.testTag("budgetEditor.cancel")
    when (state.confirm) {
        EditorConfirm.Archive -> AppAlertDialogCard(
            title = stringResource(Res.string.budgets_archive_title),
            text = stringResource(Res.string.budgets_archive_body),
            confirmLabel = stringResource(Res.string.budgets_archive_confirm),
            onConfirm = { onIntent(BudgetEditorIntent.ConfirmArchive) },
            dismissLabel = cancelLabel, onDismiss = dismiss,
            confirmLoading = state.busy, confirmModifier = confirmTag, dismissModifier = cancelTag,
        )
        EditorConfirm.Delete -> AppAlertDialogCard(
            title = stringResource(Res.string.budgets_delete_title),
            text = stringResource(Res.string.budgets_delete_body),
            confirmLabel = stringResource(Res.string.budgets_delete_confirm),
            onConfirm = { onIntent(BudgetEditorIntent.ConfirmDelete) },
            dismissLabel = cancelLabel, onDismiss = dismiss, destructive = true,
            confirmLoading = state.busy, confirmModifier = confirmTag, dismissModifier = cancelTag,
        )
        EditorConfirm.None -> Unit
    }
}

@Preview
@Composable
private fun BudgetEditorScreenPreview() {
    AppTheme {
        BudgetEditorScreen(BudgetEditorState(loaded = true, currency = "EGP", limitText = "6000", startDay = 25), {})
    }
}
