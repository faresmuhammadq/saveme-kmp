package com.fares.saveme.feature.budgets.ui.list

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun BudgetsRoute(
    onEffect: (BudgetsEffect) -> Unit,
    onRequireVerification: () -> Unit,
    viewModel: BudgetsViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) {
        viewModel.onIntent(BudgetsIntent.Opened)
        viewModel.effects.collect { effect ->
            when (effect) {
                BudgetsEffect.RequireVerification -> onRequireVerification()
                else -> onEffect(effect)
            }
        }
    }
    BudgetsScreen(state, viewModel::onIntent)
}
