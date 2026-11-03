package com.fares.saveme.feature.transactions.ui.adjust

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fares.saveme.designsystem.component.AppDatePickerDialog
import com.fares.saveme.designsystem.component.AppSelectField
import com.fares.saveme.designsystem.component.AppTextField
import com.fares.saveme.designsystem.component.CalmCard
import com.fares.saveme.designsystem.component.CalmScreen
import com.fares.saveme.designsystem.component.CalmTitle
import com.fares.saveme.designsystem.component.PrimaryButton
import com.fares.saveme.designsystem.component.formatMoney
import com.fares.saveme.designsystem.format.formatDate
import com.fares.saveme.designsystem.theme.AppTheme
import com.fares.saveme.feature.transactions.domain.AdjustDirection
import com.fares.saveme.feature.transactions.domain.AdjustMode
import com.fares.saveme.feature.transactions.domain.AdjustOutcome
import com.fares.saveme.feature.transactions.domain.model.TransactionField
import com.fares.saveme.feature.transactions.domain.model.TransactionKind
import com.fares.saveme.feature.transactions.ui.text
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import saveme.shared.generated.resources.Res
import saveme.shared.generated.resources.adj_current
import saveme.shared.generated.resources.adj_current_owed
import saveme.shared.generated.resources.adj_explainer
import saveme.shared.generated.resources.adj_field_change
import saveme.shared.generated.resources.adj_field_new_balance
import saveme.shared.generated.resources.adj_field_new_owed
import saveme.shared.generated.resources.adj_lower
import saveme.shared.generated.resources.adj_lower_owed
import saveme.shared.generated.resources.adj_mode_change
import saveme.shared.generated.resources.adj_mode_new_balance
import saveme.shared.generated.resources.adj_note_hint
import saveme.shared.generated.resources.adj_preview_balance
import saveme.shared.generated.resources.adj_preview_owed
import saveme.shared.generated.resources.adj_raise
import saveme.shared.generated.resources.adj_raise_owed
import saveme.shared.generated.resources.adj_save
import saveme.shared.generated.resources.adj_title
import saveme.shared.generated.resources.adj_updated
import saveme.shared.generated.resources.cd_back
import saveme.shared.generated.resources.tx_field_date
import saveme.shared.generated.resources.tx_gone
import saveme.shared.generated.resources.tx_reason_no_right
import kotlin.time.Instant

@Composable
fun BalanceAdjustRoute(
    walletId: String,
    onEffect: (BalanceAdjustEffect) -> Unit,
    onRequireVerification: () -> Unit,
    viewModel: BalanceAdjustViewModel = koinViewModel(key = "balance-adjust-$walletId") { parametersOf(walletId) },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is BalanceAdjustEffect.ShowMessage -> scope.launch {
                    snackbar.showSnackbar(getString(if (effect.message == AdjustMessage.NoLongerAvailable) Res.string.tx_gone else Res.string.tx_reason_no_right))
                }
                BalanceAdjustEffect.RequireVerification -> onRequireVerification()
                BalanceAdjustEffect.Close -> onEffect(effect)
            }
        }
    }
    BalanceAdjustScreen(state, viewModel::onIntent, snackbar)
}

@Composable
fun BalanceAdjustScreen(
    state: BalanceAdjustState,
    onIntent: (BalanceAdjustIntent) -> Unit,
    snackbar: SnackbarHostState = remember { SnackbarHostState() },
) {
    CalmScreen(
        title = null,
        onBack = { onIntent(BalanceAdjustIntent.Back) },
        backDescription = stringResource(Res.string.cd_back),
        snackbarHostState = snackbar,
        titleSlot = { CalmTitle(stringResource(Res.string.adj_title)) },
        bottomBar = {
            if (state.loaded && state.canEdit) {
                PrimaryButton(
                    stringResource(Res.string.adj_save), { onIntent(BalanceAdjustIntent.SaveClicked) },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp).testTag("balanceAdjust.save"),
                    enabled = state.canSave, loading = state.saving,
                )
            }
        },
    ) {
        val target = state.target ?: return@CalmScreen
        val owed = target.isLiability
        CalmCard {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(target.name, style = AppTheme.type.caption, color = AppTheme.colors.ink2)
                Text(
                    stringResource(if (owed) Res.string.adj_current_owed else Res.string.adj_current),
                    style = AppTheme.type.caption, color = AppTheme.colors.ink2,
                )
                Text(
                    formatMoney(if (owed) -target.balanceMinor else target.balanceMinor, target.currency),
                    style = AppTheme.type.itemTitle, color = AppTheme.colors.ink, modifier = Modifier.testTag("balanceAdjust.current"),
                )
                state.feedReadAt?.let { Updated(it) }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Chip(stringResource(Res.string.adj_mode_new_balance), state.mode == AdjustMode.NewBalance, "balanceAdjust.mode.newBalance") {
                onIntent(BalanceAdjustIntent.ModeChosen(AdjustMode.NewBalance))
            }
            Chip(stringResource(Res.string.adj_mode_change), state.mode == AdjustMode.Change, "balanceAdjust.mode.change") {
                onIntent(BalanceAdjustIntent.ModeChosen(AdjustMode.Change))
            }
        }
        if (state.mode == AdjustMode.Change) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Chip(stringResource(if (owed) Res.string.adj_raise_owed else Res.string.adj_raise), state.direction == AdjustDirection.Raise, "balanceAdjust.direction.raise") {
                    onIntent(BalanceAdjustIntent.DirectionChosen(AdjustDirection.Raise))
                }
                Chip(stringResource(if (owed) Res.string.adj_lower_owed else Res.string.adj_lower), state.direction == AdjustDirection.Lower, "balanceAdjust.direction.lower") {
                    onIntent(BalanceAdjustIntent.DirectionChosen(AdjustDirection.Lower))
                }
            }
        }
        AppTextField(
            value = state.amountText, onValueChange = { onIntent(BalanceAdjustIntent.AmountChanged(it)) },
            label = stringResource(
                when {
                    state.mode == AdjustMode.Change -> Res.string.adj_field_change
                    owed -> Res.string.adj_field_new_owed
                    else -> Res.string.adj_field_new_balance
                },
            ),
            enabled = state.canEdit,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            errorText = state.problem?.text(TransactionField.Amount),
            modifier = Modifier.fillMaxWidth().testTag("balanceAdjust.amount"),
        )
        Preview(state)
        AppSelectField(
            label = stringResource(Res.string.tx_field_date), value = state.date?.let { formatDate(it) }.orEmpty(),
            onClick = { onIntent(BalanceAdjustIntent.DateClicked) }, enabled = state.canEdit,
            modifier = Modifier.testTag("balanceAdjust.date"),
        )
        AppTextField(
            value = state.note, onValueChange = { onIntent(BalanceAdjustIntent.NoteChanged(it)) },
            label = stringResource(Res.string.adj_note_hint), enabled = state.canEdit,
            modifier = Modifier.fillMaxWidth().testTag("balanceAdjust.note"),
        )
        Text(stringResource(Res.string.adj_explainer), style = AppTheme.type.caption, color = AppTheme.colors.ink2)
    }
    if (state.datePickerOpen) {
        AppDatePickerDialog(
            initial = state.date ?: LocalDate(2026, 1, 1),
            onPick = { onIntent(BalanceAdjustIntent.DateChosen(it)) },
            onDismiss = { onIntent(BalanceAdjustIntent.DatePickerDismissed) },
        )
    }
}

@Composable
private fun Updated(at: Instant) {
    Text(
        stringResource(Res.string.adj_updated, com.fares.saveme.designsystem.format.formatDateTime(at, TimeZone.currentSystemDefault().id)),
        style = AppTheme.type.caption, color = AppTheme.colors.caption, modifier = Modifier.testTag("balanceAdjust.updated"),
    )
}

/** "Balance will be 1,250.00 (−250.00)" once the typed amount means something. */
@Composable
private fun Preview(state: BalanceAdjustState) {
    val target = state.target ?: return
    val ready = state.outcome as? AdjustOutcome.Ready ?: return
    val signed = if (ready.adjustment.kind == TransactionKind.AdjustmentIn) ready.adjustment.amountMinor else -ready.adjustment.amountMinor
    val shownChange = formatMoney(if (target.isLiability) -signed else signed, target.currency, showPlus = true)
    val shownBalance = formatMoney(if (target.isLiability) -ready.balanceAfterMinor else ready.balanceAfterMinor, target.currency)
    Text(
        stringResource(if (target.isLiability) Res.string.adj_preview_owed else Res.string.adj_preview_balance, shownBalance, shownChange),
        style = AppTheme.type.body, color = AppTheme.colors.ink, modifier = Modifier.testTag("balanceAdjust.preview"),
    )
}

@Composable
private fun Chip(text: String, selected: Boolean, tag: String, onClick: () -> Unit) {
    val shape = AppTheme.shapes.pill
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.heightIn(min = 48.dp).selectable(selected = selected, role = Role.RadioButton, onClick = onClick).testTag(tag),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .height(36.dp)
                .background(if (selected) AppTheme.colors.primaryContainer else AppTheme.colors.sunken, shape)
                .border(1.dp, if (selected) AppTheme.colors.primary else Color.Transparent, shape)
                .padding(horizontal = 16.dp),
        ) {
            Text(
                text,
                style = AppTheme.type.caption.copy(fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold),
                color = if (selected) AppTheme.colors.primary else AppTheme.colors.ink2,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
