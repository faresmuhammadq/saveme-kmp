package com.fares.saveme.feature.notifications.ui.open

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import com.fares.saveme.feature.notifications.domain.PushRoute
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun NotificationOpenRoute(
    route: PushRoute,
    nonce: Long,
    onEffect: (NotificationOpenEffect) -> Unit,
    viewModel: NotificationOpenViewModel = koinViewModel(key = "push-open-$nonce") { parametersOf(route) },
) {
    LaunchedEffect(viewModel) {
        viewModel.onIntent(NotificationOpenIntent.Started)
        viewModel.effects.collect(onEffect)
    }
    NotificationOpenScreen(viewModel::onIntent)
}
