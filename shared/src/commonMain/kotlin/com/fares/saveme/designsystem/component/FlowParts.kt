package com.fares.saveme.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.clickable
import com.fares.saveme.designsystem.icon.AppIcons
import com.fares.saveme.designsystem.theme.AppTheme

/** Inline notice of the calm layout: 14dp tinted panel, 20dp icon, 13.5sp text and an optional action link under it. */
@Composable
fun CalmAlert(
    tone: AlertTone,
    text: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    radius: androidx.compose.ui.unit.Dp = 14.dp,
) {
    val colors = AppTheme.colors
    val (fg, bg, defaultIcon) = when (tone) {
        AlertTone.Info -> Triple(colors.primary, colors.primaryContainer, AppIcons.Info)
        AlertTone.Warning -> Triple(AppTheme.money.pending, AppTheme.money.pendingContainer, AppIcons.Info)
        AlertTone.Error -> Triple(AppTheme.money.over, AppTheme.money.overContainer, AppIcons.Error)
    }
    Row(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = modifier
            .fillMaxWidth()
            .background(bg, RoundedCornerShape(radius))
            .padding(horizontal = 14.dp, vertical = 12.dp)
            .semantics { liveRegion = LiveRegionMode.Polite },
    ) {
        Icon(icon ?: defaultIcon, contentDescription = null, tint = fg, modifier = Modifier.size(20.dp))
        Column(Modifier.weight(1f)) {
            Text(text, style = AppTheme.type.caption.copy(fontSize = 13.5.sp), color = colors.ink)
            if (actionLabel != null && onAction != null) {
                Text(
                    actionLabel,
                    style = AppTheme.type.caption.copy(fontSize = 13.5.sp, fontWeight = FontWeight.Bold),
                    color = fg,
                    modifier = Modifier
                        .heightIn(min = 40.dp)
                        .clickable(role = Role.Button, onClick = onAction)
                        .padding(top = 6.dp),
                )
            }
        }
    }
}

/**
 * The 56dp main action. Enabled it is [PrimaryButton]; disabled it is drawn as in the calm mock-ups: sunken ground,
 * hairline border, secondary ink.
 */
@Composable
fun CalmSubmitButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
) {
    if (enabled || loading) {
        PrimaryButton(text, onClick, modifier, enabled = enabled, loading = loading)
    } else {
        val shape = RoundedCornerShape(18.dp)
        Box(
            contentAlignment = Alignment.Center,
            modifier = modifier
                .fillMaxWidth()
                .heightIn(min = AppTheme.sizes.button)
                .background(AppTheme.colors.sunken, shape)
                .border(1.dp, AppTheme.colors.outlineVariant, shape)
                .semantics { disabled() },
        ) {
            Text(text, style = AppTheme.type.label.copy(fontSize = 16.sp, fontWeight = FontWeight.Bold), color = AppTheme.colors.ink2)
        }
    }
}

/** The 56dp quiet action: white ground, 1.5dp hairline, ink text. */
@Composable
fun CalmOutlinedButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(18.dp)
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = AppTheme.sizes.button)
            .background(AppTheme.colors.surface, shape)
            .border(1.5.dp, AppTheme.colors.outlineVariant, shape)
            .clickable(role = Role.Button, onClick = onClick),
    ) {
        Text(text, style = AppTheme.type.label.copy(fontSize = 16.sp, fontWeight = FontWeight.Bold), color = AppTheme.colors.ink)
    }
}

/** [text] with its first occurrence of each of [parts] in [weight] / [color]; used to bold an address inside a sentence. */
fun styledParts(text: String, vararg parts: String, weight: FontWeight = FontWeight.Bold, color: Color = Color.Unspecified): AnnotatedString =
    buildAnnotatedString {
        append(text)
        parts.filter { it.isNotEmpty() }.forEach { part ->
            val start = text.indexOf(part)
            if (start >= 0) addStyle(SpanStyle(fontWeight = weight, color = color), start, start + part.length)
        }
    }

/** "[prefix] [action]" with the action drawn as the primary, bold part ("New here? Create account"). */
@Composable
fun linkSentence(prefix: String, action: String): AnnotatedString {
    val primary = AppTheme.colors.primary
    return buildAnnotatedString {
        append("$prefix ")
        withStyle(SpanStyle(color = primary, fontWeight = FontWeight.Bold)) { append(action) }
    }
}

/** [text] wholly in the primary colour, bold. */
@Composable
fun primaryLinkText(text: String): AnnotatedString {
    val primary = AppTheme.colors.primary
    return buildAnnotatedString { withStyle(SpanStyle(color = primary, fontWeight = FontWeight.Bold)) { append(text) } }
}

/** [PrimaryButton] with a leading icon (the calm mock-ups' "Resend email"). */
@Composable
fun CalmIconPrimaryButton(
    text: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
) {
    val colors = AppTheme.colors
    val active = enabled && !loading
    androidx.compose.material3.Button(
        onClick = onClick,
        enabled = active,
        shape = RoundedCornerShape(18.dp),
        colors = androidx.compose.material3.ButtonDefaults.buttonColors(
            containerColor = colors.primary,
            contentColor = colors.onPrimary,
            disabledContainerColor = if (loading && enabled) colors.primary else colors.sunken2,
            disabledContentColor = if (loading && enabled) colors.onPrimary else colors.ink2,
        ),
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = AppTheme.sizes.button)
            .semantics { if (!active) disabled() },
    ) {
        if (loading) {
            androidx.compose.material3.CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = colors.onPrimary)
        } else {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(22.dp))
                Text(text, style = AppTheme.type.label.copy(fontSize = 16.sp, fontWeight = FontWeight.Bold))
            }
        }
    }
}
