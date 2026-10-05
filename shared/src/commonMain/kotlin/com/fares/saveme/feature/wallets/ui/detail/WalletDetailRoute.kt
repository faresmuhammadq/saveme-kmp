package com.fares.saveme.feature.wallets.ui.detail

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getString
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import saveme.shared.generated.resources.Res
import saveme.shared.generated.resources.access_lost
import saveme.shared.generated.resources.wallet_refusal_not_owner
import saveme.shared.generated.resources.wallet_transfer_changed
import saveme.shared.generated.resources.wallet_transfer_failed
import saveme.shared.generated.resources.wallet_transfer_not_member
import saveme.shared.generated.resources.wallet_transfer_offline

@Composable
fun WalletDetailRoute(
    walletId: String,
    onEffect: (WalletDetailEffect) -> Unit,
    onRequireVerification: () -> Unit,
    viewModel: WalletDetailViewModel = koinViewModel(key = "wallet-detail-$walletId") { parametersOf(walletId) },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is WalletDetailEffect.ShowMessage -> scope.launch { snackbar.showSnackbar(getString(effect.message.res())) }
                WalletDetailEffect.RequireVerification -> onRequireVerification()
                else -> onEffect(effect)
            }
        }
    }
    WalletDetailScreen(state, viewModel::onIntent, snackbar)
}

internal fun DetailMessage.res(): StringResource = when (this) {
    DetailMessage.Gone -> Res.string.access_lost
    DetailMessage.NotOwner -> Res.string.wallet_refusal_not_owner
    DetailMessage.TransferOffline -> Res.string.wallet_transfer_offline
    DetailMessage.NoLongerMember -> Res.string.wallet_transfer_not_member
    DetailMessage.Changed -> Res.string.wallet_transfer_changed
    DetailMessage.TransferFailed -> Res.string.wallet_transfer_failed
}
