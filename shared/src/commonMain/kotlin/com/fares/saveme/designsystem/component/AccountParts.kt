package com.fares.saveme.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fares.saveme.designsystem.icon.AppIcons
import com.fares.saveme.designsystem.theme.AppTheme
import org.jetbrains.compose.resources.stringResource
import saveme.shared.generated.resources.Res
import saveme.shared.generated.resources.cd_hide_password
import saveme.shared.generated.resources.cd_show_password

/*
 * Pieces of the calm account screens: the card-style text field (label above, bold value, 2dp primary border on focus),
 * its password variant and the small outlined info pill.
 */

/** Card field of the calm layout: 18dp corners, label above the value; [modifier] lands on the editable text. */
@Composable
fun CalmField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    errorText: String? = null,
    supportingText: String? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    spaced: Boolean = false,
    trailing: (@Composable () -> Unit)? = null,
) {
    val colors = AppTheme.colors
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val focus = remember { FocusRequester() }
    val shape = RoundedCornerShape(18.dp)
    val border = when {
        errorText != null -> colors.error
        focused -> colors.primary
        else -> colors.outlineVariant
    }
    Column(Modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(if (enabled) colors.surface else colors.sunken, shape)
                .border(if (focused || errorText != null) 2.dp else 1.dp, border, shape)
                .clickable(interactionSource = null, indication = null, enabled = enabled) { focus.requestFocus() }
                .padding(start = 16.dp, end = if (trailing != null) 4.dp else 16.dp, top = 10.dp, bottom = 10.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    label,
                    style = AppTheme.type.caption.copy(fontWeight = FontWeight.SemiBold),
                    color = if (focused && errorText == null) colors.primary else colors.ink2,
                )
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    enabled = enabled,
                    singleLine = true,
                    keyboardOptions = keyboardOptions,
                    keyboardActions = keyboardActions,
                    visualTransformation = visualTransformation,
                    interactionSource = interaction,
                    cursorBrush = SolidColor(colors.primary),
                    textStyle = AppTheme.type.body.copy(
                        fontSize = 16.sp,
                        fontWeight = if (spaced) FontWeight.SemiBold else FontWeight.Bold,
                        color = colors.ink,
                    ),
                    modifier = modifier
                        .fillMaxWidth()
                        .focusRequester(focus)
                        .semantics { if (errorText != null) error(errorText) },
                )
            }
            trailing?.invoke()
        }
        (errorText ?: supportingText)?.let {
            Text(
                it,
                style = AppTheme.type.caption,
                color = if (errorText != null) colors.error else colors.caption,
                modifier = Modifier.padding(start = 16.dp, top = 4.dp),
            )
        }
    }
}

/** [CalmField] masked by default; the toggle is a labelled 48dp icon button. */
@Composable
fun CalmPasswordField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    visible: Boolean,
    onToggleVisible: () -> Unit,
    modifier: Modifier = Modifier,
    errorText: String? = null,
    enabled: Boolean = true,
    keyboardOptions: KeyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
    keyboardActions: KeyboardActions = KeyboardActions.Default,
) {
    CalmField(
        value = value,
        onValueChange = onValueChange,
        label = label,
        modifier = modifier,
        enabled = enabled,
        errorText = errorText,
        keyboardOptions = keyboardOptions.copy(keyboardType = KeyboardType.Password),
        keyboardActions = keyboardActions,
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        spaced = true,
        trailing = {
            AppIconButton(
                icon = if (visible) AppIcons.VisibilityOff else AppIcons.Visibility,
                contentDescription = stringResource(if (visible) Res.string.cd_hide_password else Res.string.cd_show_password),
                onClick = onToggleVisible,
                tint = AppTheme.colors.ink2,
            )
        },
    )
}

/** Outlined 12.5sp pill with a bold secondary label: a fact about the screen's subject. */
@Composable
fun CalmInfoPill(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = AppTheme.type.caption.copy(fontWeight = FontWeight.Bold),
        color = AppTheme.colors.ink2,
        modifier = modifier
            .background(AppTheme.colors.surface, AppTheme.shapes.pill)
            .border(1.dp, AppTheme.colors.outlineVariant, AppTheme.shapes.pill)
            .padding(horizontal = 10.dp, vertical = 6.dp),
    )
}

/** A filled circle with a check mark: a met rule, a chosen row. */
@Composable
fun CalmCheckBadge(tint: Color, modifier: Modifier = Modifier, size: androidx.compose.ui.unit.Dp = 18.dp) {
    Box(contentAlignment = Alignment.Center, modifier = modifier.size(size).background(tint, CircleShape)) {
        Icon(AppIcons.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(size * 0.62f))
    }
}

/**
 * A tap anywhere on a surface whose texts must stay separate accessibility nodes (so each reads and matches on its own);
 * pair it with [calmButtonSemantics] on the text that names the action.
 */
fun Modifier.calmTapArea(action: () -> Unit): Modifier =
    pointerInput(action) { detectTapGestures(onTap = { action() }) }

fun Modifier.calmButtonSemantics(action: () -> Unit): Modifier = semantics {
    role = Role.Button
    onClick { action(); true }
}
