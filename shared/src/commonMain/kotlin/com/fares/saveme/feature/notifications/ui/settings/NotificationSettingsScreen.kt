package com.fares.saveme.feature.notifications.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.SnackbarHostState
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
import com.fares.saveme.designsystem.component.AppScreen
import com.fares.saveme.designsystem.component.InlineAlert
import com.fares.saveme.designsystem.component.ListTrailing
import com.fares.saveme.designsystem.component.PrimaryButton
import com.fares.saveme.designsystem.component.SecondaryButton
import com.fares.saveme.designsystem.component.SettingsSwitchRow
import com.fares.saveme.designsystem.component.Skeleton
import com.fares.saveme.designsystem.component.TimeOfDayField
import com.fares.saveme.designsystem.icon.AppIcons
import com.fares.saveme.designsystem.theme.AppTheme
import com.fares.saveme.feature.notifications.domain.NotificationCategory
import com.fares.saveme.feature.notifications.domain.NotificationPreferences
import com.fares.saveme.feature.notifications.domain.QuarterHour
import com.fares.saveme.feature.notifications.domain.QuietHours
import com.fares.saveme.feature.notifications.ui.PermissionStatus
import com.fares.saveme.feature.notifications.ui.PushBackBar
import com.fares.saveme.feature.notifications.ui.descriptionRes
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
    AppScreen(
        snackbarHostState = snackbar,
        topBar = { PushBackBar(stringResource(Res.string.notifications_title)) { onIntent(NotificationSettingsIntent.Back) } },
    ) {
        val edited = state.edited
        when {
            state.loading -> {
                Text(
                    stringResource(Res.string.notifications_loading),
                    style = AppTheme.type.caption,
                    color = AppTheme.colors.caption,
                    modifier = Modifier.testTag("notifications.loading").semantics { liveRegion = LiveRegionMode.Polite },
                )
                repeat(4) { Skeleton(Modifier.fillMaxWidth().height(56.dp)) }
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
        stringResource(Res.string.notifications_section),
        style = AppTheme.type.label,
        color = AppTheme.colors.ink2,
        modifier = Modifier.semantics { heading() },
    )
    Column(Modifier.fillMaxWidth().background(AppTheme.colors.surface, AppTheme.shapes.card)) {
        NotificationCategory.entries.forEach { category ->
            SettingsSwitchRow(
                title = stringResource(category.titleRes()),
                supportingText = stringResource(category.descriptionRes()),
                checked = edited.isOn(category),
                onCheckedChange = { onIntent(NotificationSettingsIntent.CategoryToggled(category, it)) },
                modifier = Modifier.testTag("notifications.category.${category.wire}"),
            )
        }
    }

    Column(
        verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.s),
        modifier = Modifier.fillMaxWidth().background(AppTheme.colors.surface, AppTheme.shapes.card).padding(bottom = AppTheme.spacing.m),
    ) {
        SettingsSwitchRow(
            title = stringResource(Res.string.notifications_quiet_hours),
            supportingText = stringResource(Res.string.notifications_quiet_hours_desc),
            checked = edited.quietHours.enabled,
            onCheckedChange = { onIntent(NotificationSettingsIntent.QuietHoursToggled(it)) },
            modifier = Modifier.testTag("notifications.quiet"),
        )
        Column(
            verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.s),
            modifier = Modifier.padding(horizontal = AppTheme.spacing.m),
        ) {
            // Times stay editable while quiet hours are off (FR-016).
            TimeOfDayField(
                label = stringResource(Res.string.notifications_quiet_from),
                minutes = edited.quietHours.start.minutes,
                onChange = { onIntent(NotificationSettingsIntent.StartChosen(it)) },
                modifier = Modifier.testTag("notifications.quiet.start"),
            )
            TimeOfDayField(
                label = stringResource(Res.string.notifications_quiet_to),
                minutes = edited.quietHours.end.minutes,
                onChange = { onIntent(NotificationSettingsIntent.EndChosen(it)) },
                modifier = Modifier.testTag("notifications.quiet.end"),
            )
            if (!state.valid) {
                Text(
                    stringResource(Res.string.notifications_quiet_invalid),
                    style = AppTheme.type.caption,
                    color = AppTheme.colors.error,
                    modifier = Modifier.testTag("notifications.quiet.error").semantics { liveRegion = LiveRegionMode.Polite },
                )
            }
        }
    }

    state.timeZoneId?.let {
        Text(
            stringResource(Res.string.notifications_time_zone_note, it),
            style = AppTheme.type.caption,
            color = AppTheme.colors.caption,
            modifier = Modifier.testTag("notifications.timeZone"),
        )
    }
    Text(
        stringResource(Res.string.notifications_scope_note),
        style = AppTheme.type.caption,
        color = AppTheme.colors.caption,
        modifier = Modifier.testTag("notifications.scope"),
    )
    Column(Modifier.fillMaxWidth().background(AppTheme.colors.surface, AppTheme.shapes.card)) {
        AppListItem(
            stringResource(Res.string.notifications_phone_settings),
            leadingIcon = AppIcons.Bell,
            trailing = ListTrailing.Chevron,
            modifier = Modifier.testTag("notifications.phoneSettings"),
            onClick = { onIntent(NotificationSettingsIntent.OpenPhoneSettingsClicked) },
        )
    }
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

/** "Notifications are off on this phone" with Turn on where the system can still ask, Open settings otherwise (FR-020). */
@Composable
private fun PhoneCard(phone: PermissionStatus, onIntent: (NotificationSettingsIntent) -> Unit) {
    Column(
        verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.s),
        modifier = Modifier.fillMaxWidth().background(AppTheme.colors.surface, AppTheme.shapes.card).padding(AppTheme.spacing.m),
    ) {
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
