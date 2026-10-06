package com.fares.saveme.feature.account.ui.export.request

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Preview
import com.fares.saveme.designsystem.component.AlertTone
import com.fares.saveme.designsystem.component.AppScreen
import com.fares.saveme.designsystem.component.AppSelectField
import com.fares.saveme.designsystem.component.InlineAlert
import com.fares.saveme.designsystem.component.PrimaryButton
import com.fares.saveme.designsystem.component.SegmentedControl
import com.fares.saveme.designsystem.component.SelectionSheet
import com.fares.saveme.designsystem.theme.AppTheme
import com.fares.saveme.feature.account.domain.export.ExportScope
import com.fares.saveme.feature.account.ui.AccountBanner
import com.fares.saveme.feature.account.ui.BackBar
import com.fares.saveme.feature.account.ui.export.rateLimitText
import com.fares.saveme.feature.account.ui.text
import org.jetbrains.compose.resources.stringResource
import saveme.shared.generated.resources.Res
import saveme.shared.generated.resources.export_explain
import saveme.shared.generated.resources.export_in_progress
import saveme.shared.generated.resources.export_offline
import saveme.shared.generated.resources.export_request_title
import saveme.shared.generated.resources.export_scope_account
import saveme.shared.generated.resources.export_scope_wallet
import saveme.shared.generated.resources.export_see_it
import saveme.shared.generated.resources.export_submit
import saveme.shared.generated.resources.export_unsent
import saveme.shared.generated.resources.export_wallet_gone
import saveme.shared.generated.resources.export_wallet_hint
import saveme.shared.generated.resources.export_wallet_label

@Composable
fun ExportRequestScreen(state: ExportRequestState, onIntent: (ExportRequestIntent) -> Unit) {
    AppScreen(topBar = { BackBar(stringResource(Res.string.export_request_title)) { onIntent(ExportRequestIntent.Back) } }) {
        SegmentedControl(
            options = listOf(ExportScope.Account, ExportScope.Wallet),
            label = {
                stringResource(if (it == ExportScope.Account) Res.string.export_scope_account else Res.string.export_scope_wallet)
            },
            selected = state.scope,
            onSelect = { onIntent(ExportRequestIntent.ScopeChosen(it)) },
            optionModifier = {
                Modifier.testTag(if (it == ExportScope.Account) "exportRequest.scope.account" else "exportRequest.scope.wallet")
            },
        )
        if (state.scope == ExportScope.Wallet) {
            AppSelectField(
                label = stringResource(Res.string.export_wallet_label),
                value = state.chosenWallet?.name ?: stringResource(Res.string.export_wallet_hint),
                onClick = { onIntent(ExportRequestIntent.WalletFieldClicked) },
                modifier = Modifier.testTag("exportRequest.wallet"),
                enabled = !state.submitting,
            )
        }
        Text(
            stringResource(Res.string.export_explain),
            style = AppTheme.type.body,
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
        PrimaryButton(
            stringResource(Res.string.export_submit),
            { onIntent(ExportRequestIntent.Submit) },
            Modifier.testTag("exportRequest.submit"),
            enabled = state.canSubmit || state.submitting,
            loading = state.submitting,
        )
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

@Preview
@Composable
private fun ExportRequestScreenPreview() {
    AppTheme { ExportRequestScreen(ExportRequestState(unsentChanges = true)) {} }
}
