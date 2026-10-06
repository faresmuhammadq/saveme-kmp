package com.fares.saveme.feature.account.ui.export.request

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun ExportRequestRoute(
    walletId: String?,
    onEffect: (ExportRequestEffect) -> Unit,
    viewModel: ExportRequestViewModel = koinViewModel(key = "export-request-$walletId") { parametersOf(walletId) },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) { viewModel.effects.collect(onEffect) }
    ExportRequestScreen(state, viewModel::onIntent)
}
