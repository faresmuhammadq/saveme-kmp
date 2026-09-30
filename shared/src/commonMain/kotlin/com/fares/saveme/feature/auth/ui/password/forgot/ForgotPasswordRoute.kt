package com.fares.saveme.feature.auth.ui.password.forgot

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun ForgotPasswordRoute(
    prefillEmail: String?,
    onEffect: (ForgotPasswordEffect) -> Unit,
    viewModel: ForgotPasswordViewModel = koinViewModel { parametersOf(prefillEmail.orEmpty()) },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) { viewModel.effects.collect(onEffect) }
    ForgotPasswordScreen(state, viewModel::onIntent)
}
