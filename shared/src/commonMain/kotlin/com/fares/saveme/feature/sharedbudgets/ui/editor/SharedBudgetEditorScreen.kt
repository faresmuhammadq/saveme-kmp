package com.fares.saveme.feature.sharedbudgets.ui.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fares.saveme.designsystem.component.AppAlertDialog
import com.fares.saveme.designsystem.component.AppBottomSheet
import com.fares.saveme.designsystem.component.AppSelectField
import com.fares.saveme.designsystem.component.AppTextButton
import com.fares.saveme.designsystem.component.AppTextField
import com.fares.saveme.designsystem.component.CalmCard
import com.fares.saveme.designsystem.component.CalmScreen
import com.fares.saveme.designsystem.component.CalmSectionTitle
import com.fares.saveme.designsystem.component.CalmTitle
import com.fares.saveme.designsystem.component.CategoryChoiceGroup
import com.fares.saveme.designsystem.component.CategoryChoiceItem
import com.fares.saveme.designsystem.component.CategoryChoiceSheet
import com.fares.saveme.designsystem.component.DestructiveButton
import com.fares.saveme.designsystem.component.PrimaryButton
import com.fares.saveme.designsystem.component.SecondaryButton
import com.fares.saveme.designsystem.component.SelectionSheet
import com.fares.saveme.designsystem.component.formatMoney
import com.fares.saveme.designsystem.theme.AppTheme
import com.fares.saveme.feature.categories.domain.model.CategoryChoices
import com.fares.saveme.feature.sharedbudgets.domain.SharedBudgetField
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import saveme.shared.generated.resources.Res
import saveme.shared.generated.resources.action_cancel
import saveme.shared.generated.resources.cd_back
import saveme.shared.generated.resources.sb_add_category
import saveme.shared.generated.resources.sb_archive
import saveme.shared.generated.resources.sb_categories
import saveme.shared.generated.resources.sb_category_unavailable
import saveme.shared.generated.resources.sb_delete
import saveme.shared.generated.resources.sb_delete_body
import saveme.shared.generated.resources.sb_delete_title
import saveme.shared.generated.resources.sb_editor_new
import saveme.shared.generated.resources.sb_field_currency
import saveme.shared.generated.resources.sb_field_limit
import saveme.shared.generated.resources.sb_field_name
import saveme.shared.generated.resources.sb_field_start_day
import saveme.shared.generated.resources.sb_limit_invalid
import saveme.shared.generated.resources.sb_line_limit_title
import saveme.shared.generated.resources.sb_msg_failed
import saveme.shared.generated.resources.sb_msg_not_allowed
import saveme.shared.generated.resources.sb_msg_offline
import saveme.shared.generated.resources.sb_msg_plan_full
import saveme.shared.generated.resources.sb_msg_stale
import saveme.shared.generated.resources.sb_name_invalid
import saveme.shared.generated.resources.sb_no_limit
import saveme.shared.generated.resources.sb_read_only_note
import saveme.shared.generated.resources.sb_remove
import saveme.shared.generated.resources.sb_save
import saveme.shared.generated.resources.sb_start_day_value
import saveme.shared.generated.resources.sb_categories_online_note
import saveme.shared.generated.resources.sb_pick_category
import saveme.shared.generated.resources.sb_no_categories

@Composable
fun SharedBudgetEditorRoute(
    budgetId: String?,
    onEffect: (SharedBudgetEditorEffect) -> Unit,
    onRequireVerification: () -> Unit,
    viewModel: SharedBudgetEditorViewModel = koinViewModel(key = "shared-budget-editor-${budgetId.orEmpty()}") { parametersOf(budgetId.orEmpty()) },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is SharedBudgetEditorEffect.ShowMessage -> scope.launch { snackbar.showSnackbar(getString(effect.message.res())) }
                SharedBudgetEditorEffect.RequireVerification -> onRequireVerification()
                else -> onEffect(effect)
            }
        }
    }
    SharedBudgetEditorScreen(state, viewModel::onIntent, snackbar)
}

private fun EditorMessage.res() = when (this) {
    EditorMessage.Offline -> Res.string.sb_msg_offline
    EditorMessage.NotAllowed -> Res.string.sb_msg_not_allowed
    EditorMessage.PlanFull -> Res.string.sb_msg_plan_full
    EditorMessage.Stale -> Res.string.sb_msg_stale
    EditorMessage.Failed -> Res.string.sb_msg_failed
}

@Composable
fun SharedBudgetEditorScreen(
    state: SharedBudgetEditorState,
    onIntent: (SharedBudgetEditorIntent) -> Unit,
    snackbar: SnackbarHostState = remember { SnackbarHostState() },
) {
    CalmScreen(
        title = null,
        onBack = { onIntent(SharedBudgetEditorIntent.Back) },
        backDescription = stringResource(Res.string.cd_back),
        snackbarHostState = snackbar,
        titleSlot = { CalmTitle(if (state.isCreate) stringResource(Res.string.sb_editor_new) else state.name) },
        bottomBar = {
            if (state.loaded && state.canEdit) {
                PrimaryButton(
                    stringResource(Res.string.sb_save), { onIntent(SharedBudgetEditorIntent.SaveClicked) },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp).testTag("sharedBudgetEditor.save"),
                    enabled = state.canSave, loading = state.saving,
                )
            }
        },
    ) {
        if (!state.loaded) return@CalmScreen
        if (!state.isOwner) {
            Text(stringResource(Res.string.sb_read_only_note), style = AppTheme.type.body, color = AppTheme.colors.ink2, modifier = Modifier.testTag("sharedBudgetEditor.readOnly"))
        }
        Fields(state, onIntent)
        if (!state.isCreate) Lines(state, onIntent)
        if (!state.isCreate && state.canEdit) {
            SecondaryButton(stringResource(Res.string.sb_archive), { onIntent(SharedBudgetEditorIntent.ArchiveClicked) }, modifier = Modifier.fillMaxWidth().testTag("sharedBudgetEditor.archive"), enabled = !state.busy)
            DestructiveButton(stringResource(Res.string.sb_delete), { onIntent(SharedBudgetEditorIntent.DeleteClicked) }, modifier = Modifier.fillMaxWidth().testTag("sharedBudgetEditor.delete"), enabled = !state.busy)
        }
    }
    Sheets(state, onIntent)
}

@Composable
private fun Fields(state: SharedBudgetEditorState, onIntent: (SharedBudgetEditorIntent) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        AppTextField(
            value = state.name, onValueChange = { onIntent(SharedBudgetEditorIntent.NameChanged(it)) },
            label = stringResource(Res.string.sb_field_name), enabled = state.canEdit,
            errorText = if (SharedBudgetField.Name in state.errors) stringResource(Res.string.sb_name_invalid) else null,
            modifier = Modifier.fillMaxWidth().testTag("sharedBudgetEditor.name"),
        )
        AppSelectField(
            label = stringResource(Res.string.sb_field_currency), value = state.currency,
            onClick = { onIntent(SharedBudgetEditorIntent.CurrencyClicked) },
            enabled = state.isCreate && state.currencies.size > 1,
            modifier = Modifier.testTag("sharedBudgetEditor.currency"),
        )
        AppTextField(
            value = state.limitText, onValueChange = { onIntent(SharedBudgetEditorIntent.LimitChanged(it)) },
            label = stringResource(Res.string.sb_field_limit), enabled = state.canEdit,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            supportingText = if (state.limitText.isBlank()) stringResource(Res.string.sb_no_limit) else null,
            errorText = if (SharedBudgetField.Limit in state.errors) stringResource(Res.string.sb_limit_invalid) else null,
            modifier = Modifier.fillMaxWidth().testTag("sharedBudgetEditor.limit"),
        )
        AppSelectField(
            label = stringResource(Res.string.sb_field_start_day), value = stringResource(Res.string.sb_start_day_value, state.startDay),
            onClick = { onIntent(SharedBudgetEditorIntent.StartDayClicked) }, enabled = state.canEdit,
            modifier = Modifier.testTag("sharedBudgetEditor.startDay"),
        )
    }
}

/** The budget's category list: rows with their limit and a way to take one out; owners add more (a server call). */
@Composable
private fun Lines(state: SharedBudgetEditorState, onIntent: (SharedBudgetEditorIntent) -> Unit) {
    CalmSectionTitle(stringResource(Res.string.sb_categories))
    CalmCard(modifier = Modifier.testTag("sharedBudgetEditor.lines")) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            if (state.lines.isEmpty()) {
                Text(stringResource(Res.string.sb_no_categories), style = AppTheme.type.body, color = AppTheme.colors.ink2)
            }
            state.lines.forEach { line ->
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("sharedBudgetEditor.line.${line.id}")) {
                    Column(Modifier.weight(1f)) {
                        Text(line.name.ifEmpty { stringResource(Res.string.sb_category_unavailable) }, style = AppTheme.type.body, color = AppTheme.colors.ink)
                        Text(
                            line.limitMinor?.let { formatMoney(it, state.currency) } ?: stringResource(Res.string.sb_no_limit),
                            style = AppTheme.type.caption, color = AppTheme.colors.caption,
                        )
                    }
                    if (state.canEdit) {
                        AppTextButton(stringResource(Res.string.sb_field_limit), { onIntent(SharedBudgetEditorIntent.LineClicked(line.id)) }, enabled = !state.busy)
                        AppTextButton(stringResource(Res.string.sb_remove), { onIntent(SharedBudgetEditorIntent.LineRemoved(line.id)) }, enabled = !state.busy)
                    }
                }
            }
            if (state.canEdit) {
                AppTextButton(stringResource(Res.string.sb_add_category), { onIntent(SharedBudgetEditorIntent.AddCategoryClicked) }, enabled = !state.busy, modifier = Modifier.testTag("sharedBudgetEditor.addCategory"))
                Text(stringResource(Res.string.sb_categories_online_note), style = AppTheme.type.caption, color = AppTheme.colors.caption)
            }
        }
    }
}

@Composable
private fun Sheets(state: SharedBudgetEditorState, onIntent: (SharedBudgetEditorIntent) -> Unit) {
    when (state.sheet) {
        EditorSheet.None -> Unit
        EditorSheet.Currency -> SelectionSheet(
            title = stringResource(Res.string.sb_field_currency), options = state.currencies, label = { it }, selected = state.currency,
            onSelect = { onIntent(SharedBudgetEditorIntent.CurrencyChosen(it)) }, onDismiss = { onIntent(SharedBudgetEditorIntent.SheetDismissed) }, searchable = false,
        )
        EditorSheet.StartDay -> SelectionSheet(
            title = stringResource(Res.string.sb_field_start_day), options = (1..28).toList(), label = { it.toString() }, selected = state.startDay,
            onSelect = { onIntent(SharedBudgetEditorIntent.StartDayChosen(it)) }, onDismiss = { onIntent(SharedBudgetEditorIntent.SheetDismissed) }, searchable = false,
        )
        EditorSheet.AddCategory -> {
            val choices = state.categoryChoices ?: CategoryChoices(null, emptyList(), emptyList())
            val taken = state.lines.map { it.categoryId }.toSet()
            val unavailable = stringResource(Res.string.sb_category_unavailable)
            fun com.fares.saveme.feature.categories.domain.model.CategoryDisplay.item() = CategoryChoiceItem(id, name.ifEmpty { unavailable }, iconKey, colorHex)
            CategoryChoiceSheet(
                title = stringResource(Res.string.sb_pick_category),
                groups = listOf(
                    CategoryChoiceGroup(null, choices.own.filter { it.id !in taken }.map { it.item() }, "sharedBudgetCategory.own"),
                    CategoryChoiceGroup(null, choices.defaults.filter { it.id !in taken }.map { it.item() }, "sharedBudgetCategory.default"),
                ),
                selectedId = null, onSelect = { onIntent(SharedBudgetEditorIntent.CategoryChosen(it)) },
                onDismiss = { onIntent(SharedBudgetEditorIntent.SheetDismissed) }, emptyMessage = stringResource(Res.string.sb_no_categories),
            )
        }
        EditorSheet.LineLimit -> AppBottomSheet(onDismiss = { onIntent(SharedBudgetEditorIntent.SheetDismissed) }, title = stringResource(Res.string.sb_line_limit_title, state.lineEditing?.name.orEmpty())) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
                AppTextField(
                    value = state.lineLimitText, onValueChange = { onIntent(SharedBudgetEditorIntent.LineLimitChanged(it)) },
                    label = stringResource(Res.string.sb_field_limit), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    supportingText = if (state.lineLimitText.isBlank()) stringResource(Res.string.sb_no_limit) else null,
                    errorText = if (state.lineLimitInvalid) stringResource(Res.string.sb_limit_invalid) else null,
                    modifier = Modifier.fillMaxWidth().testTag("sharedBudgetEditor.lineLimit"),
                )
                PrimaryButton(stringResource(Res.string.sb_save), { onIntent(SharedBudgetEditorIntent.LineLimitSaved) }, modifier = Modifier.fillMaxWidth())
            }
        }
    }
    if (state.confirmDelete) {
        AppAlertDialog(
            title = stringResource(Res.string.sb_delete_title), text = stringResource(Res.string.sb_delete_body, state.name),
            confirmLabel = stringResource(Res.string.sb_delete), onConfirm = { onIntent(SharedBudgetEditorIntent.DeleteConfirmed) },
            dismissLabel = stringResource(Res.string.action_cancel), onDismiss = { onIntent(SharedBudgetEditorIntent.DeleteCancelled) },
            destructive = true, confirmLoading = state.busy,
        )
    }
}
