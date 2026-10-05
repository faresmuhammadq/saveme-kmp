package com.fares.saveme.feature.reports.ui.reports

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/** [walletId] is null for the cross-wallet report; the store is made for that scope (spec 014 R-09). */
@Composable
fun ReportsRoute(
    walletId: String?,
    onEffect: (ReportsEffect) -> Unit,
    viewModel: ReportsViewModel = koinViewModel(key = "reports:${walletId.orEmpty()}") { parametersOf(walletId.orEmpty()) },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) {
        viewModel.onIntent(ReportsIntent.Opened)
        viewModel.effects.collect { effect -> onEffect(effect) }
    }
    ReportsScreen(state, viewModel::onIntent)
}
