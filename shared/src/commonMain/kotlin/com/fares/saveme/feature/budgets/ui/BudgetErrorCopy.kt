package com.fares.saveme.feature.budgets.ui

import androidx.compose.runtime.Composable
import com.fares.saveme.designsystem.component.AppIconButton
import com.fares.saveme.designsystem.component.AppTopAppBar
import com.fares.saveme.designsystem.icon.AppIcons
import com.fares.saveme.feature.budgets.domain.model.BudgetField
import com.fares.saveme.feature.budgets.domain.model.BudgetFieldProblem
import com.fares.saveme.feature.budgets.domain.model.RefusalKind
import com.fares.saveme.feature.budgets.domain.model.RefusalReason
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import saveme.shared.generated.resources.Res
import saveme.shared.generated.resources.budgets_already_budgeted
import saveme.shared.generated.resources.budgets_error_category_required
import saveme.shared.generated.resources.budgets_error_field_invalid
import saveme.shared.generated.resources.budgets_error_limit_digits
import saveme.shared.generated.resources.budgets_error_limit_empty
import saveme.shared.generated.resources.budgets_error_limit_invalid
import saveme.shared.generated.resources.budgets_error_limit_not_positive
import saveme.shared.generated.resources.budgets_error_limit_range
import saveme.shared.generated.resources.budgets_immutable
import saveme.shared.generated.resources.budgets_refusal_category
import saveme.shared.generated.resources.budgets_refusal_currency
import saveme.shared.generated.resources.budgets_refusal_gone
import saveme.shared.generated.resources.budgets_refusal_immutable
import saveme.shared.generated.resources.budgets_refusal_invalid
import saveme.shared.generated.resources.budgets_refusal_limit
import saveme.shared.generated.resources.budgets_refusal_not_verified
import saveme.shared.generated.resources.budgets_refusal_other
import saveme.shared.generated.resources.budgets_refusal_other_plain
import saveme.shared.generated.resources.cd_back

/** Copy for a refusal is chosen from its kind, which the domain took from the code and pointer, never from the server's text (P-02.4, FR-007). */
fun RefusalKind.messageRes(): StringResource = when (this) {
    RefusalKind.NotVerified -> Res.string.budgets_refusal_not_verified
    RefusalKind.NoLongerAvailable -> Res.string.budgets_refusal_gone
    RefusalKind.CategoryUnusable -> Res.string.budgets_refusal_category
    RefusalKind.LimitInvalid -> Res.string.budgets_refusal_limit
    RefusalKind.CurrencyInvalid -> Res.string.budgets_refusal_currency
    RefusalKind.Immutable -> Res.string.budgets_refusal_immutable
    RefusalKind.Invalid -> Res.string.budgets_refusal_invalid
    RefusalKind.Other -> Res.string.budgets_refusal_other_plain
}

@Composable
fun RefusalReason.text(): String =
    if (kind == RefusalKind.Other && requestId != null) stringResource(Res.string.budgets_refusal_other, requestId) else stringResource(kind.messageRes())

/** One message per field problem, including the server's codes for that field (P-06.5). */
fun BudgetFieldProblem.messageRes(field: BudgetField? = null): StringResource = when (this) {
    BudgetFieldProblem.LimitEmpty -> Res.string.budgets_error_limit_empty
    BudgetFieldProblem.LimitInvalid -> Res.string.budgets_error_limit_invalid
    BudgetFieldProblem.LimitNotPositive -> Res.string.budgets_error_limit_not_positive
    BudgetFieldProblem.LimitOutOfRange -> Res.string.budgets_error_limit_range
    is BudgetFieldProblem.LimitDigits -> Res.string.budgets_error_limit_digits
    BudgetFieldProblem.AlreadyBudgeted -> Res.string.budgets_already_budgeted
    is BudgetFieldProblem.Server -> when {
        code == "IMMUTABLE_FIELD" -> Res.string.budgets_immutable
        (code == "OUT_OF_RANGE" || code == "AMOUNT_NOT_INTEGER") && field == BudgetField.Limit -> Res.string.budgets_error_limit_range
        code == "INVALID_ENUM_VALUE" && field == BudgetField.Category -> Res.string.budgets_refusal_category
        code == "REQUIRED" && field == BudgetField.Category -> Res.string.budgets_error_category_required
        else -> Res.string.budgets_error_field_invalid
    }
}

@Composable
fun BudgetFieldProblem.text(field: BudgetField? = null): String =
    if (this is BudgetFieldProblem.LimitDigits) stringResource(messageRes(field), digits) else stringResource(messageRes(field))

/** Server error codes the editor shows next to a field, from a refusal's field map. */
fun Map<BudgetField, String>.asProblems(): Map<BudgetField, BudgetFieldProblem> = mapValues { BudgetFieldProblem.Server(it.value) }

/** Top bar with a back arrow for the budget screens (other features' bars are internal to them, P-10.4). */
@Composable
internal fun BudgetsBackBar(title: String, onBack: () -> Unit, actions: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit = {}) {
    AppTopAppBar(
        title = title,
        navigationIcon = { AppIconButton(AppIcons.ArrowBack, stringResource(Res.string.cd_back), onBack) },
        actions = actions,
    )
}
