package com.fares.saveme.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.fares.saveme.designsystem.theme.AppTheme

/** 48dp segmented control, e.g. transaction type. */
@Composable
fun <T> SegmentedControl(
    options: List<T>,
    label: @Composable (T) -> String,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    /** Extra modifier per option, for example a test tag. */
    optionModifier: (T) -> Modifier = { Modifier },
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(AppTheme.sizes.touchTarget)
            .background(AppTheme.colors.sunken, AppTheme.shapes.control)
            .padding(AppTheme.spacing.xxs)
            .selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.xxs),
    ) {
        options.forEach { option ->
            val isSelected = option == selected
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .then(if (isSelected) Modifier.shadow(1.dp, AppTheme.shapes.control) else Modifier)
                    .background(if (isSelected) AppTheme.colors.surface else AppTheme.colors.sunken, AppTheme.shapes.control)
                    .selectable(selected = isSelected, role = Role.RadioButton, onClick = { onSelect(option) })
                    .then(optionModifier(option)),
            ) {
                Text(
                    label(option),
                    style = AppTheme.type.label,
                    color = if (isSelected) AppTheme.colors.ink else AppTheme.colors.ink2,
                )
            }
        }
    }
}

/** 36dp chip drawn inside a 48dp touch area (board: FilterChip). */
@Composable
fun AppFilterChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .heightIn(min = AppTheme.sizes.touchTarget)
            .selectable(selected = selected, role = Role.Checkbox, onClick = onClick),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .height(AppTheme.sizes.chip)
                .background(if (selected) AppTheme.colors.primaryContainer else AppTheme.colors.surface, AppTheme.shapes.pill)
                .border(1.dp, if (selected) AppTheme.colors.primary else AppTheme.colors.outlineVariant, AppTheme.shapes.pill)
                .padding(horizontal = AppTheme.spacing.s),
        ) {
            Text(
                label,
                style = AppTheme.type.label,
                color = if (selected) AppTheme.colors.onPrimaryContainer else AppTheme.colors.ink,
            )
        }
    }
}

/** A settings row whose whole surface toggles the switch. */
@Composable
fun SettingsSwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    supportingText: String? = null,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = if (supportingText != null) AppTheme.sizes.rowTwoLine else AppTheme.sizes.row)
            .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange)
            .padding(horizontal = AppTheme.spacing.m),
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = AppTheme.type.itemTitle, color = AppTheme.colors.ink)
            if (supportingText != null) {
                Text(supportingText, style = AppTheme.type.caption, color = AppTheme.colors.caption)
            }
        }
        Spacer(Modifier.width(AppTheme.spacing.s))
        Switch(
            checked = checked,
            onCheckedChange = null,
            colors = SwitchDefaults.colors(
                checkedTrackColor = AppTheme.colors.primary,
                uncheckedTrackColor = AppTheme.colors.sunken2,
                uncheckedBorderColor = AppTheme.colors.outline,
                uncheckedThumbColor = AppTheme.colors.outline,
            ),
        )
    }
}
