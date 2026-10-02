package com.fares.saveme.feature.billing.ui.plan

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun PlanRoute(
    onEffect: (PlanEffect) -> Unit,
    viewModel: PlanViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) {
        viewModel.onIntent(PlanIntent.Opened)
        viewModel.effects.collect(onEffect)
    }
    PlanScreen(state, viewModel::onIntent)
}
