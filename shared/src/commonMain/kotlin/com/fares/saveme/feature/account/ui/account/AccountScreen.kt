package com.fares.saveme.feature.account.ui.account

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fares.saveme.core.sync.SyncStatus
import com.fares.saveme.designsystem.component.CalmCard
import com.fares.saveme.designsystem.component.CalmCheckBadge
import com.fares.saveme.designsystem.component.CalmScreen
import com.fares.saveme.designsystem.component.calmButtonSemantics
import com.fares.saveme.designsystem.component.calmTapArea
import com.fares.saveme.designsystem.format.formatDateTime
import com.fares.saveme.designsystem.format.formatDate
import com.fares.saveme.designsystem.icon.AccDownload
import com.fares.saveme.designsystem.icon.AccMedal
import com.fares.saveme.designsystem.icon.AccVerified
import com.fares.saveme.designsystem.icon.AppIcons
import com.fares.saveme.designsystem.theme.AppTheme
import com.fares.saveme.feature.auth.ui.signout.SignOutConfirmSheet
import com.fares.saveme.feature.notifications.domain.NotificationCategory
import kotlinx.datetime.TimeZone
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import saveme.shared.generated.resources.Res
import saveme.shared.generated.resources.account_change_password
import saveme.shared.generated.resources.account_delete
import saveme.shared.generated.resources.account_dev_tools
import saveme.shared.generated.resources.acct_devices
import saveme.shared.generated.resources.account_email_unverified
import saveme.shared.generated.resources.account_email_verified
import saveme.shared.generated.resources.account_export
import saveme.shared.generated.resources.account_notifications
import saveme.shared.generated.resources.account_plan
import saveme.shared.generated.resources.account_profile
import saveme.shared.generated.resources.acct_plan_sub
import saveme.shared.generated.resources.acct_tile_devices_count
import saveme.shared.generated.resources.plan_status_active_ends
import saveme.shared.generated.resources.plan_status_active_renews
import saveme.shared.generated.resources.plan_status_grace
import saveme.shared.generated.resources.plan_status_no_end
import saveme.shared.generated.resources.acct_tile_delete_sub
import saveme.shared.generated.resources.acct_tile_dev_sub
import saveme.shared.generated.resources.acct_tile_devices_sub
import saveme.shared.generated.resources.acct_tile_export_sub
import saveme.shared.generated.resources.acct_tile_notifications_sub
import saveme.shared.generated.resources.acct_tile_password_sub
import saveme.shared.generated.resources.acct_tile_profile_sub
import saveme.shared.generated.resources.action_sync_now
import saveme.shared.generated.resources.cd_back
import saveme.shared.generated.resources.sign_out_action
import saveme.shared.generated.resources.sync_all_synced
import saveme.shared.generated.resources.home_categories
import saveme.shared.generated.resources.homec_categories_sub
import saveme.shared.generated.resources.sync_last_synced
import saveme.shared.generated.resources.sync_needs_decision_count
import saveme.shared.generated.resources.sync_never
import saveme.shared.generated.resources.account_server
import saveme.shared.generated.resources.sync_waiting_count

private class Tile(
    val title: org.jetbrains.compose.resources.StringResource,
    val sub: String,
    val icon: ImageVector,
    val destination: AccountDestination,
    val tag: String? = null,
    val danger: Boolean = false,
)

@Composable
fun AccountScreen(state: AccountState, onIntent: (AccountIntent) -> Unit) {
    val tiles = listOfNotNull(
        Tile(Res.string.account_profile, stringResource(Res.string.acct_tile_profile_sub), AppIcons.Person, AccountDestination.Profile),
        Tile(
            Res.string.account_notifications,
            stringResource(Res.string.acct_tile_notifications_sub, NotificationCategory.entries.size),
            AppIcons.Bell,
            AccountDestination.Notifications,
            "account.notifications",
        ).takeIf { state.notificationsAvailable },
        Tile(Res.string.home_categories, stringResource(Res.string.homec_categories_sub), AppIcons.Sparkle, AccountDestination.Categories, "account.categories"),
        Tile(
            Res.string.acct_devices,
            state.deviceCount?.let { stringResource(Res.string.acct_tile_devices_count, it) } ?: stringResource(Res.string.acct_tile_devices_sub),
            AppIcons.Devices,
            AccountDestination.Devices,
        ),
        Tile(Res.string.account_export, stringResource(Res.string.acct_tile_export_sub), AppIcons.AccDownload, AccountDestination.Exports, "account.export"),
        Tile(Res.string.account_change_password, stringResource(Res.string.acct_tile_password_sub), AppIcons.Lock, AccountDestination.ChangePassword),
        state.serverAddress?.let { Tile(Res.string.account_server, it, AppIcons.Sync, AccountDestination.Server, "account.server") },
        Tile(Res.string.account_dev_tools, stringResource(Res.string.acct_tile_dev_sub), AppIcons.Laptop, AccountDestination.DevTools, "account.devTools")
            .takeIf { state.devToolsAvailable },
        Tile(Res.string.account_delete, stringResource(Res.string.acct_tile_delete_sub), AppIcons.Delete, AccountDestination.DeleteAccount, danger = true),
    )
    CalmScreen(
        title = null,
        onBack = { onIntent(AccountIntent.Back) },
        backDescription = stringResource(Res.string.cd_back),
    ) {
        val name = state.displayName ?: state.email
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(64.dp).background(AppTheme.colors.primary, CircleShape),
            ) {
                Text(
                    name.firstOrNull { it.isLetterOrDigit() }?.uppercase().orEmpty(),
                    style = AppTheme.type.headline.copy(fontSize = 26.sp),
                    color = AppTheme.colors.onPrimary,
                )
            }
            Column(Modifier.weight(1f)) {
                Text(
                    name,
                    style = AppTheme.type.headline.copy(fontSize = 24.sp),
                    color = AppTheme.colors.ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.semantics { heading() },
                )
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        state.email,
                        style = AppTheme.type.caption.copy(fontWeight = FontWeight.SemiBold),
                        color = AppTheme.colors.ink2,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    val verified = stringResource(if (state.emailVerified) Res.string.account_email_verified else Res.string.account_email_unverified)
                    if (state.emailVerified) {
                        CalmCheckBadge(AppTheme.money.positive, Modifier.semantics { contentDescription = verified }, size = 16.dp)
                    } else {
                        Icon(AppIcons.Warning, contentDescription = verified, tint = AppTheme.money.pending, modifier = Modifier.size(16.dp))
                    }
                }
            }
        }
        PlanCard(state.plan, onClick = { onIntent(AccountIntent.Open(AccountDestination.Plan)) })
        SyncStatusCard(state.sync, onSyncNow = { onIntent(AccountIntent.SyncNowClicked) })
        tiles.chunked(2).forEach { pair ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
                pair.forEach { tile ->
                    SettingsTile(tile, Modifier.weight(1f).fillMaxHeight()) { onIntent(AccountIntent.Open(tile.destination)) }
                }
                if (pair.size == 1) Box(Modifier.weight(1f))
            }
        }
        CalmCard(
            radius = 18.dp,
            padding = PaddingValues(0.dp),
            onClick = { onIntent(AccountIntent.SignOutClicked) },
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) {
                Icon(AppIcons.Logout, contentDescription = null, tint = AppTheme.colors.ink, modifier = Modifier.size(22.dp))
                Text(stringResource(Res.string.sign_out_action), style = AppTheme.type.body.copy(fontWeight = FontWeight.Bold), color = AppTheme.colors.ink)
            }
        }
    }
    if (state.confirmSignOutVisible) {
        SignOutConfirmSheet(
            signingOut = state.signingOut,
            onConfirm = { onIntent(AccountIntent.SignOutConfirmed) },
            onDismiss = { onIntent(AccountIntent.SignOutDismissed) },
            pendingChanges = state.sync.pending,
            onSyncNow = { onIntent(AccountIntent.SyncNowClicked) },
        )
    }
}

@Composable
private fun PlanCard(plan: AccountPlan?, onClick: () -> Unit) {
    CalmCard(
        container = AppTheme.colors.ink,
        bordered = false,
        radius = 22.dp,
        padding = PaddingValues(horizontal = 18.dp, vertical = 16.dp),
        onClick = onClick,
        modifier = Modifier.testTag("account.plan"),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Icon(AppIcons.AccMedal, contentDescription = null, tint = Color(0xFFE3B45A), modifier = Modifier.size(28.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    plan?.name ?: stringResource(Res.string.account_plan),
                    style = AppTheme.type.title.copy(fontWeight = FontWeight.ExtraBold),
                    color = AppTheme.colors.background,
                )
                Text(
                    plan?.status?.text() ?: stringResource(Res.string.acct_plan_sub),
                    style = AppTheme.type.caption.copy(fontWeight = FontWeight.SemiBold),
                    color = AppTheme.colors.background.copy(alpha = 0.75f),
                )
            }
            Icon(AppIcons.ChevronForward, contentDescription = null, tint = AppTheme.colors.background.copy(alpha = 0.7f), modifier = Modifier.size(22.dp))
        }
    }
}

@Composable
private fun AccountPlanStatus.text(): String {
    val zone = TimeZone.currentSystemDefault().id
    return when (this) {
        is AccountPlanStatus.Renews -> stringResource(Res.string.plan_status_active_renews, formatDate(on, zone))
        is AccountPlanStatus.Ends -> stringResource(Res.string.plan_status_active_ends, formatDate(on, zone))
        AccountPlanStatus.NoEnd -> stringResource(Res.string.plan_status_no_end)
        is AccountPlanStatus.Grace -> stringResource(Res.string.plan_status_grace, formatDate(until, zone))
    }
}

@Composable
private fun SettingsTile(tile: Tile, modifier: Modifier, onClick: () -> Unit) {
    val tint = if (tile.danger) AppTheme.colors.error else AppTheme.colors.primary
    CalmCard(
        radius = 20.dp,
        padding = PaddingValues(16.dp),
        modifier = modifier.then(if (tile.tag != null) Modifier.testTag(tile.tag) else Modifier).calmTapArea(onClick),
    ) {
        Icon(tile.icon, contentDescription = null, tint = tint, modifier = Modifier.size(24.dp))
        Text(
            stringResource(tile.title),
            style = AppTheme.type.body.copy(fontWeight = FontWeight.Bold),
            color = tint,
            modifier = Modifier.padding(top = 10.dp, bottom = 4.dp).calmButtonSemantics(onClick),
        )
        Text(tile.sub, style = AppTheme.type.caption.copy(fontWeight = FontWeight.Medium), color = AppTheme.colors.ink2)
    }
}

/** What is still only on this phone, and when the app last synced (spec 004 FR-016, FR-010). */
@Composable
private fun SyncStatusCard(sync: SyncStatus, onSyncNow: () -> Unit) {
    val headline = when {
        sync.waiting > 0 -> pluralStringResource(Res.plurals.sync_waiting_count, sync.waiting, sync.waiting)
        sync.needsDecision > 0 -> pluralStringResource(Res.plurals.sync_needs_decision_count, sync.needsDecision, sync.needsDecision)
        else -> stringResource(Res.string.sync_all_synced)
    }
    val lines = listOfNotNull(
        pluralStringResource(Res.plurals.sync_needs_decision_count, sync.needsDecision, sync.needsDecision)
            .takeIf { sync.waiting > 0 && sync.needsDecision > 0 },
        sync.lastSyncedAt?.let { stringResource(Res.string.sync_last_synced, formatDateTime(it, TimeZone.currentSystemDefault().id)) }
            ?: stringResource(Res.string.sync_never).takeIf { sync.pending > 0 },
    )
    val waiting = sync.waiting > 0
    val tint = if (waiting) AppTheme.money.pending else AppTheme.money.positive
    CalmCard(
        container = if (waiting) AppTheme.money.pendingContainer else AppTheme.colors.surface,
        bordered = !waiting,
        radius = 16.dp,
        padding = PaddingValues(start = 14.dp, end = 12.dp, top = 10.dp, bottom = 10.dp),
        modifier = Modifier.testTag("account.syncStatus"),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(if (waiting) AppIcons.Sync else AppIcons.AccVerified, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
            Column(Modifier.weight(1f)) {
                Text(headline, style = AppTheme.type.body.copy(fontWeight = FontWeight.SemiBold), color = AppTheme.colors.ink)
                if (lines.isNotEmpty() && !waiting) {
                    Text(lines.joinToString("\n"), style = AppTheme.type.caption, color = AppTheme.colors.ink2)
                }
            }
            if (waiting) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .clickable(role = Role.Button, onClick = onSyncNow)
                        .testTag("account.syncNow"),
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.height(34.dp).background(AppTheme.colors.surface, AppTheme.shapes.pill).padding(horizontal = 12.dp),
                    ) {
                        Text(
                            stringResource(Res.string.action_sync_now),
                            style = AppTheme.type.caption.copy(fontWeight = FontWeight.Bold),
                            color = AppTheme.money.pending,
                        )
                    }
                }
            }
        }
    }
}

@Preview
@Composable
private fun AccountScreenPreview() {
    AppTheme { AccountScreen(AccountState(email = "sara@example.com", emailVerified = false)) {} }
}
