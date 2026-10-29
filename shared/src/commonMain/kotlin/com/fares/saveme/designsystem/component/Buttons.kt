package com.fares.saveme.designsystem.component

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.fares.saveme.designsystem.theme.AppTheme

private data class ButtonPalette(val container: Color, val pressed: Color, val content: Color)

@Composable
private fun AppButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier,
    enabled: Boolean,
    loading: Boolean,
    palette: ButtonPalette,
    fullWidth: Boolean,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val colors = AppTheme.colors
    val active = enabled && !loading
    Button(
        onClick = onClick,
        enabled = active,
        interactionSource = interaction,
        shape = androidx.compose.foundation.shape.RoundedCornerShape(18.dp),
        contentPadding = PaddingValues(horizontal = AppTheme.spacing.l),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (pressed) palette.pressed else palette.container,
            contentColor = palette.content,
            // Loading keeps its colour: it is busy, not unavailable.
            disabledContainerColor = if (loading && enabled) palette.container else colors.sunken2,
            disabledContentColor = if (loading && enabled) palette.content else colors.ink2,
        ),
        modifier = modifier
            .heightIn(min = AppTheme.sizes.button)
            .then(if (fullWidth) Modifier.fillMaxWidth() else Modifier)
            .semantics { if (!active) disabled() },
    ) {
        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                strokeWidth = 2.dp,
                color = palette.content,
            )
        } else {
            Text(text, style = AppTheme.type.label)
        }
    }
}

/** Main action, 52dp, full width by default (board: Buttons). */
@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
    fullWidth: Boolean = true,
) = AppButton(
    text, onClick, modifier, enabled, loading,
    ButtonPalette(AppTheme.colors.primary, AppTheme.colors.primaryPressed, AppTheme.colors.onPrimary),
    fullWidth,
)

/** Tonal secondary action on primaryContainer. */
@Composable
fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
    fullWidth: Boolean = true,
) = AppButton(
    text, onClick, modifier, enabled, loading,
    ButtonPalette(AppTheme.colors.primaryContainer, AppTheme.colors.sunken2, AppTheme.colors.onPrimaryContainer),
    fullWidth,
)

/** Destructive action; error text on errorContainer so it never shouts. */
@Composable
fun DestructiveButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
    fullWidth: Boolean = true,
) = AppButton(
    text, onClick, modifier, enabled, loading,
    ButtonPalette(AppTheme.colors.errorContainer, AppTheme.colors.sunken2, AppTheme.colors.error),
    fullWidth,
)

/** Low-emphasis action; still a 48dp target. */
@Composable
fun AppTextButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    androidx.compose.material3.TextButton(
        onClick = onClick,
        enabled = enabled,
        shape = AppTheme.shapes.control,
        colors = ButtonDefaults.textButtonColors(
            contentColor = AppTheme.colors.primary,
            disabledContentColor = AppTheme.colors.ink2,
        ),
        modifier = modifier.heightIn(min = AppTheme.sizes.touchTarget),
    ) {
        Text(text, style = AppTheme.type.label)
    }
}
