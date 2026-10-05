package com.fares.saveme.feature.sharing.ui.invite

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fares.saveme.feature.sharing.domain.LinkSharer
import org.jetbrains.compose.resources.getString
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import saveme.shared.generated.resources.Res
import saveme.shared.generated.resources.invite_copied

@Composable
fun InviteRoute(
    walletId: String,
    replacing: String?,
    onEffect: (InviteEffect) -> Unit,
    onRequireVerification: () -> Unit,
    viewModel: InviteViewModel = koinViewModel(key = "invite-$walletId-${replacing.orEmpty()}") { parametersOf(InviteArgs(walletId, replacing)) },
    sharer: LinkSharer = koinInject(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                // The text carries a one-time link: straight to the platform, never stored, logged or shown (P-09.2).
                is InviteEffect.Share -> sharer.share(effect.text)
                is InviteEffect.Copy -> sharer.copy(effect.text)
                InviteEffect.Copied -> snackbar.showSnackbar(getString(Res.string.invite_copied))
                InviteEffect.ShowVerifyPrompt -> onRequireVerification()
                else -> onEffect(effect)
            }
        }
    }
    InviteScreen(state, viewModel::onIntent, snackbar)
}
