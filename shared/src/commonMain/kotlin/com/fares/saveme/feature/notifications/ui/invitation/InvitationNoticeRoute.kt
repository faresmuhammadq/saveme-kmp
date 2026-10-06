package com.fares.saveme.feature.notifications.ui.invitation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun InvitationNoticeRoute(
    onEffect: (InvitationNoticeEffect) -> Unit,
    viewModel: InvitationNoticeViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) { viewModel.effects.collect(onEffect) }
    InvitationNoticeScreen(state, viewModel::onIntent)
}
