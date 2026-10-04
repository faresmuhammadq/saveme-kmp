package com.fares.saveme.feature.budgets.ui.editor

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
import saveme.shared.generated.resources.budgets_gone

/**
 * [budgetId] null creates a budget. [onRequireVerification] shows the verify prompt (auth UI is not imported here, P-10.4).
 */
@Composable
fun BudgetEditorRoute(
    budgetId: String?,
    onEffect: (BudgetEditorEffect) -> Unit,
    onRequireVerification: () -> Unit,
    viewModel: BudgetEditorViewModel = koinViewModel(key = "budget-editor-${budgetId.orEmpty()}") { parametersOf(budgetId.orEmpty()) },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is BudgetEditorEffect.ShowMessage -> scope.launch { snackbar.showSnackbar(getString(Res.string.budgets_gone)) }
                BudgetEditorEffect.RequireVerification -> onRequireVerification()
                else -> onEffect(effect)
            }
        }
    }
    BudgetEditorScreen(state, viewModel::onIntent, snackbar)
}
