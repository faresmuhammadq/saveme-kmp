package com.fares.saveme.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import com.fares.saveme.designsystem.icon.AppIcons
import com.fares.saveme.designsystem.icon.CategoryIcons
import com.fares.saveme.designsystem.theme.AppTheme
import com.fares.saveme.designsystem.tokens.CategoryColors

/** An icon on the category's colour: [iconKey] and [colorHex] are the wire values; unknown ones show the neutral tile. */
@Composable
fun CategoryTile(iconKey: String?, colorHex: String?, modifier: Modifier = Modifier) {
    val color = CategoryColors.of(colorHex)
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(AppTheme.sizes.iconTile)
            .background(color.container, AppTheme.shapes.tile),
    ) {
        Icon(CategoryIcons.of(iconKey), contentDescription = null, tint = color.tint)
    }
}

/** What a category's change is doing, shown as an icon and text, never colour alone (P-08.3). */
enum class CategoryRowStatusKind { Waiting, Changed, NotSaved }

data class CategoryRowStatus(val kind: CategoryRowStatusKind, val label: String)

/**
 * One category: tile, name, an optional caption ("Default", "Archived") and sync status. With
 * [selected] set the row is a radio choice and a selected one shows a check mark. [onClick] makes
 * the whole row one touch target.
 */
@Composable
fun CategoryRow(
    name: String,
    iconKey: String?,
    colorHex: String?,
    modifier: Modifier = Modifier,
    caption: String? = null,
    status: CategoryRowStatus? = null,
    selected: Boolean? = null,
    onClick: (() -> Unit)? = null,
    statusModifier: Modifier = Modifier,
) {
    val interaction = when {
        selected != null && onClick != null -> Modifier.selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
        onClick != null -> Modifier.clickable(role = Role.Button, onClick = onClick)
        else -> Modifier.semantics(mergeDescendants = true) {}
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.s),
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = AppTheme.sizes.rowTwoLine)
            .then(interaction)
            .padding(horizontal = AppTheme.spacing.m, vertical = AppTheme.spacing.xs),
    ) {
        CategoryTile(iconKey, colorHex)
        Column(Modifier.weight(1f)) {
            Text(name, style = AppTheme.type.itemTitle, color = AppTheme.colors.ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (caption != null) Text(caption, style = AppTheme.type.caption, color = AppTheme.colors.caption)
            if (status != null) {
                WalletStatusLine(
                    WalletCardStatus(
                        when (status.kind) {
                            CategoryRowStatusKind.Waiting -> WalletCardStatusKind.Waiting
                            CategoryRowStatusKind.Changed -> WalletCardStatusKind.Changed
                            CategoryRowStatusKind.NotSaved -> WalletCardStatusKind.NotSaved
                        },
                        status.label,
                    ),
                    statusModifier,
                )
            }
        }
        if (selected == true) Icon(AppIcons.Check, contentDescription = null, tint = AppTheme.colors.primary)
    }
}

/** One choosable category in a [CategoryChoiceGroup]. */
@Immutable
data class CategoryChoiceItem(
    val id: String,
    val name: String,
    val iconKey: String?,
    val colorHex: String?,
    val caption: String? = null,
)

/** A titled group of choices; each row is tagged `<tagPrefix>.<index>` (or just `<tagPrefix>` when [indexed] is false). */
@Immutable
data class CategoryChoiceGroup(
    val title: String?,
    val items: List<CategoryChoiceItem>,
    val tagPrefix: String,
    val indexed: Boolean = true,
)

/**
 * The choice list: titled groups with radio semantics and a check mark on the selected row.
 * [emptyMessage] shows when no group has an item. Stateless: the store behind it is C6's.
 */
@Composable
fun CategoryChoiceList(
    groups: List<CategoryChoiceGroup>,
    selectedId: String?,
    onSelect: (String) -> Unit,
    emptyMessage: String,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth().selectableGroup()) {
        if (groups.all { it.items.isEmpty() }) {
            Text(
                emptyMessage,
                style = AppTheme.type.body,
                color = AppTheme.colors.ink2,
                modifier = Modifier.padding(vertical = AppTheme.spacing.m).testTag("categoryChoice.empty"),
            )
            return@Column
        }
        groups.filter { it.items.isNotEmpty() }.forEach { group ->
            if (group.title != null) {
                Text(
                    group.title,
                    style = AppTheme.type.label,
                    color = AppTheme.colors.ink2,
                    modifier = Modifier.padding(top = AppTheme.spacing.xs, start = AppTheme.spacing.m).semantics { heading() },
                )
            }
            group.items.forEachIndexed { index, item ->
                CategoryRow(
                    name = item.name,
                    iconKey = item.iconKey,
                    colorHex = item.colorHex,
                    caption = item.caption,
                    selected = item.id == selectedId,
                    onClick = { onSelect(item.id) },
                    modifier = Modifier.testTag(if (group.indexed) "${group.tagPrefix}.$index" else group.tagPrefix),
                )
            }
        }
    }
}

/** [CategoryChoiceList] in a bottom sheet. */
@Composable
fun CategoryChoiceSheet(
    title: String,
    groups: List<CategoryChoiceGroup>,
    selectedId: String?,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
    emptyMessage: String,
) {
    AppBottomSheet(onDismiss = onDismiss, title = title) {
        CategoryChoiceList(groups, selectedId, onSelect, emptyMessage)
    }
}
