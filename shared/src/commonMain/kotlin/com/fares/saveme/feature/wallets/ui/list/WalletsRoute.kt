package com.fares.saveme.feature.wallets.ui.list

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun WalletsRoute(
    onEffect: (WalletsEffect) -> Unit,
    onRequireVerification: () -> Unit,
    viewModel: WalletsViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) {
        viewModel.onIntent(WalletsIntent.Opened)
        viewModel.effects.collect { effect ->
            when (effect) {
                WalletsEffect.RequireVerification -> onRequireVerification()
                else -> onEffect(effect)
            }
        }
    }
    WalletsScreen(state, viewModel::onIntent)
}
