package com.fares.saveme.feature.recurring.ui.list

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/** The recurring screen of [walletId], or of every wallet when null; reads again when it opens and returns to the foreground. */
@Composable
fun RecurringRoute(
    walletId: String?,
    onEffect: (RecurringEffect) -> Unit,
    onRequireVerification: () -> Unit,
    viewModel: RecurringViewModel = koinViewModel(key = "recurring-${walletId.orEmpty()}") { parametersOf(RecurringArgs(walletId)) },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var started by remember { mutableStateOf(false) }
    LifecycleStartEffect(viewModel) {
        if (started) viewModel.onIntent(RecurringIntent.Foregrounded) else started = true
        onStopOrDispose { }
    }
    LaunchedEffect(viewModel) {
        viewModel.onIntent(RecurringIntent.Opened)
        viewModel.effects.collect { effect ->
            when (effect) {
                RecurringEffect.RequireVerification -> onRequireVerification()
                else -> onEffect(effect)
            }
        }
    }
    RecurringScreen(state, viewModel::onIntent)
}
