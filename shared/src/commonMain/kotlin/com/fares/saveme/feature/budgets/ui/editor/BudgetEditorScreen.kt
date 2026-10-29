package com.fares.saveme.feature.budgets.ui.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.border
import saveme.shared.generated.resources.budgets_calm_set
import saveme.shared.generated.resources.budgets_calm_more
import saveme.shared.generated.resources.budgets_calm_last_period
import saveme.shared.generated.resources.budgets_calm_average
import com.fares.saveme.feature.budgets.ui.list.compactAmount
import com.fares.saveme.feature.budgets.domain.BudgetValidation
import com.fares.saveme.designsystem.icon.BudgetsInfinity
import com.fares.saveme.designsystem.icon.CategoryIcons
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.alpha
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.sp
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
import com.fares.saveme.designsystem.component.AppAlertDialogCard
import com.fares.saveme.designsystem.component.AppListItem
import com.fares.saveme.designsystem.component.CalmActionBar
import com.fares.saveme.designsystem.component.CalmCard
import com.fares.saveme.designsystem.component.CalmCircleButton
import com.fares.saveme.designsystem.component.CalmIconTile
import com.fares.saveme.designsystem.component.CalmScreen
import com.fares.saveme.designsystem.component.CalmSectionTitle
import com.fares.saveme.designsystem.component.CategoryChoiceGroup
import com.fares.saveme.designsystem.component.CategoryChoiceItem
import com.fares.saveme.designsystem.component.CategoryChoiceSheet
import com.fares.saveme.designsystem.component.CategoryTile
import com.fares.saveme.designsystem.component.DestructiveButton
import com.fares.saveme.designsystem.component.InlineAlert
import com.fares.saveme.designsystem.component.ListTrailing
import com.fares.saveme.designsystem.component.PrimaryButton
import com.fares.saveme.designsystem.component.SecondaryButton
import com.fares.saveme.designsystem.component.SelectionSheet
import com.fares.saveme.designsystem.component.Skeleton
import com.fares.saveme.designsystem.component.formatMoney
import com.fares.saveme.designsystem.component.ltr
import com.fares.saveme.designsystem.icon.AppIcons
import com.fares.saveme.designsystem.icon.BudgetsChevronDown
import com.fares.saveme.designsystem.theme.AppTheme
import com.fares.saveme.feature.budgets.domain.model.BudgetChoices
import com.fares.saveme.feature.budgets.domain.model.BudgetField
import com.fares.saveme.feature.budgets.domain.model.RefusalReason
import com.fares.saveme.feature.budgets.ui.BudgetRowContent
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
import saveme.shared.generated.resources.budgets_calm_cover_title
import saveme.shared.generated.resources.budgets_calm_limit_title
import saveme.shared.generated.resources.budgets_cancel
import saveme.shared.generated.resources.cd_back
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

/** Categories shown in the create grid; the eighth cell opens the full chooser. */
private const val MAX_GRID = 7

@Composable
fun BudgetEditorScreen(
    state: BudgetEditorState,
    onIntent: (BudgetEditorIntent) -> Unit,
    snackbar: SnackbarHostState = remember { SnackbarHostState() },
) {
    CalmScreen(
        title = null,
        snackbarHostState = snackbar,
        spacing = 8.dp,
        titleSlot = {
            EditorHeader(stringResource(if (state.isCreate) Res.string.budgets_new_title else Res.string.budgets_edit_title)) {
                onIntent(BudgetEditorIntent.Back)
            }
        },
        bottomBar = {
            if (state.loaded) {
                CalmActionBar {
                    PrimaryButton(
                        stringResource(if (state.isCreate) Res.string.budgets_calm_set else Res.string.budgets_save),
                        { onIntent(BudgetEditorIntent.SaveClicked) },
                        modifier = Modifier.testTag("budgetEditor.save"),
                        enabled = state.canSave,
                        loading = state.saving,
                    )
                }
            }
        },
    ) {
        if (!state.loaded) {
            repeat(4) { Skeleton(Modifier.fillMaxWidth().height(52.dp)) }
            return@CalmScreen
        }
        if (!state.isCreate) {
            state.row?.let {
                CalmCard { BudgetRowContent(it.copy(status = null), Modifier.testTag("budgetEditor.progress")) }
            }
        }
        StatusArea(state, onIntent)
        CalmSectionTitle(stringResource(Res.string.budgets_calm_cover_title), Modifier.padding(top = 12.dp))
        CategoryField(state, onIntent)
        CalmSectionTitle(stringResource(Res.string.budgets_calm_limit_title), Modifier.padding(top = 12.dp))
        LimitField(state, onIntent)
        HistoryChips(state, onIntent)
        PeriodNote(state)
        if (state.readOnly) {
            Text(stringResource(Res.string.budgets_newer_version), style = AppTheme.type.body, color = AppTheme.colors.ink2, modifier = Modifier.testTag("budgetEditor.readOnly"))
        }
        if (!state.isCreate) EditActions(state, onIntent)
    }
    Sheets(state, onIntent)
    Confirmations(state, onIntent)
}

@Composable
private fun EditorHeader(title: String, onBack: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
        CalmCircleButton(AppIcons.Close, stringResource(Res.string.cd_back), onBack)
        Text(title, style = AppTheme.type.headline.copy(fontSize = 22.sp), color = AppTheme.colors.ink, modifier = Modifier.semantics { heading() })
    }
}

private fun categoryDisplay(state: BudgetEditorState): CategoryDisplay? {
    val id = state.categoryId ?: return null
    val choices = state.choices
    return listOfNotNull(choices?.current).plus(choices?.own.orEmpty()).plus(choices?.defaults.orEmpty()).firstOrNull { it.id == id }
}

@Composable
private fun categoryLabel(state: BudgetEditorState): String {
    if (!state.categoryChosen) return ""
    if (state.categoryId == null) return stringResource(Res.string.budgets_all_expenses)
    return budgetName(categoryDisplay(state)?.name ?: state.row?.categoryName ?: "")
}

/** While creating: "All expenses" and a grid of categories; otherwise the category in a card (locked once created). */
@Composable
private fun CategoryField(state: BudgetEditorState, onIntent: (BudgetEditorIntent) -> Unit) {
    val choices = state.choices
    if (state.categoryEditable && choices != null) CategoryGrid(state, choices, onIntent) else CategoryCard(state, onIntent)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CategoryGrid(state: BudgetEditorState, choices: BudgetChoices, onIntent: (BudgetEditorIntent) -> Unit) {
    val all = listOfNotNull(choices.current) + choices.own + choices.defaults
    val shown = all.take(MAX_GRID).toMutableList()
    val selected = state.categoryId.takeIf { state.categoryChosen }
    if (selected != null && shown.none { it.id == selected }) all.firstOrNull { it.id == selected }?.let { shown[MAX_GRID - 1] = it }
    val allSelected = state.categoryChosen && state.categoryId == null
    val error = state.errors[BudgetField.Category]?.text(BudgetField.Category)
    val allShape = RoundedCornerShape(18.dp)
    val allBlocked = choices.isAlreadyBudgeted(null)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.selectableGroup()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("budgetEditor.categoryAll")
                .clip(allShape)
                .background(if (allSelected) AppTheme.colors.primaryContainer else AppTheme.colors.surface, allShape)
                .border(1.5.dp, if (allSelected) AppTheme.colors.primary else AppTheme.colors.outlineVariant, allShape)
                .selectable(selected = allSelected, enabled = !allBlocked, role = Role.RadioButton, onClick = { onIntent(BudgetEditorIntent.CategoryChosen(null)) })
                .padding(horizontal = 16.dp, vertical = 14.dp),
        ) {
            Icon(AppIcons.BudgetsInfinity, contentDescription = null, tint = if (allSelected) AppTheme.colors.primary else AppTheme.colors.ink2, modifier = Modifier.size(22.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(Res.string.budgets_all_expenses),
                    style = AppTheme.type.itemTitle.copy(fontSize = 15.sp, fontWeight = FontWeight.Bold),
                    color = AppTheme.colors.ink,
                )
                if (allBlocked) Text(stringResource(Res.string.budgets_already_budgeted), style = AppTheme.type.caption.copy(fontSize = 12.5.sp, fontWeight = FontWeight.Normal), color = AppTheme.colors.ink2)
            }
        }
        (shown.map { it as CategoryDisplay? } + listOf(null)).chunked(4).forEach { chunk ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                chunk.forEach { display ->
                    if (display == null) {
                        GridCell(
                            AppIcons.More, stringResource(Res.string.budgets_calm_more), selected = false, enabled = true,
                            onClick = { onIntent(BudgetEditorIntent.CategoryClicked) },
                            modifier = Modifier.weight(1f).testTag("budgetEditor.category"),
                            role = Role.Button,
                        )
                    } else {
                        GridCell(
                            CategoryIcons.of(display.iconKey), budgetName(display.name), selected = display.id == selected,
                            enabled = !choices.isAlreadyBudgeted(display.id),
                            onClick = { onIntent(BudgetEditorIntent.CategoryChosen(display.id)) },
                            modifier = Modifier.weight(1f).testTag("budgetEditor.category.${display.id}"),
                        )
                    }
                }
                repeat(4 - chunk.size) { Spacer(Modifier.weight(1f)) }
            }
        }
        if (error != null) Text(error, style = AppTheme.type.caption, color = AppTheme.colors.error)
    }
}

/** An icon over a label; the chosen one is tinted, a category that already has a budget is dimmed and not selectable. */
@Composable
private fun GridCell(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    role: Role = Role.RadioButton,
) {
    val shape = RoundedCornerShape(16.dp)
    val tint = if (selected) AppTheme.colors.primary else AppTheme.colors.ink
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(5.dp),
        modifier = modifier
            .alpha(if (enabled) 1f else 0.5f)
            .clip(shape)
            .background(if (selected) AppTheme.colors.primaryContainer else Color.Transparent, shape)
            .then(
                if (role == Role.Button) {
                    Modifier.clickable(role = role, onClick = onClick)
                } else {
                    Modifier.selectable(selected = selected, enabled = enabled, role = role, onClick = onClick)
                },
            )
            .padding(vertical = 3.5.dp),
    ) {
        Icon(icon, contentDescription = null, tint = if (selected || !enabled) tint else AppTheme.colors.ink2, modifier = Modifier.size(22.dp))
        Text(
            label,
            style = AppTheme.type.caption.copy(fontSize = 12.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold),
            color = tint,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Last period and three-period average spending as quick limits. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HistoryChips(state: BudgetEditorState, onIntent: (BudgetEditorIntent) -> Unit) {
    val history = state.history ?: return
    if (state.readOnly) return
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.offset(y = (-5).dp)) {
        history.lastPeriodMinor?.let { minor ->
            HistoryChip(stringResource(Res.string.budgets_calm_last_period, compactAmount(minor, state.currency))) {
                onIntent(BudgetEditorIntent.LimitChanged(BudgetValidation.format(minor, state.currency)))
            }
        }
        history.averageMinor?.let { minor ->
            HistoryChip(stringResource(Res.string.budgets_calm_average, compactAmount(minor, state.currency))) {
                onIntent(BudgetEditorIntent.LimitChanged(BudgetValidation.format(minor, state.currency)))
            }
        }
    }
}

@Composable
private fun HistoryChip(label: String, onClick: () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.heightIn(min = 48.dp).clickable(role = Role.Button, onClick = onClick),
    ) {
        Text(label, style = AppTheme.type.caption.copy(fontSize = 12.5.sp), color = AppTheme.colors.ink)
    }
}

/** The category in a card that opens the chooser sheet; a created budget shows it locked. */
@Composable
private fun CategoryCard(state: BudgetEditorState, onIntent: (BudgetEditorIntent) -> Unit) {
    val label = stringResource(Res.string.budgets_field_category)
    val editable = state.categoryEditable
    val display = categoryDisplay(state)
    val value = if (editable) categoryLabel(state) else budgetName(state.row?.categoryName)
    val error = if (editable) state.errors[BudgetField.Category]?.text(BudgetField.Category) else null
    val shape = RoundedCornerShape(18.dp)
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("budgetEditor.category")
                .clip(shape)
                .background(AppTheme.colors.surface, shape)
                .border(1.5.dp, if (error != null) AppTheme.colors.error else AppTheme.colors.outlineVariant, shape)
                .then(
                    if (editable) {
                        Modifier
                            .clickable(role = Role.Button, onClick = { onIntent(BudgetEditorIntent.CategoryClicked) })
                            .semantics {
                                contentDescription = label
                                stateDescription = value
                            }
                    } else {
                        Modifier.semantics(mergeDescendants = true) {}
                    },
                )
                .padding(horizontal = 16.dp, vertical = 14.dp),
        ) {
            if (display != null) {
                CategoryTile(display.iconKey, display.colorHex)
            } else {
                CalmIconTile(AppIcons.Budgets, AppTheme.colors.ink2, AppTheme.colors.sunken, size = AppTheme.sizes.iconTile)
            }
            Column(Modifier.weight(1f)) {
                Text(label, style = AppTheme.type.caption, color = AppTheme.colors.ink2)
                Text(
                    value.ifEmpty { stringResource(Res.string.budgets_choose_category) },
                    style = AppTheme.type.itemTitle.copy(fontSize = 15.sp, fontWeight = FontWeight.Bold),
                    color = if (value.isEmpty()) AppTheme.colors.caption else AppTheme.colors.ink,
                    maxLines = 2,
                )
            }
            if (editable) Icon(AppIcons.ChevronForward, contentDescription = null, tint = AppTheme.colors.outline)
        }
        if (error != null) {
            Text(error, style = AppTheme.type.caption, color = AppTheme.colors.error)
        } else if (!editable) {
            Text(stringResource(Res.string.budgets_immutable), style = AppTheme.type.caption, color = AppTheme.colors.caption)
        }
    }
}

/** The limit as a large number in an outlined card, between the currency code and the currency button. */
@Composable
private fun LimitField(state: BudgetEditorState, onIntent: (BudgetEditorIntent) -> Unit) {
    val label = stringResource(Res.string.budgets_field_limit)
    val error = state.errors[BudgetField.Limit]?.text(BudgetField.Limit)
    val shape = RoundedCornerShape(18.dp)
    val currencyLabel = stringResource(Res.string.budgets_field_currency)
    val currencyEnabled = state.isCreate && (state.choices?.currencies?.size ?: 0) > 1
    val currencyError = if (state.isCreate) state.errors[BudgetField.Currency]?.text(BudgetField.Currency) else null
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .background(AppTheme.colors.surface, shape)
                .border(2.dp, if (error != null) AppTheme.colors.error else AppTheme.colors.primary, shape)
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            Text(state.currency, style = AppTheme.type.label.ltr().copy(fontSize = 18.sp, fontWeight = FontWeight.Bold), color = AppTheme.colors.ink2)
            BasicTextField(
                value = state.limitText,
                onValueChange = { onIntent(BudgetEditorIntent.LimitChanged(it)) },
                enabled = !state.readOnly,
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                textStyle = AppTheme.type.numeralL.ltr().copy(fontSize = 30.sp, color = AppTheme.colors.ink),
                cursorBrush = SolidColor(AppTheme.colors.primary),
                modifier = Modifier
                    .weight(1f)
                    .testTag("budgetEditor.limit")
                    .semantics { contentDescription = label },
            )
            if (state.isCreate) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("budgetEditor.currency")
                        .then(
                            if (currencyEnabled) {
                                Modifier
                                    .clickable(role = Role.Button, onClick = { onIntent(BudgetEditorIntent.CurrencyClicked) })
                                    .semantics {
                                        contentDescription = currencyLabel
                                        stateDescription = state.currency
                                    }
                            } else {
                                Modifier
                            },
                        ),
                ) {
                    Text(state.currency, style = AppTheme.type.label.ltr().copy(fontSize = 13.sp, fontWeight = FontWeight.Bold), color = AppTheme.colors.primary)
                    if (currencyEnabled) {
                        Icon(AppIcons.BudgetsChevronDown, contentDescription = null, tint = AppTheme.colors.primary, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
        val note = error ?: currencyError
        if (note != null) Text(note, style = AppTheme.type.caption, color = AppTheme.colors.error)
    }
}

/** The fixed monthly period and where to change its start day. */
@Composable
private fun PeriodNote(state: BudgetEditorState) {
    val monthly = stringResource(Res.string.budgets_period_monthly, state.startDay)
    val hint = stringResource(Res.string.budgets_period_hint)
    val immutable = stringResource(Res.string.budgets_immutable)
    val shape = RoundedCornerShape(14.dp)
    val base = AppTheme.type.caption.copy(fontSize = 13.sp, fontWeight = FontWeight.Medium)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier
            .padding(top = 1.dp)
            .fillMaxWidth()
            .testTag("budgetEditor.period")
            .background(AppTheme.colors.sunken, shape)
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Icon(AppIcons.Calendar, contentDescription = null, tint = AppTheme.colors.ink2, modifier = Modifier.size(20.dp))
        if (state.isCreate) {
            Text(
                buildAnnotatedString {
                    append("$monthly. ")
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = AppTheme.colors.primary)) { append(hint) }
                },
                style = base,
                color = AppTheme.colors.ink2,
                modifier = Modifier.weight(1f),
            )
        } else {
            Column(Modifier.weight(1f)) {
                Text(monthly, style = base, color = AppTheme.colors.ink2)
                Text(immutable, style = base, color = AppTheme.colors.ink2)
            }
        }
    }
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
    CalmCard(padding = PaddingValues(AppTheme.spacing.m)) { Column(verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.xs)) {
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
    } }
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
        CalmCard(padding = PaddingValues(0.dp)) {
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
