package com.fares.saveme.feature.notifications.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.SnackbarHostState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import saveme.shared.generated.resources.authb_notif_quiet_note_plain
import saveme.shared.generated.resources.authb_notif_quiet_note
import saveme.shared.generated.resources.authb_notif_intro
import com.fares.saveme.feature.notifications.ui.descriptionRes
import com.fares.saveme.designsystem.component.TimeOfDayList
import com.fares.saveme.designsystem.component.AppBottomSheet
import androidx.compose.ui.Alignment
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.sp
import com.fares.saveme.designsystem.component.CalmActionBar
import com.fares.saveme.designsystem.component.CalmCard
import com.fares.saveme.designsystem.component.CalmScreen
import com.fares.saveme.designsystem.format.formatTimeOfDay
import com.fares.saveme.designsystem.icon.FlowCrown
import com.fares.saveme.designsystem.icon.FlowDownload
import com.fares.saveme.designsystem.icon.FlowGroup
import com.fares.saveme.designsystem.icon.FlowMoon
import com.fares.saveme.designsystem.icon.FlowPersonRemove
import saveme.shared.generated.resources.authb_state_off
import saveme.shared.generated.resources.authb_state_on
import saveme.shared.generated.resources.cd_back
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.fares.saveme.designsystem.component.AlertTone
import com.fares.saveme.designsystem.component.AppAlertDialog
import com.fares.saveme.designsystem.component.AppListItem
import com.fares.saveme.designsystem.component.InlineAlert
import com.fares.saveme.designsystem.component.ListTrailing
import com.fares.saveme.designsystem.component.PrimaryButton
import com.fares.saveme.designsystem.component.SecondaryButton
import com.fares.saveme.designsystem.component.Skeleton
import com.fares.saveme.designsystem.icon.AppIcons
import com.fares.saveme.designsystem.theme.AppTheme
import com.fares.saveme.feature.notifications.domain.NotificationCategory
import com.fares.saveme.feature.notifications.domain.NotificationPreferences
import com.fares.saveme.feature.notifications.domain.QuarterHour
import com.fares.saveme.feature.notifications.domain.QuietHours
import com.fares.saveme.feature.notifications.ui.PermissionStatus
import com.fares.saveme.feature.notifications.ui.text
import com.fares.saveme.feature.notifications.ui.titleRes
import org.jetbrains.compose.resources.stringResource
import saveme.shared.generated.resources.Res
import saveme.shared.generated.resources.action_retry
import saveme.shared.generated.resources.notifications_discard
import saveme.shared.generated.resources.notifications_discard_body
import saveme.shared.generated.resources.notifications_discard_title
import saveme.shared.generated.resources.notifications_keep_editing
import saveme.shared.generated.resources.notifications_loading
import saveme.shared.generated.resources.notifications_open_settings
import saveme.shared.generated.resources.notifications_phone_off
import saveme.shared.generated.resources.notifications_phone_settings
import saveme.shared.generated.resources.notifications_quiet_from
import saveme.shared.generated.resources.notifications_quiet_hours
import saveme.shared.generated.resources.notifications_quiet_hours_desc
import saveme.shared.generated.resources.notifications_quiet_invalid
import saveme.shared.generated.resources.notifications_quiet_to
import saveme.shared.generated.resources.notifications_save
import saveme.shared.generated.resources.notifications_scope_note
import saveme.shared.generated.resources.notifications_section
import saveme.shared.generated.resources.notifications_time_zone_note
import saveme.shared.generated.resources.notifications_title
import saveme.shared.generated.resources.notifications_turn_on

@Composable
fun NotificationSettingsScreen(
    state: NotificationSettingsState,
    onIntent: (NotificationSettingsIntent) -> Unit,
    snackbar: SnackbarHostState = remember { SnackbarHostState() },
) {
    val edited = state.edited
    CalmScreen(
        title = stringResource(Res.string.notifications_title),
        onBack = { onIntent(NotificationSettingsIntent.Back) },
        backDescription = stringResource(Res.string.cd_back),
        snackbarHostState = snackbar,
        bottomBar = {
            if (!state.loading && edited != null) {
                CalmActionBar {
                    state.message?.let { message ->
                        InlineAlert(
                            if (message is SettingsMessage.Conflict) AlertTone.Warning else AlertTone.Error,
                            message.text(),
                            modifier = Modifier.testTag("notifications.message"),
                        )
                    }
                    PrimaryButton(
                        stringResource(Res.string.notifications_save),
                        { onIntent(NotificationSettingsIntent.Save) },
                        modifier = Modifier.testTag("notifications.save"),
                        enabled = state.canSave,
                        loading = state.saving,
                    )
                }
            }
        },
    ) {
        when {
            state.loading -> {
                Text(
                    stringResource(Res.string.notifications_loading),
                    style = AppTheme.type.caption,
                    color = AppTheme.colors.caption,
                    modifier = Modifier.testTag("notifications.loading").semantics { liveRegion = LiveRegionMode.Polite },
                )
                repeat(3) { Skeleton(Modifier.fillMaxWidth().height(104.dp), RoundedCornerShape(20.dp)) }
            }
            edited == null -> NotLoaded(state, onIntent)
            else -> Loaded(state, edited, onIntent)
        }
    }
    if (state.discardConfirmVisible) {
        AppAlertDialog(
            title = stringResource(Res.string.notifications_discard_title),
            text = stringResource(Res.string.notifications_discard_body),
            confirmLabel = stringResource(Res.string.notifications_discard),
            onConfirm = { onIntent(NotificationSettingsIntent.DiscardConfirmed) },
            dismissLabel = stringResource(Res.string.notifications_keep_editing),
            onDismiss = { onIntent(NotificationSettingsIntent.DiscardDismissed) },
            destructive = true,
            confirmModifier = Modifier.testTag("notifications.discard"),
            dismissModifier = Modifier.testTag("notifications.keepEditing"),
        )
    }
}

@Composable
private fun NotLoaded(state: NotificationSettingsState, onIntent: (NotificationSettingsIntent) -> Unit) {
    state.notLoaded?.let { message ->
        InlineAlert(
            AlertTone.Info,
            message.text(),
            modifier = Modifier.testTag("notifications.notLoaded"),
            actionLabel = stringResource(Res.string.action_retry),
            onAction = { onIntent(NotificationSettingsIntent.TryAgain) },
        )
    }
}

@Composable
private fun Loaded(state: NotificationSettingsState, edited: NotificationPreferences, onIntent: (NotificationSettingsIntent) -> Unit) {
    if (state.phone != PermissionStatus.Allowed) PhoneCard(state.phone, onIntent)

    Text(
        stringResource(Res.string.authb_notif_intro),
        style = AppTheme.type.body.copy(fontSize = 14.sp, lineHeight = 20.sp),
        color = AppTheme.colors.ink2,
        modifier = Modifier.semantics { heading() },
    )
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        NotificationCategory.entries.chunked(2).forEach { pair ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Max)) {
                pair.forEach { category ->
                    CategoryTile(
                        title = stringResource(category.titleRes()),
                        description = stringResource(category.descriptionRes()),
                        icon = category.icon(),
                        on = edited.isOn(category),
                        onToggle = { onIntent(NotificationSettingsIntent.CategoryToggled(category, it)) },
                        modifier = Modifier.weight(1f).fillMaxHeight().testTag("notifications.category.${category.wire}"),
                    )
                }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }

    CalmCard(padding = PaddingValues(horizontal = 18.dp, vertical = 16.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .testTag("notifications.quiet")
                .toggleable(value = edited.quietHours.enabled, role = Role.Switch) { onIntent(NotificationSettingsIntent.QuietHoursToggled(it)) },
        ) {
            Icon(AppIcons.FlowMoon, contentDescription = null, tint = QuietTint, modifier = Modifier.size(24.dp))
            Text(
                stringResource(Res.string.notifications_quiet_hours),
                style = AppTheme.type.body.copy(fontWeight = FontWeight.Bold),
                color = AppTheme.colors.ink,
                modifier = Modifier.weight(1f),
            )
            CalmSwitch(edited.quietHours.enabled)
        }
        Spacer(Modifier.height(8.dp))
        QuietHoursBar(edited.quietHours.start.minutes, edited.quietHours.end.minutes)
        Spacer(Modifier.height(12.dp))
        // Times stay editable while quiet hours are off (FR-016).
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            QuietTimeButton(
                label = stringResource(Res.string.notifications_quiet_from),
                minutes = edited.quietHours.start.minutes,
                onChange = { onIntent(NotificationSettingsIntent.StartChosen(it)) },
                modifier = Modifier.weight(1f).testTag("notifications.quiet.start"),
            )
            QuietTimeButton(
                label = stringResource(Res.string.notifications_quiet_to),
                minutes = edited.quietHours.end.minutes,
                onChange = { onIntent(NotificationSettingsIntent.EndChosen(it)) },
                modifier = Modifier.weight(1f).testTag("notifications.quiet.end"),
            )
        }
        if (!state.valid) {
            Text(
                stringResource(Res.string.notifications_quiet_invalid),
                style = AppTheme.type.caption,
                color = AppTheme.colors.error,
                modifier = Modifier.padding(top = 8.dp).testTag("notifications.quiet.error").semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
        Text(
            state.timeZoneId?.let { stringResource(Res.string.authb_notif_quiet_note, it) } ?: stringResource(Res.string.authb_notif_quiet_note_plain),
            style = AppTheme.type.caption.copy(fontSize = 12.5.sp, fontWeight = FontWeight.Medium),
            color = AppTheme.colors.ink2,
            modifier = Modifier.padding(top = 10.dp).testTag("notifications.timeZone"),
        )
    }

    Text(
        stringResource(Res.string.notifications_scope_note),
        style = AppTheme.type.caption,
        color = AppTheme.colors.caption,
        modifier = Modifier.testTag("notifications.scope"),
    )
    CalmCard(padding = PaddingValues(0.dp)) {
        AppListItem(
            stringResource(Res.string.notifications_phone_settings),
            leadingIcon = AppIcons.Bell,
            trailing = ListTrailing.Chevron,
            modifier = Modifier.testTag("notifications.phoneSettings"),
            onClick = { onIntent(NotificationSettingsIntent.OpenPhoneSettingsClicked) },
        )
    }
}

private fun NotificationCategory.icon(): ImageVector = when (this) {
    NotificationCategory.RecurringDue -> AppIcons.Cycle
    NotificationCategory.BudgetExceeded -> AppIcons.Budgets
    NotificationCategory.Sharing -> AppIcons.FlowGroup
    NotificationCategory.Billing -> AppIcons.FlowCrown
    NotificationCategory.ExportReady -> AppIcons.FlowDownload
    NotificationCategory.DeletionReminder -> AppIcons.FlowPersonRemove
}

/** A category as a toggle tile: tinted and outlined when on, plain when off; the state is spelled out as text too. */
@Composable
private fun CategoryTile(
    title: String,
    description: String,
    icon: ImageVector,
    on: Boolean,
    onToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = AppTheme.colors
    val shape = RoundedCornerShape(20.dp)
    Column(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = modifier
            .heightIn(min = 104.dp)
            .clip(shape)
            .background(if (on) colors.primaryContainer else colors.surface, shape)
            .border(1.5.dp, if (on) colors.primary else colors.outlineVariant, shape)
            .toggleable(value = on, role = Role.Switch, onValueChange = onToggle)
            .semantics { contentDescription = "$title. $description" }
            .padding(14.dp),
    ) {
        Icon(icon, contentDescription = null, tint = if (on) colors.primary else colors.ink2, modifier = Modifier.size(22.dp))
        Text(title, style = AppTheme.type.label.copy(fontWeight = FontWeight.Bold), color = if (on) colors.ink else colors.ink2)
        Text(
            stringResource(if (on) Res.string.authb_state_on else Res.string.authb_state_off),
            style = AppTheme.type.caption,
            color = if (on) colors.primary else colors.ink2,
        )
    }
}

/** The 46x28 switch of the calm layout; the row around it carries the toggle semantics. */
@Composable
private fun CalmSwitch(checked: Boolean) {
    Box(
        contentAlignment = if (checked) Alignment.CenterEnd else Alignment.CenterStart,
        modifier = Modifier
            .size(width = 46.dp, height = 28.dp)
            .background(if (checked) AppTheme.colors.primary else AppTheme.colors.outlineVariant, CircleShape)
            .padding(3.dp),
    ) {
        Box(Modifier.size(22.dp).background(Color.White, CircleShape))
    }
}

/** "From 23:00": a 44dp button that opens the quarter-hour list. */
@Composable
private fun QuietTimeButton(label: String, minutes: Int, onChange: (Int) -> Unit, modifier: Modifier = Modifier) {
    var open by remember { mutableStateOf(false) }
    val options = remember { (0 until MinutesPerDay step 15).toList() }
    val selected = (minutes.coerceIn(0, MinutesPerDay - 1) / 15) * 15
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .heightIn(min = 48.dp)
            .clickable(role = Role.Button) { open = true },
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.fillMaxWidth().height(44.dp).background(AppTheme.colors.sunken, RoundedCornerShape(12.dp)),
        ) {
            Text(
                "$label ${formatTimeOfDay(selected)}",
                style = AppTheme.type.body.copy(fontWeight = FontWeight.Bold),
                color = AppTheme.colors.ink,
            )
        }
    }
    if (open) {
        AppBottomSheet(onDismiss = { open = false }, title = label) {
            TimeOfDayList(options, selected, onSelect = { onChange(it); open = false })
        }
    }
}

private val QuietTint = Color(0xFF6B49A8)
private const val MinutesPerDay = 24 * 60

/** The 24 hours as a strip with the quiet stretch shaded; it wraps past midnight. */
@Composable
private fun QuietHoursBar(startMinutes: Int, endMinutes: Int) {
    val description = "${stringResource(Res.string.notifications_quiet_from)} ${formatTimeOfDay(startMinutes)} " +
        "${stringResource(Res.string.notifications_quiet_to)} ${formatTimeOfDay(endMinutes)}"
    val track = AppTheme.colors.sunken
    Column(Modifier.clearAndSetSemantics { contentDescription = description }) {
        Canvas(Modifier.fillMaxWidth().height(28.dp).clip(RoundedCornerShape(10.dp))) {
            drawRect(track)
            fun band(from: Int, to: Int) {
                val x0 = size.width * from / MinutesPerDay
                val x1 = size.width * to / MinutesPerDay
                val left = if (layoutDirection == LayoutDirection.Rtl) size.width - x1 else x0
                drawRect(QuietTint.copy(alpha = 0.85f), topLeft = Offset(left, 0f), size = Size(x1 - x0, size.height))
            }
            if (startMinutes < endMinutes) {
                band(startMinutes, endMinutes)
            } else if (startMinutes > endMinutes) {
                band(0, endMinutes)
                band(startMinutes, MinutesPerDay)
            }
        }
        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth().padding(top = 6.dp)) {
            listOf("00", "06", "12", "18", "24").forEach {
                Text(it, style = AppTheme.type.caption.copy(fontSize = 11.sp), color = AppTheme.colors.ink2)
            }
        }
    }
}

/** "Notifications are off on this phone" with Turn on where the system can still ask, Open settings otherwise (FR-020). */
@Composable
private fun PhoneCard(phone: PermissionStatus, onIntent: (NotificationSettingsIntent) -> Unit) {
    CalmCard {
        Column(verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.s)) {
            Text(
                stringResource(Res.string.notifications_phone_off),
                style = AppTheme.type.body,
                color = AppTheme.colors.ink,
                modifier = Modifier.testTag("notifications.phoneOff"),
            )
            if (phone == PermissionStatus.Askable) {
                PrimaryButton(
                    stringResource(Res.string.notifications_turn_on),
                    { onIntent(NotificationSettingsIntent.TurnOnClicked) },
                    modifier = Modifier.testTag("notifications.turnOn"),
                )
            } else {
                SecondaryButton(
                    stringResource(Res.string.notifications_open_settings),
                    { onIntent(NotificationSettingsIntent.OpenPhoneSettingsClicked) },
                    modifier = Modifier.testTag("notifications.openSettings"),
                )
            }
        }
    }
}

@Preview
@Composable
private fun NotificationSettingsScreenPreview() {
    val prefs = NotificationPreferences(
        version = 1,
        categories = NotificationCategory.entries.associateWith { true },
        quietHours = QuietHours(true, QuarterHour(22 * 60), QuarterHour(8 * 60)),
    )
    AppTheme { NotificationSettingsScreen(NotificationSettingsState(loading = false, read = prefs, edited = prefs, timeZoneId = "Africa/Cairo"), {}) }
}
