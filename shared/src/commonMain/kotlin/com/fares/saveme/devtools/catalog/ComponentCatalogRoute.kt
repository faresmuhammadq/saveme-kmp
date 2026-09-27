package com.fares.saveme.devtools.catalog

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun ComponentCatalogRoute(
    onEffect: (ComponentCatalogEffect) -> Unit,
    viewModel: ComponentCatalogViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) { viewModel.effects.collect(onEffect) }
    ComponentCatalogScreen(state, viewModel::onIntent)
}
