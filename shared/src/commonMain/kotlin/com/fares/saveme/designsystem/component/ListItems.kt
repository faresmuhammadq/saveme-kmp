package com.fares.saveme.designsystem.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.fares.saveme.designsystem.icon.AppIcons
import com.fares.saveme.designsystem.theme.AppTheme

/** What sits at the end of an [AppListItem]. */
sealed interface ListTrailing {
    data object None : ListTrailing
    data object Chevron : ListTrailing
    data class Text(val value: String) : ListTrailing
    data class Custom(val content: @Composable () -> Unit) : ListTrailing
}

/**
 * A settings or account row, at least 56dp tall. When [onClick] is set the whole row is one touch
 * target announced as a button; the chevron mirrors in RTL.
 */
@Composable
fun AppListItem(
    headline: String,
    modifier: Modifier = Modifier,
    supporting: String? = null,
    leadingIcon: ImageVector? = null,
    trailing: ListTrailing = ListTrailing.None,
    headlineColor: Color = AppTheme.colors.ink,
    onClick: (() -> Unit)? = null,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.m),
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .then(
                if (onClick != null) {
                    Modifier.clickable(role = Role.Button, onClick = onClick)
                } else {
                    Modifier.semantics(mergeDescendants = true) {}
                },
            )
            .padding(horizontal = AppTheme.spacing.m, vertical = AppTheme.spacing.xs),
    ) {
        if (leadingIcon != null) {
            Icon(leadingIcon, contentDescription = null, tint = AppTheme.colors.ink2, modifier = Modifier.size(24.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(headline, style = AppTheme.type.itemTitle, color = headlineColor)
            if (supporting != null) {
                Text(supporting, style = AppTheme.type.caption, color = AppTheme.colors.caption)
            }
        }
        when (trailing) {
            ListTrailing.None -> Unit
            ListTrailing.Chevron -> Icon(AppIcons.ChevronForward, contentDescription = null, tint = AppTheme.colors.outline)
            is ListTrailing.Text -> Text(trailing.value, style = AppTheme.type.label, color = AppTheme.colors.primary)
            is ListTrailing.Custom -> trailing.content()
        }
    }
}
