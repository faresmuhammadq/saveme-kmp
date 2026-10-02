package com.fares.saveme.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.style.TextAlign
import com.fares.saveme.designsystem.theme.AppTheme

@Composable
fun EmptyState(
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    actionModifier: Modifier = Modifier,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.xs),
        modifier = modifier.fillMaxWidth().padding(AppTheme.spacing.xl),
    ) {
        Text(title, style = AppTheme.type.title, color = AppTheme.colors.ink, textAlign = TextAlign.Center)
        Text(body, style = AppTheme.type.body, color = AppTheme.colors.ink2, textAlign = TextAlign.Center)
        if (actionLabel != null && onAction != null) {
            PrimaryButton(actionLabel, onAction, fullWidth = false, modifier = actionModifier.padding(top = AppTheme.spacing.xs))
        }
    }
}

/** Static placeholder block; shown only on first sync (board: Empty & loading). */
@Composable
fun Skeleton(modifier: Modifier = Modifier, shape: Shape = AppTheme.shapes.control) {
    Box(modifier.background(AppTheme.colors.sunken2, shape))
}
