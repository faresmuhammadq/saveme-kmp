package com.fares.saveme.feature.categories.ui.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import com.fares.saveme.designsystem.component.AppScreen
import com.fares.saveme.designsystem.component.AppTextField
import com.fares.saveme.designsystem.component.ChoiceGrid
import com.fares.saveme.designsystem.component.ChoiceItem
import com.fares.saveme.designsystem.component.DestructiveButton
import com.fares.saveme.designsystem.component.InlineAlert
import com.fares.saveme.designsystem.component.ListTrailing
import com.fares.saveme.designsystem.component.PrimaryButton
import com.fares.saveme.designsystem.component.SecondaryButton
import com.fares.saveme.designsystem.component.SegmentedControl
import com.fares.saveme.designsystem.component.Skeleton
import com.fares.saveme.designsystem.icon.CategoryIcons
import com.fares.saveme.designsystem.theme.AppTheme
import com.fares.saveme.designsystem.tokens.CategoryColors
import com.fares.saveme.designsystem.tokens.WalletAccents
import com.fares.saveme.feature.categories.domain.model.CategoryField
import com.fares.saveme.feature.categories.domain.model.CategoryKind
import com.fares.saveme.feature.categories.domain.model.RefusalReason
import com.fares.saveme.feature.categories.ui.CategoriesBackBar
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
    AppScreen(
        snackbarHostState = snackbar,
        topBar = {
            CategoriesBackBar(
                stringResource(if (state.isCreate) Res.string.category_title_new else Res.string.category_title_edit),
            ) { onIntent(CategoryEditorIntent.Back) }
        },
    ) {
        if (!state.loaded) {
            repeat(4) { Skeleton(Modifier.fillMaxWidth().height(52.dp)) }
            return@AppScreen
        }
        StatusArea(state, onIntent)
        KindSection(state, onIntent)
        AppTextField(
            value = state.name,
            onValueChange = { onIntent(CategoryEditorIntent.NameChanged(it)) },
            label = stringResource(Res.string.category_field_name),
            modifier = Modifier.fillMaxWidth().testTag("categoryEditor.name"),
            errorText = state.errors[CategoryField.Name]?.text(CategoryField.Name),
        )
        IconSection(state, onIntent)
        ColorSection(state, onIntent)
        PrimaryButton(
            stringResource(Res.string.category_save),
            { onIntent(CategoryEditorIntent.SaveClicked) },
            modifier = Modifier.testTag("categoryEditor.save"),
            enabled = state.canSave,
            loading = state.saving,
        )
        if (!state.isCreate) EditActions(state, onIntent)
    }
    Confirmations(state, onIntent)
}

@Composable
private fun SectionLabel(text: String) {
    Text(text, style = AppTheme.type.label, color = AppTheme.colors.ink2)
}

@Composable
private fun kindLabel(kind: CategoryKind): String =
    stringResource(if (kind == CategoryKind.Expense) Res.string.category_kind_expense else Res.string.category_kind_income)

@Composable
private fun KindSection(state: CategoryEditorState, onIntent: (CategoryEditorIntent) -> Unit) {
    if (state.kindEditable) {
        SectionLabel(stringResource(Res.string.category_field_kind))
        SegmentedControl(
            options = CategoryKind.entries,
            label = { kindLabel(it) },
            selected = state.kind ?: CategoryKind.Expense,
            onSelect = { onIntent(CategoryEditorIntent.KindChosen(it)) },
            optionModifier = { Modifier.testTag("categoryEditor.kind.${it.wire}") },
        )
        return
    }
    AppTextField(
        value = state.kind?.let { kindLabel(it) } ?: state.kindWire,
        onValueChange = {},
        label = stringResource(Res.string.category_field_kind),
        modifier = Modifier.fillMaxWidth().testTag("categoryEditor.kind"),
        enabled = false,
        supportingText = stringResource(Res.string.category_kind_locked_note),
        errorText = state.errors[CategoryField.Kind]?.text(CategoryField.Kind),
    )
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
    Column(
        verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.xs),
        modifier = Modifier.fillMaxWidth().background(AppTheme.colors.surface, AppTheme.shapes.card).padding(AppTheme.spacing.m),
    ) {
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
    }
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
private fun IconSection(state: CategoryEditorState, onIntent: (CategoryEditorIntent) -> Unit) {
    SectionLabel(stringResource(Res.string.category_field_icon))
    val none = stringResource(Res.string.category_none)
    val items = listOf(ChoiceItem(NONE_KEY, none, "categoryEditor.icon.none") { Text(none, style = AppTheme.type.label, color = AppTheme.colors.ink2) }) +
        CategoryIcons.keys.map { key ->
            ChoiceItem(key, stringResource(iconLabelRes(key)), "categoryEditor.icon.$key") {
                Icon(CategoryIcons.of(key), contentDescription = null, tint = AppTheme.colors.ink2, modifier = Modifier.size(24.dp))
            }
        }
    ChoiceGrid(items, state.iconKey ?: NONE_KEY, { key -> onIntent(CategoryEditorIntent.IconChosen(key.takeUnless { it == NONE_KEY })) })
}

@Composable
private fun ColorSection(state: CategoryEditorState, onIntent: (CategoryEditorIntent) -> Unit) {
    SectionLabel(stringResource(Res.string.category_field_color))
    val none = stringResource(Res.string.category_none)
    val items = listOf(ChoiceItem(NONE_KEY, none, "categoryEditor.color.none") { Text(none, style = AppTheme.type.label, color = AppTheme.colors.ink2) }) +
        WalletAccents.all.mapIndexed { index, accent ->
            ChoiceItem(accent.key, stringResource(colorLabelRes(index)), "categoryEditor.color.$index") {
                Box(Modifier.size(24.dp).background(CategoryColors.of(accent.key).swatch, CircleShape))
            }
        }
    val selected = state.colorHex?.let { hex -> WalletAccents.all.firstOrNull { it.key.equals(hex, ignoreCase = true) }?.key }
        ?: if (state.colorHex == null) NONE_KEY else null
    ChoiceGrid(items, selected, { key -> onIntent(CategoryEditorIntent.ColorChosen(key.takeUnless { it == NONE_KEY })) })
}

@Composable
private fun EditActions(state: CategoryEditorState, onIntent: (CategoryEditorIntent) -> Unit) {
    if (state.canArchive) {
        Column(Modifier.fillMaxWidth().background(AppTheme.colors.surface, AppTheme.shapes.card)) {
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
