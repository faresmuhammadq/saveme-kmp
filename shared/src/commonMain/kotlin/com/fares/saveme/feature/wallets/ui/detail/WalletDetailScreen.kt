package com.fares.saveme.feature.wallets.ui.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fares.saveme.designsystem.component.CalmCard
import com.fares.saveme.designsystem.component.CalmDivider
import com.fares.saveme.designsystem.component.CalmScreen
import com.fares.saveme.designsystem.component.ltr
import com.fares.saveme.designsystem.icon.AppIcons
import com.fares.saveme.designsystem.icon.WlcGroup
import com.fares.saveme.designsystem.icon.WlcMoreDots
import com.fares.saveme.designsystem.icon.WlcHistory
import com.fares.saveme.designsystem.icon.WlcPending
import com.fares.saveme.designsystem.icon.WlcPersonAdd
import com.fares.saveme.designsystem.icon.WlcRecurring
import saveme.shared.generated.resources.cd_back
import saveme.shared.generated.resources.invite_action
import saveme.shared.generated.resources.member_you
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.fares.saveme.designsystem.component.CalmCircleButton
import com.fares.saveme.designsystem.component.MoneyKind
import com.fares.saveme.designsystem.component.MoneyText
import com.fares.saveme.designsystem.format.formatDayMonth
import com.fares.saveme.designsystem.icon.CategoryIcons
import com.fares.saveme.designsystem.icon.WlcSwap
import com.fares.saveme.feature.wallets.domain.model.RecentAuthor
import com.fares.saveme.feature.wallets.domain.model.RecentDirection
import com.fares.saveme.feature.wallets.domain.model.RecentKind
import com.fares.saveme.feature.wallets.domain.model.RecentTransaction
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import saveme.shared.generated.resources.transactions_today
import saveme.shared.generated.resources.tx_added_by
import saveme.shared.generated.resources.tx_added_by_former
import saveme.shared.generated.resources.tx_added_by_other
import saveme.shared.generated.resources.tx_another_wallet
import saveme.shared.generated.resources.tx_kind_expense
import saveme.shared.generated.resources.tx_kind_income
import saveme.shared.generated.resources.tx_kind_transfer
import saveme.shared.generated.resources.tx_kind_unknown
import saveme.shared.generated.resources.tx_transfer_from
import saveme.shared.generated.resources.tx_transfer_to
import saveme.shared.generated.resources.wlc_add
import saveme.shared.generated.resources.wlc_all_transactions
import saveme.shared.generated.resources.wlc_added_by_you
import saveme.shared.generated.resources.wlc_invited_count
import saveme.shared.generated.resources.wlc_manage
import saveme.shared.generated.resources.wlc_pending
import saveme.shared.generated.resources.wlc_recent
import saveme.shared.generated.resources.wlc_recent_empty
import saveme.shared.generated.resources.wlc_see
import saveme.shared.generated.resources.wlc_wallet_settings
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
import com.fares.saveme.designsystem.component.DestructiveButton
import com.fares.saveme.designsystem.component.InlineAlert
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
import saveme.shared.generated.resources.members_pending_count
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
    var menuOpen by rememberSaveable { mutableStateOf(false) }
    val hasMenu = state.canEdit || state.exportable
    CalmScreen(
        title = null,
        snackbarHostState = snackbar,
        onBack = { onIntent(WalletDetailIntent.Back) },
        backDescription = stringResource(Res.string.cd_back),
        actions = {
            if (view != null && hasMenu) {
                CalmCircleButton(
                    icon = AppIcons.WlcMoreDots,
                    contentDescription = stringResource(Res.string.wlc_wallet_settings),
                    onClick = { menuOpen = !menuOpen },
                    modifier = Modifier.testTag("walletDetail.more"),
                )
            }
        },
    ) {
        if (view == null) {
            if (state.joining) Joining(state, onIntent) else Skeleton(Modifier.fillMaxWidth().padding(vertical = AppTheme.spacing.xl))
        } else {
            if (menuOpen && hasMenu) {
                WalletMenu(state, view) { intent ->
                    menuOpen = false
                    onIntent(intent)
                }
            }
            StatusArea(view, state.busy, onIntent)
            Hero(view, state.members)
            if (view.archived) ArchivedPill()
            if (state.isViewer) {
                InlineAlert(
                    AlertTone.Info,
                    stringResource(if (state.planLimited) Res.string.viewer_note_plan else Res.string.viewer_note),
                    Modifier.testTag("walletDetail.viewerNote"),
                )
            }
            QuickActions(state, onIntent)
            RecentCard(state, onIntent)
            MembersSection(state, onIntent)
        }
    }
    Confirmations(state, onIntent)
}

/** The wallet settings opened by the header's "more" button: owner actions and the export. */
@Composable
private fun WalletMenu(state: WalletDetailState, view: WalletView, onIntent: (WalletDetailIntent) -> Unit) {
    CalmCard(padding = PaddingValues(0.dp)) {
        val rows = buildList<@Composable () -> Unit> {
            if (state.canEdit) {
                add { CalmRow(stringResource(Res.string.wallet_edit), Modifier.testTag("walletDetail.edit")) { onIntent(WalletDetailIntent.EditClicked) } }
                if (!view.archived) {
                    add { CalmRow(stringResource(Res.string.wallet_archive), Modifier.testTag("walletDetail.archive")) { onIntent(WalletDetailIntent.ArchiveClicked) } }
                }
                if (state.canTransfer) {
                    add { CalmRow(stringResource(Res.string.wallet_transfer), Modifier.testTag("walletDetail.transfer")) { onIntent(WalletDetailIntent.TransferClicked) } }
                }
            }
            if (state.exportable) {
                add { CalmRow(stringResource(Res.string.wallet_export), Modifier.testTag("walletDetail.export")) { onIntent(WalletDetailIntent.ExportClicked) } }
            }
            if (state.canEdit) {
                add {
                    CalmRow(
                        stringResource(Res.string.wallet_delete),
                        Modifier.testTag("walletDetail.delete"),
                        destructive = true,
                    ) { onIntent(WalletDetailIntent.DeleteClicked) }
                }
            }
        }
        rows.forEachIndexed { index, row ->
            if (index > 0) CalmDivider()
            row()
        }
    }
}

@Composable
private fun Hero(view: WalletView, members: List<Member>) {
    val colors = AppTheme.colors
    val liability = view.isLiability
    val shownAmount = if (liability && view.displayedBalanceMinor <= 0) -view.displayedBalanceMinor else view.displayedBalanceMinor
    val ground = if (view.archived) colors.ink2 else WalletAccents.find(view.shown.color)?.tint ?: colors.primary
    val on = colors.onPrimary
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 188.dp)
            .background(ground, RoundedCornerShape(26.dp))
            .padding(20.dp)
            .testTag("walletDetail.balance"),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                view.shown.name,
                style = AppTheme.type.title.copy(fontSize = 17.sp, fontWeight = FontWeight.Bold),
                color = on,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).semantics { heading() },
            )
            if (members.size > 1) Icon(AppIcons.WlcGroup, contentDescription = null, tint = on, modifier = Modifier.size(22.dp))
        }
        Text(
            stringResource(Res.string.wallet_type_currency_caption, view.shown.walletType.label(), stringResource(view.server.myRole.labelRes())),
            style = AppTheme.type.caption.copy(fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold),
            color = on.copy(alpha = 0.8f),
        )
        Spacer(Modifier.weight(1f))
        Spacer(Modifier.height(16.dp))
        Text(
            stringResource(if (liability) Res.string.wallet_field_amount_owed else Res.string.wallet_balance),
            style = AppTheme.type.label.copy(fontSize = 13.sp),
            color = on.copy(alpha = 0.8f),
        )
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                formatMoney(shownAmount, view.shown.currency),
                style = AppTheme.type.numeralHero.copy(fontSize = 34.sp, fontWeight = FontWeight.ExtraBold).ltr(),
                color = on,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (members.size > 1) {
                Row(horizontalArrangement = Arrangement.spacedBy((-9).dp)) {
                    members.take(3).forEachIndexed { index, member ->
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(30.dp)
                                .background(if (index == 0) on else AppTheme.money.positiveContainer, CircleShape)
                                .border(2.dp, ground, CircleShape),
                        ) {
                            Text(
                                (member.displayName ?: "?").take(1).uppercase(),
                                style = AppTheme.type.caption.copy(fontWeight = FontWeight.ExtraBold),
                                color = if (index == 0) ground else AppTheme.money.positive,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun QuickActions(state: WalletDetailState, onIntent: (WalletDetailIntent) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
        if (state.canAddTransaction) {
            QuickAction(AppIcons.Plus, stringResource(Res.string.wlc_add), "walletDetail.addTransaction", filled = true, description = stringResource(Res.string.transactions_add)) {
                onIntent(WalletDetailIntent.AddTransactionClicked)
            }
        }
        QuickAction(AppIcons.WlcRecurring, stringResource(Res.string.recurring_open), "walletDetail.recurring") {
            onIntent(WalletDetailIntent.RecurringClicked)
        }
        QuickAction(AppIcons.Reports, stringResource(Res.string.reports_wallet_action), "wallet.report") {
            onIntent(WalletDetailIntent.ReportClicked)
        }
        if (state.canEdit) {
            QuickAction(AppIcons.WlcPersonAdd, stringResource(Res.string.invite_action), "walletDetail.invite") {
                onIntent(WalletDetailIntent.MembersClicked)
            }
        }
        QuickAction(AppIcons.WlcHistory, stringResource(Res.string.activity_title), "walletDetail.activity") {
            onIntent(WalletDetailIntent.ActivityClicked)
        }
    }
}

@Composable
private fun RowScope.QuickAction(
    icon: ImageVector,
    label: String,
    tag: String,
    filled: Boolean = false,
    description: String = label,
    onClick: () -> Unit,
) {
    val colors = AppTheme.colors
    val shape = RoundedCornerShape(18.dp)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier
            .weight(1f)
            .testTag(tag)
            .clip(shape)
            .semantics(mergeDescendants = true) { contentDescription = description }
            .clickable(role = Role.Button, onClick = onClick),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(52.dp)
                .background(if (filled) colors.primary else colors.surface, shape)
                .border(1.dp, if (filled) colors.primary else colors.outlineVariant, shape),
        ) {
            Icon(icon, contentDescription = null, tint = if (filled) colors.onPrimary else colors.primary, modifier = Modifier.size(24.dp))
        }
        Text(
            label,
            style = AppTheme.type.caption.copy(fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold),
            color = colors.primary,
            textAlign = TextAlign.Center,
            maxLines = 2,
        )
    }
}

@Composable
private fun CalmRow(label: String, modifier: Modifier = Modifier, supporting: String? = null, destructive: Boolean = false, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.s),
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 18.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, style = AppTheme.type.itemTitle, color = if (destructive) AppTheme.colors.error else AppTheme.colors.ink)
            if (supporting != null) Text(supporting, style = AppTheme.type.caption, color = AppTheme.colors.ink2)
        }
        if (!destructive) Icon(AppIcons.ChevronForward, contentDescription = null, tint = AppTheme.colors.ink2, modifier = Modifier.size(20.dp))
    }
}

/** The wallet's three newest transactions with a link to all of them. */
@Composable
private fun RecentCard(state: WalletDetailState, onIntent: (WalletDetailIntent) -> Unit) {
    val colors = AppTheme.colors
    val today = remember(state.timeZoneId) {
        Clock.System.now().toLocalDateTime(runCatching { TimeZone.of(state.timeZoneId) }.getOrElse { TimeZone.UTC }).date
    }
    CalmCard(padding = PaddingValues(start = 18.dp, end = 18.dp, top = 4.dp, bottom = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
            Text(
                stringResource(Res.string.wlc_recent),
                style = AppTheme.type.title.copy(fontSize = 15.sp, fontWeight = FontWeight.Bold),
                color = colors.ink,
                modifier = Modifier.weight(1f).semantics { heading() },
            )
            Box(
                contentAlignment = Alignment.CenterEnd,
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .testTag("walletDetail.transactions")
                    .clickable(role = Role.Button) { onIntent(WalletDetailIntent.TransactionsClicked) },
            ) {
                Text(
                    stringResource(Res.string.wlc_all_transactions),
                    style = AppTheme.type.caption.copy(fontSize = 12.5.sp, fontWeight = FontWeight.Bold),
                    color = colors.primary,
                )
            }
        }
        if (state.recent.isEmpty()) {
            Text(
                stringResource(Res.string.wlc_recent_empty),
                style = AppTheme.type.body,
                color = colors.ink2,
                modifier = Modifier.padding(vertical = 12.dp).testTag("walletDetail.recentEmpty"),
            )
        }
        state.recent.forEachIndexed { index, item ->
            if (index > 0) CalmDivider()
            RecentRow(item, today, state.members.size > 1, Modifier.testTag("walletDetail.recent.$index"))
        }
    }
}

@Composable
private fun RecentRow(item: RecentTransaction, today: LocalDate, shared: Boolean, modifier: Modifier = Modifier) {
    val colors = AppTheme.colors
    val transfer = item.kind == RecentKind.Transfer
    val title = when {
        transfer -> {
            val name = item.counterpart ?: stringResource(Res.string.tx_another_wallet)
            stringResource(if (item.direction == RecentDirection.In) Res.string.tx_transfer_from else Res.string.tx_transfer_to, name)
        }
        else -> item.title ?: stringResource(
            when (item.kind) {
                RecentKind.Income -> Res.string.tx_kind_income
                RecentKind.Expense -> Res.string.tx_kind_expense
                else -> Res.string.tx_kind_unknown
            },
        )
    }
    val date = if (item.date == today) stringResource(Res.string.transactions_today) else formatDayMonth(item.date)
    val lead = when {
        transfer -> stringResource(Res.string.tx_kind_transfer)
        shared -> when (val author = item.author) {
            RecentAuthor.You -> stringResource(Res.string.wlc_added_by_you)
            is RecentAuthor.Member -> stringResource(Res.string.tx_added_by, author.name)
            RecentAuthor.Another -> stringResource(Res.string.tx_added_by_other)
            RecentAuthor.Former -> stringResource(Res.string.tx_added_by_former)
        }
        else -> null
    }
    val kind = when (item.direction) {
        RecentDirection.In -> MoneyKind.Income
        RecentDirection.Out -> MoneyKind.Expense
        RecentDirection.None -> MoneyKind.Balance
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier.fillMaxWidth().padding(vertical = 11.dp),
    ) {
        Icon(
            if (transfer) AppIcons.WlcSwap else CategoryIcons.find(item.iconKey) ?: AppIcons.More,
            contentDescription = null,
            tint = if (transfer) AppTheme.money.positive else colors.primary,
            modifier = Modifier.size(22.dp),
        )
        Column(Modifier.weight(1f)) {
            Text(title, style = AppTheme.type.itemTitle.copy(fontSize = 14.5.sp, fontWeight = FontWeight.SemiBold), color = colors.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                listOfNotNull(lead, date).joinToString(" \u00B7 "),
                style = AppTheme.type.caption.copy(fontSize = 12.5.sp),
                color = colors.ink2,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        MoneyText(item.amountMinor, item.currency, kind, style = AppTheme.type.itemTitle.copy(fontSize = 15.sp, fontWeight = FontWeight.Bold))
    }
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
    val colors = AppTheme.colors
    val others = listOf(AppTheme.money.positive, WalletAccents.all[4].tint, WalletAccents.all[5].tint, WalletAccents.all[6].tint)
    CalmCard(padding = PaddingValues(horizontal = 18.dp, vertical = 14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text(
                stringResource(Res.string.wallet_members),
                style = AppTheme.type.title.copy(fontSize = 15.sp, fontWeight = FontWeight.Bold),
                color = colors.ink,
                modifier = Modifier.weight(1f).semantics { heading() },
            )
            Box(
                contentAlignment = Alignment.CenterEnd,
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .testTag("walletDetail.manageMembers")
                    .clickable(role = Role.Button) { onIntent(WalletDetailIntent.MembersClicked) },
            ) {
                Text(
                    stringResource(if (state.canEdit) Res.string.wlc_manage else Res.string.wlc_see),
                    style = AppTheme.type.caption.copy(fontSize = 12.5.sp, fontWeight = FontWeight.Bold),
                    color = colors.primary,
                )
            }
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(top = 4.dp, bottom = 4.dp),
        ) {
            var other = 0
            state.members.forEachIndexed { index, member ->
                val name = if (member.isYou) stringResource(Res.string.member_you) else member.displayName ?: stringResource(Res.string.member_unnamed)
                val ground = if (member.isYou) colors.primary else others[other++ % others.size]
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.testTag("walletDetail.member.$index"),
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.size(44.dp).background(ground, CircleShape),
                    ) {
                        Text(
                            (member.displayName ?: name).take(1).uppercase(),
                            style = AppTheme.type.itemTitle.copy(fontWeight = FontWeight.ExtraBold),
                            color = colors.onPrimary,
                        )
                    }
                    Text(
                        name,
                        style = AppTheme.type.caption.copy(fontSize = 12.sp, fontWeight = FontWeight.SemiBold),
                        color = colors.ink,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.widthIn(max = 72.dp),
                    )
                    Text(stringResource(member.role.labelRes()), style = AppTheme.type.caption.copy(fontSize = 11.sp), color = colors.ink2)
                }
            }
            if (state.pendingInvitations > 0) {
                val dash = colors.caption
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.testTag("walletDetail.pendingInvitations"),
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(44.dp)
                            .drawBehind {
                                drawCircle(
                                    dash,
                                    style = Stroke(1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f))),
                                )
                            },
                    ) {
                        Icon(AppIcons.WlcPending, contentDescription = null, tint = colors.ink2, modifier = Modifier.size(20.dp))
                    }
                    Text(
                        stringResource(Res.string.wlc_pending),
                        style = AppTheme.type.caption.copy(fontSize = 12.sp, fontWeight = FontWeight.SemiBold),
                        color = colors.ink2,
                    )
                    Text(
                        stringResource(Res.string.wlc_invited_count, state.pendingInvitations),
                        style = AppTheme.type.caption.copy(fontSize = 11.sp),
                        color = colors.ink2,
                    )
                }
            }
        }
    }
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
