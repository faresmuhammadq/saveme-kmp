package com.fares.saveme.feature.categories.ui.list

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun CategoriesRoute(
    onEffect: (CategoriesEffect) -> Unit,
    onRequireVerification: () -> Unit,
    viewModel: CategoriesViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) {
        viewModel.onIntent(CategoriesIntent.Opened)
        viewModel.effects.collect { effect ->
            when (effect) {
                CategoriesEffect.RequireVerification -> onRequireVerification()
                else -> onEffect(effect)
            }
        }
    }
    CategoriesScreen(state, viewModel::onIntent)
}
