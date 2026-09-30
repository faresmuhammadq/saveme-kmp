package com.fares.saveme.feature.auth.ui.password.reset

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun ResetPasswordRoute(
    token: String,
    onEffect: (ResetPasswordEffect) -> Unit,
    viewModel: ResetPasswordViewModel = koinViewModel { parametersOf(token) },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) { viewModel.effects.collect(onEffect) }
    ResetPasswordScreen(state, viewModel::onIntent)
}
