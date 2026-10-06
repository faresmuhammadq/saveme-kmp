package com.fares.saveme.feature.account.ui.export.list

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun ExportsRoute(
    onEffect: (ExportsEffect) -> Unit,
    viewModel: ExportsViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) {
        viewModel.onIntent(ExportsIntent.Opened)
        viewModel.effects.collect(onEffect)
    }
    ExportsScreen(state, viewModel::onIntent)
}
