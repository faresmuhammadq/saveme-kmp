package com.fares.saveme.feature.auth.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.layout
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.sp
import com.fares.saveme.designsystem.component.CalmCircleButton
import com.fares.saveme.designsystem.component.CalmDivider
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.fares.saveme.designsystem.component.AppIconButton
import com.fares.saveme.designsystem.component.CalmGutter
import com.fares.saveme.designsystem.icon.AppIcons
import com.fares.saveme.designsystem.theme.AppTheme
import org.jetbrains.compose.resources.stringResource
import saveme.shared.generated.resources.Res
import saveme.shared.generated.resources.cd_back
import saveme.shared.generated.resources.cd_hide_password
import saveme.shared.generated.resources.cd_show_password

private val SheetShape = RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp)

/** The app mark: a three-quarter ring with a dot at its centre. */
@Composable
internal fun AuthLogo(size: Dp, modifier: Modifier = Modifier, ring: Color = AppTheme.colors.primary, dot: Color = AppTheme.colors.ink) {
    Canvas(modifier.size(size)) {
        val unit = this.size.minDimension / 64f
        drawArc(
            color = ring,
            startAngle = -90f,
            sweepAngle = 270f,
            useCenter = false,
            topLeft = Offset(10f * unit, 10f * unit),
            size = Size(44f * unit, 44f * unit),
            style = Stroke(width = 8f * unit, cap = StrokeCap.Round),
        )
        drawCircle(dot, radius = 6.5f * unit, center = center)
    }
}

/** A tinted rounded square holding one icon, the lead of the auth headers. */
@Composable
internal fun AuthBadge(icon: ImageVector, tint: Color, size: Dp, iconSize: Dp, radius: Dp, modifier: Modifier = Modifier, container: Color = AppTheme.colors.surface) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier.size(size).background(container, RoundedCornerShape(radius)),
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(iconSize))
    }
}

/**
 * Form screens of the sign-in flow: a tinted canvas with the round back button and a [header], and the form on a
 * white sheet pinned to the bottom. The header gives way when the keyboard opens; the sheet scrolls.
 */
@Composable
internal fun AuthSheetScreen(
    onBack: () -> Unit,
    header: @Composable ColumnScope.() -> Unit,
    modifier: Modifier = Modifier,
    headerAlignment: Alignment.Horizontal = Alignment.Start,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(AppTheme.colors.primaryContainer)
            .imePadding(),
    ) {
        Box(Modifier.windowInsetsPadding(WindowInsets.statusBars).padding(start = CalmGutter, end = CalmGutter, top = 8.dp)) {
            CalmCircleButton(AppIcons.ArrowBack, stringResource(Res.string.cd_back), onBack, bordered = false)
        }
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
            horizontalAlignment = headerAlignment,
            modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 24.dp),
            content = header,
        )
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .background(AppTheme.colors.surface, SheetShape)
                .verticalScroll(rememberScrollState())
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(horizontal = CalmGutter, vertical = 24.dp),
            content = content,
        )
    }
}

/**
 * Status screens (inbox, verification): a tinted header, a canvas-coloured panel overlapping it, and a pinned
 * [actions] area.
 */
@Composable
internal fun AuthStatusScreen(
    tint: Color,
    header: @Composable ColumnScope.() -> Unit,
    actions: @Composable ColumnScope.() -> Unit,
    modifier: Modifier = Modifier,
    panel: @Composable ColumnScope.() -> Unit = {},
) {
    Column(modifier = modifier.fillMaxSize().background(tint)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 24.dp, end = 24.dp, top = 72.dp, bottom = 44.dp),
            content = header,
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .pulledUp(16.dp)
                .background(AppTheme.colors.background, RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                .verticalScroll(rememberScrollState())
                .padding(start = 24.dp, end = 24.dp, top = 16.dp),
            content = panel,
        )
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .background(AppTheme.colors.background)
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(start = 18.dp, end = 18.dp, top = 12.dp, bottom = 16.dp),
            content = actions,
        )
    }
}

/**
 * Filled 16dp field of the calm layout: a 12sp label over the 16sp value on a sunken ground, a 2dp primary outline while
 * focused, [trailing] (the show/hide toggle) on the right. The whole field takes the tap, so tapping the label focuses it.
 */
@Composable
internal fun CalmTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    supportingText: String? = null,
    errorText: String? = null,
    enabled: Boolean = true,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    trailingIcon: (@Composable () -> Unit)? = null,
) {
    val colors = AppTheme.colors
    val shape = RoundedCornerShape(16.dp)
    val focus = remember { FocusRequester() }
    var focused by remember { mutableStateOf(false) }
    val outline = when {
        errorText != null -> colors.error
        focused -> colors.primary
        else -> Color.Transparent
    }
    Column(Modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(colors.sunken, shape)
                .border(2.dp, outline, shape)
                .clickable(enabled = enabled, indication = null, interactionSource = remember { MutableInteractionSource() }) { focus.requestFocus() }
                .padding(start = 16.dp, end = if (trailingIcon != null) 2.dp else 16.dp, top = 10.dp, bottom = 10.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    label,
                    style = AppTheme.type.caption,
                    color = when {
                        errorText != null -> colors.error
                        focused -> colors.primary
                        else -> colors.ink2
                    },
                )
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    enabled = enabled,
                    singleLine = true,
                    keyboardOptions = keyboardOptions,
                    keyboardActions = keyboardActions,
                    visualTransformation = visualTransformation,
                    textStyle = AppTheme.type.body.copy(fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = colors.ink),
                    cursorBrush = SolidColor(colors.primary),
                    modifier = modifier
                        .fillMaxWidth()
                        .focusRequester(focus)
                        .onFocusChanged { focused = it.isFocused }
                        .semantics { if (errorText != null) error(errorText) },
                )
            }
            trailingIcon?.invoke()
        }
        (errorText ?: supportingText)?.let {
            Text(
                it,
                style = AppTheme.type.caption,
                color = if (errorText != null) colors.error else colors.caption,
                modifier = Modifier.padding(start = 16.dp, top = 4.dp, end = 16.dp),
            )
        }
    }
}

/** [CalmTextField] masked by default, with the labelled show/hide toggle. */
@Composable
internal fun CalmPasswordField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    visible: Boolean,
    onToggleVisible: () -> Unit,
    modifier: Modifier = Modifier,
    supportingText: String? = null,
    errorText: String? = null,
    enabled: Boolean = true,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
) {
    CalmTextField(
        value = value,
        onValueChange = onValueChange,
        label = label,
        modifier = modifier,
        supportingText = supportingText,
        errorText = errorText,
        enabled = enabled,
        keyboardOptions = keyboardOptions.copy(keyboardType = KeyboardType.Password),
        keyboardActions = keyboardActions,
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        trailingIcon = {
            CalmCircleButton(
                icon = if (visible) AppIcons.VisibilityOff else AppIcons.Visibility,
                contentDescription = stringResource(if (visible) Res.string.cd_hide_password else Res.string.cd_show_password),
                onClick = onToggleVisible,
                tint = AppTheme.colors.ink2,
                container = Color.Transparent,
                bordered = false,
            )
        },
    )
}

/** A text link under a form: [text] already carries its own styling, drawn centred in a 48dp target. */
@Composable
internal fun AuthLink(text: AnnotatedString, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clickable(role = Role.Button, onClick = onClick),
    ) {
        Text(text, style = AppTheme.type.label.copy(fontSize = 14.5.sp, fontWeight = FontWeight.SemiBold), color = AppTheme.colors.ink2)
    }
}

/** One line of the "what happens next" list: a 28dp marker, the text, a hairline above every row but the first. */
@Composable
internal fun AuthStep(
    marker: @Composable () -> Unit,
    text: AnnotatedString,
    first: Boolean,
    muted: Boolean = false,
) {
    Column {
        if (!first) CalmDivider()
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.fillMaxWidth().padding(vertical = 14.dp)) {
            marker()
            Text(
                text,
                style = AppTheme.type.body.copy(fontSize = 15.sp, lineHeight = 22.sp),
                color = if (muted) AppTheme.colors.ink2 else AppTheme.colors.ink,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
internal fun AuthStepMarker(content: @Composable () -> Unit, container: Color, border: Color? = null) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(28.dp)
            .background(container, CircleShape)
            .then(if (border != null) Modifier.border(1.5.dp, border, CircleShape) else Modifier),
    ) { content() }
}

/** Lets a panel ride [amount] over the block above it without leaving a gap below: it grows by [amount] and shifts up. */
private fun Modifier.pulledUp(amount: Dp): Modifier = layout { measurable, constraints ->
    val extra = amount.roundToPx()
    val placeable = measurable.measure(
        constraints.copy(
            minHeight = (constraints.minHeight + extra).coerceAtMost(constraints.maxHeight + extra),
            maxHeight = constraints.maxHeight + extra,
        ),
    )
    layout(placeable.width, placeable.height - extra) { placeable.place(0, -extra) }
}
