package com.fares.saveme.feature.account.ui.deleteaccount

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.fares.saveme.designsystem.component.AlertTone
import com.fares.saveme.designsystem.component.AppListItem
import com.fares.saveme.designsystem.component.AppScreen
import com.fares.saveme.designsystem.component.DestructiveButton
import com.fares.saveme.designsystem.component.InlineAlert
import com.fares.saveme.designsystem.component.ListTrailing
import com.fares.saveme.designsystem.component.PasswordField
import com.fares.saveme.designsystem.component.PrimaryButton
import com.fares.saveme.designsystem.component.SecondaryButton
import com.fares.saveme.designsystem.format.formatDate
import com.fares.saveme.designsystem.icon.AppIcons
import com.fares.saveme.designsystem.theme.AppTheme
import com.fares.saveme.feature.account.domain.deletion.Candidate
import com.fares.saveme.feature.account.domain.deletion.OwnedSharedWallet
import com.fares.saveme.feature.account.domain.deletion.PlanState
import com.fares.saveme.feature.account.domain.deletion.SuccessorReason
import com.fares.saveme.feature.account.ui.AccountBanner
import com.fares.saveme.feature.account.ui.BackBar
import com.fares.saveme.feature.account.ui.text
import com.fares.saveme.feature.auth.ui.FieldProblem
import com.fares.saveme.feature.auth.ui.text
import com.fares.saveme.feature.wallets.domain.model.WalletRole
import kotlinx.datetime.TimeZone
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import saveme.shared.generated.resources.Res
import saveme.shared.generated.resources.account_export
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

@Composable
fun DeleteAccountScreen(state: DeleteAccountState, onIntent: (DeleteAccountIntent) -> Unit) {
    AppScreen(topBar = { BackBar(stringResource(Res.string.delete_title)) { onIntent(DeleteAccountIntent.Back) } }) {
        when (val step = state.step) {
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
            DeleteStep.Review -> Review(state, onIntent)
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
                PrimaryButton(stringResource(Res.string.action_done), { onIntent(DeleteAccountIntent.Done) })
            }
            DeleteStep.Pending -> Pending(state, onIntent)
            is DeleteStep.Cancelled -> {
                InlineAlert(AlertTone.Info, stringResource(Res.string.home_deletion_cancelled_done))
                if (step.choicesDropped) {
                    Text(stringResource(Res.string.delete_choices_dropped), style = AppTheme.type.body, color = AppTheme.colors.ink2, modifier = Modifier.testTag("delete.cancelled.dropped"))
                }
                PrimaryButton(stringResource(Res.string.action_done), { onIntent(DeleteAccountIntent.Done) })
            }
        }
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
    Text(stringResource(Res.string.delete_explain_body), style = AppTheme.type.body, color = AppTheme.colors.ink)
    Text(stringResource(Res.string.delete_explain_shared), style = AppTheme.type.body, color = AppTheme.colors.ink)
    PlayNotice(Modifier.testTag("delete.explain.play"))
    Banner(state.planBanner, onRetry = { onIntent(DeleteAccountIntent.Continue) })
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

@Composable
private fun NewOwners(state: DeleteAccountState, onIntent: (DeleteAccountIntent) -> Unit) {
    val plan = state.plan ?: return
    Text(stringResource(Res.string.delete_owners_title), style = AppTheme.type.title, color = AppTheme.colors.ink)
    if (state.successorsChanged) {
        InlineAlert(AlertTone.Warning, stringResource(Res.string.delete_successors_changed), Modifier.testTag("delete.message"))
    }
    Banner(state.banner)
    Column(
        verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.m),
        modifier = Modifier.testTag("delete.owners"),
    ) {
        plan.wallets.forEach { wallet -> WalletOwners(wallet, state, onIntent) }
    }
    PrimaryButton(stringResource(Res.string.delete_owners_next), { onIntent(DeleteAccountIntent.OwnersNext) }, Modifier.testTag("delete.owners.next"))
}

@Composable
private fun WalletOwners(wallet: OwnedSharedWallet, state: DeleteAccountState, onIntent: (DeleteAccountIntent) -> Unit) {
    val chosen = state.ownerOf(wallet)
    OwnersCard(Modifier.testTag("delete.owners.wallet.${wallet.walletId}")) {
        Text(
            wallet.walletName ?: stringResource(Res.string.delete_wallet_unknown),
            style = AppTheme.type.itemTitle,
            color = AppTheme.colors.ink,
            modifier = Modifier.padding(horizontal = AppTheme.spacing.m, vertical = AppTheme.spacing.xs),
        )
        if (wallet.choiceOffered) {
            wallet.candidates.forEach { candidate ->
                val selected = candidate.userId == chosen
                AppListItem(
                    headline = candidateName(candidate),
                    supporting = candidateSupporting(candidate, suggested = candidate.userId == wallet.successorUserId),
                    trailing = if (selected) {
                        ListTrailing.Custom { Icon(AppIcons.Check, contentDescription = null, tint = AppTheme.colors.primary) }
                    } else {
                        ListTrailing.None
                    },
                    modifier = Modifier
                        .selectable(selected = selected, role = Role.RadioButton, onClick = {
                            onIntent(DeleteAccountIntent.PickOwner(wallet.walletId, candidate.userId))
                        })
                        .testTag("delete.owners.choice.${wallet.walletId}.${candidate.userId}"),
                )
            }
        } else {
            AppListItem(
                headline = stringResource(Res.string.delete_owner_fixed, ownerName(wallet, wallet.successorUserId)),
                supporting = reasonText(wallet.reason),
            )
        }
    }
}

@Composable
private fun Review(state: DeleteAccountState, onIntent: (DeleteAccountIntent) -> Unit) {
    val plan = state.plan ?: return
    Column(verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.m), modifier = Modifier.testTag("delete.review")) {
        Text(stringResource(Res.string.delete_review_title), style = AppTheme.type.title, color = AppTheme.colors.ink)
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
        Text(stringResource(Res.string.delete_after_30_days), style = AppTheme.type.body, color = AppTheme.colors.ink)
        PlayNotice(Modifier.testTag("delete.review.play"))
    }
    DestructiveButton(stringResource(Res.string.delete_confirm), { onIntent(DeleteAccountIntent.ReviewConfirm) }, Modifier.testTag("delete.review.submit"))
}

@Composable
private fun Confirm(state: DeleteAccountState, onIntent: (DeleteAccountIntent) -> Unit) {
    state.waitMinutes?.let { InlineAlert(AlertTone.Warning, pluralStringResource(Res.plurals.error_account_locked, it, it)) }
    Banner(state.banner)
    Text(stringResource(Res.string.delete_explain_body), style = AppTheme.type.body, color = AppTheme.colors.ink2)
    PasswordField(
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
    DestructiveButton(
        stringResource(Res.string.delete_confirm),
        { onIntent(DeleteAccountIntent.Confirm) },
        modifier = Modifier.testTag("deleteAccount.confirm"),
        enabled = state.waitMinutes == null,
        loading = state.submitting,
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
    }
    if (state.canChangeOwners) {
        SecondaryButton(stringResource(Res.string.delete_change_owners), { onIntent(DeleteAccountIntent.ChangeOwners) }, Modifier.testTag("delete.pending.change"))
    }
    SecondaryButton(stringResource(Res.string.account_export), { onIntent(DeleteAccountIntent.ExportClicked) }, Modifier.testTag("delete.pending.export"))
    PlayNotice(Modifier.testTag("delete.pending.play"))
    DestructiveButton(
        stringResource(Res.string.home_cancel_deletion),
        { onIntent(DeleteAccountIntent.CancelDeletion) },
        Modifier.testTag("delete.pending.cancel"),
        loading = state.cancelling,
    )
}

@Composable
private fun OwnersCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Column(modifier.fillMaxWidth().background(AppTheme.colors.surface, AppTheme.shapes.card)) { content() }
}

@Composable
private fun candidateName(candidate: Candidate): String = when {
    candidate.unknown -> stringResource(Res.string.delete_member_unknown)
    else -> candidate.displayName ?: stringResource(Res.string.member_unnamed)
}

@Composable
private fun candidateSupporting(candidate: Candidate, suggested: Boolean): String? {
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
    val line = when {
        role != null && joined != null -> stringResource(Res.string.delete_owner_joined, role, joined)
        else -> role
    }
    return listOfNotNull(stringResource(Res.string.delete_owner_suggested).takeIf { suggested }, line).joinToString("\n").ifEmpty { null }
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
