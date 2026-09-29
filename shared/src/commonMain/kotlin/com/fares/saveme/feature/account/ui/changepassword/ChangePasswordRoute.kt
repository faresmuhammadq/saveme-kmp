package com.fares.saveme.feature.account.ui.changepassword

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun ChangePasswordRoute(
    onEffect: (ChangePasswordEffect) -> Unit,
    viewModel: ChangePasswordViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) { viewModel.effects.collect(onEffect) }
    ChangePasswordScreen(state, viewModel::onIntent)
}
