package com.fares.saveme.feature.notifications.ui.settings

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fares.saveme.feature.notifications.ui.rememberNotificationPermissionRequester
import com.fares.saveme.feature.notifications.ui.rememberOpenAppNotificationSettings
import org.jetbrains.compose.resources.getString
import org.koin.compose.viewmodel.koinViewModel
import saveme.shared.generated.resources.Res
import saveme.shared.generated.resources.notifications_saved

@Composable
fun NotificationSettingsRoute(
    onEffect: (NotificationSettingsEffect) -> Unit,
    viewModel: NotificationSettingsViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val requester = rememberNotificationPermissionRequester()
    val openPhoneSettings = rememberOpenAppNotificationSettings()
    // Re-read every time the app resumes (the requester's status follows the lifecycle): the person may have changed it in settings (FR-012).
    val status = requester.status()
    LaunchedEffect(status) { viewModel.onIntent(NotificationSettingsIntent.Resumed(status)) }
    LaunchedEffect(viewModel) {
        viewModel.onIntent(NotificationSettingsIntent.Opened)
        viewModel.effects.collect { effect ->
            when (effect) {
                NotificationSettingsEffect.RequestPermission ->
                    requester.request { granted -> viewModel.onIntent(NotificationSettingsIntent.PermissionResult(granted)) }
                NotificationSettingsEffect.OpenPhoneSettings -> openPhoneSettings()
                NotificationSettingsEffect.ShowSaved -> snackbar.showSnackbar(getString(Res.string.notifications_saved))
                NotificationSettingsEffect.NavigateBack -> onEffect(effect)
            }
        }
    }
    NotificationSettingsScreen(state, viewModel::onIntent, snackbar)
}
