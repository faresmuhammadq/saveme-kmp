package com.fares.saveme.devtools.push

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun SimulatePushRoute(
    onEffect: (SimulatePushEffect) -> Unit,
    viewModel: SimulatePushViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) {
        viewModel.onIntent(SimulatePushIntent.Opened)
        viewModel.effects.collect(onEffect)
    }
    SimulatePushScreen(state, viewModel::onIntent)
}
