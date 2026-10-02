package com.fares.saveme.designsystem.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.fares.saveme.designsystem.icon.AppIcons
import com.fares.saveme.designsystem.theme.AppTheme

/** One option of a [ChoiceGrid]: [label] is announced; [testTag] is optional. */
@Immutable
data class ChoiceItem(
    val key: String,
    val label: String,
    val testTag: String? = null,
    val content: @Composable () -> Unit,
)

/**
 * A wrap-around grid of single-choice tiles for icons, colours and short labelled choices. Every tile is at least a 48dp radio
 * button with a content description, and the selection is shown by a check mark and a heavier
 * border, never by colour alone (P-08).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ChoiceGrid(
    items: List<ChoiceItem>,
    selectedKey: String?,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.xs),
        verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.xs),
        modifier = modifier.selectableGroup(),
    ) {
        items.forEach { item -> ChoiceTile(item, item.key == selectedKey) { onSelect(item.key) } }
    }
}

@Composable
private fun ChoiceTile(item: ChoiceItem, selected: Boolean, onClick: () -> Unit) {
    val colors = AppTheme.colors
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .heightIn(min = AppTheme.sizes.touchTarget)
            .widthIn(min = AppTheme.sizes.touchTarget)
            .clip(AppTheme.shapes.tile)
            .background(if (selected) colors.primaryContainer else colors.surface)
            .border(
                BorderStroke(if (selected) 2.dp else androidx.compose.ui.unit.Dp.Hairline, if (selected) colors.primary else colors.outlineVariant),
                AppTheme.shapes.tile,
            )
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .semantics { contentDescription = item.label }
            .then(if (item.testTag != null) Modifier.testTag(item.testTag) else Modifier),
    ) {
        Box(Modifier.padding(AppTheme.spacing.s), contentAlignment = Alignment.Center) { item.content() }
        if (selected) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(16.dp)
                    .background(colors.primary, CircleShape),
            ) {
                Icon(AppIcons.Check, contentDescription = null, tint = colors.onPrimary, modifier = Modifier.size(12.dp))
            }
        }
    }
}
