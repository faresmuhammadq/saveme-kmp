package com.fares.saveme.feature.account.ui.profile

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.jetbrains.compose.resources.getString
import org.koin.compose.viewmodel.koinViewModel
import saveme.shared.generated.resources.Res
import saveme.shared.generated.resources.profile_saved

@Composable
fun ProfileRoute(
    onEffect: (ProfileEffect) -> Unit,
    viewModel: ProfileViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                ProfileEffect.Saved -> snackbar.showSnackbar(getString(Res.string.profile_saved))
                ProfileEffect.NavigateBack -> onEffect(effect)
            }
        }
    }
    ProfileScreen(state, viewModel::onIntent, snackbar)
}
