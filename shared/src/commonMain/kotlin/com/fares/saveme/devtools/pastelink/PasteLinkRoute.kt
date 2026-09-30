package com.fares.saveme.devtools.pastelink

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fares.saveme.navigation.DeepLinkBus
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun PasteLinkRoute(
    onEffect: (PasteLinkEffect) -> Unit,
    viewModel: PasteLinkViewModel = koinViewModel(),
    deepLinks: DeepLinkBus = koinInject(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is PasteLinkEffect.SubmitLink -> deepLinks.submit(effect.uri)
                PasteLinkEffect.NavigateBack -> onEffect(effect)
            }
        }
    }
    PasteLinkScreen(state, viewModel::onIntent)
}
