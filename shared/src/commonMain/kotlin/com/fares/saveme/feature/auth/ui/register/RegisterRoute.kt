package com.fares.saveme.feature.auth.ui.register

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun RegisterRoute(
    prefillEmail: String?,
    onEffect: (RegisterEffect) -> Unit,
    viewModel: RegisterViewModel = koinViewModel { parametersOf(prefillEmail.orEmpty()) },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) { viewModel.effects.collect(onEffect) }
    RegisterScreen(state, viewModel::onIntent)
}
