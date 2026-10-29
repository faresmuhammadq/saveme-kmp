package com.fares.saveme.feature.sharing.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fares.saveme.designsystem.format.formatDate
import com.fares.saveme.designsystem.format.formatDayMonth
import com.fares.saveme.designsystem.theme.AppTheme
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.Instant

/** Avatar and wallet-hero colours, picked by a stable hash of a name (the phone holds no colour for them). */
internal val calmAvatarPalette = listOf(
    Color(0xFF2F8566), Color(0xFF6B49A8), Color(0xFF21777C), Color(0xFFB04A6E), Color(0xFF8A6210), Color(0xFF3557C0),
)

internal fun calmColorFor(key: String): Color = calmAvatarPalette[(key.hashCode() and Int.MAX_VALUE) % calmAvatarPalette.size]

internal fun calmColorAt(index: Int): Color = calmAvatarPalette[index.mod(calmAvatarPalette.size)]

/** "24 Oct" within this year, "24 Oct 2025" otherwise. */
@Composable
internal fun shortDate(date: LocalDate): String {
    val thisYear = remember { Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).year }
    return if (date.year == thisYear) formatDayMonth(date) else formatDate(date)
}

@Composable
internal fun shortDate(instant: Instant, timeZoneId: String): String =
    shortDate(instant.toLocalDateTime(runCatching { TimeZone.of(timeZoneId) }.getOrElse { TimeZone.UTC }).date)

/** 56dp white button with a hairline border (the mock's "Done"). */
@Composable
internal fun CalmOutlineButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val shape = RoundedCornerShape(18.dp)
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .height(AppTheme.sizes.button)
            .clip(shape)
            .background(AppTheme.colors.surface, shape)
            .border(1.dp, AppTheme.colors.outlineVariant, shape)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
    ) {
        Text(text, style = AppTheme.type.label.copy(fontSize = 16.sp), color = if (enabled) AppTheme.colors.ink else AppTheme.colors.outline)
    }
}

/** 56dp primary button with a leading icon (the mock's "Share"). */
@Composable
internal fun CalmIconButton(text: String, icon: ImageVector, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val shape = RoundedCornerShape(18.dp)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        modifier = modifier
            .height(AppTheme.sizes.button)
            .clip(shape)
            .background(if (enabled) AppTheme.colors.primary else AppTheme.colors.sunken2, shape)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
    ) {
        val content = if (enabled) AppTheme.colors.onPrimary else AppTheme.colors.outline
        Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(22.dp))
        Text(text, style = AppTheme.type.label.copy(fontSize = 16.sp, fontWeight = FontWeight.Bold), color = content)
    }
}
