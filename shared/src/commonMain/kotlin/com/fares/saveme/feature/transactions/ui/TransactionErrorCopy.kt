package com.fares.saveme.feature.transactions.ui

import androidx.compose.runtime.Composable
import com.fares.saveme.designsystem.component.AppIconButton
import com.fares.saveme.designsystem.component.AppTopAppBar
import com.fares.saveme.designsystem.icon.AppIcons
import com.fares.saveme.feature.categories.domain.model.CategoryDisplay
import com.fares.saveme.feature.categories.domain.model.CategorySource
import com.fares.saveme.feature.transactions.domain.model.RefusalKind
import com.fares.saveme.feature.transactions.domain.model.RefusalReason
import com.fares.saveme.feature.transactions.domain.model.TransactionField
import com.fares.saveme.feature.transactions.domain.model.TransactionFieldProblem
import com.fares.saveme.feature.transactions.domain.model.TransactionKind
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import saveme.shared.generated.resources.Res
import saveme.shared.generated.resources.category_unavailable
import saveme.shared.generated.resources.cd_back
import saveme.shared.generated.resources.tx_error_amount_digits
import saveme.shared.generated.resources.tx_error_amount_empty
import saveme.shared.generated.resources.tx_error_amount_invalid
import saveme.shared.generated.resources.tx_error_amount_positive
import saveme.shared.generated.resources.tx_error_amount_range
import saveme.shared.generated.resources.tx_error_category_required
import saveme.shared.generated.resources.tx_error_date_range
import saveme.shared.generated.resources.tx_error_destination_required
import saveme.shared.generated.resources.tx_error_field_invalid
import saveme.shared.generated.resources.tx_error_immutable
import saveme.shared.generated.resources.tx_error_too_long
import saveme.shared.generated.resources.tx_kind_expense
import saveme.shared.generated.resources.tx_kind_income
import saveme.shared.generated.resources.tx_kind_transfer
import saveme.shared.generated.resources.tx_kind_unknown
import saveme.shared.generated.resources.tx_reason_category
import saveme.shared.generated.resources.tx_reason_currencies_differ
import saveme.shared.generated.resources.tx_reason_currency_changed
import saveme.shared.generated.resources.tx_reason_destination
import saveme.shared.generated.resources.tx_reason_gone
import saveme.shared.generated.resources.tx_reason_invalid
import saveme.shared.generated.resources.tx_reason_no_right
import saveme.shared.generated.resources.tx_reason_no_right_other
import saveme.shared.generated.resources.tx_reason_not_verified
import saveme.shared.generated.resources.tx_reason_other
import saveme.shared.generated.resources.tx_reason_other_plain

/** Copy for a refusal is chosen from its kind (code and pointer), never from the server's text (P-02.4, P-06.5). */
fun RefusalKind.messageRes(): StringResource = when (this) {
    RefusalKind.NotVerified -> Res.string.tx_reason_not_verified
    RefusalKind.NoLongerAvailable -> Res.string.tx_reason_gone
    RefusalKind.NoRightHere -> Res.string.tx_reason_no_right
    RefusalKind.NoRightInOtherWallet -> Res.string.tx_reason_no_right_other
    RefusalKind.CategoryUnusable -> Res.string.tx_reason_category
    RefusalKind.DestinationUnavailable -> Res.string.tx_reason_destination
    RefusalKind.CurrencyChanged -> Res.string.tx_reason_currency_changed
    RefusalKind.CurrenciesDiffer -> Res.string.tx_reason_currencies_differ
    RefusalKind.Invalid -> Res.string.tx_reason_invalid
    RefusalKind.Other -> Res.string.tx_reason_other_plain
}

/** [currency] is the wallet's present currency, named by "the wallet's currency changed". */
@Composable
fun RefusalReason.text(currency: String = ""): String = when {
    kind == RefusalKind.Other && requestId != null -> stringResource(Res.string.tx_reason_other, requestId)
    kind == RefusalKind.CurrencyChanged -> stringResource(Res.string.tx_reason_currency_changed, currency)
    else -> stringResource(kind.messageRes())
}

/** One message per field problem, including the server's codes for that field (P-06.5). */
fun TransactionFieldProblem.messageRes(field: TransactionField? = null): StringResource = when (this) {
    TransactionFieldProblem.AmountEmpty -> Res.string.tx_error_amount_empty
    TransactionFieldProblem.AmountNotPositive -> Res.string.tx_error_amount_positive
    TransactionFieldProblem.AmountInvalid -> Res.string.tx_error_amount_invalid
    TransactionFieldProblem.AmountOutOfRange -> Res.string.tx_error_amount_range
    is TransactionFieldProblem.AmountDigits -> Res.string.tx_error_amount_digits
    TransactionFieldProblem.DateOutOfRange -> Res.string.tx_error_date_range
    TransactionFieldProblem.CategoryRequired -> Res.string.tx_error_category_required
    TransactionFieldProblem.DestinationRequired -> Res.string.tx_error_destination_required
    TransactionFieldProblem.KindUnknown -> Res.string.tx_error_field_invalid
    is TransactionFieldProblem.TooLong -> Res.string.tx_error_too_long
    is TransactionFieldProblem.Server -> when {
        code == "IMMUTABLE_FIELD" -> Res.string.tx_error_immutable
        code == "OUT_OF_RANGE" && field == TransactionField.Amount -> Res.string.tx_error_amount_range
        code == "OUT_OF_RANGE" && field == TransactionField.Date -> Res.string.tx_error_date_range
        code == "REQUIRED" && field == TransactionField.Amount -> Res.string.tx_error_amount_empty
        code == "REQUIRED" && field == TransactionField.Category -> Res.string.tx_error_category_required
        code == "REQUIRED" && field == TransactionField.Destination -> Res.string.tx_error_destination_required
        else -> Res.string.tx_error_field_invalid
    }
}

@Composable
fun TransactionFieldProblem.text(field: TransactionField? = null): String = when (this) {
    is TransactionFieldProblem.AmountDigits -> stringResource(messageRes(field), digits)
    is TransactionFieldProblem.TooLong -> stringResource(messageRes(field), max)
    else -> stringResource(messageRes(field))
}

/** Server error codes the editor shows next to a field, from a refusal's field map. */
fun Map<TransactionField, String>.asProblems(): Map<TransactionField, TransactionFieldProblem> =
    mapValues { TransactionFieldProblem.Server(it.value) }

fun TransactionKind.labelRes(): StringResource = when (this) {
    TransactionKind.Expense -> Res.string.tx_kind_expense
    TransactionKind.Income -> Res.string.tx_kind_income
    TransactionKind.Transfer -> Res.string.tx_kind_transfer
    TransactionKind.Unknown -> Res.string.tx_kind_unknown
}

/** Top bar with a back arrow for the transaction screens (other features' bars are internal to them, P-10.4). */
@Composable
internal fun TransactionsBackBar(title: String, onBack: () -> Unit) {
    AppTopAppBar(
        title = title,
        navigationIcon = { AppIconButton(AppIcons.ArrowBack, stringResource(Res.string.cd_back), onBack) },
    )
}

/** A category's name, or "Category unavailable" when it can't be resolved on this phone (FR-019). */
@Composable
internal fun CategoryDisplay.label(): String =
    if (source == CategorySource.Unavailable || name.isEmpty()) stringResource(Res.string.category_unavailable) else name
