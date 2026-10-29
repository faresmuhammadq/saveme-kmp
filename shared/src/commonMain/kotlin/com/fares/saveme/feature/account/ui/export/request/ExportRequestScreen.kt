package com.fares.saveme.feature.account.ui.export.request

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fares.saveme.designsystem.component.AlertTone
import com.fares.saveme.designsystem.component.CalmCard
import com.fares.saveme.designsystem.component.CalmCheckBadge
import com.fares.saveme.designsystem.component.CalmDivider
import com.fares.saveme.designsystem.component.CalmInfoPill
import com.fares.saveme.designsystem.component.CalmActionBar
import com.fares.saveme.designsystem.component.CalmCircleButton
import com.fares.saveme.designsystem.component.CalmScreen
import com.fares.saveme.designsystem.component.InlineAlert
import com.fares.saveme.designsystem.component.PrimaryButton
import com.fares.saveme.designsystem.component.SelectionSheet
import com.fares.saveme.designsystem.icon.AccArchive
import com.fares.saveme.designsystem.icon.WalletIcons
import com.fares.saveme.designsystem.tokens.WalletAccents
import com.fares.saveme.feature.wallets.ui.cardType
import com.fares.saveme.designsystem.icon.AppIcons
import com.fares.saveme.designsystem.theme.AppTheme
import com.fares.saveme.feature.account.domain.export.ExportScope
import com.fares.saveme.feature.account.ui.AccountBanner
import com.fares.saveme.feature.account.ui.export.rateLimitText
import com.fares.saveme.feature.account.ui.text
import org.jetbrains.compose.resources.stringResource
import saveme.shared.generated.resources.Res
import saveme.shared.generated.resources.acct_export_pill_24h
import saveme.shared.generated.resources.acct_export_pill_email
import saveme.shared.generated.resources.acct_export_pill_zip
import saveme.shared.generated.resources.acct_export_scope_all_sub
import saveme.shared.generated.resources.acct_export_scope_wallet_sub
import saveme.shared.generated.resources.cd_back
import saveme.shared.generated.resources.export_explain
import saveme.shared.generated.resources.export_in_progress
import saveme.shared.generated.resources.export_offline
import saveme.shared.generated.resources.export_request_title
import saveme.shared.generated.resources.export_kind_account
import saveme.shared.generated.resources.export_scope_wallet
import saveme.shared.generated.resources.export_see_it
import saveme.shared.generated.resources.export_submit
import saveme.shared.generated.resources.export_unsent
import saveme.shared.generated.resources.export_wallet_gone
import saveme.shared.generated.resources.export_wallet_hint
import saveme.shared.generated.resources.export_wallet_label

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ExportRequestScreen(state: ExportRequestState, onIntent: (ExportRequestIntent) -> Unit) {
    CalmScreen(
        title = null,
        titleSlot = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                CalmCircleButton(AppIcons.Close, stringResource(Res.string.cd_back), { onIntent(ExportRequestIntent.Back) })
                Text(
                    stringResource(Res.string.export_request_title),
                    style = AppTheme.type.headline.copy(fontSize = 22.sp),
                    color = AppTheme.colors.ink,
                    modifier = Modifier.semantics { heading() },
                )
            }
        },
        bottomBar = {
            CalmActionBar {
                PrimaryButton(
                    stringResource(Res.string.export_submit),
                    { onIntent(ExportRequestIntent.Submit) },
                    Modifier.testTag("exportRequest.submit"),
                    enabled = state.canSubmit || state.submitting,
                    loading = state.submitting,
                )
            }
        },
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min).selectableGroup()) {
            ScopeCard(
                title = stringResource(Res.string.export_kind_account),
                sub = stringResource(Res.string.acct_export_scope_all_sub),
                icon = AppIcons.AccArchive,
                selected = state.scope == ExportScope.Account,
                onClick = { onIntent(ExportRequestIntent.ScopeChosen(ExportScope.Account)) },
                modifier = Modifier.weight(1f).fillMaxHeight().testTag("exportRequest.scope.account"),
            )
            ScopeCard(
                title = stringResource(Res.string.export_scope_wallet),
                sub = stringResource(Res.string.acct_export_scope_wallet_sub),
                icon = AppIcons.Wallet,
                selected = state.scope == ExportScope.Wallet,
                onClick = { onIntent(ExportRequestIntent.ScopeChosen(ExportScope.Wallet)) },
                modifier = Modifier.weight(1f).fillMaxHeight().testTag("exportRequest.scope.wallet"),
            )
        }
        if (state.scope == ExportScope.Wallet) {
            if (state.wallets.isEmpty()) {
                Text(stringResource(Res.string.export_wallet_hint), style = AppTheme.type.body, color = AppTheme.colors.ink2)
            } else {
                CalmCard(
                    radius = 22.dp,
                    padding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                    modifier = Modifier.testTag("exportRequest.wallet").selectableGroup(),
                ) {
                    state.wallets.forEachIndexed { index, wallet ->
                        if (index > 0) CalmDivider()
                        WalletRow(wallet, selected = wallet.id == state.walletId, enabled = !state.submitting) {
                            onIntent(ExportRequestIntent.WalletChosen(wallet.id))
                        }
                    }
                }
            }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            CalmInfoPill(stringResource(Res.string.acct_export_pill_zip))
            CalmInfoPill(stringResource(Res.string.acct_export_pill_24h))
            CalmInfoPill(stringResource(Res.string.acct_export_pill_email))
        }
        Text(
            stringResource(Res.string.export_explain),
            style = AppTheme.type.caption,
            color = AppTheme.colors.ink2,
            modifier = Modifier.testTag("exportRequest.explain"),
        )
        if (state.unsentChanges) {
            InlineAlert(AlertTone.Info, stringResource(Res.string.export_unsent), Modifier.testTag("exportRequest.unsent"))
        }
        state.message?.let { message ->
            val seeIt = (message as? ExportRequestMessage.InProgress)
            InlineAlert(
                if (message is ExportRequestMessage.RateLimited) AlertTone.Warning else AlertTone.Error,
                when (message) {
                    is ExportRequestMessage.InProgress -> stringResource(Res.string.export_in_progress)
                    is ExportRequestMessage.RateLimited -> rateLimitText(message.seconds)
                    ExportRequestMessage.WalletGone -> stringResource(Res.string.export_wallet_gone)
                    ExportRequestMessage.Offline -> stringResource(Res.string.export_offline)
                    is ExportRequestMessage.Error -> AccountBanner.Error(message.code, message.requestId).text()
                },
                Modifier.testTag("exportRequest.message"),
                actionLabel = seeIt?.let { stringResource(Res.string.export_see_it) },
                onAction = seeIt?.let { { onIntent(ExportRequestIntent.SeeIt) } },
            )
        }
    }
    if (state.walletPickerVisible) {
        // The sheet keys its rows by label, so two wallets with one name get a number.
        val uniqueLabels = state.wallets.groupBy { it.name }.flatMap { (name, same) ->
            if (same.size == 1) listOf(same.single().id to name) else same.mapIndexed { i, w -> w.id to "$name (${i + 1})" }
        }.toMap()
        SelectionSheet(
            title = stringResource(Res.string.export_wallet_label),
            options = state.wallets,
            label = { uniqueLabels[it.id] ?: it.name },
            selected = state.chosenWallet,
            onSelect = { onIntent(ExportRequestIntent.WalletChosen(it.id)) },
            onDismiss = { onIntent(ExportRequestIntent.WalletPickerDismissed) },
            searchable = state.wallets.size > 8,
        )
    }
}

@Composable
private fun WalletRow(wallet: WalletChoice, selected: Boolean, enabled: Boolean, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .selectable(selected = selected, enabled = enabled, role = Role.RadioButton, onClick = onClick)
            .testTag("exportRequest.wallet.${wallet.id}")
            .padding(vertical = 6.dp),
    ) {
        Icon(
            WalletIcons.find(wallet.icon) ?: wallet.type.cardType().icon,
            contentDescription = null,
            tint = WalletAccents.find(wallet.color)?.tint ?: AppTheme.colors.ink2,
            modifier = Modifier.size(22.dp),
        )
        Text(
            wallet.name,
            style = AppTheme.type.body.copy(fontSize = 14.5.sp, fontWeight = FontWeight.SemiBold),
            color = AppTheme.colors.ink,
            modifier = Modifier.weight(1f),
        )
        if (selected) CalmCheckBadge(AppTheme.colors.primary, size = 22.dp)
    }
}

@Composable
private fun ScopeCard(
    title: String,
    sub: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(22.dp)
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier
            .clip(shape)
            .background(if (selected) AppTheme.colors.primaryContainer else AppTheme.colors.surface, shape)
            .border(1.5.dp, if (selected) AppTheme.colors.primary else AppTheme.colors.outlineVariant, shape)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(16.dp),
    ) {
        Icon(icon, contentDescription = null, tint = if (selected) AppTheme.colors.primary else AppTheme.colors.ink2, modifier = Modifier.size(24.dp))
        Text(title, style = AppTheme.type.body.copy(fontWeight = FontWeight.Bold), color = AppTheme.colors.ink)
        Text(sub, style = AppTheme.type.caption, color = AppTheme.colors.ink2)
    }
}

@Preview
@Composable
private fun ExportRequestScreenPreview() {
    AppTheme { ExportRequestScreen(ExportRequestState(unsentChanges = true)) {} }
}
