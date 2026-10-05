package com.fares.saveme.feature.sharing.ui.members

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
import saveme.shared.generated.resources.gate_not_included
import saveme.shared.generated.resources.invite_copied
import saveme.shared.generated.resources.invite_saved_offline
import saveme.shared.generated.resources.invite_sent
import saveme.shared.generated.resources.leave_owner_cannot
import saveme.shared.generated.resources.member_changed
import saveme.shared.generated.resources.member_failed
import saveme.shared.generated.resources.member_failed_plain
import saveme.shared.generated.resources.member_gone
import saveme.shared.generated.resources.member_only_owner
import saveme.shared.generated.resources.member_owner_role
import saveme.shared.generated.resources.member_plan_full
import saveme.shared.generated.resources.member_plan_other
import saveme.shared.generated.resources.needs_connection

@Composable
fun MembersRoute(
    walletId: String,
    onEffect: (MembersEffect) -> Unit,
    onRequireVerification: () -> Unit,
    viewModel: MembersViewModel = koinViewModel(key = "wallet-members-$walletId") { parametersOf(walletId) },
    sharer: LinkSharer = koinInject(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                // The text carries a one-time link: straight to the platform, never stored, logged or shown (P-09.2).
                is MembersEffect.Share -> sharer.share(effect.text)
                is MembersEffect.Copy -> sharer.copy(effect.text)
                is MembersEffect.ShowMessage -> snackbar.showSnackbar(effect.message.text())
                MembersEffect.ShowVerifyPrompt -> onRequireVerification()
                else -> onEffect(effect)
            }
        }
    }
    MembersScreen(state, viewModel::onIntent, snackbar)
}

private suspend fun MembersMessage.text(): String = when (this) {
    MembersMessage.Changed -> getString(Res.string.member_changed)
    MembersMessage.OnlyOwner -> getString(Res.string.member_only_owner)
    MembersMessage.Gone -> getString(Res.string.member_gone)
    MembersMessage.OwnerRole -> getString(Res.string.member_owner_role)
    MembersMessage.Offline -> getString(Res.string.needs_connection)
    MembersMessage.PlanSharing -> getString(Res.string.gate_not_included)
    MembersMessage.PlanFull -> getString(Res.string.member_plan_full)
    MembersMessage.PlanOther -> getString(Res.string.member_plan_other)
    MembersMessage.Copied -> getString(Res.string.invite_copied)
    MembersMessage.OwnerCannotLeave -> getString(Res.string.leave_owner_cannot)
    MembersMessage.InviteSent -> getString(Res.string.invite_sent)
    MembersMessage.InviteSaved -> getString(Res.string.invite_saved_offline)
    is MembersMessage.Failed -> if (requestId != null) getString(Res.string.member_failed, requestId) else getString(Res.string.member_failed_plain)
}
