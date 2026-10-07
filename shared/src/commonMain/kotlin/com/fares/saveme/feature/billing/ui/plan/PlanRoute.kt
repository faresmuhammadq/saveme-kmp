package com.fares.saveme.feature.billing.ui.plan

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalUriHandler
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.jetbrains.compose.resources.getString
import org.koin.compose.viewmodel.koinViewModel
import saveme.shared.generated.resources.Res
import saveme.shared.generated.resources.plan_activated
import saveme.shared.generated.resources.plan_play_open_failed

@Composable
fun PlanRoute(
    onEffect: (PlanEffect) -> Unit,
    viewModel: PlanViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val uriHandler = LocalUriHandler.current
    LaunchedEffect(viewModel) {
        viewModel.onIntent(PlanIntent.Opened)
        viewModel.effects.collect { effect ->
            when (effect) {
                is PlanEffect.OpenUrl ->
                    // A plain link into Google Play's subscription centre; nothing there is started by the app (FR-054).
                    try {
                        uriHandler.openUri(effect.url)
                    } catch (e: Exception) {
                        snackbar.showSnackbar(getString(Res.string.plan_play_open_failed))
                    }
                is PlanEffect.ShowActivated -> snackbar.showSnackbar(getString(Res.string.plan_activated, effect.planName))
                PlanEffect.NavigateBack, PlanEffect.OpenRedeem -> onEffect(effect)
            }
        }
    }
    PlanScreen(state, viewModel::onIntent, snackbar)
}
