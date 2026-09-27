package com.fares.saveme.designsystem.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.fares.saveme.designsystem.theme.AppTheme

/** 48dp icon-only button; [contentDescription] is required (P-08.2). Directional icons auto-mirror. */
@Composable
fun AppIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    tint: Color = AppTheme.colors.ink,
) {
    IconButton(
        onClick = onClick,
        enabled = enabled,
        colors = IconButtonDefaults.iconButtonColors(contentColor = tint, disabledContentColor = AppTheme.colors.outline),
        modifier = modifier.size(AppTheme.sizes.touchTarget),
    ) {
        Icon(icon, contentDescription = contentDescription)
    }
}

/** 56dp FAB; extended with [text] on Home, collapsing on scroll via [expanded]. */
@Composable
fun AppFab(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    text: String? = null,
    expanded: Boolean = text != null,
) {
    FloatingActionButton(
        onClick = onClick,
        shape = RoundedCornerShape(18.dp),
        containerColor = AppTheme.colors.primary,
        contentColor = AppTheme.colors.onPrimary,
        modifier = modifier
            .heightIn(min = AppTheme.sizes.fab)
            .widthIn(min = AppTheme.sizes.fab)
            .semantics { this.contentDescription = contentDescription },
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = if (expanded && text != null) AppTheme.spacing.l else 0.dp),
        ) {
            Icon(icon, contentDescription = null)
            AnimatedVisibility(visible = expanded && text != null) {
                Row {
                    Spacer(Modifier.width(AppTheme.spacing.xs))
                    Text(text.orEmpty(), style = AppTheme.type.label)
                }
            }
        }
    }
}
