package com.fares.saveme.feature.auth.ui.home

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.jetbrains.compose.resources.getString
import org.koin.compose.viewmodel.koinViewModel
import saveme.shared.generated.resources.Res
import saveme.shared.generated.resources.error_generic
import saveme.shared.generated.resources.error_offline
import saveme.shared.generated.resources.home_deletion_cancelled_done

@Composable
fun HomeRoute(
    onEffect: (HomeEffect) -> Unit,
    viewModel: HomeViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is HomeEffect.ShowMessage -> snackbar.showSnackbar(
                    getString(
                        when (effect.message) {
                            HomeMessage.DeletionCancelled -> Res.string.home_deletion_cancelled_done
                            HomeMessage.Offline -> Res.string.error_offline
                            HomeMessage.Failed -> Res.string.error_generic
                        },
                    ),
                )
                else -> onEffect(effect)
            }
        }
    }
    HomeScreen(state, viewModel::onIntent, snackbar)
}
