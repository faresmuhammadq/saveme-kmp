package com.fares.saveme.feature.auth.ui.update

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalUriHandler
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun UpdateRequiredRoute(
    minimumVersion: String?,
    viewModel: UpdateRequiredViewModel = koinViewModel(key = "update-$minimumVersion") { parametersOf(minimumVersion.orEmpty()) },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val uriHandler = LocalUriHandler.current
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is UpdateRequiredEffect.OpenStore -> runCatching { uriHandler.openUri(effect.url) }
            }
        }
    }
    UpdateRequiredScreen(state, viewModel::onIntent)
}
