package com.fares.saveme.feature.sharing.ui.activity

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun ActivityRoute(
    walletId: String,
    onEffect: (ActivityEffect) -> Unit,
    viewModel: ActivityViewModel = koinViewModel(key = "wallet-activity-$walletId") { parametersOf(walletId) },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) {
        viewModel.onIntent(ActivityIntent.Opened)
        viewModel.effects.collect(onEffect)
    }
    ActivityScreen(state, viewModel::onIntent)
}
