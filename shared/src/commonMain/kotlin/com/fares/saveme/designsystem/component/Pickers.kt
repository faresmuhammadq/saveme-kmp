package com.fares.saveme.designsystem.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.fares.saveme.designsystem.icon.AppIcons
import com.fares.saveme.designsystem.theme.AppTheme
import org.jetbrains.compose.resources.stringResource
import saveme.shared.generated.resources.Res
import saveme.shared.generated.resources.action_search

/** Looks like [AppTextField] but opens a picker; announced as a button. */
@Composable
fun AppSelectField(
    label: String,
    value: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    supportingText: String? = null,
    errorText: String? = null,
    enabled: Boolean = true,
) {
    Box(modifier) {
        AppTextField(
            value = value,
            onValueChange = {},
            label = label,
            supportingText = supportingText,
            errorText = errorText,
            enabled = enabled,
            trailingIcon = if (enabled) {
                { Icon(AppIcons.ChevronForward, contentDescription = null, tint = AppTheme.colors.outline) }
            } else {
                null
            },
        )
        // The field itself never takes focus or a keyboard; the overlay turns it into one button.
        // The overlay covers the field, so accessibility services see only the overlay: it carries
        // the label and the chosen value itself ("Currency, EGP, button"), or both would be lost.
        Box(
            Modifier
                .matchParentSize()
                .then(
                    if (enabled) {
                        Modifier
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = onClick,
                            )
                            .semantics {
                                role = Role.Button
                                contentDescription = label
                                stateDescription = value
                                text = AnnotatedString(value)
                                onClick { onClick(); true }
                            }
                    } else {
                        Modifier
                    },
                ),
        )
    }
}

/** Single choice from a long list, in a bottom sheet with an optional search field. */
@Composable
fun <T> SelectionSheet(
    title: String,
    options: List<T>,
    label: (T) -> String,
    selected: T?,
    onSelect: (T) -> Unit,
    onDismiss: () -> Unit,
    searchable: Boolean = true,
) {
    AppBottomSheet(onDismiss = onDismiss, title = title) {
        SelectionList(options, label, selected, onSelect, searchable)
    }
}

/** The sheet's content, usable without the sheet window (catalogue, screenshots). */
@Composable
fun <T> SelectionList(
    options: List<T>,
    label: (T) -> String,
    selected: T?,
    onSelect: (T) -> Unit,
    searchable: Boolean = true,
    modifier: Modifier = Modifier,
) {
    var query by remember { mutableStateOf("") }
    val visible = remember(options, query) {
        if (query.isBlank()) options else options.filter { label(it).contains(query.trim(), ignoreCase = true) }
    }
    Column(modifier) {
        if (searchable) {
            AppTextField(value = query, onValueChange = { query = it }, label = stringResource(Res.string.action_search))
        }
        LazyColumn(Modifier.heightIn(max = 420.dp)) {
            items(visible, key = { label(it) }) { option ->
                AppListItem(
                    headline = label(option),
                    trailing = if (option == selected) {
                        ListTrailing.Custom { Icon(AppIcons.Check, contentDescription = null, tint = AppTheme.colors.primary) }
                    } else {
                        ListTrailing.None
                    },
                    onClick = { onSelect(option) },
                )
            }
        }
    }
}
