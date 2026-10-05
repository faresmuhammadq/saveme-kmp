package com.fares.saveme.feature.reports.ui.categories

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/** [walletId] is null for the cross-wallet report; [kind] is `expense` or `income`. */
@Composable
fun ReportCategoriesRoute(
    walletId: String?,
    kind: String,
    onEffect: (ReportCategoriesEffect) -> Unit,
    viewModel: ReportCategoriesViewModel = koinViewModel(key = "report-categories:${walletId.orEmpty()}") { parametersOf(walletId.orEmpty(), kind) },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) { viewModel.effects.collect { effect -> onEffect(effect) } }
    ReportCategoriesScreen(state, viewModel::onIntent)
}
