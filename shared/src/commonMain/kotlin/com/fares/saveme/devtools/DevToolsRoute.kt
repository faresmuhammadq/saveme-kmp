package com.fares.saveme.devtools

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun DevToolsRoute(
    onEffect: (DevToolsEffect) -> Unit,
    viewModel: DevToolsViewModel = koinViewModel(),
) {
    LaunchedEffect(viewModel) { viewModel.effects.collect(onEffect) }
    DevToolsScreen(viewModel::onIntent)
}
