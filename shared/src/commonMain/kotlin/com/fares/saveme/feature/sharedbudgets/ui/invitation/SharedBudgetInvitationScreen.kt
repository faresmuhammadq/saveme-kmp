package com.fares.saveme.feature.sharedbudgets.ui.invitation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fares.saveme.designsystem.component.AppTextButton
import com.fares.saveme.designsystem.component.CalmCard
import com.fares.saveme.designsystem.component.CalmScreen
import com.fares.saveme.designsystem.component.CalmTitle
import com.fares.saveme.designsystem.component.PrimaryButton
import com.fares.saveme.designsystem.component.SecondaryButton
import com.fares.saveme.designsystem.component.Skeleton
import com.fares.saveme.designsystem.theme.AppTheme
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import saveme.shared.generated.resources.Res
import saveme.shared.generated.resources.cd_back
import saveme.shared.generated.resources.sb_inv_already
import saveme.shared.generated.resources.sb_inv_decline
import saveme.shared.generated.resources.sb_inv_failed
import saveme.shared.generated.resources.sb_inv_invalid
import saveme.shared.generated.resources.sb_inv_join
import saveme.shared.generated.resources.sb_inv_not_now
import saveme.shared.generated.resources.sb_inv_offline
import saveme.shared.generated.resources.sb_inv_owner
import saveme.shared.generated.resources.sb_inv_plan_full
import saveme.shared.generated.resources.sb_inv_register
import saveme.shared.generated.resources.sb_inv_retry
import saveme.shared.generated.resources.sb_inv_see
import saveme.shared.generated.resources.sb_inv_sign_in
import saveme.shared.generated.resources.sb_inv_signed_out
import saveme.shared.generated.resources.sb_inv_title
import saveme.shared.generated.resources.sb_inv_too_many
import saveme.shared.generated.resources.sb_inv_unavailable
import saveme.shared.generated.resources.sb_member_count

@Composable
fun SharedBudgetInvitationRoute(
    token: String,
    onEffect: (SharedBudgetInvitationEffect) -> Unit,
    viewModel: SharedBudgetInvitationViewModel = koinViewModel(key = "shared-budget-invitation") { parametersOf(token) },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) { viewModel.effects.collect(onEffect) }
    SharedBudgetInvitationScreen(state, viewModel::onIntent)
}

@Composable
fun SharedBudgetInvitationScreen(state: SharedBudgetInvitationState, onIntent: (SharedBudgetInvitationIntent) -> Unit) {
    CalmScreen(
        title = null,
        onBack = { onIntent(SharedBudgetInvitationIntent.Close) },
        backDescription = stringResource(Res.string.cd_back),
        titleSlot = { CalmTitle(stringResource(Res.string.sb_inv_title)) },
    ) {
        when (val stage = state.stage) {
            InvitationStage.Loading -> Skeleton(Modifier.fillMaxWidth().height(140.dp), AppTheme.shapes.hero)
            InvitationStage.SignedOut -> {
                Message(Res.string.sb_inv_signed_out.let { stringResource(it) }, "sharedBudgetInvitation.signedOut")
                PrimaryButton(stringResource(Res.string.sb_inv_sign_in), { onIntent(SharedBudgetInvitationIntent.SignInClicked) }, modifier = Modifier.fillMaxWidth().testTag("sharedBudgetInvitation.signIn"))
                SecondaryButton(stringResource(Res.string.sb_inv_register), { onIntent(SharedBudgetInvitationIntent.RegisterClicked) }, modifier = Modifier.fillMaxWidth().testTag("sharedBudgetInvitation.register"))
            }
            is InvitationStage.Preview -> {
                val preview = stage.preview
                CalmCard(modifier = Modifier.testTag("sharedBudgetInvitation.preview")) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(preview.name, style = AppTheme.type.itemTitle, color = AppTheme.colors.ink)
                        preview.ownerDisplayName?.let { Text(stringResource(Res.string.sb_inv_owner, it), style = AppTheme.type.body, color = AppTheme.colors.ink2) }
                        Text(pluralStringResource(Res.plurals.sb_member_count, preview.memberCount, preview.memberCount), style = AppTheme.type.caption, color = AppTheme.colors.caption)
                    }
                }
                Message(stringResource(Res.string.sb_inv_see), "sharedBudgetInvitation.see")
                if (preview.alreadyMember) Message(stringResource(Res.string.sb_inv_already), "sharedBudgetInvitation.already")
                PrimaryButton(stringResource(Res.string.sb_inv_join), { onIntent(SharedBudgetInvitationIntent.JoinClicked) }, modifier = Modifier.fillMaxWidth().testTag("sharedBudgetInvitation.join"), enabled = !state.busy, loading = state.busy)
                SecondaryButton(stringResource(Res.string.sb_inv_decline), { onIntent(SharedBudgetInvitationIntent.DeclineClicked) }, modifier = Modifier.fillMaxWidth().testTag("sharedBudgetInvitation.decline"), enabled = !state.busy)
                AppTextButton(stringResource(Res.string.sb_inv_not_now), { onIntent(SharedBudgetInvitationIntent.NotNowClicked) }, enabled = !state.busy)
            }
            InvitationStage.InvalidLink -> Message(stringResource(Res.string.sb_inv_invalid), "sharedBudgetInvitation.invalid")
            InvitationStage.Unavailable -> Message(stringResource(Res.string.sb_inv_unavailable), "sharedBudgetInvitation.unavailable")
            InvitationStage.PlanFull -> Message(stringResource(Res.string.sb_inv_plan_full), "sharedBudgetInvitation.planFull")
            is InvitationStage.TooMany -> Message(stringResource(Res.string.sb_inv_too_many, stage.minutes), "sharedBudgetInvitation.tooMany")
            InvitationStage.Offline, InvitationStage.Failed -> {
                Message(stringResource(if (stage == InvitationStage.Offline) Res.string.sb_inv_offline else Res.string.sb_inv_failed), "sharedBudgetInvitation.retryNote")
                SecondaryButton(stringResource(Res.string.sb_inv_retry), { onIntent(SharedBudgetInvitationIntent.Retry) }, modifier = Modifier.fillMaxWidth().testTag("sharedBudgetInvitation.retry"))
            }
        }
    }
}

@Composable
private fun Message(text: String, tag: String) {
    Text(text, style = AppTheme.type.body, color = AppTheme.colors.ink2, modifier = Modifier.testTag(tag))
}
