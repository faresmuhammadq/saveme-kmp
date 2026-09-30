package com.fares.saveme.feature.auth.ui.verify

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun VerifyEmailRoute(
    token: String,
    onEffect: (VerifyEmailEffect) -> Unit,
    viewModel: VerifyEmailViewModel = koinViewModel { parametersOf(token) },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) { viewModel.onIntent(VerifyEmailIntent.Started) }
    LaunchedEffect(viewModel) { viewModel.effects.collect(onEffect) }
    VerifyEmailScreen(state, viewModel::onIntent)
}
