package com.fares.saveme.feature.server

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun ServerAddressRoute(
    onEffect: (ServerAddressEffect) -> Unit,
    viewModel: ServerAddressViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) { viewModel.effects.collect(onEffect) }
    ServerAddressScreen(state, viewModel::onIntent)
}
