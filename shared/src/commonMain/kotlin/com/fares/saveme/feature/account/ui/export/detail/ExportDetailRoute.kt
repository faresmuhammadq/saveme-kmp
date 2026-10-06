package com.fares.saveme.feature.account.ui.export.detail

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalUriHandler
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * Reads the export while the screen is in the foreground (`LifecycleStartEffect`, FR-010/FR-011) and hands a fresh link to
 * the system browser. The link exists here only for the call to `openUri`; a platform that cannot open it reports
 * [ExportDetailIntent.OpenFailed] (FR-016).
 */
@Composable
fun ExportDetailRoute(
    exportId: String,
    onEffect: (ExportDetailEffect) -> Unit,
    viewModel: ExportDetailViewModel = koinViewModel(key = "export-$exportId") { parametersOf(exportId) },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val uriHandler = LocalUriHandler.current
    LifecycleStartEffect(viewModel) {
        viewModel.onIntent(ExportDetailIntent.Started)
        onStopOrDispose { viewModel.onIntent(ExportDetailIntent.Stopped) }
    }
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is ExportDetailEffect.OpenDownload ->
                    if (runCatching { uriHandler.openUri(effect.link.value) }.isFailure) viewModel.onIntent(ExportDetailIntent.OpenFailed)
                else -> onEffect(effect)
            }
        }
    }
    ExportDetailScreen(state, viewModel::onIntent)
}
