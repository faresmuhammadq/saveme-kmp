package com.fares.saveme.feature.transactions.ui.editor

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import saveme.shared.generated.resources.Res
import saveme.shared.generated.resources.tx_gone
import saveme.shared.generated.resources.tx_reason_no_right

/** Which transaction to open and from which wallet's side; one object so Koin never confuses two strings. */
data class TransactionEditorArgs(val transactionId: String?, val walletId: String?)

/**
 * [transactionId] null records a new transaction in [walletId] (or the default wallet); otherwise the
 * transaction is shown and edited from [walletId]'s side. [onRequireVerification] shows the verify
 * prompt (auth UI is not imported here, P-10.4).
 */
@Composable
fun TransactionEditorRoute(
    transactionId: String?,
    walletId: String?,
    onEffect: (TransactionEditorEffect) -> Unit,
    onRequireVerification: () -> Unit,
    viewModel: TransactionEditorViewModel = koinViewModel(key = "tx-editor-${transactionId.orEmpty()}-${walletId.orEmpty()}") {
        parametersOf(TransactionEditorArgs(transactionId, walletId))
    },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is TransactionEditorEffect.ShowMessage -> scope.launch {
                    snackbar.showSnackbar(
                        getString(if (effect.message == EditorMessage.Gone) Res.string.tx_gone else Res.string.tx_reason_no_right),
                    )
                }
                TransactionEditorEffect.RequireVerification -> onRequireVerification()
                else -> onEffect(effect)
            }
        }
    }
    TransactionEditorScreen(state, viewModel::onIntent, snackbar)
}
