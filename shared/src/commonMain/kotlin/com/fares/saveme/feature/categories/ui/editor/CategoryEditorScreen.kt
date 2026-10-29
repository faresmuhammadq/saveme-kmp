package com.fares.saveme.feature.categories.ui.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
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
import com.fares.saveme.designsystem.component.CalmDivider
import com.fares.saveme.designsystem.component.CalmIconTile
import com.fares.saveme.designsystem.component.CalmScreen
import com.fares.saveme.designsystem.component.DestructiveButton
import com.fares.saveme.designsystem.component.InlineAlert
import com.fares.saveme.designsystem.component.ListTrailing
import com.fares.saveme.designsystem.component.PrimaryButton
import com.fares.saveme.designsystem.component.SecondaryButton
import com.fares.saveme.designsystem.component.Skeleton
import com.fares.saveme.designsystem.icon.AppIcons
import com.fares.saveme.designsystem.icon.CategoryIcons
import com.fares.saveme.designsystem.theme.AppTheme
import com.fares.saveme.designsystem.tokens.CategoryColors
import com.fares.saveme.designsystem.tokens.WalletAccents
import com.fares.saveme.feature.categories.domain.model.CategoryField
import com.fares.saveme.feature.categories.domain.model.CategoryKind
import com.fares.saveme.feature.categories.domain.model.RefusalReason
import com.fares.saveme.feature.categories.ui.text
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import saveme.shared.generated.resources.Res
import saveme.shared.generated.resources.action_accept_deletion
import saveme.shared.generated.resources.action_archive_instead
import saveme.shared.generated.resources.action_cancel
import saveme.shared.generated.resources.action_delete_anyway
import saveme.shared.generated.resources.action_discard
import saveme.shared.generated.resources.action_keep_category
import saveme.shared.generated.resources.action_retry
import saveme.shared.generated.resources.categories_archived
import saveme.shared.generated.resources.categories_calm_create
import saveme.shared.generated.resources.cd_back
import saveme.shared.generated.resources.category_archive
import saveme.shared.generated.resources.category_archive_body
import saveme.shared.generated.resources.category_archive_title
import saveme.shared.generated.resources.category_clash_body
import saveme.shared.generated.resources.category_delete
import saveme.shared.generated.resources.category_delete_body
import saveme.shared.generated.resources.category_delete_title
import saveme.shared.generated.resources.category_field_color
import saveme.shared.generated.resources.category_field_icon
import saveme.shared.generated.resources.category_field_kind
import saveme.shared.generated.resources.category_field_name
import saveme.shared.generated.resources.category_in_use_note
import saveme.shared.generated.resources.category_keep_mine
import saveme.shared.generated.resources.category_kind_expense
import saveme.shared.generated.resources.category_kind_income
import saveme.shared.generated.resources.category_kind_locked_note
import saveme.shared.generated.resources.category_none
import saveme.shared.generated.resources.category_other_device
import saveme.shared.generated.resources.category_save
import saveme.shared.generated.resources.category_status_clash
import saveme.shared.generated.resources.category_status_waiting
import saveme.shared.generated.resources.category_this_phone
import saveme.shared.generated.resources.category_title_edit
import saveme.shared.generated.resources.category_title_new
import saveme.shared.generated.resources.category_use_theirs
import saveme.shared.generated.resources.sync_deleted_elsewhere
import saveme.shared.generated.resources.cd_category_icon_cart
import saveme.shared.generated.resources.cd_category_icon_restaurant
import saveme.shared.generated.resources.cd_category_icon_bus
import saveme.shared.generated.resources.cd_category_icon_fuel
import saveme.shared.generated.resources.cd_category_icon_home
import saveme.shared.generated.resources.cd_category_icon_bolt
import saveme.shared.generated.resources.cd_category_icon_phone
import saveme.shared.generated.resources.cd_category_icon_health
import saveme.shared.generated.resources.cd_category_icon_school
import saveme.shared.generated.resources.cd_category_icon_shirt
import saveme.shared.generated.resources.cd_category_icon_sofa
import saveme.shared.generated.resources.cd_category_icon_care
import saveme.shared.generated.resources.cd_category_icon_ticket
import saveme.shared.generated.resources.cd_category_icon_repeat
import saveme.shared.generated.resources.cd_category_icon_plane
import saveme.shared.generated.resources.cd_category_icon_child
import saveme.shared.generated.resources.cd_category_icon_gift
import saveme.shared.generated.resources.cd_category_icon_heart
import saveme.shared.generated.resources.cd_category_icon_dots
import saveme.shared.generated.resources.cd_category_icon_wallet
import saveme.shared.generated.resources.cd_category_icon_store
import saveme.shared.generated.resources.cd_category_icon_laptop
import saveme.shared.generated.resources.cd_category_icon_chart
import saveme.shared.generated.resources.cd_category_icon_undo
import saveme.shared.generated.resources.cd_category_color_0
import saveme.shared.generated.resources.cd_category_color_1
import saveme.shared.generated.resources.cd_category_color_2
import saveme.shared.generated.resources.cd_category_color_3
import saveme.shared.generated.resources.cd_category_color_4
import saveme.shared.generated.resources.cd_category_color_5
import saveme.shared.generated.resources.cd_category_color_6
import saveme.shared.generated.resources.cd_category_color_7

private const val NONE_KEY = "none"

@Composable
fun CategoryEditorScreen(
    state: CategoryEditorState,
    onIntent: (CategoryEditorIntent) -> Unit,
    snackbar: SnackbarHostState = remember { SnackbarHostState() },
) {
    var colourTab by rememberSaveable { mutableStateOf(false) }
    CalmScreen(
        title = null,
        snackbarHostState = snackbar,
        titleSlot = {
            EditorHeader(stringResource(if (state.isCreate) Res.string.category_title_new else Res.string.category_title_edit)) {
                onIntent(CategoryEditorIntent.Back)
            }
        },
        bottomBar = {
            if (state.loaded) {
                CalmActionBar {
                    PrimaryButton(
                        stringResource(if (state.isCreate) Res.string.categories_calm_create else Res.string.category_save),
                        { onIntent(CategoryEditorIntent.SaveClicked) },
                        modifier = Modifier.testTag("categoryEditor.save"),
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
        StatusArea(state, onIntent)
        Identity(state, onIntent)
        PickerTabs(colourTab) { colourTab = it }
        if (colourTab) ColorGrid(state, onIntent) else IconGrid(state, onIntent)
        if (!state.isCreate) EditActions(state, onIntent)
    }
    Confirmations(state, onIntent)
}

@Composable
private fun EditorHeader(title: String, onBack: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
        CalmCircleButton(AppIcons.Close, stringResource(Res.string.cd_back), onBack)
        Text(title, style = AppTheme.type.headline.copy(fontSize = 22.sp), color = AppTheme.colors.ink, modifier = Modifier.semantics { heading() })
    }
}

@Composable
private fun kindLabel(kind: CategoryKind): String =
    stringResource(if (kind == CategoryKind.Expense) Res.string.category_kind_expense else Res.string.category_kind_income)

/** Tile preview of the chosen icon and colour, the centred name field and the expense/income choice. */
@Composable
private fun Identity(state: CategoryEditorState, onIntent: (CategoryEditorIntent) -> Unit) {
    val colour = CategoryColors.of(state.colorHex)
    val nameLabel = stringResource(Res.string.category_field_name)
    val nameError = state.errors[CategoryField.Name]?.text(CategoryField.Name)
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(7.dp), modifier = Modifier.fillMaxWidth().padding(top = 6.dp)) {
        CalmIconTile(
            CategoryIcons.of(state.iconKey), colour.tint, colour.container,
            size = 96.dp, iconSize = 48.dp, radius = 30.dp,
        )
        BasicTextField(
            value = state.name,
            onValueChange = { onIntent(CategoryEditorIntent.NameChanged(it)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            textStyle = AppTheme.type.headline.copy(textAlign = TextAlign.Center, color = AppTheme.colors.ink),
            cursorBrush = SolidColor(AppTheme.colors.primary),
            decorationBox = { inner ->
                Column {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxWidth()) {
                        if (state.name.isEmpty()) {
                            Text(nameLabel, style = AppTheme.type.headline, color = AppTheme.colors.caption, textAlign = TextAlign.Center)
                        }
                        inner()
                    }
                    Box(Modifier.padding(top = 6.dp).fillMaxWidth().height(2.dp).background(if (nameError != null) AppTheme.colors.error else AppTheme.colors.primary))
                }
            },
            modifier = Modifier.fillMaxWidth().padding(top = 5.dp).testTag("categoryEditor.name").semantics { contentDescription = nameLabel },
        )
        if (nameError != null) Text(nameError, style = AppTheme.type.caption, color = AppTheme.colors.error, textAlign = TextAlign.Center)
        KindChoice(state, onIntent)
    }
}

@Composable
private fun KindChoice(state: CategoryEditorState, onIntent: (CategoryEditorIntent) -> Unit) {
    val kindLabel = stringResource(Res.string.category_field_kind)
    val kindError = state.errors[CategoryField.Kind]?.text(CategoryField.Kind)
    if (state.kindEditable) {
        Row(horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.xs), modifier = Modifier.selectableGroup().semantics { contentDescription = kindLabel }) {
            CategoryKind.entries.forEach { kind ->
                KindPill(kind, selected = kind == (state.kind ?: CategoryKind.Expense), onClick = { onIntent(CategoryEditorIntent.KindChosen(kind)) }, modifier = Modifier.testTag("categoryEditor.kind.${kind.wire}"))
            }
        }
    } else {
        val kind = state.kind
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (kind != null) {
                KindPill(kind, selected = true, onClick = null, modifier = Modifier.testTag("categoryEditor.kind"))
            } else {
                Text(state.kindWire, style = AppTheme.type.label, color = AppTheme.colors.ink2, modifier = Modifier.testTag("categoryEditor.kind"))
            }
            Text(stringResource(Res.string.category_kind_locked_note), style = AppTheme.type.caption, color = AppTheme.colors.caption, textAlign = TextAlign.Center)
        }
    }
    if (kindError != null) Text(kindError, style = AppTheme.type.caption, color = AppTheme.colors.error)
}

/** 34dp pill; the selected one is tinted (expense in the over tone, income in the positive tone). A null [onClick] shows it as plain text. */
@Composable
private fun KindPill(kind: CategoryKind, selected: Boolean, onClick: (() -> Unit)?, modifier: Modifier = Modifier) {
    val money = AppTheme.money
    val (tint, container) = if (kind == CategoryKind.Expense) money.over to money.overContainer else money.positive to money.positiveContainer
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .heightIn(min = if (onClick != null) 48.dp else 34.dp)
            .then(if (onClick != null) Modifier.selectable(selected = selected, role = Role.RadioButton, onClick = onClick) else Modifier),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .height(34.dp)
                .background(if (selected) container else AppTheme.colors.surface, AppTheme.shapes.pill)
                .then(if (selected) Modifier else Modifier.border(1.dp, AppTheme.colors.outlineVariant, AppTheme.shapes.pill))
                .padding(horizontal = 14.dp),
        ) {
            Text(
                kindLabel(kind),
                style = AppTheme.type.caption.copy(fontSize = 13.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold),
                color = if (selected) tint else AppTheme.colors.ink,
            )
        }
    }
}

/** Icon and Colour as two underlined tabs. */
@Composable
private fun PickerTabs(colourSelected: Boolean, onSelect: (Boolean) -> Unit) {
    Column(Modifier.padding(top = 1.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(20.dp), modifier = Modifier.selectableGroup()) {
            @Composable
            fun Tab(label: String, selected: Boolean, tag: String, onClick: () -> Unit) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .width(IntrinsicSize.Max)
                        .heightIn(min = 48.dp)
                        .testTag(tag)
                        .selectable(selected = selected, role = Role.Tab, onClick = onClick),
                ) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.height(45.5.dp)) {
                        Text(
                            label,
                            style = AppTheme.type.label.copy(fontSize = 14.5.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold),
                            color = if (selected) AppTheme.colors.primary else AppTheme.colors.ink2,
                        )
                    }
                    Box(Modifier.height(2.5.dp).fillMaxWidth().background(if (selected) AppTheme.colors.primary else Color.Transparent))
                }
            }
            Tab(stringResource(Res.string.category_field_icon), !colourSelected, "categoryEditor.tab.icon") { onSelect(false) }
            Tab(stringResource(Res.string.category_field_color), colourSelected, "categoryEditor.tab.color") { onSelect(true) }
        }
        CalmDivider()
    }
}

/** One 52dp cell of the icon and colour grids; the chosen one gets a heavier outline and a tinted ground. */
@Composable
private fun PickCell(
    selected: Boolean,
    label: String,
    tag: String,
    accent: Color,
    accentContainer: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val shape = RoundedCornerShape(16.dp)
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .height(52.dp)
            .testTag(tag)
            .clip(shape)
            .background(if (selected) accentContainer else Color.Transparent, shape)
            .then(if (selected) Modifier.border(2.dp, accent, shape) else Modifier)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .semantics { contentDescription = label },
    ) { content() }
}

@Composable
private fun PickGrid(cells: List<@Composable (Modifier) -> Unit>) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth().selectableGroup()) {
        cells.chunked(5).forEach { chunk ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                chunk.forEach { cell -> cell(Modifier.weight(1f)) }
                repeat(5 - chunk.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun IconGrid(state: CategoryEditorState, onIntent: (CategoryEditorIntent) -> Unit) {
    val none = stringResource(Res.string.category_none)
    val chosen = state.iconKey ?: NONE_KEY
    val colour = CategoryColors.of(state.colorHex)
    val valid = CategoryColors.isValid(state.colorHex)
    val accent = if (valid) colour.tint else AppTheme.colors.primary
    val container = if (valid) colour.container else AppTheme.colors.primaryContainer
    val keys = listOf(NONE_KEY) + CategoryIcons.keys
    PickGrid(
        keys.map { key ->
            val label = if (key == NONE_KEY) none else stringResource(iconLabelRes(key))
            val cell: @Composable (Modifier) -> Unit = { modifier ->
                val selected = key == chosen
                PickCell(
                    selected, label, "categoryEditor.icon.${if (key == NONE_KEY) "none" else key}", accent, container,
                    { onIntent(CategoryEditorIntent.IconChosen(key.takeUnless { it == NONE_KEY })) }, modifier,
                ) {
                    if (key == NONE_KEY) {
                        Text(none, style = AppTheme.type.label, color = if (selected) accent else AppTheme.colors.ink)
                    } else {
                        Icon(CategoryIcons.of(key), contentDescription = null, tint = if (selected) accent else AppTheme.colors.ink, modifier = Modifier.size(24.dp))
                    }
                }
            }
            cell
        },
    )
}

@Composable
private fun ColorGrid(state: CategoryEditorState, onIntent: (CategoryEditorIntent) -> Unit) {
    val none = stringResource(Res.string.category_none)
    val selectedKey = state.colorHex?.let { hex -> WalletAccents.all.firstOrNull { it.key.equals(hex, ignoreCase = true) }?.key }
        ?: if (state.colorHex == null) NONE_KEY else null
    val cells = mutableListOf<@Composable (Modifier) -> Unit>()
    val noneCell: @Composable (Modifier) -> Unit = { modifier ->
        val selected = selectedKey == NONE_KEY
        PickCell(
            selected, none, "categoryEditor.color.none", AppTheme.colors.primary, AppTheme.colors.primaryContainer,
            { onIntent(CategoryEditorIntent.ColorChosen(null)) }, modifier,
        ) { Text(none, style = AppTheme.type.label, color = if (selected) AppTheme.colors.primary else AppTheme.colors.ink2) }
    }
    cells += noneCell
    WalletAccents.all.forEachIndexed { index, accent ->
        val label = stringResource(colorLabelRes(index))
        val colour = CategoryColors.of(accent.key)
        val cell: @Composable (Modifier) -> Unit = { modifier ->
            val selected = accent.key == selectedKey
            PickCell(
                selected, label, "categoryEditor.color.$index", colour.tint, colour.container,
                { onIntent(CategoryEditorIntent.ColorChosen(accent.key)) }, modifier,
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.size(28.dp).background(colour.swatch, CircleShape)) {
                    if (selected) Icon(AppIcons.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                }
            }
        }
        cells += cell
    }
    PickGrid(cells)
}

@Composable
private fun StatusArea(state: CategoryEditorState, onIntent: (CategoryEditorIntent) -> Unit) {
    when (val sync = state.sync) {
        EditorSync.None -> Unit
        EditorSync.Waiting -> InlineAlert(AlertTone.Info, stringResource(Res.string.category_status_waiting), Modifier.testTag("categoryEditor.status"))
        is EditorSync.Clash -> ClashCard(sync, state.busy, onIntent)
        is EditorSync.Refused -> RefusedCard(sync.reason, state.busy, onIntent)
    }
}

@Composable
private fun RefusedCard(reason: RefusalReason, busy: Boolean, onIntent: (CategoryEditorIntent) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.xs)) {
        InlineAlert(AlertTone.Error, reason.text(), Modifier.testTag("categoryEditor.status"))
        if (reason == RefusalReason.InUse) {
            PrimaryButton(
                stringResource(Res.string.action_archive_instead), { onIntent(CategoryEditorIntent.ArchiveInstead) },
                Modifier.testTag("categoryEditor.archiveInstead"), loading = busy,
            )
            SecondaryButton(
                stringResource(Res.string.action_keep_category), { onIntent(CategoryEditorIntent.KeepCategory) },
                Modifier.testTag("categoryEditor.keepCategory"), enabled = !busy,
            )
            return@Column
        }
        if (reason.canRetryUnchanged) {
            PrimaryButton(
                stringResource(Res.string.action_retry), { onIntent(CategoryEditorIntent.TryAgain) },
                Modifier.testTag("categoryEditor.tryAgain"), enabled = !busy,
            )
        }
        SecondaryButton(
            stringResource(Res.string.action_discard), { onIntent(CategoryEditorIntent.Discard) },
            Modifier.testTag("categoryEditor.discard"), enabled = !busy,
        )
    }
}

/** Both versions side by side, and the person chooses; nothing is merged (FR-012, FR-013). */
@Composable
private fun ClashCard(clash: EditorSync.Clash, busy: Boolean, onIntent: (CategoryEditorIntent) -> Unit) {
    CalmCard(padding = PaddingValues(AppTheme.spacing.m)) { Column(verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.xs)) {
        if (clash.deletedElsewhere) {
            InlineAlert(AlertTone.Warning, stringResource(Res.string.sync_deleted_elsewhere), Modifier.testTag("categoryEditor.status"))
            PrimaryButton(
                stringResource(Res.string.action_accept_deletion), { onIntent(CategoryEditorIntent.AcceptDeletion) },
                Modifier.testTag("categoryEditor.acceptDeletion"), loading = busy,
            )
            return@Column
        }
        InlineAlert(AlertTone.Warning, stringResource(Res.string.category_status_clash), Modifier.testTag("categoryEditor.status"))
        Text(stringResource(Res.string.category_clash_body), style = AppTheme.type.body, color = AppTheme.colors.ink2)
        clash.lines.forEach { line -> ClashLineView(line) }
        if (clash.isDelete) {
            PrimaryButton(
                stringResource(Res.string.action_delete_anyway), { onIntent(CategoryEditorIntent.KeepMine) },
                Modifier.testTag("categoryEditor.deleteAnyway"), loading = busy,
            )
            SecondaryButton(
                stringResource(Res.string.action_keep_category), { onIntent(CategoryEditorIntent.UseTheirs) },
                Modifier.testTag("categoryEditor.keepCategory"), enabled = !busy,
            )
        } else {
            PrimaryButton(
                stringResource(Res.string.category_keep_mine), { onIntent(CategoryEditorIntent.KeepMine) },
                Modifier.testTag("categoryEditor.keepMine"), loading = busy,
            )
            SecondaryButton(
                stringResource(Res.string.category_use_theirs), { onIntent(CategoryEditorIntent.UseTheirs) },
                Modifier.testTag("categoryEditor.useTheirs"), enabled = !busy,
            )
        }
    } }
}

@Composable
private fun ClashLineView(line: ClashLine) {
    val label = when (line.field) {
        ClashField.Name -> Res.string.category_field_name
        ClashField.Icon -> Res.string.category_field_icon
        ClashField.Color -> Res.string.category_field_color
        ClashField.Archived -> Res.string.categories_archived
    }
    Text(stringResource(label), style = AppTheme.type.label, color = AppTheme.colors.ink)
    Text(stringResource(Res.string.category_this_phone, line.mine.text()), style = AppTheme.type.body, color = AppTheme.colors.ink2)
    Text(stringResource(Res.string.category_other_device, line.theirs.text()), style = AppTheme.type.body, color = AppTheme.colors.ink2)
}

@Composable
private fun ClashValue.text(): String = when (this) {
    is ClashValue.Text -> value ?: stringResource(Res.string.category_none)
    is ClashValue.Icon -> key?.let { k -> if (CategoryIcons.find(k) != null) stringResource(iconLabelRes(k)) else k } ?: stringResource(Res.string.category_none)
    is ClashValue.Color -> hex?.let { h -> WalletAccents.all.indexOfFirst { it.key == h }.takeIf { it >= 0 }?.let { i -> stringResource(colorLabelRes(i)) } ?: h }
        ?: stringResource(Res.string.category_none)
    is ClashValue.Archived -> stringResource(if (archived) Res.string.categories_archived else Res.string.category_none)
}

@Composable
private fun EditActions(state: CategoryEditorState, onIntent: (CategoryEditorIntent) -> Unit) {
    if (state.canArchive) {
        CalmCard(padding = PaddingValues(0.dp)) {
            AppListItem(
                stringResource(Res.string.category_archive),
                modifier = Modifier.testTag("categoryEditor.archive"),
                trailing = ListTrailing.Chevron,
                onClick = { onIntent(CategoryEditorIntent.ArchiveClicked) },
            )
        }
    }
    if (state.canDelete) {
        DestructiveButton(
            stringResource(Res.string.category_delete),
            { onIntent(CategoryEditorIntent.DeleteClicked) },
            modifier = Modifier.testTag("categoryEditor.delete"),
        )
    } else if (state.inUseNote) {
        Text(
            stringResource(Res.string.category_in_use_note),
            style = AppTheme.type.body,
            color = AppTheme.colors.ink2,
            modifier = Modifier.testTag("categoryEditor.inUseNote"),
        )
    }
}

@Composable
private fun Confirmations(state: CategoryEditorState, onIntent: (CategoryEditorIntent) -> Unit) {
    if (state.confirm == EditorConfirm.None) return
    Dialog(onDismissRequest = { if (!state.busy) onIntent(CategoryEditorIntent.DismissConfirm) }) {
        ConfirmCard(state, onIntent)
    }
}

/** The confirmation dialog's surface (also shown by the screenshot tests, which cannot see a dialog window). */
@Composable
internal fun ConfirmCard(state: CategoryEditorState, onIntent: (CategoryEditorIntent) -> Unit) {
    val name = state.name.trim()
    val dismiss = { onIntent(CategoryEditorIntent.DismissConfirm) }
    val cancelLabel = stringResource(Res.string.action_cancel)
    val confirmTag = Modifier.testTag("categoryEditor.confirm")
    val cancelTag = Modifier.testTag("categoryEditor.cancel")
    when (state.confirm) {
        EditorConfirm.Archive -> AppAlertDialogCard(
            title = stringResource(Res.string.category_archive_title, name),
            text = stringResource(Res.string.category_archive_body),
            confirmLabel = stringResource(Res.string.category_archive),
            onConfirm = { onIntent(CategoryEditorIntent.ConfirmArchive) },
            dismissLabel = cancelLabel, onDismiss = dismiss,
            confirmLoading = state.busy, confirmModifier = confirmTag, dismissModifier = cancelTag,
        )
        EditorConfirm.Delete -> AppAlertDialogCard(
            title = stringResource(Res.string.category_delete_title, name),
            text = stringResource(Res.string.category_delete_body),
            confirmLabel = stringResource(Res.string.category_delete),
            onConfirm = { onIntent(CategoryEditorIntent.ConfirmDelete) },
            dismissLabel = cancelLabel, onDismiss = dismiss, destructive = true,
            confirmLoading = state.busy, confirmModifier = confirmTag, dismissModifier = cancelTag,
        )
        EditorConfirm.None -> Unit
    }
}

internal fun iconLabelRes(key: String): StringResource = when (key) {
        "cart" -> Res.string.cd_category_icon_cart
        "restaurant" -> Res.string.cd_category_icon_restaurant
        "bus" -> Res.string.cd_category_icon_bus
        "fuel" -> Res.string.cd_category_icon_fuel
        "home" -> Res.string.cd_category_icon_home
        "bolt" -> Res.string.cd_category_icon_bolt
        "phone" -> Res.string.cd_category_icon_phone
        "health" -> Res.string.cd_category_icon_health
        "school" -> Res.string.cd_category_icon_school
        "shirt" -> Res.string.cd_category_icon_shirt
        "sofa" -> Res.string.cd_category_icon_sofa
        "care" -> Res.string.cd_category_icon_care
        "ticket" -> Res.string.cd_category_icon_ticket
        "repeat" -> Res.string.cd_category_icon_repeat
        "plane" -> Res.string.cd_category_icon_plane
        "child" -> Res.string.cd_category_icon_child
        "gift" -> Res.string.cd_category_icon_gift
        "heart" -> Res.string.cd_category_icon_heart
        "dots" -> Res.string.cd_category_icon_dots
        "wallet" -> Res.string.cd_category_icon_wallet
        "store" -> Res.string.cd_category_icon_store
        "laptop" -> Res.string.cd_category_icon_laptop
        "chart" -> Res.string.cd_category_icon_chart
        "undo" -> Res.string.cd_category_icon_undo
    else -> Res.string.category_none
}

internal fun colorLabelRes(index: Int): StringResource = when (index) {
        0 -> Res.string.cd_category_color_0
        1 -> Res.string.cd_category_color_1
        2 -> Res.string.cd_category_color_2
        3 -> Res.string.cd_category_color_3
        4 -> Res.string.cd_category_color_4
        5 -> Res.string.cd_category_color_5
        6 -> Res.string.cd_category_color_6
        7 -> Res.string.cd_category_color_7
    else -> Res.string.category_none
}

@Preview
@Composable
private fun CategoryEditorScreenPreview() {
    AppTheme {
        CategoryEditorScreen(
            CategoryEditorState(
                categoryId = "c1", loaded = true, name = "Coffee", iconKey = "restaurant", colorHex = "#1F7F8C", canArchive = true, canDelete = true,
            ),
            {},
        )
    }
}
