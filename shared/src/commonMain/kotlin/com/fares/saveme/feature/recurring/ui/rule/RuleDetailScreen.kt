package com.fares.saveme.feature.recurring.ui.rule

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.fares.saveme.designsystem.component.AlertTone
import com.fares.saveme.designsystem.component.AppAlertDialog
import com.fares.saveme.designsystem.component.AppScreen
import com.fares.saveme.designsystem.component.DestructiveButton
import com.fares.saveme.designsystem.component.InlineAlert
import com.fares.saveme.designsystem.component.MoneyKind
import com.fares.saveme.designsystem.component.PreviewDateItem
import com.fares.saveme.designsystem.component.PreviewDateList
import com.fares.saveme.designsystem.component.PrimaryButton
import com.fares.saveme.designsystem.component.SecondaryButton
import com.fares.saveme.designsystem.component.Skeleton
import com.fares.saveme.designsystem.component.formatMoney
import com.fares.saveme.designsystem.format.formatDate
import com.fares.saveme.designsystem.theme.AppTheme
import com.fares.saveme.feature.recurring.domain.model.RecurringSyncState
import com.fares.saveme.feature.recurring.ui.OccurrenceListRow
import com.fares.saveme.feature.recurring.ui.RecurringBackBar
import com.fares.saveme.feature.recurring.ui.RuleListRow
import com.fares.saveme.feature.recurring.ui.cadenceEndText
import com.fares.saveme.feature.recurring.ui.moneyKind
import com.fares.saveme.feature.recurring.ui.text
import org.jetbrains.compose.resources.stringResource
import saveme.shared.generated.resources.Res
import saveme.shared.generated.resources.action_accept_deletion
import saveme.shared.generated.resources.action_cancel
import saveme.shared.generated.resources.action_discard
import saveme.shared.generated.resources.action_retry
import saveme.shared.generated.resources.recurring_action_catch_up
import saveme.shared.generated.resources.recurring_action_change_from
import saveme.shared.generated.resources.recurring_action_delete
import saveme.shared.generated.resources.recurring_action_edit
import saveme.shared.generated.resources.recurring_action_end
import saveme.shared.generated.resources.recurring_action_pause
import saveme.shared.generated.resources.recurring_action_resume
import saveme.shared.generated.resources.recurring_author
import saveme.shared.generated.resources.recurring_auto_off
import saveme.shared.generated.resources.recurring_auto_on
import saveme.shared.generated.resources.recurring_catch_up_done
import saveme.shared.generated.resources.recurring_catch_up_failed
import saveme.shared.generated.resources.recurring_catch_up_failed_reference
import saveme.shared.generated.resources.recurring_catch_up_hint
import saveme.shared.generated.resources.recurring_catch_up_offline
import saveme.shared.generated.resources.recurring_clash_body
import saveme.shared.generated.resources.recurring_clash_delete_body
import saveme.shared.generated.resources.recurring_delete_body
import saveme.shared.generated.resources.recurring_delete_confirm
import saveme.shared.generated.resources.recurring_delete_title
import saveme.shared.generated.resources.recurring_deleted_body
import saveme.shared.generated.resources.recurring_dates_after_sync
import saveme.shared.generated.resources.recurring_end_body
import saveme.shared.generated.resources.recurring_end_confirm
import saveme.shared.generated.resources.recurring_end_title
import saveme.shared.generated.resources.recurring_held
import saveme.shared.generated.resources.recurring_occurrences_empty
import saveme.shared.generated.resources.recurring_occurrences_title
import saveme.shared.generated.resources.recurring_payout
import saveme.shared.generated.resources.recurring_payout_amount
import saveme.shared.generated.resources.recurring_preview_label
import saveme.shared.generated.resources.recurring_preview_none
import saveme.shared.generated.resources.recurring_readonly_ended
import saveme.shared.generated.resources.recurring_readonly_unknown
import saveme.shared.generated.resources.recurring_readonly_viewer
import saveme.shared.generated.resources.recurring_rule_title
import saveme.shared.generated.resources.recurring_section_due
import saveme.shared.generated.resources.recurring_section_history
import saveme.shared.generated.resources.recurring_section_upcoming
import saveme.shared.generated.resources.recurring_status_clash
import saveme.shared.generated.resources.recurring_status_deleted
import saveme.shared.generated.resources.recurring_status_waiting
import saveme.shared.generated.resources.recurring_unsynced_hint
import saveme.shared.generated.resources.recurring_update_after_sync
import saveme.shared.generated.resources.wallet_keep_mine
import saveme.shared.generated.resources.wallet_use_theirs

@Composable
fun RuleDetailScreen(
    state: RuleDetailState,
    onIntent: (RuleDetailIntent) -> Unit,
    snackbar: SnackbarHostState = remember { SnackbarHostState() },
) {
    AppScreen(
        snackbarHostState = snackbar,
        topBar = { RecurringBackBar(stringResource(Res.string.recurring_rule_title)) { onIntent(RuleDetailIntent.Back) } },
    ) {
        val view = state.view
        if (!state.loaded || view == null) {
            repeat(4) { Skeleton(Modifier.fillMaxWidth().height(52.dp)) }
            return@AppScreen
        }
        RuleListRow(view, statusModifier = Modifier.testTag("rule.status"))
        Summary(state)
        StatusArea(state, onIntent)
        Preview(state)
        Occurrences(state, onIntent)
        Actions(state, onIntent)
    }
    Dialogs(state, onIntent)
}

@Composable
private fun Summary(state: RuleDetailState) {
    val view = state.view ?: return
    val draft = view.shown
    Column(verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.xxs)) {
        cadenceEndText(draft.endDate, draft.occurrenceCount)?.let { Line(it) }
        Line(stringResource(if (draft.autoConfirm) Res.string.recurring_auto_on else Res.string.recurring_auto_off))
        view.authorName?.let { Line(stringResource(Res.string.recurring_author, it)) }
        view.walletName?.let { Line(it) }
        when (state.readOnlyReason) {
            ReadOnlyReason.Unknown -> Line(stringResource(Res.string.recurring_readonly_unknown))
            ReadOnlyReason.Ended -> Line(stringResource(Res.string.recurring_readonly_ended))
            ReadOnlyReason.Viewer -> Line(stringResource(Res.string.recurring_readonly_viewer))
            ReadOnlyReason.None -> Unit
        }
    }
}

@Composable
private fun Line(text: String) {
    Text(text, style = AppTheme.type.body, color = AppTheme.colors.ink2)
}

@Composable
private fun StatusArea(state: RuleDetailState, onIntent: (RuleDetailIntent) -> Unit) {
    when (val sync = state.view?.syncState ?: RecurringSyncState.None) {
        RecurringSyncState.None -> Unit
        RecurringSyncState.Waiting -> InlineAlert(AlertTone.Info, stringResource(Res.string.recurring_status_waiting), Modifier.testTag("rule.syncStatus"))
        is RecurringSyncState.Held -> InlineAlert(
            AlertTone.Info,
            stringResource(Res.string.recurring_held, state.heldOnTitle ?: stringResource(Res.string.recurring_rule_title)),
            Modifier.testTag("rule.syncStatus"),
        )
        is RecurringSyncState.Clash -> Column(
            verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.xs),
            modifier = Modifier.fillMaxWidth().background(AppTheme.colors.surface, AppTheme.shapes.card).padding(AppTheme.spacing.m),
        ) {
            if (sync.deletedElsewhere) {
                InlineAlert(AlertTone.Warning, stringResource(Res.string.recurring_status_deleted), Modifier.testTag("rule.syncStatus"))
                Text(stringResource(Res.string.recurring_deleted_body), style = AppTheme.type.body, color = AppTheme.colors.ink2)
                PrimaryButton(
                    stringResource(Res.string.action_accept_deletion), { onIntent(RuleDetailIntent.AcceptDeletion) },
                    Modifier.testTag("rule.acceptDeletion"), loading = state.busy,
                )
            } else {
                InlineAlert(AlertTone.Warning, stringResource(Res.string.recurring_status_clash), Modifier.testTag("rule.syncStatus"))
                Text(
                    stringResource(if (sync.mineIsDeletion) Res.string.recurring_clash_delete_body else Res.string.recurring_clash_body),
                    style = AppTheme.type.body, color = AppTheme.colors.ink2,
                )
                PrimaryButton(
                    stringResource(Res.string.wallet_keep_mine), { onIntent(RuleDetailIntent.KeepMine) },
                    Modifier.testTag("rule.keepMine"), loading = state.busy,
                )
                SecondaryButton(
                    stringResource(Res.string.wallet_use_theirs), { onIntent(RuleDetailIntent.UseTheirs) },
                    Modifier.testTag("rule.useTheirs"), enabled = !state.busy,
                )
            }
        }
        is RecurringSyncState.Refused -> Column(verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.xs)) {
            InlineAlert(
                AlertTone.Error,
                sync.text(isOccurrence = false, currency = state.view?.rule?.currency.orEmpty(), isTransfer = state.view?.rule?.counterWalletId != null),
                Modifier.testTag("rule.syncStatus"),
            )
            PrimaryButton(stringResource(Res.string.action_retry), { onIntent(RuleDetailIntent.TryAgain) }, Modifier.testTag("rule.tryAgain"), enabled = !state.busy)
            SecondaryButton(stringResource(Res.string.action_discard), { onIntent(RuleDetailIntent.Discard) }, Modifier.testTag("rule.discard"), enabled = !state.busy)
        }
    }
    if (state.datesUpdateAfterSync) {
        InlineAlert(AlertTone.Info, stringResource(Res.string.recurring_update_after_sync), Modifier.testTag("rule.updateAfterSync"))
    }
}

@Composable
private fun Preview(state: RuleDetailState) {
    if (!state.showPreview) return
    val view = state.view ?: return
    val payoutLabel = stringResource(Res.string.recurring_payout)
    val kind = view.shown.kind.moneyKind()
    PreviewDateList(
        items = state.preview.rounds.map { round ->
            PreviewDateItem(
                dateText = formatDate(round.date),
                amountMinor = round.amountMinor,
                kind = if (round.isPayout) MoneyKind.Income else kind,
                tag = if (round.isPayout) payoutLabel else null,
            )
        },
        currency = view.rule.currency,
        label = stringResource(Res.string.recurring_preview_label),
        modifier = Modifier.testTag("rule.preview"),
        caption = state.preview.payoutMinor?.let { stringResource(Res.string.recurring_payout_amount, formatMoney(it, view.rule.currency)) }
            ?: stringResource(if (view.rule.isPlaceholder) Res.string.recurring_unsynced_hint else Res.string.recurring_dates_after_sync),
        emptyText = stringResource(Res.string.recurring_preview_none),
    )
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = AppTheme.type.title, color = AppTheme.colors.ink, modifier = Modifier.padding(top = AppTheme.spacing.xs))
}

@Composable
private fun Occurrences(state: RuleDetailState, onIntent: (RuleDetailIntent) -> Unit) {
    val lists = state.occurrences
    if (state.showPreview && lists.isEmpty) return
    SectionTitle(stringResource(Res.string.recurring_occurrences_title))
    if (lists.isEmpty) {
        Line(stringResource(Res.string.recurring_occurrences_empty))
        return
    }
    var index = 0
    listOf(
        Res.string.recurring_section_due to lists.due,
        Res.string.recurring_section_upcoming to lists.upcoming,
        Res.string.recurring_section_history to lists.history,
    ).forEach { (title, items) ->
        if (items.isEmpty()) return@forEach
        Line(stringResource(title))
        items.forEach { view ->
            val i = index++
            OccurrenceListRow(view, modifier = Modifier.testTag("rule.occurrence.$i"), onClick = { onIntent(RuleDetailIntent.OccurrenceClicked(view.id)) })
        }
    }
}

@Composable
private fun Actions(state: RuleDetailState, onIntent: (RuleDetailIntent) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.xs)) {
        if (state.canEdit) {
            PrimaryButton(stringResource(Res.string.recurring_action_edit), { onIntent(RuleDetailIntent.EditClicked) }, Modifier.testTag("rule.edit"), enabled = !state.busy)
        }
        if (state.canChangeFrom) {
            SecondaryButton(stringResource(Res.string.recurring_action_change_from), { onIntent(RuleDetailIntent.ChangeFromClicked) }, Modifier.testTag("rule.changeFrom"), enabled = !state.busy)
        }
        if (state.canPause) {
            SecondaryButton(stringResource(Res.string.recurring_action_pause), { onIntent(RuleDetailIntent.PauseClicked) }, Modifier.testTag("rule.pause"), enabled = !state.busy)
        }
        if (state.canResume) {
            SecondaryButton(stringResource(Res.string.recurring_action_resume), { onIntent(RuleDetailIntent.ResumeClicked) }, Modifier.testTag("rule.resume"), enabled = !state.busy)
        }
        if (state.canEnd) {
            SecondaryButton(stringResource(Res.string.recurring_action_end), { onIntent(RuleDetailIntent.EndClicked) }, Modifier.testTag("rule.end"), enabled = !state.busy)
        }
        if (state.canCatchUp) {
            SecondaryButton(
                stringResource(Res.string.recurring_action_catch_up), { onIntent(RuleDetailIntent.CatchUpClicked) },
                Modifier.testTag("rule.catchUp"), enabled = !state.busy && !state.catchingUp,
            )
            CatchUpText(state)
        }
        if (state.canDelete) {
            DestructiveButton(stringResource(Res.string.recurring_action_delete), { onIntent(RuleDetailIntent.DeleteClicked) }, Modifier.testTag("rule.delete"), enabled = !state.busy)
        }
    }
}

@Composable
private fun CatchUpText(state: RuleDetailState) {
    val note = state.catchUpNote
    val (tone, text) = when (note) {
        null -> return Line(stringResource(Res.string.recurring_catch_up_hint))
        CatchUpNote.Done -> AlertTone.Info to stringResource(Res.string.recurring_catch_up_done)
        CatchUpNote.Offline -> AlertTone.Warning to stringResource(Res.string.recurring_catch_up_offline)
        CatchUpNote.NotAllowed -> AlertTone.Warning to stringResource(Res.string.recurring_catch_up_failed)
        is CatchUpNote.Failed -> AlertTone.Error to
            (note.requestId?.let { stringResource(Res.string.recurring_catch_up_failed_reference, it) } ?: stringResource(Res.string.recurring_catch_up_failed))
    }
    InlineAlert(tone, text, Modifier.testTag("rule.catchUpNote"))
}

@Composable
private fun Dialogs(state: RuleDetailState, onIntent: (RuleDetailIntent) -> Unit) {
    when (state.dialog) {
        RuleDialog.End -> AppAlertDialog(
            title = stringResource(Res.string.recurring_end_title),
            text = stringResource(Res.string.recurring_end_body),
            confirmLabel = stringResource(Res.string.recurring_end_confirm),
            onConfirm = { onIntent(RuleDetailIntent.DialogConfirmed) },
            dismissLabel = stringResource(Res.string.action_cancel),
            onDismiss = { onIntent(RuleDetailIntent.DialogDismissed) },
        )
        RuleDialog.Delete -> AppAlertDialog(
            title = stringResource(Res.string.recurring_delete_title),
            text = stringResource(Res.string.recurring_delete_body),
            confirmLabel = stringResource(Res.string.recurring_delete_confirm),
            onConfirm = { onIntent(RuleDetailIntent.DialogConfirmed) },
            dismissLabel = stringResource(Res.string.action_cancel),
            onDismiss = { onIntent(RuleDetailIntent.DialogDismissed) },
            destructive = true,
        )
        RuleDialog.None -> Unit
    }
}

@Preview
@Composable
private fun RuleDetailScreenPreview() {
    AppTheme { RuleDetailScreen(RuleDetailState(), {}) }
}
