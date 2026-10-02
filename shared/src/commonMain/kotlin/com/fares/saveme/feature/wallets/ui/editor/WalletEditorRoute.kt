package com.fares.saveme.feature.wallets.ui.editor

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/** [walletId] null creates a wallet. [onRequireVerification] shows the verify prompt (auth UI is not imported here, P-10.4). */
@Composable
fun WalletEditorRoute(
    walletId: String?,
    onEffect: (WalletEditorEffect) -> Unit,
    onRequireVerification: () -> Unit,
    viewModel: WalletEditorViewModel = koinViewModel(key = "wallet-editor-${walletId.orEmpty()}") { parametersOf(walletId.orEmpty()) },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                WalletEditorEffect.RequireVerification -> onRequireVerification()
                else -> onEffect(effect)
            }
        }
    }
    WalletEditorScreen(state, viewModel::onIntent)
}
