package com.fares.saveme.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.fares.saveme.designsystem.icon.AppIcons
import com.fares.saveme.designsystem.theme.AppTheme
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import saveme.shared.generated.resources.Res
import saveme.shared.generated.resources.sync_offline
import saveme.shared.generated.resources.sync_synced

sealed interface SyncBannerState {
    data class Offline(val pendingCount: Int) : SyncBannerState
    data object Synced : SyncBannerState
}

@Composable
fun SyncBanner(state: SyncBannerState, modifier: Modifier = Modifier) {
    val (text, icon, fg, bg) = when (state) {
        is SyncBannerState.Offline -> Quad(
            pluralStringResource(Res.plurals.sync_offline, state.pendingCount, state.pendingCount),
            AppIcons.CloudOff, AppTheme.money.pending, AppTheme.money.pendingContainer,
        )
        SyncBannerState.Synced -> Quad(
            stringResource(Res.string.sync_synced), AppIcons.Check, AppTheme.money.positive, AppTheme.money.positiveContainer,
        )
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.xs),
        modifier = modifier
            .fillMaxWidth()
            .background(bg, AppTheme.shapes.control)
            .padding(horizontal = AppTheme.spacing.s, vertical = AppTheme.spacing.xs)
            .semantics { liveRegion = LiveRegionMode.Polite },
    ) {
        Icon(icon, contentDescription = null, tint = fg, modifier = Modifier.size(18.dp))
        Text(text, style = AppTheme.type.label, color = fg)
    }
}

private data class Quad<A, B, C, D>(val a: A, val b: B, val c: C, val d: D)

enum class AlertTone { Info, Warning, Error }

/** Copy comes from the app's own strings chosen by error code, never server text (P-06.5). */
@Composable
fun InlineAlert(
    tone: AlertTone,
    text: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    val colors = AppTheme.colors
    val (fg, bg, icon) = when (tone) {
        AlertTone.Info -> Triple(colors.onPrimaryContainer, colors.primaryContainer, AppIcons.Info)
        AlertTone.Warning -> Triple(AppTheme.money.pending, AppTheme.money.pendingContainer, AppIcons.Warning)
        AlertTone.Error -> Triple(colors.error, colors.errorContainer, AppIcons.Error)
    }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(bg, AppTheme.shapes.tile)
            .padding(AppTheme.spacing.s)
            .semantics { liveRegion = LiveRegionMode.Polite },
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.xs)) {
            Icon(icon, contentDescription = null, tint = fg, modifier = Modifier.size(20.dp))
            Text(text, style = AppTheme.type.body, color = colors.ink, modifier = Modifier.weight(1f))
        }
        if (actionLabel != null && onAction != null) {
            TextButton(
                onClick = onAction,
                modifier = Modifier.align(Alignment.End).heightIn(min = AppTheme.sizes.touchTarget),
            ) {
                Text(actionLabel, style = AppTheme.type.label, color = fg)
            }
        }
    }
}

/** Snackbars in ink with an optional action such as Undo. */
@Composable
fun AppSnackbarHost(hostState: SnackbarHostState, modifier: Modifier = Modifier) {
    SnackbarHost(hostState, modifier) { data ->
        Snackbar(
            snackbarData = data,
            shape = AppTheme.shapes.control,
            containerColor = AppTheme.colors.ink,
            contentColor = AppTheme.colors.surface,
            actionColor = AppTheme.colors.primaryContainer,
        )
    }
}
