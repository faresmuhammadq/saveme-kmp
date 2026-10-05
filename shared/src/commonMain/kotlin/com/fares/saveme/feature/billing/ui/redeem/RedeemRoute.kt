package com.fares.saveme.feature.billing.ui.redeem

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fares.saveme.core.platform.SecureWindow
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun RedeemRoute(
    onEffect: (RedeemEffect) -> Unit,
    viewModel: RedeemViewModel = koinViewModel(),
) {
    // The whole screen stays out of screenshots, recordings and recents while the code can be on it (FR-029).
    SecureWindow()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val focus = remember { FocusRequester() }
    LaunchedEffect(viewModel) {
        viewModel.onIntent(RedeemIntent.Opened)
        viewModel.effects.collect(onEffect)
    }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    RedeemScreen(state, viewModel::onIntent, focus)
}
