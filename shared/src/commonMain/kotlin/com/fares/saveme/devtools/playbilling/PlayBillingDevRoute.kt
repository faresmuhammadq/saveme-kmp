package com.fares.saveme.devtools.playbilling

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun PlayBillingDevRoute(
    onEffect: (PlayBillingDevEffect) -> Unit,
    viewModel: PlayBillingDevViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) { viewModel.effects.collect(onEffect) }
    PlayBillingDevScreen(state, viewModel::onIntent)
}
