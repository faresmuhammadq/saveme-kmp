package com.fares.saveme.feature.wallets.ui.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.window.Dialog
import com.fares.saveme.designsystem.component.AlertTone
import com.fares.saveme.designsystem.component.AppAlertDialogCard
import com.fares.saveme.designsystem.component.AppBottomSheet
import com.fares.saveme.designsystem.component.AppListItem
import com.fares.saveme.designsystem.component.AppScreen
import com.fares.saveme.designsystem.component.BalanceHero
import com.fares.saveme.designsystem.component.DestructiveButton
import com.fares.saveme.designsystem.component.InlineAlert
import com.fares.saveme.designsystem.component.ListTrailing
import com.fares.saveme.designsystem.component.MemberRole
import com.fares.saveme.designsystem.component.MemberRow
import com.fares.saveme.designsystem.component.PrimaryButton
import com.fares.saveme.designsystem.component.SecondaryButton
import com.fares.saveme.designsystem.component.Skeleton
import com.fares.saveme.designsystem.component.formatMoney
import com.fares.saveme.designsystem.theme.AppTheme
import com.fares.saveme.designsystem.tokens.WalletAccents
import com.fares.saveme.feature.wallets.domain.model.Member
import com.fares.saveme.feature.wallets.domain.model.RefusalKind
import com.fares.saveme.feature.wallets.domain.model.RefusalReason
import com.fares.saveme.feature.wallets.domain.model.SyncState
import com.fares.saveme.feature.wallets.domain.model.Wallet
import com.fares.saveme.feature.wallets.domain.model.WalletDraft
import com.fares.saveme.feature.wallets.domain.model.WalletRole
import com.fares.saveme.feature.wallets.domain.model.WalletView
import com.fares.saveme.feature.wallets.ui.WalletsBackBar
import com.fares.saveme.feature.wallets.ui.label
import com.fares.saveme.feature.wallets.ui.text
import org.jetbrains.compose.resources.stringResource
import saveme.shared.generated.resources.Res
import saveme.shared.generated.resources.action_accept_deletion
import saveme.shared.generated.resources.action_cancel
import saveme.shared.generated.resources.action_delete_anyway
import saveme.shared.generated.resources.action_discard
import saveme.shared.generated.resources.action_keep_wallet
import saveme.shared.generated.resources.action_retry
import saveme.shared.generated.resources.cd_wallet_color_0
import saveme.shared.generated.resources.cd_wallet_color_1
import saveme.shared.generated.resources.cd_wallet_color_2
import saveme.shared.generated.resources.cd_wallet_color_3
import saveme.shared.generated.resources.cd_wallet_color_4
import saveme.shared.generated.resources.cd_wallet_color_5
import saveme.shared.generated.resources.cd_wallet_color_6
import saveme.shared.generated.resources.cd_wallet_color_7
import saveme.shared.generated.resources.cd_wallet_icon_bank
import saveme.shared.generated.resources.cd_wallet_icon_card
import saveme.shared.generated.resources.cd_wallet_icon_cash
import saveme.shared.generated.resources.cd_wallet_icon_investment
import saveme.shared.generated.resources.cd_wallet_icon_loan
import saveme.shared.generated.resources.cd_wallet_icon_savings
import saveme.shared.generated.resources.activity_title
import saveme.shared.generated.resources.member_unnamed
import saveme.shared.generated.resources.members_manage
import saveme.shared.generated.resources.members_pending_count
import saveme.shared.generated.resources.members_see
import saveme.shared.generated.resources.viewer_note
import saveme.shared.generated.resources.viewer_note_plan
import saveme.shared.generated.resources.wallet_joining
import saveme.shared.generated.resources.role_editor
import saveme.shared.generated.resources.role_owner
import saveme.shared.generated.resources.role_viewer
import saveme.shared.generated.resources.sync_deleted_elsewhere
import saveme.shared.generated.resources.wallet_archive
import saveme.shared.generated.resources.wallet_archive_body
import saveme.shared.generated.resources.wallet_archive_title
import saveme.shared.generated.resources.wallet_archived_pill
import saveme.shared.generated.resources.wallet_balance
import saveme.shared.generated.resources.wallet_clash_body
import saveme.shared.generated.resources.wallet_delete
import saveme.shared.generated.resources.wallet_delete_body
import saveme.shared.generated.resources.wallet_delete_title
import saveme.shared.generated.resources.wallet_edit
import saveme.shared.generated.resources.wallet_export
import saveme.shared.generated.resources.wallet_field_amount_owed
import saveme.shared.generated.resources.wallet_field_color
import saveme.shared.generated.resources.wallet_field_currency
import saveme.shared.generated.resources.wallet_field_icon
import saveme.shared.generated.resources.wallet_field_name
import saveme.shared.generated.resources.wallet_field_opening_balance
import saveme.shared.generated.resources.wallet_keep_mine
import saveme.shared.generated.resources.wallet_members
import saveme.shared.generated.resources.recurring_open
import saveme.shared.generated.resources.reports_wallet_action
import saveme.shared.generated.resources.transactions_add
import saveme.shared.generated.resources.wallet_transactions
import saveme.shared.generated.resources.wallet_none
import saveme.shared.generated.resources.wallet_other_device
import saveme.shared.generated.resources.wallet_status_clash
import saveme.shared.generated.resources.wallet_status_waiting
import saveme.shared.generated.resources.wallet_this_phone
import saveme.shared.generated.resources.wallet_transfer
import saveme.shared.generated.resources.wallet_transfer_body
import saveme.shared.generated.resources.wallet_transfer_confirm
import saveme.shared.generated.resources.wallet_type_currency_caption
import saveme.shared.generated.resources.wallet_use_theirs
import saveme.shared.generated.resources.wallets_archived

@Composable
fun WalletDetailScreen(
    state: WalletDetailState,
    onIntent: (WalletDetailIntent) -> Unit,
    snackbar: SnackbarHostState = remember { SnackbarHostState() },
) {
    val view = state.wallet
    AppScreen(
        snackbarHostState = snackbar,
        topBar = { WalletsBackBar(view?.shown?.name.orEmpty()) { onIntent(WalletDetailIntent.Back) } },
    ) {
        if (view == null) {
            if (state.joining) Joining(state, onIntent) else Skeleton(Modifier.fillMaxWidth().padding(vertical = AppTheme.spacing.xl))
            return@AppScreen
        }
        StatusArea(view, state.busy, onIntent)
        Balance(view)
        if (view.archived) ArchivedPill()
        if (state.isViewer) {
            InlineAlert(
                AlertTone.Info,
                stringResource(if (state.planLimited) Res.string.viewer_note_plan else Res.string.viewer_note),
                Modifier.testTag("walletDetail.viewerNote"),
            )
        }
        TransactionsSection(state, onIntent)
        MembersSection(state, onIntent)
        if (state.canEdit) OwnerActions(state, view, onIntent)
    }
    Confirmations(state, onIntent)
}

/** The wallet's transactions and, for an owner or editor of an active wallet, "Add transaction" (spec 008). */
@Composable
private fun TransactionsSection(state: WalletDetailState, onIntent: (WalletDetailIntent) -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().background(AppTheme.colors.surface, AppTheme.shapes.card)) {
        AppListItem(
            stringResource(Res.string.wallet_transactions),
            modifier = Modifier.testTag("walletDetail.transactions"),
            trailing = ListTrailing.Chevron,
            onClick = { onIntent(WalletDetailIntent.TransactionsClicked) },
        )
        AppListItem(
            stringResource(Res.string.recurring_open),
            modifier = Modifier.testTag("walletDetail.recurring"),
            trailing = ListTrailing.Chevron,
            onClick = { onIntent(WalletDetailIntent.RecurringClicked) },
        )
        AppListItem(
            stringResource(Res.string.reports_wallet_action),
            modifier = Modifier.testTag("wallet.report"),
            trailing = ListTrailing.Chevron,
            onClick = { onIntent(WalletDetailIntent.ReportClicked) },
        )
        if (state.canAddTransaction) {
            AppListItem(
                stringResource(Res.string.transactions_add),
                modifier = Modifier.testTag("walletDetail.addTransaction"),
                trailing = ListTrailing.Chevron,
                onClick = { onIntent(WalletDetailIntent.AddTransactionClicked) },
            )
        }
    }
}

@Composable
private fun Balance(view: WalletView) {
    val liability = view.isLiability
    val shownAmount = if (liability && view.displayedBalanceMinor <= 0) -view.displayedBalanceMinor else view.displayedBalanceMinor
    BalanceHero(
        label = stringResource(if (liability) Res.string.wallet_field_amount_owed else Res.string.wallet_balance),
        amountMinor = shownAmount,
        currency = view.shown.currency,
        modifier = Modifier.testTag("walletDetail.balance"),
        caption = stringResource(Res.string.wallet_type_currency_caption, view.shown.walletType.label(), view.shown.currency),
    )
}

@Composable
private fun ArchivedPill() {
    Text(
        stringResource(Res.string.wallet_archived_pill),
        style = AppTheme.type.caption,
        color = AppTheme.colors.ink2,
        modifier = Modifier
            .background(AppTheme.colors.sunken, AppTheme.shapes.pill)
            .padding(horizontal = AppTheme.spacing.s, vertical = AppTheme.spacing.xxs)
            .testTag("walletDetail.archived"),
    )
}

/** The person joined and the wallet is on its way (spec 013 FR-013): never "gone" meanwhile. */
@Composable
private fun Joining(state: WalletDetailState, onIntent: (WalletDetailIntent) -> Unit) {
    Text(
        stringResource(Res.string.wallet_joining),
        style = AppTheme.type.title,
        color = AppTheme.colors.ink,
        modifier = Modifier.padding(vertical = AppTheme.spacing.xl).testTag("walletDetail.joining"),
    )
    if (state.joiningFailed) {
        PrimaryButton(stringResource(Res.string.action_retry), { onIntent(WalletDetailIntent.JoiningRetryClicked) }, Modifier.testTag("walletDetail.joiningRetry"))
    } else {
        Skeleton(Modifier.fillMaxWidth().padding(vertical = AppTheme.spacing.xs))
    }
}

@Composable
private fun MembersSection(state: WalletDetailState, onIntent: (WalletDetailIntent) -> Unit) {
    if (state.members.isEmpty()) return
    Text(
        stringResource(Res.string.wallet_members),
        style = AppTheme.type.title,
        color = AppTheme.colors.ink,
        modifier = Modifier.semantics { heading() },
    )
    Column(
        modifier = Modifier.fillMaxWidth().background(AppTheme.colors.surface, AppTheme.shapes.card),
    ) {
        state.members.forEachIndexed { index, member ->
            val name = member.displayName ?: stringResource(Res.string.member_unnamed)
            MemberRow(
                name = name,
                initial = name,
                role = when (member.role) {
                    WalletRole.Owner -> MemberRole.Owner
                    WalletRole.Editor -> MemberRole.Editor
                    WalletRole.Viewer -> MemberRole.Viewer
                },
                isYou = member.isYou,
                modifier = Modifier.testTag("walletDetail.member.$index"),
            )
        }
        AppListItem(
            stringResource(if (state.canEdit) Res.string.members_manage else Res.string.members_see),
            modifier = Modifier.testTag("walletDetail.manageMembers"),
            supporting = if (state.pendingInvitations > 0) stringResource(Res.string.members_pending_count, state.pendingInvitations) else null,
            trailing = ListTrailing.Chevron,
            onClick = { onIntent(WalletDetailIntent.MembersClicked) },
        )
        AppListItem(
            stringResource(Res.string.activity_title),
            modifier = Modifier.testTag("walletDetail.activity"),
            trailing = ListTrailing.Chevron,
            onClick = { onIntent(WalletDetailIntent.ActivityClicked) },
        )
        if (state.exportable) {
            AppListItem(
                stringResource(Res.string.wallet_export),
                modifier = Modifier.testTag("walletDetail.export"),
                trailing = ListTrailing.Chevron,
                onClick = { onIntent(WalletDetailIntent.ExportClicked) },
            )
        }
    }
}

@Composable
private fun OwnerActions(state: WalletDetailState, view: WalletView, onIntent: (WalletDetailIntent) -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().background(AppTheme.colors.surface, AppTheme.shapes.card),
    ) {
        AppListItem(
            stringResource(Res.string.wallet_edit),
            modifier = Modifier.testTag("walletDetail.edit"),
            trailing = ListTrailing.Chevron,
            onClick = { onIntent(WalletDetailIntent.EditClicked) },
        )
        if (!view.archived) {
            AppListItem(
                stringResource(Res.string.wallet_archive),
                modifier = Modifier.testTag("walletDetail.archive"),
                trailing = ListTrailing.Chevron,
                onClick = { onIntent(WalletDetailIntent.ArchiveClicked) },
            )
        }
        if (state.canTransfer) {
            AppListItem(
                stringResource(Res.string.wallet_transfer),
                modifier = Modifier.testTag("walletDetail.transfer"),
                trailing = ListTrailing.Chevron,
                onClick = { onIntent(WalletDetailIntent.TransferClicked) },
            )
        }
    }
    DestructiveButton(
        stringResource(Res.string.wallet_delete),
        { onIntent(WalletDetailIntent.DeleteClicked) },
        modifier = Modifier.testTag("walletDetail.delete"),
    )
}

@Composable
private fun StatusArea(view: WalletView, busy: Boolean, onIntent: (WalletDetailIntent) -> Unit) {
    when (val sync = view.syncState) {
        SyncState.None -> if (view.hasPendingPostings) {
            InlineAlert(AlertTone.Info, stringResource(Res.string.wallet_status_waiting), Modifier.testTag("walletDetail.status"))
        }
        SyncState.Waiting -> InlineAlert(AlertTone.Info, stringResource(Res.string.wallet_status_waiting), Modifier.testTag("walletDetail.status"))
        is SyncState.Clash -> ClashCard(view, sync, busy, onIntent)
        is SyncState.Refused -> Column(verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.xs)) {
            InlineAlert(AlertTone.Error, sync.reason.text(), Modifier.testTag("walletDetail.status"))
            if (sync.reason.canRetryUnchanged) {
                PrimaryButton(
                    stringResource(Res.string.action_retry), { onIntent(WalletDetailIntent.TryAgain) },
                    Modifier.testTag("walletDetail.tryAgain"), enabled = !busy,
                )
            }
            SecondaryButton(
                stringResource(Res.string.action_discard), { onIntent(WalletDetailIntent.Discard) },
                Modifier.testTag("walletDetail.discard"), enabled = !busy,
            )
        }
    }
}

/** Both versions side by side, and the person chooses; nothing is merged (FR-016, FR-017). */
@Composable
private fun ClashCard(view: WalletView, clash: SyncState.Clash, busy: Boolean, onIntent: (WalletDetailIntent) -> Unit) {
    Column(
        verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.xs),
        modifier = Modifier.fillMaxWidth().background(AppTheme.colors.surface, AppTheme.shapes.card).padding(AppTheme.spacing.m),
    ) {
        if (clash.deletedElsewhere) {
            InlineAlert(AlertTone.Warning, stringResource(Res.string.sync_deleted_elsewhere), Modifier.testTag("walletDetail.status"))
            PrimaryButton(
                stringResource(Res.string.action_accept_deletion), { onIntent(WalletDetailIntent.AcceptDeletion) },
                Modifier.testTag("walletDetail.acceptDeletion"), loading = busy,
            )
            return@Column
        }
        InlineAlert(AlertTone.Warning, stringResource(Res.string.wallet_status_clash), Modifier.testTag("walletDetail.status"))
        Text(stringResource(Res.string.wallet_clash_body), style = AppTheme.type.body, color = AppTheme.colors.ink2)
        val theirs = clash.theirs
        if (theirs != null && !view.pendingDelete) {
            clashLines(view.shown, theirs).forEach { line -> ClashLineView(line) }
        }
        if (view.pendingDelete) {
            PrimaryButton(
                stringResource(Res.string.action_delete_anyway), { onIntent(WalletDetailIntent.KeepMine) },
                Modifier.testTag("walletDetail.deleteAnyway"), loading = busy,
            )
            SecondaryButton(
                stringResource(Res.string.action_keep_wallet), { onIntent(WalletDetailIntent.UseTheirs) },
                Modifier.testTag("walletDetail.keepWallet"), enabled = !busy,
            )
        } else {
            PrimaryButton(
                stringResource(Res.string.wallet_keep_mine), { onIntent(WalletDetailIntent.KeepMine) },
                Modifier.testTag("walletDetail.keepMine"), loading = busy,
            )
            SecondaryButton(
                stringResource(Res.string.wallet_use_theirs), { onIntent(WalletDetailIntent.UseTheirs) },
                Modifier.testTag("walletDetail.useTheirs"), enabled = !busy,
            )
        }
    }
}

@Composable
private fun ClashLineView(line: ClashLine) {
    val label = when (line.field) {
        ClashField.Name -> Res.string.wallet_field_name
        ClashField.OpeningBalance -> Res.string.wallet_field_opening_balance
        ClashField.Currency -> Res.string.wallet_field_currency
        ClashField.Icon -> Res.string.wallet_field_icon
        ClashField.Color -> Res.string.wallet_field_color
        ClashField.Archived -> Res.string.wallets_archived
    }
    Text(stringResource(label), style = AppTheme.type.label, color = AppTheme.colors.ink)
    Text(stringResource(Res.string.wallet_this_phone, line.mine.text()), style = AppTheme.type.body, color = AppTheme.colors.ink2)
    Text(stringResource(Res.string.wallet_other_device, line.theirs.text()), style = AppTheme.type.body, color = AppTheme.colors.ink2)
}

@Composable
private fun ClashValue.text(): String = when (this) {
    is ClashValue.Text -> value ?: stringResource(Res.string.wallet_none)
    is ClashValue.Money -> formatMoney(minor, currency)
    is ClashValue.Icon -> key?.let { if (com.fares.saveme.designsystem.icon.WalletIcons.find(it) != null) iconName(it) else it } ?: stringResource(Res.string.wallet_none)
    is ClashValue.Accent -> key?.let { k -> WalletAccents.all.indexOfFirst { it.key.equals(k, true) }.takeIf { it >= 0 }?.let { colorName(it) } ?: k }
        ?: stringResource(Res.string.wallet_none)
    is ClashValue.Archived -> stringResource(if (archived) Res.string.wallets_archived else Res.string.wallet_none)
}

@Composable
private fun Confirmations(state: WalletDetailState, onIntent: (WalletDetailIntent) -> Unit) {
    when (val confirm = state.confirm) {
        DetailConfirm.None -> Unit
        is DetailConfirm.Transfer -> AppBottomSheet(
            onDismiss = { onIntent(WalletDetailIntent.DismissConfirm) },
            title = stringResource(Res.string.wallet_transfer),
        ) { TransferCandidates(confirm.candidates, onIntent) }
        else -> Dialog(onDismissRequest = { if (!state.busy) onIntent(WalletDetailIntent.DismissConfirm) }) {
            ConfirmCard(state, onIntent)
        }
    }
}

/** The confirmation dialog's surface (also shown by the screenshot tests, which cannot see a dialog window). */
@Composable
internal fun ConfirmCard(state: WalletDetailState, onIntent: (WalletDetailIntent) -> Unit) {
    val name = state.wallet?.shown?.name.orEmpty()
    val dismiss = { onIntent(WalletDetailIntent.DismissConfirm) }
    val cancelLabel = stringResource(Res.string.action_cancel)
    val confirmTag = Modifier.testTag("walletDetail.confirm")
    val cancelTag = Modifier.testTag("walletDetail.cancel")
    when (val confirm = state.confirm) {
        DetailConfirm.Archive -> AppAlertDialogCard(
            title = stringResource(Res.string.wallet_archive_title, name),
            text = stringResource(Res.string.wallet_archive_body),
            confirmLabel = stringResource(Res.string.wallet_archive),
            onConfirm = { onIntent(WalletDetailIntent.ConfirmArchive) },
            dismissLabel = cancelLabel, onDismiss = dismiss,
            confirmLoading = state.busy, confirmModifier = confirmTag, dismissModifier = cancelTag,
        )
        DetailConfirm.Delete -> AppAlertDialogCard(
            title = stringResource(Res.string.wallet_delete_title, name),
            text = stringResource(Res.string.wallet_delete_body),
            confirmLabel = stringResource(Res.string.wallet_delete),
            onConfirm = { onIntent(WalletDetailIntent.ConfirmDelete) },
            dismissLabel = cancelLabel, onDismiss = dismiss, destructive = true,
            confirmLoading = state.busy, confirmModifier = confirmTag, dismissModifier = cancelTag,
        )
        is DetailConfirm.TransferTo -> AppAlertDialogCard(
            title = stringResource(Res.string.wallet_transfer),
            text = stringResource(Res.string.wallet_transfer_body, confirm.member.displayName ?: stringResource(Res.string.member_unnamed)),
            confirmLabel = stringResource(Res.string.wallet_transfer_confirm),
            onConfirm = { onIntent(WalletDetailIntent.ConfirmTransfer(confirm.member.userId)) },
            dismissLabel = cancelLabel, onDismiss = dismiss,
            confirmLoading = state.busy, confirmModifier = confirmTag, dismissModifier = cancelTag,
        )
        else -> Unit
    }
}

/** The members who could become the owner, as the sheet lists them. */
@Composable
internal fun TransferCandidates(candidates: List<Member>, onIntent: (WalletDetailIntent) -> Unit) {
    candidates.forEachIndexed { index, member ->
        AppListItem(
            headline = member.displayName ?: stringResource(Res.string.member_unnamed),
            supporting = stringResource(member.role.labelRes()),
            modifier = Modifier.testTag("walletDetail.transferCandidate.$index"),
            onClick = { onIntent(WalletDetailIntent.TransferTargetPicked(member.userId)) },
        )
    }
}

private fun WalletRole.labelRes() = when (this) {
    WalletRole.Owner -> Res.string.role_owner
    WalletRole.Editor -> Res.string.role_editor
    WalletRole.Viewer -> Res.string.role_viewer
}

@Composable
private fun iconName(key: String): String = stringResource(
    when (key) {
        "cash" -> Res.string.cd_wallet_icon_cash
        "bank" -> Res.string.cd_wallet_icon_bank
        "savings" -> Res.string.cd_wallet_icon_savings
        "card" -> Res.string.cd_wallet_icon_card
        "loan" -> Res.string.cd_wallet_icon_loan
        else -> Res.string.cd_wallet_icon_investment
    },
)

@Composable
private fun colorName(index: Int): String = stringResource(
    when (index) {
        0 -> Res.string.cd_wallet_color_0
        1 -> Res.string.cd_wallet_color_1
        2 -> Res.string.cd_wallet_color_2
        3 -> Res.string.cd_wallet_color_3
        4 -> Res.string.cd_wallet_color_4
        5 -> Res.string.cd_wallet_color_5
        6 -> Res.string.cd_wallet_color_6
        else -> Res.string.cd_wallet_color_7
    },
)

@Preview
@Composable
private fun WalletDetailScreenPreview() {
    val wallet = Wallet(
        "w1", "cash", "Pocket", "EGP", 25_000, 25_000, "cash", "#2C7A5B", null, false, WalletRole.Owner, 3,
        kotlin.time.Instant.parse("2026-10-01T10:00:00Z"),
    )
    AppTheme {
        WalletDetailScreen(
            WalletDetailState(
                walletId = "w1",
                wallet = WalletView(wallet, null, false, SyncState.None, 25_000),
                members = listOf(Member("u1", "Sara", WalletRole.Owner, true)),
            ),
            {},
        )
    }
}
