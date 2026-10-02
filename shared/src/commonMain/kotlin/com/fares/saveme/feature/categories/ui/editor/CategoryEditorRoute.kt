package com.fares.saveme.feature.categories.ui.editor

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
import saveme.shared.generated.resources.category_gone

/**
 * [categoryId] null creates a category of [kind]. [onRequireVerification] shows the verify prompt
 * (auth UI is not imported here, P-10.4).
 */
@Composable
fun CategoryEditorRoute(
    categoryId: String?,
    kind: String,
    onEffect: (CategoryEditorEffect) -> Unit,
    onRequireVerification: () -> Unit,
    viewModel: CategoryEditorViewModel = koinViewModel(key = "category-editor-${categoryId.orEmpty()}-$kind") {
        parametersOf(categoryId.orEmpty(), kind)
    },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is CategoryEditorEffect.ShowMessage -> scope.launch { snackbar.showSnackbar(getString(Res.string.category_gone)) }
                CategoryEditorEffect.RequireVerification -> onRequireVerification()
                else -> onEffect(effect)
            }
        }
    }
    CategoryEditorScreen(state, viewModel::onIntent, snackbar)
}
