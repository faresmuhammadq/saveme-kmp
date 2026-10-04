package com.fares.saveme.feature.recurring.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.fares.saveme.designsystem.component.AppIconButton
import com.fares.saveme.designsystem.icon.AppIcons
import com.fares.saveme.designsystem.component.AppTopAppBar
import com.fares.saveme.designsystem.component.MoneyKind
import com.fares.saveme.designsystem.component.OccurrenceRow
import com.fares.saveme.designsystem.component.OccurrenceVisualState
import com.fares.saveme.designsystem.component.RuleRow
import com.fares.saveme.designsystem.component.RuleStatePill
import com.fares.saveme.designsystem.format.formatDate
import com.fares.saveme.feature.recurring.domain.model.OccurrenceState
import com.fares.saveme.feature.recurring.domain.model.OccurrenceView
import com.fares.saveme.feature.recurring.domain.model.RuleStatus
import com.fares.saveme.feature.recurring.domain.model.RuleView
import com.fares.saveme.feature.transactions.domain.model.TransactionKind
import org.jetbrains.compose.resources.stringResource
import saveme.shared.generated.resources.Res
import saveme.shared.generated.resources.cd_back
import saveme.shared.generated.resources.recurring_amount_of_total
import saveme.shared.generated.resources.recurring_next_date
import saveme.shared.generated.resources.recurring_next_none
import saveme.shared.generated.resources.recurring_payout
import saveme.shared.generated.resources.recurring_payout_amount
import saveme.shared.generated.resources.recurring_rule_title
import com.fares.saveme.designsystem.component.formatMoney
import com.fares.saveme.feature.recurring.domain.model.AmountBasis
import com.fares.saveme.feature.recurring.domain.model.Preset

/** The app bar every recurring screen uses: the title and a back arrow. */
@Composable
fun RecurringBackBar(title: String, onBack: () -> Unit) {
    AppTopAppBar(
        title = title,
        navigationIcon = { AppIconButton(AppIcons.ArrowBack, stringResource(Res.string.cd_back), onBack) },
    )
}

internal fun TransactionKind.moneyKind(): MoneyKind = when (this) {
    TransactionKind.Income -> MoneyKind.Income
    TransactionKind.Expense -> MoneyKind.Expense
    else -> MoneyKind.Balance
}

internal fun OccurrenceState.visual(): OccurrenceVisualState = when (this) {
    OccurrenceState.Upcoming, OccurrenceState.Unknown -> OccurrenceVisualState.Upcoming
    OccurrenceState.Due -> OccurrenceVisualState.Due
    OccurrenceState.Missed -> OccurrenceVisualState.Missed
    OccurrenceState.Confirmed -> OccurrenceVisualState.Confirmed
    OccurrenceState.Skipped -> OccurrenceVisualState.Skipped
}

/** One occurrence as a list row: date, rule title, shown amount, state as icon and words, sync status. */
@Composable
fun OccurrenceListRow(
    view: OccurrenceView,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    statusModifier: Modifier = Modifier,
) {
    OccurrenceRow(
        dateText = formatDate(view.shownDate),
        title = view.ruleDescription?.takeIf { it.isNotBlank() } ?: stringResource(Res.string.recurring_rule_title),
        amountMinor = view.shownAmountMinor,
        currency = view.currency,
        kind = view.occurrence.kind.moneyKind(),
        state = view.state.visual(),
        stateLabel = stringResource(view.state.labelRes()),
        modifier = modifier,
        payoutLabel = if (view.isPayout) stringResource(Res.string.recurring_payout) else null,
        status = view.syncState.statusLine(),
        onClick = onClick,
        statusModifier = statusModifier,
    )
}

internal fun RuleStatus.pill(): RuleStatePill = when (this) {
    RuleStatus.Paused -> RuleStatePill.Paused
    RuleStatus.Ended -> RuleStatePill.Ended
    else -> RuleStatePill.Active
}

/** One rule as a list row: title, cadence words, next date, amount (or the share of a total) and status. */
@Composable
fun RuleListRow(
    view: RuleView,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    statusModifier: Modifier = Modifier,
) {
    val draft = view.shown
    val rule = view.rule
    val noNext = stringResource(Res.string.recurring_next_none)
    val next = view.nextDate?.let { stringResource(Res.string.recurring_next_date, formatDate(it)) } ?: noNext
    val note = when {
        draft.amountBasis == AmountBasis.Total && draft.occurrenceCount != null ->
            stringResource(Res.string.recurring_amount_of_total, formatMoney(draft.amountMinor / draft.occurrenceCount, rule.currency), formatMoney(draft.amountMinor, rule.currency))
        draft.preset == Preset.Gameya && draft.occurrenceCount != null ->
            stringResource(Res.string.recurring_payout_amount, formatMoney(draft.amountMinor * draft.occurrenceCount, rule.currency))
        else -> null
    }
    RuleRow(
        title = draft.description?.takeIf { it.isNotBlank() } ?: stringResource(Res.string.recurring_rule_title),
        cadence = cadenceText(draft.frequency, draft.interval, draft.anchorDay, draft.startDate),
        nextText = next,
        amountMinor = if (draft.amountBasis == AmountBasis.Total && draft.occurrenceCount != null) draft.amountMinor / draft.occurrenceCount else draft.amountMinor,
        currency = rule.currency,
        kind = draft.kind.moneyKind(),
        pill = rule.status.pill(),
        pillLabel = stringResource(rule.status.labelRes()),
        modifier = modifier,
        amountNote = note,
        status = view.syncState.statusLine(),
        onClick = onClick,
        statusModifier = statusModifier,
    )
}
