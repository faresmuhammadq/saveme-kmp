package com.fares.saveme.feature.auth.ui.checkinbox

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun CheckInboxRoute(
    email: String,
    kind: CheckInboxKind,
    onEffect: (CheckInboxEffect) -> Unit,
    viewModel: CheckInboxViewModel = koinViewModel { parametersOf(email, kind) },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) { viewModel.effects.collect(onEffect) }
    CheckInboxScreen(state, viewModel::onIntent)
}
