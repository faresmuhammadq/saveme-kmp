package com.fares.saveme.feature.recurring.ui.rule

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.fares.saveme.designsystem.format.formatDayMonth
import com.fares.saveme.feature.recurring.domain.model.OccurrenceState
import com.fares.saveme.feature.recurring.domain.model.Preset
import com.fares.saveme.feature.recurring.ui.CalmRound
import com.fares.saveme.feature.recurring.ui.CalmRoundsGrid
import com.fares.saveme.feature.recurring.ui.RecurringHeading
import com.fares.saveme.feature.recurring.ui.calmMoney
import com.fares.saveme.feature.recurring.ui.label
import saveme.shared.generated.resources.recurring_auto_confirm
import saveme.shared.generated.resources.recurring_calm_added_by
import saveme.shared.generated.resources.recurring_calm_more
import saveme.shared.generated.resources.recurring_calm_next_round
import saveme.shared.generated.resources.recurring_calm_off
import saveme.shared.generated.resources.recurring_calm_on
import saveme.shared.generated.resources.recurring_calm_paid_of
import saveme.shared.generated.resources.recurring_calm_payout_on
import saveme.shared.generated.resources.recurring_calm_rounds
import saveme.shared.generated.resources.recurring_calm_rounds_cd
import saveme.shared.generated.resources.recurring_calm_you
import saveme.shared.generated.resources.recurring_field_payout_category
import saveme.shared.generated.resources.tx_field_category
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.fares.saveme.designsystem.component.AlertTone
import com.fares.saveme.designsystem.component.AppAlertDialog
import com.fares.saveme.designsystem.component.CalmCard
import com.fares.saveme.designsystem.component.CalmScreen
import com.fares.saveme.designsystem.component.CalmSectionTitle
import com.fares.saveme.designsystem.component.CalmTitle
import com.fares.saveme.designsystem.component.InlineAlert
import com.fares.saveme.designsystem.component.MoneyKind
import com.fares.saveme.designsystem.component.PreviewDateItem
import com.fares.saveme.designsystem.component.WalletStatusLine
import com.fares.saveme.designsystem.component.PrimaryButton
import com.fares.saveme.designsystem.component.SecondaryButton
import com.fares.saveme.designsystem.component.Skeleton
import com.fares.saveme.designsystem.component.formatMoney
import com.fares.saveme.designsystem.format.formatDate
import com.fares.saveme.designsystem.icon.AppIcons
import com.fares.saveme.designsystem.icon.RecurringPause
import com.fares.saveme.designsystem.icon.RecurringPencil
import com.fares.saveme.designsystem.icon.RecurringPlay
import com.fares.saveme.designsystem.icon.RecurringStop
import com.fares.saveme.designsystem.theme.AppTheme
import com.fares.saveme.feature.recurring.domain.model.AmountBasis
import com.fares.saveme.feature.recurring.domain.model.RecurringSyncState
import com.fares.saveme.feature.recurring.domain.model.RuleAuthor
import com.fares.saveme.feature.recurring.domain.model.RuleView
import com.fares.saveme.feature.recurring.ui.CalmLabelValue
import com.fares.saveme.feature.recurring.ui.CalmOccurrenceCard
import com.fares.saveme.feature.recurring.ui.CalmPillAction
import com.fares.saveme.feature.recurring.ui.CalmPillRow
import com.fares.saveme.feature.recurring.ui.CalmPreviewCard
import com.fares.saveme.feature.recurring.ui.CalmRows
import com.fares.saveme.feature.recurring.ui.calmPill
import com.fares.saveme.feature.recurring.ui.cadenceEndText
import com.fares.saveme.feature.recurring.ui.cadenceText
import com.fares.saveme.feature.recurring.ui.labelRes
import com.fares.saveme.feature.recurring.ui.moneyKind
import com.fares.saveme.feature.recurring.ui.statusLine
import com.fares.saveme.feature.recurring.ui.text
import org.jetbrains.compose.resources.stringResource
import saveme.shared.generated.resources.Res
import saveme.shared.generated.resources.action_accept_deletion
import saveme.shared.generated.resources.action_cancel
import saveme.shared.generated.resources.action_discard
import saveme.shared.generated.resources.action_retry
import saveme.shared.generated.resources.cd_back
import saveme.shared.generated.resources.recurring_action_catch_up
import saveme.shared.generated.resources.recurring_action_change_from
import saveme.shared.generated.resources.recurring_action_delete
import saveme.shared.generated.resources.recurring_action_edit
import saveme.shared.generated.resources.recurring_action_end
import saveme.shared.generated.resources.recurring_action_pause
import saveme.shared.generated.resources.recurring_action_resume
import saveme.shared.generated.resources.recurring_author
import saveme.shared.generated.resources.recurring_author_former
import saveme.shared.generated.resources.recurring_author_other
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
import saveme.shared.generated.resources.recurring_next_date
import saveme.shared.generated.resources.recurring_next_none
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
import saveme.shared.generated.resources.tx_field_wallet
import saveme.shared.generated.resources.wallet_keep_mine
import saveme.shared.generated.resources.wallet_use_theirs

@Composable
fun RuleDetailScreen(
    state: RuleDetailState,
    onIntent: (RuleDetailIntent) -> Unit,
    snackbar: SnackbarHostState = remember { SnackbarHostState() },
) {
    val view = state.view
    val loaded = state.loaded && view != null
    CalmScreen(
        title = if (loaded) null else stringResource(Res.string.recurring_rule_title),
        onBack = { onIntent(RuleDetailIntent.Back) },
        backDescription = stringResource(Res.string.cd_back),
        snackbarHostState = snackbar,
        actions = { if (view != null && loaded) view.rule.status.calmPill(stringResource(view.rule.status.labelRes())) },
        titleSlot = if (view != null && loaded) ({ Header(view) }) else null,
    ) {
        if (view == null || !loaded) {
            repeat(4) { Skeleton(Modifier.fillMaxWidth().height(52.dp)) }
            return@CalmScreen
        }
        view.syncState.statusLine()?.let { WalletStatusLine(it, Modifier.testTag("rule.status")) }
        StatusArea(state, onIntent)
        Rounds(state)
        Actions(state, onIntent)
        Summary(state)
        Preview(state)
        Occurrences(state, onIntent)
    }
    Dialogs(state, onIntent)
}

@Composable
private fun Header(view: RuleView) {
    val draft = view.shown
    val amount = calmMoney(
        if (draft.amountBasis == AmountBasis.Total && draft.occurrenceCount != null) draft.amountMinor / draft.occurrenceCount else draft.amountMinor,
        view.rule.currency,
    )
    val cadence = cadenceText(draft.frequency, draft.interval, draft.anchorDay, draft.startDate).replaceFirstChar { it.lowercase() }
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        draft.preset.labelRes()?.let {
            Text(
                stringResource(it).uppercase(),
                style = AppTheme.type.label.copy(fontSize = 13.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.06.em),
                color = AppTheme.colors.primary,
                maxLines = 1,
            )
        }
        CalmTitle(draft.description?.takeIf { it.isNotBlank() } ?: stringResource(Res.string.recurring_rule_title))
        Text(
            "$amount $cadence",
            style = AppTheme.type.body.copy(fontSize = 14.sp),
            color = AppTheme.colors.ink2,
            modifier = Modifier.padding(top = 2.dp),
        )
    }
}

/** The rounds card of a rule with a fixed number of rounds: which are paid, which is next, where the payout falls. */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun Rounds(state: RuleDetailState) {
    val view = state.view ?: return
    val draft = view.shown
    val total = draft.occurrenceCount ?: return
    if (total !in 1..120) return
    val known = (state.occurrences.due + state.occurrences.upcoming + state.occurrences.history).associateBy { it.occurrence.ordinal }
    val preview = state.preview.rounds.associateBy { it.ordinal }
    val resolved = known.values.filter { it.state == OccurrenceState.Confirmed || it.state == OccurrenceState.Skipped }.map { it.occurrence.ordinal }.toSet()
    val paid = known.values.count { it.state == OccurrenceState.Confirmed }
    val next = (1..total).firstOrNull { it !in resolved }
    val gameya = draft.preset == Preset.Gameya
    val payoutOrdinal = if (gameya) {
        known.values.firstOrNull { it.isPayout }?.occurrence?.ordinal ?: preview.values.firstOrNull { it.isPayout }?.ordinal ?: draft.payoutRound
    } else {
        null
    }
    val currency = view.rule.currency
    val nextDate = next?.let { known[it]?.shownDate ?: preview[it]?.date }
    val payoutDate = payoutOrdinal?.let { known[it]?.shownDate ?: preview[it]?.date }
    val payoutAmount = if (gameya) state.preview.payoutMinor ?: (draft.amountMinor * total) else null
    CalmCard(padding = PaddingValues(18.dp), modifier = Modifier.testTag("rule.rounds")) {
        Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.fillMaxWidth()) {
            RecurringHeading(stringResource(Res.string.recurring_calm_rounds), Modifier.weight(1f))
            Text(
                stringResource(Res.string.recurring_calm_paid_of, paid, total),
                style = AppTheme.type.caption.copy(fontSize = 13.sp, fontWeight = FontWeight.SemiBold),
                color = AppTheme.colors.ink2,
            )
        }
        CalmRoundsGrid(
            rounds = (1..total).map { CalmRound(it, paid = it in resolved && it != payoutOrdinal, next = it == next, payout = it == payoutOrdinal) },
            description = stringResource(Res.string.recurring_calm_rounds_cd, paid, total),
            modifier = Modifier.padding(top = 14.dp),
        )
        if (next != null || payoutDate != null) {
            androidx.compose.foundation.layout.FlowRow(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
                modifier = Modifier.padding(top = 14.dp),
            ) {
                if (next != null && nextDate != null) {
                    Text(
                        stringResource(Res.string.recurring_calm_next_round, next, formatDayMonth(nextDate)),
                        style = AppTheme.type.caption.copy(fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold),
                        color = AppTheme.colors.ink2,
                    )
                }
                if (payoutDate != null && payoutAmount != null) {
                    Text(
                        stringResource(Res.string.recurring_calm_payout_on, formatDayMonth(payoutDate), calmMoney(payoutAmount, currency, showPlus = true)),
                        style = AppTheme.type.caption.copy(fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold),
                        color = AppTheme.money.positive,
                    )
                }
            }
        }
    }
}

@Composable
private fun Summary(state: RuleDetailState) {
    val view = state.view ?: return
    val draft = view.shown
    val rows = buildList<@Composable () -> Unit> {
        view.walletName?.let { add { CalmLabelValue(stringResource(Res.string.tx_field_wallet), it) } }
        if (draft.preset == Preset.Gameya) {
            state.payoutCategory?.let { add { CalmLabelValue(stringResource(Res.string.recurring_field_payout_category), it.label()) } }
        } else {
            state.category?.let { add { CalmLabelValue(stringResource(Res.string.tx_field_category), it.label()) } }
        }
        if (!draft.preset.let { it == Preset.Gameya || it == Preset.Installment }) {
            cadenceEndText(draft.endDate, draft.occurrenceCount)?.let { add { Line(it) } }
        }
        add { CalmLabelValue(stringResource(Res.string.recurring_auto_confirm), stringResource(if (draft.autoConfirm) Res.string.recurring_calm_on else Res.string.recurring_calm_off)) }
        when (val author = view.author) {
            RuleAuthor.You -> add { CalmLabelValue(stringResource(Res.string.recurring_calm_added_by), stringResource(Res.string.recurring_calm_you)) }
            is RuleAuthor.Member -> add { AuthorLine(stringResource(Res.string.recurring_author, author.name)) }
            RuleAuthor.AnotherMember -> add { AuthorLine(stringResource(Res.string.recurring_author_other)) }
            RuleAuthor.FormerMember -> add { AuthorLine(stringResource(Res.string.recurring_author_former)) }
        }
        when (state.readOnlyReason) {
            ReadOnlyReason.Unknown -> add { Line(stringResource(Res.string.recurring_readonly_unknown)) }
            ReadOnlyReason.Ended -> add { Line(stringResource(Res.string.recurring_readonly_ended)) }
            ReadOnlyReason.Viewer -> add { Line(stringResource(Res.string.recurring_readonly_viewer)) }
            ReadOnlyReason.None -> Unit
        }
    }
    CalmRows(rows)
}

@Composable
private fun AuthorLine(text: String) {
    Text(
        text, style = AppTheme.type.body, color = AppTheme.colors.ink2,
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp).testTag("ruleDetail.author"),
    )
}

@Composable
private fun Line(text: String) {
    Text(text, style = AppTheme.type.body, color = AppTheme.colors.ink2, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp))
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
        is RecurringSyncState.Clash -> CalmCard {
            Column(verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.xs)) {
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
    CalmPreviewCard(
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
private fun Occurrences(state: RuleDetailState, onIntent: (RuleDetailIntent) -> Unit) {
    val lists = state.occurrences
    if (state.showPreview && lists.isEmpty) return
    CalmSectionTitle(stringResource(Res.string.recurring_occurrences_title))
    if (lists.isEmpty) {
        Text(stringResource(Res.string.recurring_occurrences_empty), style = AppTheme.type.body, color = AppTheme.colors.ink2)
        return
    }
    var index = 0
    listOf(
        Res.string.recurring_section_due to lists.due,
        Res.string.recurring_section_upcoming to lists.upcoming,
        Res.string.recurring_section_history to lists.history,
    ).forEach { (title, items) ->
        if (items.isEmpty()) return@forEach
        Text(stringResource(title), style = AppTheme.type.label, color = AppTheme.colors.ink2)
        items.forEach { view ->
            val i = index++
            CalmOccurrenceCard(view, modifier = Modifier.testTag("rule.occurrence.$i"), onClick = { onIntent(RuleDetailIntent.OccurrenceClicked(view.id)) })
        }
    }
}

@Composable
private fun Actions(state: RuleDetailState, onIntent: (RuleDetailIntent) -> Unit) {
    var more by rememberSaveable { mutableStateOf(false) }
    val hasMore = state.canEnd || state.canCatchUp || state.canDelete
    Column(verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.xs)) {
        CalmPillRow {
            if (state.canEdit) {
                CalmPillAction(
                    AppIcons.RecurringPencil, stringResource(Res.string.recurring_action_edit), { onIntent(RuleDetailIntent.EditClicked) },
                    Modifier.testTag("rule.edit"), enabled = !state.busy,
                )
            }
            if (state.canChangeFrom) {
                CalmPillAction(
                    AppIcons.Calendar, stringResource(Res.string.recurring_action_change_from), { onIntent(RuleDetailIntent.ChangeFromClicked) },
                    Modifier.testTag("rule.changeFrom"), enabled = !state.busy,
                )
            }
            if (state.canPause) {
                CalmPillAction(
                    AppIcons.RecurringPause, stringResource(Res.string.recurring_action_pause), { onIntent(RuleDetailIntent.PauseClicked) },
                    Modifier.testTag("rule.pause"), enabled = !state.busy,
                )
            }
            if (state.canResume) {
                CalmPillAction(
                    AppIcons.RecurringPlay, stringResource(Res.string.recurring_action_resume), { onIntent(RuleDetailIntent.ResumeClicked) },
                    Modifier.testTag("rule.resume"), enabled = !state.busy,
                )
            }
            if (hasMore) {
                CalmPillAction(AppIcons.More, stringResource(Res.string.recurring_calm_more), { more = true }, Modifier.testTag("rule.more"))
            }
            if (hasMore && more) {
                if (state.canEnd) {
                    CalmPillAction(
                        AppIcons.RecurringStop, stringResource(Res.string.recurring_action_end), { onIntent(RuleDetailIntent.EndClicked) },
                        Modifier.testTag("rule.end"), enabled = !state.busy,
                    )
                }
                if (state.canCatchUp) {
                    CalmPillAction(
                        AppIcons.Sync, stringResource(Res.string.recurring_action_catch_up), { onIntent(RuleDetailIntent.CatchUpClicked) },
                        Modifier.testTag("rule.catchUp"), enabled = !state.busy && !state.catchingUp,
                    )
                }
                if (state.canDelete) {
                    CalmPillAction(
                        AppIcons.Delete, stringResource(Res.string.recurring_action_delete), { onIntent(RuleDetailIntent.DeleteClicked) },
                        Modifier.testTag("rule.delete"), enabled = !state.busy, tint = AppTheme.colors.error,
                    )
                }
            }
        }
        if (state.canCatchUp && (more || state.catchUpNote != null)) CatchUpText(state)
    }
}

@Composable
private fun CatchUpText(state: RuleDetailState) {
    val note = state.catchUpNote
    val (tone, text) = when (note) {
        null -> return Text(stringResource(Res.string.recurring_catch_up_hint), style = AppTheme.type.caption, color = AppTheme.colors.caption)
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
