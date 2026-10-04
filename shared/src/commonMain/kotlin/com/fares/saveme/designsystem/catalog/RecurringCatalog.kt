package com.fares.saveme.designsystem.catalog

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import com.fares.saveme.designsystem.component.DueCard
import com.fares.saveme.designsystem.component.MoneyKind
import com.fares.saveme.designsystem.component.OccurrenceRow
import com.fares.saveme.designsystem.component.OccurrenceVisualState
import com.fares.saveme.designsystem.component.PreviewDateItem
import com.fares.saveme.designsystem.component.PreviewDateList
import com.fares.saveme.designsystem.component.RuleRow
import com.fares.saveme.designsystem.component.RuleStatePill
import com.fares.saveme.designsystem.component.WalletCardStatus
import com.fares.saveme.designsystem.component.WalletCardStatusKind
import com.fares.saveme.designsystem.format.formatDate
import kotlinx.datetime.LocalDate
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import saveme.shared.generated.resources.Res
import saveme.shared.generated.resources.recurring_cadence_monthly
import saveme.shared.generated.resources.recurring_catalog_gameya
import saveme.shared.generated.resources.recurring_catalog_installment
import saveme.shared.generated.resources.recurring_catalog_rent
import saveme.shared.generated.resources.recurring_catalog_section
import saveme.shared.generated.resources.recurring_dates_after_sync
import saveme.shared.generated.resources.recurring_due_count
import saveme.shared.generated.resources.recurring_payout
import saveme.shared.generated.resources.recurring_preview_label
import saveme.shared.generated.resources.recurring_state_confirmed
import saveme.shared.generated.resources.recurring_state_due
import saveme.shared.generated.resources.recurring_state_missed
import saveme.shared.generated.resources.recurring_state_skipped
import saveme.shared.generated.resources.recurring_state_upcoming
import saveme.shared.generated.resources.recurring_status_active
import saveme.shared.generated.resources.recurring_status_ended
import saveme.shared.generated.resources.recurring_status_paused
import saveme.shared.generated.resources.recurring_status_waiting
import saveme.shared.generated.resources.recurring_status_not_saved
import saveme.shared.generated.resources.recurring_status_clash
import saveme.shared.generated.resources.recurring_cd_due_card
import saveme.shared.generated.resources.recurring_next_date

/** Recurring rows and cards (spec 010): occurrence in every state, rule rows, Home's due card, the date preview. */
@Composable
fun RecurringSection() {
    RecurringOccurrencesSection()
    RecurringRulesSection()
}

@Composable
fun RecurringOccurrencesSection() = CatalogSection(Res.string.recurring_catalog_section) {
    val rent = stringResource(Res.string.recurring_catalog_rent)
    val payout = stringResource(Res.string.recurring_payout)
    val nov1 = formatDate(LocalDate(2026, 11, 1))
    Column {
        OccurrenceRow(nov1, rent, 450_000, "EGP", MoneyKind.Expense, OccurrenceVisualState.Upcoming, stringResource(Res.string.recurring_state_upcoming))
        OccurrenceRow(
            formatDate(LocalDate(2026, 10, 1)), rent, 450_000, "EGP", MoneyKind.Expense, OccurrenceVisualState.Due,
            stringResource(Res.string.recurring_state_due),
            status = WalletCardStatus(WalletCardStatusKind.Waiting, stringResource(Res.string.recurring_status_waiting)),
        )
        OccurrenceRow(
            formatDate(LocalDate(2026, 9, 1)), rent, 450_000, "EGP", MoneyKind.Expense, OccurrenceVisualState.Missed,
            stringResource(Res.string.recurring_state_missed),
            status = WalletCardStatus(WalletCardStatusKind.Changed, stringResource(Res.string.recurring_status_clash)),
        )
        OccurrenceRow(
            formatDate(LocalDate(2026, 8, 1)), rent, 450_000, "EGP", MoneyKind.Expense, OccurrenceVisualState.Confirmed,
            stringResource(Res.string.recurring_state_confirmed),
        )
        OccurrenceRow(
            formatDate(LocalDate(2026, 7, 1)), rent, 450_000, "EGP", MoneyKind.Expense, OccurrenceVisualState.Skipped,
            stringResource(Res.string.recurring_state_skipped),
            status = WalletCardStatus(WalletCardStatusKind.NotSaved, stringResource(Res.string.recurring_status_not_saved)),
        )
        OccurrenceRow(
            nov1, stringResource(Res.string.recurring_catalog_gameya), 1_000_000, "EGP", MoneyKind.Income, OccurrenceVisualState.Due,
            stringResource(Res.string.recurring_state_due), payoutLabel = payout,
        )
    }
}

@Composable
fun RecurringRulesSection() = CatalogSection(Res.string.recurring_catalog_section) {
    val rent = stringResource(Res.string.recurring_catalog_rent)
    val payout = stringResource(Res.string.recurring_payout)
    val nov1 = formatDate(LocalDate(2026, 11, 1))
    Column {
        RuleRow(
            rent, pluralStringResource(Res.plurals.recurring_cadence_monthly, 1, 1, 1), stringResource(Res.string.recurring_next_date, nov1),
            450_000, "EGP", MoneyKind.Expense, RuleStatePill.Active, stringResource(Res.string.recurring_status_active),
        )
        RuleRow(
            stringResource(Res.string.recurring_catalog_installment), pluralStringResource(Res.plurals.recurring_cadence_monthly, 1, 1, 15),
            stringResource(Res.string.recurring_dates_after_sync),
            83_333, "EGP", MoneyKind.Expense, RuleStatePill.Paused, stringResource(Res.string.recurring_status_paused),
            amountNote = "10,000.00",
            status = WalletCardStatus(WalletCardStatusKind.Waiting, stringResource(Res.string.recurring_status_waiting)),
        )
        RuleRow(
            rent, pluralStringResource(Res.plurals.recurring_cadence_monthly, 1, 1, 1), null,
            450_000, "EGP", MoneyKind.Expense, RuleStatePill.Ended, stringResource(Res.string.recurring_status_ended),
        )
    }
    val countText = pluralStringResource(Res.plurals.recurring_due_count, 3, 3)
    DueCard(countText, rent, nov1, contentDescription = stringResource(Res.string.recurring_cd_due_card, countText), onClick = {})
    PreviewDateList(
        listOf(
            PreviewDateItem(nov1, 450_000, MoneyKind.Expense),
            PreviewDateItem(formatDate(LocalDate(2026, 12, 1)), 450_000, MoneyKind.Expense),
            PreviewDateItem(formatDate(LocalDate(2027, 1, 1)), 4_500_000, MoneyKind.Income, tag = payout),
        ),
        "EGP", stringResource(Res.string.recurring_preview_label), caption = stringResource(Res.string.recurring_dates_after_sync),
    )
}
