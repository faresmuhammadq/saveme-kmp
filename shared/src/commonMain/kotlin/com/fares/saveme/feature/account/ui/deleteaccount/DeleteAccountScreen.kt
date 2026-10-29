package com.fares.saveme.feature.account.ui.deleteaccount

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fares.saveme.designsystem.component.AlertTone
import com.fares.saveme.designsystem.component.AppListItem
import com.fares.saveme.designsystem.component.CalmActionBar
import com.fares.saveme.designsystem.component.CalmCard
import com.fares.saveme.designsystem.component.CalmDivider
import com.fares.saveme.designsystem.component.CalmScreen
import com.fares.saveme.designsystem.component.DestructiveButton
import com.fares.saveme.designsystem.component.InlineAlert
import com.fares.saveme.designsystem.component.ListTrailing
import com.fares.saveme.designsystem.component.CalmPasswordField
import com.fares.saveme.designsystem.component.PrimaryButton
import com.fares.saveme.designsystem.component.SecondaryButton
import com.fares.saveme.designsystem.format.formatDate
import com.fares.saveme.designsystem.icon.AccGroup
import com.fares.saveme.designsystem.icon.WalletIcons
import com.fares.saveme.designsystem.tokens.WalletAccents
import com.fares.saveme.designsystem.icon.AccRadioOff
import com.fares.saveme.designsystem.icon.AccRadioOn
import com.fares.saveme.designsystem.icon.AppIcons
import com.fares.saveme.designsystem.theme.AppTheme
import com.fares.saveme.feature.account.domain.deletion.Candidate
import com.fares.saveme.feature.account.domain.deletion.OwnedSharedWallet
import com.fares.saveme.feature.account.domain.deletion.PlanState
import com.fares.saveme.feature.account.domain.deletion.SuccessorReason
import com.fares.saveme.feature.account.ui.AccountBanner
import com.fares.saveme.feature.account.ui.text
import com.fares.saveme.feature.auth.ui.FieldProblem
import com.fares.saveme.feature.auth.ui.text
import com.fares.saveme.feature.wallets.domain.model.WalletRole
import kotlinx.datetime.TimeZone
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import saveme.shared.generated.resources.Res
import saveme.shared.generated.resources.account_export
import saveme.shared.generated.resources.acct_delete_owners_sub
import saveme.shared.generated.resources.acct_delete_owners_title
import saveme.shared.generated.resources.acct_step_of
import saveme.shared.generated.resources.cd_back
import saveme.shared.generated.resources.action_done
import saveme.shared.generated.resources.action_retry
import saveme.shared.generated.resources.delete_after_30_days
import saveme.shared.generated.resources.delete_being_deleted
import saveme.shared.generated.resources.delete_change_owners
import saveme.shared.generated.resources.delete_choices_dropped
import saveme.shared.generated.resources.delete_confirm
import saveme.shared.generated.resources.delete_continue
import saveme.shared.generated.resources.delete_explain_body
import saveme.shared.generated.resources.delete_explain_shared
import saveme.shared.generated.resources.delete_export_first
import saveme.shared.generated.resources.delete_member_unknown
import saveme.shared.generated.resources.delete_owner_fixed
import saveme.shared.generated.resources.delete_owner_joined
import saveme.shared.generated.resources.delete_owner_suggested
import saveme.shared.generated.resources.delete_owners_next
import saveme.shared.generated.resources.delete_owners_saved
import saveme.shared.generated.resources.delete_owners_title
import saveme.shared.generated.resources.delete_password
import saveme.shared.generated.resources.delete_pending_title
import saveme.shared.generated.resources.delete_pending_unavailable
import saveme.shared.generated.resources.delete_plan_loading
import saveme.shared.generated.resources.delete_play_notice
import saveme.shared.generated.resources.delete_reason_chosen
import saveme.shared.generated.resources.delete_reason_oldest_editor
import saveme.shared.generated.resources.delete_reason_oldest_viewer
import saveme.shared.generated.resources.delete_reason_only_member
import saveme.shared.generated.resources.delete_review_none
import saveme.shared.generated.resources.delete_review_rest
import saveme.shared.generated.resources.delete_review_solo
import saveme.shared.generated.resources.delete_review_title
import saveme.shared.generated.resources.delete_scheduled_body
import saveme.shared.generated.resources.delete_successors_changed
import saveme.shared.generated.resources.delete_title
import saveme.shared.generated.resources.delete_wallet_unknown
import saveme.shared.generated.resources.error_account_locked
import saveme.shared.generated.resources.error_offline
import saveme.shared.generated.resources.error_password_incorrect
import saveme.shared.generated.resources.field_error_required
import saveme.shared.generated.resources.home_cancel_deletion
import saveme.shared.generated.resources.home_deletion_cancelled_done
import saveme.shared.generated.resources.member_unnamed
import saveme.shared.generated.resources.role_editor
import saveme.shared.generated.resources.role_owner
import saveme.shared.generated.resources.role_viewer

private const val STEP_COUNT = 4

private fun DeleteStep.index(): Int? = when (this) {
    DeleteStep.Explain -> 0
    DeleteStep.NewOwners -> 1
    DeleteStep.Review -> 2
    DeleteStep.Confirm -> 3
    else -> null
}

@Composable
fun DeleteAccountScreen(state: DeleteAccountState, onIntent: (DeleteAccountIntent) -> Unit) {
    val step = state.step
    CalmScreen(
        title = when (step) {
            DeleteStep.NewOwners -> stringResource(Res.string.acct_delete_owners_title)
            DeleteStep.Review -> stringResource(Res.string.delete_review_title)
            else -> stringResource(Res.string.delete_title)
        },
        onBack = { onIntent(DeleteAccountIntent.Back) },
        backDescription = stringResource(Res.string.cd_back),
        spacing = 12.dp,
        actions = { step.index()?.let { StepDots(it) } },
        bottomBar = { ActionBar(state, onIntent) },
    ) {
        when (step) {
            DeleteStep.Explain -> Explain(state, onIntent)
            DeleteStep.LoadingPlan -> {
                Text(
                    stringResource(Res.string.delete_plan_loading),
                    style = AppTheme.type.body,
                    color = AppTheme.colors.ink2,
                    modifier = Modifier.testTag("delete.plan.loading"),
                )
                CircularProgressIndicator(
                    modifier = Modifier.size(28.dp),
                    strokeWidth = 3.dp,
                    color = AppTheme.colors.primary,
                )
            }
            DeleteStep.NewOwners -> NewOwners(state, onIntent)
            DeleteStep.Review -> Review(state)
            DeleteStep.Confirm -> Confirm(state, onIntent)
            is DeleteStep.Scheduled -> {
                InlineAlert(
                    AlertTone.Warning,
                    stringResource(Res.string.delete_scheduled_body, formatDate(step.date, TimeZone.currentSystemDefault().id)),
                )
                val wallets = state.plan?.wallets.orEmpty()
                if (step.owners.isNotEmpty()) {
                    OwnersCard(Modifier.testTag("delete.scheduled.owners")) {
                        step.owners.forEach { owner ->
                            val wallet = wallets.firstOrNull { it.walletId == owner.walletId }
                            AppListItem(
                                headline = wallet?.walletName ?: stringResource(Res.string.delete_wallet_unknown),
                                supporting = stringResource(Res.string.delete_owner_fixed, ownerName(wallet, owner.ownerUserId)),
                            )
                        }
                    }
                }
                PlayNotice(Modifier.testTag("delete.scheduled.play"))
            }
            DeleteStep.Pending -> Pending(state, onIntent)
            is DeleteStep.Cancelled -> {
                InlineAlert(AlertTone.Info, stringResource(Res.string.home_deletion_cancelled_done))
                if (step.choicesDropped) {
                    Text(stringResource(Res.string.delete_choices_dropped), style = AppTheme.type.body, color = AppTheme.colors.ink2, modifier = Modifier.testTag("delete.cancelled.dropped"))
                }
            }
        }
    }
}

/** Four bars, filled up to the current step. */
@Composable
private fun StepDots(index: Int) {
    val description = stringResource(Res.string.acct_step_of, index + 1, STEP_COUNT)
    Row(
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        modifier = Modifier.semantics { contentDescription = description },
    ) {
        repeat(STEP_COUNT) { i ->
            Box(
                Modifier
                    .size(width = 22.dp, height = 5.dp)
                    .background(if (i <= index) AppTheme.money.over else AppTheme.colors.outlineVariant, RoundedCornerShape(9.dp)),
            )
        }
    }
}

@Composable
private fun ActionBar(state: DeleteAccountState, onIntent: (DeleteAccountIntent) -> Unit) {
    when (val step = state.step) {
        DeleteStep.Explain -> CalmActionBar {
            SecondaryButton(
                stringResource(Res.string.delete_export_first),
                { onIntent(DeleteAccountIntent.ExportClicked) },
                Modifier.testTag("delete.explain.export"),
            )
            DestructiveButton(
                stringResource(Res.string.delete_continue),
                { onIntent(DeleteAccountIntent.Continue) },
                Modifier.testTag("delete.continue"),
            )
        }
        DeleteStep.NewOwners -> if (state.plan != null) CalmActionBar {
            InkButton(stringResource(Res.string.delete_owners_next), { onIntent(DeleteAccountIntent.OwnersNext) }, Modifier.testTag("delete.owners.next"))
        }
        DeleteStep.Review -> if (state.plan != null) CalmActionBar {
            DestructiveButton(stringResource(Res.string.delete_confirm), { onIntent(DeleteAccountIntent.ReviewConfirm) }, Modifier.testTag("delete.review.submit"))
        }
        DeleteStep.Confirm -> CalmActionBar {
            DestructiveButton(
                stringResource(Res.string.delete_confirm),
                { onIntent(DeleteAccountIntent.Confirm) },
                modifier = Modifier.testTag("deleteAccount.confirm"),
                enabled = state.waitMinutes == null,
                loading = state.submitting,
            )
        }
        is DeleteStep.Scheduled, is DeleteStep.Cancelled -> CalmActionBar {
            PrimaryButton(stringResource(Res.string.action_done), { onIntent(DeleteAccountIntent.Done) })
        }
        DeleteStep.Pending -> CalmActionBar {
            if (state.canChangeOwners) {
                SecondaryButton(stringResource(Res.string.delete_change_owners), { onIntent(DeleteAccountIntent.ChangeOwners) }, Modifier.testTag("delete.pending.change"))
            }
            SecondaryButton(stringResource(Res.string.account_export), { onIntent(DeleteAccountIntent.ExportClicked) }, Modifier.testTag("delete.pending.export"))
            DestructiveButton(
                stringResource(Res.string.home_cancel_deletion),
                { onIntent(DeleteAccountIntent.CancelDeletion) },
                Modifier.testTag("delete.pending.cancel"),
                loading = state.cancelling,
            )
        }
        DeleteStep.LoadingPlan -> Unit
    }
}

/** The ink-filled main action of a step that is not destructive yet. */
@Composable
private fun InkButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .fillMaxWidth()
            .height(AppTheme.sizes.button)
            .background(AppTheme.colors.ink, RoundedCornerShape(18.dp))
            .clickable(role = Role.Button, onClick = onClick),
    ) {
        Text(text, style = AppTheme.type.body.copy(fontWeight = FontWeight.Bold), color = AppTheme.colors.background)
    }
}

@Composable
private fun PlayNotice(modifier: Modifier = Modifier) {
    InlineAlert(AlertTone.Info, stringResource(Res.string.delete_play_notice), modifier)
}

@Composable
private fun Banner(banner: DeleteBanner?, tag: String = "delete.message", onRetry: (() -> Unit)? = null) {
    banner ?: return
    InlineAlert(
        AlertTone.Error,
        when (banner) {
            DeleteBanner.Offline -> stringResource(Res.string.error_offline)
            is DeleteBanner.Error -> AccountBanner.Error(banner.code, banner.requestId).text()
        },
        Modifier.testTag(tag),
        actionLabel = onRetry?.let { stringResource(Res.string.action_retry) },
        onAction = onRetry,
    )
}

@Composable
private fun Explain(state: DeleteAccountState, onIntent: (DeleteAccountIntent) -> Unit) {
    // Explained before the password is asked for (spec 002 FR-017); extended by spec 016 FR-030.
    Text(stringResource(Res.string.delete_explain_body), style = AppTheme.type.body, color = AppTheme.colors.ink2)
    Text(stringResource(Res.string.delete_explain_shared), style = AppTheme.type.body, color = AppTheme.colors.ink2)
    PlayNotice(Modifier.testTag("delete.explain.play"))
    Banner(state.planBanner, onRetry = { onIntent(DeleteAccountIntent.Continue) })
}

@Composable
private fun NewOwners(state: DeleteAccountState, onIntent: (DeleteAccountIntent) -> Unit) {
    val plan = state.plan ?: return
    Text(stringResource(Res.string.acct_delete_owners_sub), style = AppTheme.type.body.copy(fontSize = 14.sp), color = AppTheme.colors.ink2)
    if (state.successorsChanged) {
        InlineAlert(AlertTone.Warning, stringResource(Res.string.delete_successors_changed), Modifier.testTag("delete.message"))
    }
    Banner(state.banner)
    Column(
        verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.s),
        modifier = Modifier.testTag("delete.owners"),
    ) {
        plan.wallets.forEach { wallet -> WalletOwners(wallet, state, onIntent) }
    }
}

@Composable
private fun WalletOwners(wallet: OwnedSharedWallet, state: DeleteAccountState, onIntent: (DeleteAccountIntent) -> Unit) {
    val chosen = state.ownerOf(wallet)
    CalmCard(
        radius = 22.dp,
        padding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
        modifier = Modifier.testTag("delete.owners.wallet.${wallet.walletId}"),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 4.dp)) {
            Icon(
                WalletIcons.find(wallet.walletIcon) ?: AppIcons.AccGroup,
                contentDescription = null,
                tint = WalletAccents.find(wallet.walletColor)?.tint ?: AvatarPalette[1],
                modifier = Modifier.size(22.dp),
            )
            Text(
                wallet.walletName ?: stringResource(Res.string.delete_wallet_unknown),
                style = AppTheme.type.body.copy(fontWeight = FontWeight.ExtraBold),
                color = AppTheme.colors.ink,
            )
        }
        if (wallet.choiceOffered) {
            wallet.candidates.forEachIndexed { index, candidate ->
                if (index > 0) CalmDivider()
                val selected = candidate.userId == chosen
                val name = candidateName(candidate)
                CandidateRow(
                    name = name,
                    supporting = listOfNotNull(
                        candidateSupporting(candidate),
                        reasonText(wallet.reason).takeIf { candidate.userId == wallet.successorUserId },
                    ).joinToString(" · ").ifEmpty { null },
                    suggested = candidate.userId == wallet.successorUserId,
                    selected = selected,
                    color = AvatarPalette[index % AvatarPalette.size],
                    modifier = Modifier
                        .selectable(selected = selected, role = Role.RadioButton, onClick = {
                            onIntent(DeleteAccountIntent.PickOwner(wallet.walletId, candidate.userId))
                        })
                        .testTag("delete.owners.choice.${wallet.walletId}.${candidate.userId}"),
                )
            }
        } else {
            val name = ownerName(wallet, wallet.successorUserId)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(vertical = 10.dp)) {
                Avatar(name, AvatarPalette[0])
                Column(Modifier.weight(1f)) {
                    Text(
                        stringResource(Res.string.delete_owner_fixed, name),
                        style = AppTheme.type.body.copy(fontWeight = FontWeight.Bold),
                        color = AppTheme.colors.ink,
                    )
                    reasonText(wallet.reason)?.replaceFirstChar { it.uppercase() }?.let { Text(it, style = AppTheme.type.caption, color = AppTheme.colors.ink2) }
                }
            }
        }
    }
}

private val AvatarPalette = listOf(Color(0xFF2F8566), Color(0xFF6B49A8), Color(0xFFB04A6E), Color(0xFF21777C), Color(0xFF3557C0))

@Composable
private fun Avatar(name: String, color: Color) {
    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(36.dp).background(color, CircleShape)) {
        Text(
            name.firstOrNull { it.isLetterOrDigit() }?.uppercase().orEmpty(),
            style = AppTheme.type.caption.copy(fontWeight = FontWeight.ExtraBold),
            color = Color.White,
        )
    }
}

@Composable
private fun CandidateRow(
    name: String,
    supporting: String?,
    suggested: Boolean,
    selected: Boolean,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier.fillMaxWidth().heightIn(min = 56.dp).padding(vertical = 6.dp),
    ) {
        Icon(
            if (selected) AppIcons.AccRadioOn else AppIcons.AccRadioOff,
            contentDescription = null,
            tint = if (selected) AppTheme.colors.primary else AppTheme.colors.caption,
            modifier = Modifier.size(22.dp),
        )
        Avatar(name, color)
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(name, style = AppTheme.type.body.copy(fontWeight = FontWeight.Bold), color = AppTheme.colors.ink, modifier = Modifier.weight(1f, fill = false))
                if (suggested) {
                    Text(
                        stringResource(Res.string.delete_owner_suggested),
                        style = AppTheme.type.overline.copy(fontWeight = FontWeight.Bold, letterSpacing = TextUnit.Unspecified),
                        color = AppTheme.money.positive,
                        modifier = Modifier
                            .background(AppTheme.money.positiveContainer, AppTheme.shapes.pill)
                            .padding(horizontal = 7.dp, vertical = 2.dp),
                    )
                }
            }
            supporting?.let { Text(it, style = AppTheme.type.caption, color = AppTheme.colors.ink2) }
        }
    }
}

@Composable
private fun Review(state: DeleteAccountState) {
    val plan = state.plan ?: return
    Column(verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.m), modifier = Modifier.testTag("delete.review")) {
        if (plan.wallets.isEmpty()) {
            Text(stringResource(Res.string.delete_review_none), style = AppTheme.type.body, color = AppTheme.colors.ink2)
        } else {
            OwnersCard {
                plan.wallets.forEach { wallet ->
                    AppListItem(
                        headline = wallet.walletName ?: stringResource(Res.string.delete_wallet_unknown),
                        supporting = stringResource(Res.string.delete_owner_fixed, ownerName(wallet, state.ownerOf(wallet))),
                    )
                }
            }
        }
        if (plan.wallets.any { it.choiceExpected && !it.choiceOffered }) {
            Text(stringResource(Res.string.delete_review_rest), style = AppTheme.type.body, color = AppTheme.colors.ink2)
        }
        Text(stringResource(Res.string.delete_review_solo), style = AppTheme.type.body, color = AppTheme.colors.ink2)
        Text(stringResource(Res.string.delete_after_30_days), style = AppTheme.type.body.copy(fontWeight = FontWeight.SemiBold), color = AppTheme.colors.ink)
        PlayNotice(Modifier.testTag("delete.review.play"))
    }
}

@Composable
private fun Confirm(state: DeleteAccountState, onIntent: (DeleteAccountIntent) -> Unit) {
    state.waitMinutes?.let { InlineAlert(AlertTone.Warning, pluralStringResource(Res.plurals.error_account_locked, it, it)) }
    Banner(state.banner)
    Text(stringResource(Res.string.delete_explain_body), style = AppTheme.type.body, color = AppTheme.colors.ink2)
    CalmPasswordField(
        value = state.password,
        onValueChange = { onIntent(DeleteAccountIntent.PasswordChanged(it)) },
        label = stringResource(Res.string.delete_password),
        visible = state.passwordVisible,
        onToggleVisible = { onIntent(DeleteAccountIntent.TogglePasswordVisible) },
        errorText = when (val error = state.passwordError) {
            DeletePasswordProblem.Required -> stringResource(Res.string.field_error_required)
            DeletePasswordProblem.Incorrect -> stringResource(Res.string.error_password_incorrect)
            is DeletePasswordProblem.Server -> FieldProblem.Server(error.code).text()
            null -> null
        },
        enabled = !state.submitting,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { onIntent(DeleteAccountIntent.Confirm) }),
    )
}

@Composable
private fun Pending(state: DeleteAccountState, onIntent: (DeleteAccountIntent) -> Unit) {
    val plan = state.plan
    Column(verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.m), modifier = Modifier.testTag("delete.pending")) {
        val date = state.scheduledFor
        Text(
            if (state.beingDeleted || date == null) {
                stringResource(Res.string.delete_being_deleted)
            } else {
                stringResource(Res.string.delete_pending_title, formatDate(date, TimeZone.currentSystemDefault().id))
            },
            style = AppTheme.type.title,
            color = AppTheme.colors.ink,
        )
        if (state.ownersSaved) {
            InlineAlert(AlertTone.Info, stringResource(Res.string.delete_owners_saved), Modifier.testTag("delete.message"))
        }
        Banner(state.planBanner, onRetry = { onIntent(DeleteAccountIntent.Opened) })
        Banner(state.banner)
        if (plan?.state is PlanState.Unknown) {
            Text(stringResource(Res.string.delete_pending_unavailable), style = AppTheme.type.body, color = AppTheme.colors.ink2)
        } else if (plan != null && plan.wallets.isNotEmpty()) {
            OwnersCard {
                plan.wallets.forEach { wallet ->
                    AppListItem(
                        headline = wallet.walletName ?: stringResource(Res.string.delete_wallet_unknown),
                        supporting = listOfNotNull(
                            stringResource(Res.string.delete_owner_fixed, ownerName(wallet, wallet.successorUserId)),
                            reasonText(wallet.reason),
                        ).joinToString("\n"),
                    )
                }
            }
        }
        PlayNotice(Modifier.testTag("delete.pending.play"))
    }
}

@Composable
private fun OwnersCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    CalmCard(radius = 22.dp, padding = PaddingValues(vertical = 4.dp), modifier = modifier) { content() }
}

@Composable
private fun candidateName(candidate: Candidate): String = when {
    candidate.unknown -> stringResource(Res.string.delete_member_unknown)
    else -> candidate.displayName ?: stringResource(Res.string.member_unnamed)
}

@Composable
private fun candidateSupporting(candidate: Candidate): String? {
    val role = candidate.role?.let {
        stringResource(
            when (it) {
                WalletRole.Owner -> Res.string.role_owner
                WalletRole.Editor -> Res.string.role_editor
                WalletRole.Viewer -> Res.string.role_viewer
            },
        )
    }
    val joined = candidate.joinedAt?.let { formatDate(it, TimeZone.currentSystemDefault().id) }
    return when {
        role != null && joined != null -> stringResource(Res.string.delete_owner_joined, role, joined)
        else -> role
    }
}

@Composable
private fun ownerName(wallet: OwnedSharedWallet?, userId: String): String {
    val candidate = wallet?.candidates?.firstOrNull { it.userId == userId }
    return when {
        candidate == null || candidate.unknown -> stringResource(Res.string.delete_member_unknown)
        else -> candidate.displayName ?: stringResource(Res.string.member_unnamed)
    }
}

@Composable
private fun reasonText(reason: SuccessorReason): String? = when (reason) {
    SuccessorReason.OnlyMember -> stringResource(Res.string.delete_reason_only_member)
    SuccessorReason.Chosen -> stringResource(Res.string.delete_reason_chosen)
    SuccessorReason.OldestEditor -> stringResource(Res.string.delete_reason_oldest_editor)
    SuccessorReason.OldestViewer -> stringResource(Res.string.delete_reason_oldest_viewer)
    is SuccessorReason.Unknown -> null
}

@Preview
@Composable
private fun DeleteAccountScreenPreview() {
    AppTheme { DeleteAccountScreen(DeleteAccountState(step = DeleteStep.Confirm, passwordError = DeletePasswordProblem.Incorrect)) {} }
}
