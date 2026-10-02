package com.fares.saveme.feature.transactions.ui.list

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

/**
 * One wallet's transactions. The screen reads the wallet again when it opens and when it comes
 * back to the foreground (FR-018); only the open screen knows which wallet that is, so the Route
 * forwards the lifecycle as the `Foregrounded` intent (analysis A2).
 */
@Composable
fun TransactionsRoute(
    walletId: String,
    onEffect: (TransactionsEffect) -> Unit,
    onRequireVerification: () -> Unit,
    viewModel: TransactionsViewModel = koinViewModel(key = "transactions-$walletId") { parametersOf(walletId) },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var started by remember { mutableStateOf(false) }
    LifecycleStartEffect(viewModel) {
        // The first start is the opening, which sends `Opened` below.
        if (started) viewModel.onIntent(TransactionsIntent.Foregrounded) else started = true
        onStopOrDispose { }
    }
    LaunchedEffect(viewModel) {
        viewModel.onIntent(TransactionsIntent.Opened)
        viewModel.effects.collect { effect ->
            when (effect) {
                TransactionsEffect.RequireVerification -> onRequireVerification()
                else -> onEffect(effect)
            }
        }
    }
    TransactionsScreen(state, viewModel::onIntent)
}
