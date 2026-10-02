package com.fares.saveme.feature.wallets.ui

import androidx.compose.runtime.Composable
import com.fares.saveme.feature.wallets.domain.model.RefusalKind
import com.fares.saveme.feature.wallets.domain.model.RefusalReason
import com.fares.saveme.feature.wallets.domain.model.WalletField
import com.fares.saveme.feature.wallets.domain.model.WalletFieldProblem
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import saveme.shared.generated.resources.Res
import saveme.shared.generated.resources.error_feature_not_entitled
import saveme.shared.generated.resources.wallet_error_amount_digits
import saveme.shared.generated.resources.wallet_error_amount_invalid
import saveme.shared.generated.resources.wallet_error_amount_range
import saveme.shared.generated.resources.wallet_error_currency_locked
import saveme.shared.generated.resources.wallet_error_field_invalid
import saveme.shared.generated.resources.wallet_error_name_blank
import saveme.shared.generated.resources.wallet_error_name_long
import saveme.shared.generated.resources.wallet_error_type_immutable
import saveme.shared.generated.resources.wallet_refusal_gone
import saveme.shared.generated.resources.wallet_refusal_invalid
import saveme.shared.generated.resources.wallet_refusal_not_owner
import saveme.shared.generated.resources.wallet_refusal_not_verified
import saveme.shared.generated.resources.wallet_refusal_other
import saveme.shared.generated.resources.wallet_refusal_other_plain

/** Copy for a refusal is chosen from its kind, never from the server's text (P-02.4, P-06.5). */
fun RefusalKind.messageRes(): StringResource = when (this) {
    RefusalKind.NotVerified -> Res.string.wallet_refusal_not_verified
    RefusalKind.NotEntitled -> Res.string.error_feature_not_entitled
    RefusalKind.NotOwner -> Res.string.wallet_refusal_not_owner
    RefusalKind.NoLongerAvailable -> Res.string.wallet_refusal_gone
    RefusalKind.Invalid -> Res.string.wallet_refusal_invalid
    RefusalKind.Other -> Res.string.wallet_refusal_other_plain
}

@Composable
fun RefusalReason.text(): String =
    if (kind == RefusalKind.Other && requestId != null) stringResource(Res.string.wallet_refusal_other, requestId) else stringResource(kind.messageRes())

/** One message per field problem, including the server's codes for that field (P-06.5). */
fun WalletFieldProblem.messageRes(field: WalletField? = null): StringResource = when (this) {
    WalletFieldProblem.NameBlank -> Res.string.wallet_error_name_blank
    WalletFieldProblem.NameTooLong -> Res.string.wallet_error_name_long
    WalletFieldProblem.AmountEmpty, WalletFieldProblem.AmountInvalid -> Res.string.wallet_error_amount_invalid
    WalletFieldProblem.AmountOutOfRange -> Res.string.wallet_error_amount_range
    is WalletFieldProblem.AmountDigits -> Res.string.wallet_error_amount_digits
    is WalletFieldProblem.Server -> when {
        code == "IMMUTABLE_FIELD" && field == WalletField.Currency -> Res.string.wallet_error_currency_locked
        code == "IMMUTABLE_FIELD" && field == WalletField.Type -> Res.string.wallet_error_type_immutable
        code == "OUT_OF_RANGE" && field == WalletField.Name -> Res.string.wallet_error_name_long
        code == "OUT_OF_RANGE" && field == WalletField.OpeningBalance -> Res.string.wallet_error_amount_range
        else -> Res.string.wallet_error_field_invalid
    }
}

@Composable
fun WalletFieldProblem.text(field: WalletField? = null): String =
    if (this is WalletFieldProblem.AmountDigits) stringResource(messageRes(field), digits) else stringResource(messageRes(field))

/** Server error codes the editor shows next to a field, from a refusal's field map. */
fun Map<WalletField, String>.asProblems(): Map<WalletField, WalletFieldProblem> = mapValues { WalletFieldProblem.Server(it.value) }
