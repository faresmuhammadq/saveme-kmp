package com.fares.saveme.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.fares.saveme.designsystem.theme.AppTheme

/**
 * Confirmation dialog. Buttons stack vertically so long Arabic labels and 200% text never clip;
 * a [destructive] confirm uses the destructive tone.
 */
@Composable
fun AppAlertDialog(
    title: String,
    text: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    dismissLabel: String,
    onDismiss: () -> Unit,
    destructive: Boolean = false,
    confirmLoading: Boolean = false,
) {
    Dialog(onDismissRequest = { if (!confirmLoading) onDismiss() }) {
        AppAlertDialogCard(title, text, confirmLabel, onConfirm, dismissLabel, onDismiss, destructive, confirmLoading)
    }
}

/** The dialog's surface without the window, so the catalogue and screenshots can show it. */
@Composable
fun AppAlertDialogCard(
    title: String,
    text: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    dismissLabel: String,
    onDismiss: () -> Unit,
    destructive: Boolean = false,
    confirmLoading: Boolean = false,
    modifier: Modifier = Modifier,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.s),
        modifier = modifier
            .widthIn(max = 400.dp)
            .fillMaxWidth()
            .background(AppTheme.colors.surface, AppTheme.shapes.card)
            .padding(AppTheme.spacing.xl)
            .semantics { paneTitle = title },
    ) {
        Text(title, style = AppTheme.type.title, color = AppTheme.colors.ink, modifier = Modifier.semantics { heading() })
        Text(text, style = AppTheme.type.body, color = AppTheme.colors.ink2)
        Column(
            verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.xs),
            modifier = Modifier.padding(top = AppTheme.spacing.xs),
        ) {
            if (destructive) {
                DestructiveButton(confirmLabel, onConfirm, loading = confirmLoading)
            } else {
                PrimaryButton(confirmLabel, onConfirm, loading = confirmLoading)
            }
            AppTextButton(dismissLabel, onDismiss, Modifier.fillMaxWidth(), enabled = !confirmLoading)
        }
    }
}
