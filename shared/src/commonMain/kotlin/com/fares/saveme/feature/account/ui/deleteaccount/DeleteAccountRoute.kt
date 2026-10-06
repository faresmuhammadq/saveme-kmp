package com.fares.saveme.feature.account.ui.deleteaccount

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun DeleteAccountRoute(
    onEffect: (DeleteAccountEffect) -> Unit,
    viewModel: DeleteAccountViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) {
        viewModel.onIntent(DeleteAccountIntent.Opened)
        viewModel.effects.collect(onEffect)
    }
    DeleteAccountScreen(state, viewModel::onIntent)
}
