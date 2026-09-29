package com.fares.saveme.feature.account.ui.sessions

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.jetbrains.compose.resources.getString
import org.koin.compose.viewmodel.koinViewModel
import saveme.shared.generated.resources.Res
import saveme.shared.generated.resources.sessions_ended_done
import saveme.shared.generated.resources.sessions_ended_others_done

@Composable
fun DeviceSessionsRoute(
    onEffect: (DeviceSessionsEffect) -> Unit,
    viewModel: DeviceSessionsViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is DeviceSessionsEffect.Ended -> snackbar.showSnackbar(
                    getString(if (effect.others) Res.string.sessions_ended_others_done else Res.string.sessions_ended_done),
                )
                DeviceSessionsEffect.NavigateBack -> onEffect(effect)
            }
        }
    }
    DeviceSessionsScreen(state, viewModel::onIntent, snackbar)
}
