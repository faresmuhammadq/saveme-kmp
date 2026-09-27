package com.fares.saveme.designsystem.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import com.fares.saveme.designsystem.theme.AppTheme

/** Top radius 22, drag handle, ink scrim at 38% (board: ModalBottomSheet). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppBottomSheet(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    title: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        shape = AppTheme.shapes.sheet,
        containerColor = AppTheme.colors.surface,
        scrimColor = AppTheme.colors.ink.copy(alpha = 0.38f),
        dragHandle = { BottomSheetDefaults.DragHandle(color = AppTheme.colors.outlineVariant) },
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = AppTheme.spacing.gutter)
                .padding(bottom = AppTheme.spacing.xl)
                .navigationBarsPadding(),
        ) {
            if (title != null) {
                Text(
                    title,
                    style = AppTheme.type.title,
                    color = AppTheme.colors.ink,
                    modifier = Modifier.padding(bottom = AppTheme.spacing.s).semantics { heading() },
                )
            }
            content()
        }
    }
}
