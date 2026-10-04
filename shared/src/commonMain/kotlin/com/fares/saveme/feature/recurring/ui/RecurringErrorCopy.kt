package com.fares.saveme.feature.recurring.ui

import androidx.compose.runtime.Composable
import com.fares.saveme.designsystem.component.WalletCardStatus
import com.fares.saveme.designsystem.component.WalletCardStatusKind
import com.fares.saveme.feature.recurring.domain.model.AutoConfirmRefusal
import com.fares.saveme.feature.recurring.domain.model.ConfirmField
import com.fares.saveme.feature.recurring.domain.model.Frequency
import com.fares.saveme.feature.recurring.domain.model.OccurrenceState
import com.fares.saveme.feature.recurring.domain.model.Preset
import com.fares.saveme.feature.recurring.domain.model.RecurringSyncState
import com.fares.saveme.feature.recurring.domain.model.RuleField
import com.fares.saveme.feature.recurring.domain.model.RuleFieldProblem
import com.fares.saveme.feature.recurring.domain.model.RuleStatus
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import saveme.shared.generated.resources.Res
import saveme.shared.generated.resources.recurring_auto_refusal_currency
import saveme.shared.generated.resources.recurring_auto_refusal_destination
import saveme.shared.generated.resources.recurring_auto_refusal_not_permitted
import saveme.shared.generated.resources.recurring_auto_refusal_unknown
import saveme.shared.generated.resources.recurring_error_amount_digits
import saveme.shared.generated.resources.recurring_error_amount_empty
import saveme.shared.generated.resources.recurring_error_amount_positive
import saveme.shared.generated.resources.recurring_error_category_required
import saveme.shared.generated.resources.recurring_error_cuts_round
import saveme.shared.generated.resources.recurring_error_date_range
import saveme.shared.generated.resources.recurring_error_destination_required
import saveme.shared.generated.resources.recurring_error_from_range
import saveme.shared.generated.resources.recurring_error_immutable
import saveme.shared.generated.resources.recurring_error_invalid
import saveme.shared.generated.resources.recurring_error_out_of_range
import saveme.shared.generated.resources.recurring_error_payout_category
import saveme.shared.generated.resources.recurring_error_payout_large
import saveme.shared.generated.resources.recurring_error_required
import saveme.shared.generated.resources.recurring_error_too_long
import saveme.shared.generated.resources.recurring_error_total_small
import saveme.shared.generated.resources.recurring_frequency_daily
import saveme.shared.generated.resources.recurring_frequency_monthly
import saveme.shared.generated.resources.recurring_frequency_unknown
import saveme.shared.generated.resources.recurring_frequency_weekly
import saveme.shared.generated.resources.recurring_frequency_yearly
import saveme.shared.generated.resources.recurring_frozen_reason
import saveme.shared.generated.resources.recurring_preset_custom
import saveme.shared.generated.resources.recurring_preset_gameya
import saveme.shared.generated.resources.recurring_preset_installment
import saveme.shared.generated.resources.recurring_reason_category
import saveme.shared.generated.resources.recurring_reason_confirmation
import saveme.shared.generated.resources.recurring_reason_currencies_differ
import saveme.shared.generated.resources.recurring_reason_currency_changed
import saveme.shared.generated.resources.recurring_reason_destination
import saveme.shared.generated.resources.recurring_reason_gone
import saveme.shared.generated.resources.recurring_reason_immutable
import saveme.shared.generated.resources.recurring_reason_invalid
import saveme.shared.generated.resources.recurring_reason_no_right
import saveme.shared.generated.resources.recurring_reason_no_right_other
import saveme.shared.generated.resources.recurring_reason_not_verified
import saveme.shared.generated.resources.recurring_reason_occurrence_gone
import saveme.shared.generated.resources.recurring_reason_other
import saveme.shared.generated.resources.recurring_reason_other_plain
import saveme.shared.generated.resources.recurring_reason_range
import saveme.shared.generated.resources.recurring_state_confirmed
import saveme.shared.generated.resources.recurring_state_due
import saveme.shared.generated.resources.recurring_state_missed
import saveme.shared.generated.resources.recurring_state_skipped
import saveme.shared.generated.resources.recurring_state_unknown
import saveme.shared.generated.resources.recurring_state_upcoming
import saveme.shared.generated.resources.recurring_status_active
import saveme.shared.generated.resources.recurring_status_clash
import saveme.shared.generated.resources.recurring_status_deleted
import saveme.shared.generated.resources.recurring_status_ended
import saveme.shared.generated.resources.recurring_status_not_saved
import saveme.shared.generated.resources.recurring_status_paused
import saveme.shared.generated.resources.recurring_status_unknown
import saveme.shared.generated.resources.recurring_status_waiting
import saveme.shared.generated.resources.tx_error_amount_range

/** What a refusal means to the person, chosen from the problem code and the pointed fields, never from the server's text (P-02.4, P-06.5). */
enum class RecurringRefusalKind {
    NotVerified, NoLongerAvailable, NoRight, NoRightInOtherWallet, CategoryUnusable, DestinationUnavailable,
    CurrencyChanged, CurrenciesDiffer, OutOfRange, ConfirmationRefused, Immutable, Invalid, Other,
}

/**
 * [fields] maps the plain field name (see `RecurringFields`) to the error code at its pointer; [isTransfer] picks
 * "the other wallet" for `FORBIDDEN`. An occurrence's `/status` pointer means the destination is gone or the
 * currencies differ (FR-025).
 */
fun recurringRefusalKind(code: String?, fields: Map<String, String>, isTransfer: Boolean = false): RecurringRefusalKind = when (code) {
    "EMAIL_NOT_VERIFIED" -> RecurringRefusalKind.NotVerified
    "NOT_FOUND" -> RecurringRefusalKind.NoLongerAvailable
    "FORBIDDEN" -> if (isTransfer) RecurringRefusalKind.NoRightInOtherWallet else RecurringRefusalKind.NoRight
    "VALIDATION_FAILED", "INVALID_ID" -> when {
        fields["category"] == "INVALID_ENUM_VALUE" || fields["payoutCategory"] == "INVALID_ENUM_VALUE" -> RecurringRefusalKind.CategoryUnusable
        fields["destination"] == "CURRENCY_MISMATCH" -> RecurringRefusalKind.CurrenciesDiffer
        fields["destination"] in setOf("INVALID_ENUM_VALUE", "REQUIRED") -> RecurringRefusalKind.DestinationUnavailable
        fields["currency"] == "CURRENCY_MISMATCH" -> RecurringRefusalKind.CurrencyChanged
        fields["status"] == "CURRENCY_MISMATCH" -> RecurringRefusalKind.CurrenciesDiffer
        fields["status"] == "INVALID_ENUM_VALUE" && isTransfer -> RecurringRefusalKind.DestinationUnavailable
        fields["status"] != null -> RecurringRefusalKind.ConfirmationRefused
        fields.values.any { it == "IMMUTABLE_FIELD" } -> RecurringRefusalKind.Immutable
        fields.values.any { it == "OUT_OF_RANGE" } -> RecurringRefusalKind.OutOfRange
        else -> RecurringRefusalKind.Invalid
    }
    else -> RecurringRefusalKind.Other
}

fun RecurringRefusalKind.messageRes(): StringResource = when (this) {
    RecurringRefusalKind.NotVerified -> Res.string.recurring_reason_not_verified
    RecurringRefusalKind.NoLongerAvailable -> Res.string.recurring_reason_gone
    RecurringRefusalKind.NoRight -> Res.string.recurring_reason_no_right
    RecurringRefusalKind.NoRightInOtherWallet -> Res.string.recurring_reason_no_right_other
    RecurringRefusalKind.CategoryUnusable -> Res.string.recurring_reason_category
    RecurringRefusalKind.DestinationUnavailable -> Res.string.recurring_reason_destination
    RecurringRefusalKind.CurrencyChanged -> Res.string.recurring_reason_currency_changed
    RecurringRefusalKind.CurrenciesDiffer -> Res.string.recurring_reason_currencies_differ
    RecurringRefusalKind.OutOfRange -> Res.string.recurring_reason_range
    RecurringRefusalKind.ConfirmationRefused -> Res.string.recurring_reason_confirmation
    RecurringRefusalKind.Immutable -> Res.string.recurring_reason_immutable
    RecurringRefusalKind.Invalid -> Res.string.recurring_reason_invalid
    RecurringRefusalKind.Other -> Res.string.recurring_reason_other_plain
}

/** A "gone" refusal of an occurrence reads "This one is no longer available". */
fun RecurringRefusalKind.messageRes(isOccurrence: Boolean): StringResource =
    if (isOccurrence && this == RecurringRefusalKind.NoLongerAvailable) Res.string.recurring_reason_occurrence_gone else messageRes()

/** [currency] is the wallet's present currency; [isTransfer] decides which wallet "no right" names. */
@Composable
fun RecurringSyncState.Refused.text(isOccurrence: Boolean, currency: String = "", isTransfer: Boolean = false): String {
    val kind = recurringRefusalKind(code, fields, isTransfer)
    return when {
        kind == RecurringRefusalKind.Other && requestId != null -> stringResource(Res.string.recurring_reason_other, requestId)
        kind == RecurringRefusalKind.CurrencyChanged -> stringResource(Res.string.recurring_reason_currency_changed, currency)
        else -> stringResource(kind.messageRes(isOccurrence))
    }
}

/** "Try again" is offered unchanged only when sending again can cure it (an unverified address). */
val RecurringRefusalKind.canRetryUnchanged: Boolean get() = this == RecurringRefusalKind.NotVerified

fun AutoConfirmRefusal.messageRes(): StringResource = when (this) {
    AutoConfirmRefusal.NotPermitted -> Res.string.recurring_auto_refusal_not_permitted
    AutoConfirmRefusal.CounterWalletUnavailable -> Res.string.recurring_auto_refusal_destination
    AutoConfirmRefusal.CurrencyMismatch -> Res.string.recurring_auto_refusal_currency
    AutoConfirmRefusal.Unknown -> Res.string.recurring_auto_refusal_unknown
}

/** One message per problem found before anything is queued, or reported at a field by the server (FR-005, FR-035). */
fun RuleFieldProblem.messageRes(field: RuleField? = null): StringResource = when (this) {
    RuleFieldProblem.Required -> when (field) {
        RuleField.Amount -> Res.string.recurring_error_amount_empty
        RuleField.Category -> Res.string.recurring_error_category_required
        RuleField.Destination -> Res.string.recurring_error_destination_required
        RuleField.PayoutCategory -> Res.string.recurring_error_payout_category
        else -> Res.string.recurring_error_required
    }
    RuleFieldProblem.Invalid -> if (field == RuleField.Amount) Res.string.recurring_error_amount_positive else Res.string.recurring_error_invalid
    is RuleFieldProblem.OutOfRange -> when (field) {
        RuleField.Amount -> Res.string.tx_error_amount_range
        RuleField.Start, RuleField.End, RuleField.From -> Res.string.recurring_error_date_range
        else -> Res.string.recurring_error_out_of_range
    }
    is RuleFieldProblem.TooLong -> Res.string.recurring_error_too_long
    is RuleFieldProblem.AmountDigits -> Res.string.recurring_error_amount_digits
    RuleFieldProblem.TotalTooSmall -> Res.string.recurring_error_total_small
    RuleFieldProblem.PayoutTooLarge -> Res.string.recurring_error_payout_large
    RuleFieldProblem.Frozen -> Res.string.recurring_frozen_reason
    RuleFieldProblem.CutsResolvedRound -> Res.string.recurring_error_cuts_round
    RuleFieldProblem.FromOutOfRange -> Res.string.recurring_error_from_range
}

@Composable
fun RuleFieldProblem.text(field: RuleField? = null): String = when (this) {
    is RuleFieldProblem.OutOfRange ->
        if (field == RuleField.Amount || field == RuleField.Start || field == RuleField.End || field == RuleField.From) {
            stringResource(messageRes(field))
        } else {
            stringResource(messageRes(field), min, max)
        }
    is RuleFieldProblem.TooLong -> stringResource(messageRes(field), max)
    is RuleFieldProblem.AmountDigits -> stringResource(messageRes(field), digits)
    else -> stringResource(messageRes(field))
}

/** A server code at a field, shown next to it (REQUIRED / OUT_OF_RANGE / IMMUTABLE_FIELD / the rest as "check this field"). */
fun serverFieldMessageRes(code: String, field: RuleField?): StringResource = when {
    code == "IMMUTABLE_FIELD" -> Res.string.recurring_error_immutable
    code == "OUT_OF_RANGE" -> RuleFieldProblem.OutOfRange(0, 0).messageRes(field)
    code == "REQUIRED" -> RuleFieldProblem.Required.messageRes(field)
    else -> Res.string.recurring_error_invalid
}

fun ConfirmField.asRuleField(): RuleField = when (this) {
    ConfirmField.Date -> RuleField.Start
    ConfirmField.Amount -> RuleField.Amount
    ConfirmField.Category -> RuleField.Category
    ConfirmField.Note -> RuleField.Note
    ConfirmField.Details -> RuleField.Details
    ConfirmField.Person -> RuleField.Person
}

fun OccurrenceState.labelRes(): StringResource = when (this) {
    OccurrenceState.Upcoming -> Res.string.recurring_state_upcoming
    OccurrenceState.Due -> Res.string.recurring_state_due
    OccurrenceState.Missed -> Res.string.recurring_state_missed
    OccurrenceState.Confirmed -> Res.string.recurring_state_confirmed
    OccurrenceState.Skipped -> Res.string.recurring_state_skipped
    OccurrenceState.Unknown -> Res.string.recurring_state_unknown
}

fun RuleStatus.labelRes(): StringResource = when (this) {
    RuleStatus.Active -> Res.string.recurring_status_active
    RuleStatus.Paused -> Res.string.recurring_status_paused
    RuleStatus.Ended -> Res.string.recurring_status_ended
    RuleStatus.Unknown -> Res.string.recurring_status_unknown
}

fun Frequency.labelRes(): StringResource = when (this) {
    Frequency.Daily -> Res.string.recurring_frequency_daily
    Frequency.Weekly -> Res.string.recurring_frequency_weekly
    Frequency.Monthly -> Res.string.recurring_frequency_monthly
    Frequency.Yearly -> Res.string.recurring_frequency_yearly
    Frequency.Unknown -> Res.string.recurring_frequency_unknown
}

fun Preset.labelRes(): StringResource? = when (this) {
    Preset.Custom -> Res.string.recurring_preset_custom
    Preset.Gameya -> Res.string.recurring_preset_gameya
    Preset.Installment -> Res.string.recurring_preset_installment
    Preset.Unknown -> null
}

/** The line under a title for a waiting, clashing or refused change; `null` when there is nothing to say (P-08.3: icon and words). */
@Composable
fun RecurringSyncState.statusLine(): WalletCardStatus? = when (this) {
    RecurringSyncState.None -> null
    RecurringSyncState.Waiting, is RecurringSyncState.Held -> WalletCardStatus(WalletCardStatusKind.Waiting, stringResource(Res.string.recurring_status_waiting))
    is RecurringSyncState.Clash -> WalletCardStatus(
        WalletCardStatusKind.Changed,
        stringResource(if (deletedElsewhere) Res.string.recurring_status_deleted else Res.string.recurring_status_clash),
    )
    is RecurringSyncState.Refused -> WalletCardStatus(WalletCardStatusKind.NotSaved, stringResource(Res.string.recurring_status_not_saved))
}
