package com.fares.saveme.feature.sharing.ui.invitation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/** The key of the store is constant: it never contains the secret (P-09.2); each back stack entry has its own store anyway. */
@Composable
fun InvitationRoute(
    token: String,
    onEffect: (InvitationEffect) -> Unit,
    viewModel: InvitationViewModel = koinViewModel(key = "invitation") { parametersOf(token) },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) {
        viewModel.onIntent(InvitationIntent.Opened)
        viewModel.effects.collect(onEffect)
    }
    InvitationScreen(state, viewModel::onIntent)
}
