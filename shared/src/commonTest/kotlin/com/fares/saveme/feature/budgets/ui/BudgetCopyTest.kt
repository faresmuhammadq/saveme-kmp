package com.fares.saveme.feature.budgets.ui

import com.fares.saveme.feature.budgets.domain.model.BudgetField
import com.fares.saveme.feature.budgets.domain.model.BudgetFieldProblem
import com.fares.saveme.feature.budgets.domain.model.RefusalKind
import com.fares.saveme.feature.budgets.domain.model.RefusalReason
import com.fares.saveme.feature.budgets.domain.refusalKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
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
import saveme.shared.generated.resources.budgets_refusal_other_plain
import saveme.shared.generated.resources.error_generic
import saveme.shared.generated.resources.error_generic_reference

/** Refusal and field copy is chosen by code and pointer, never by the server's text, and is always the budget's own (P-02.4, P-06.5, FR-007). */
class BudgetCopyTest {

    private fun kind(code: String?, vararg fields: Pair<BudgetField, String>) = refusalKind(code, fields.toMap())

    @Test
    fun everyCodeOfSpecSection5MapsToItsKind() {
        assertEquals(RefusalKind.NotVerified, kind("EMAIL_NOT_VERIFIED"))
        assertEquals(RefusalKind.NoLongerAvailable, kind("NOT_FOUND"))
        assertEquals(RefusalKind.CategoryUnusable, kind("VALIDATION_FAILED", BudgetField.Category to "INVALID_ENUM_VALUE"))
        assertEquals(RefusalKind.LimitInvalid, kind("VALIDATION_FAILED", BudgetField.Limit to "OUT_OF_RANGE"))
        assertEquals(RefusalKind.LimitInvalid, kind("VALIDATION_FAILED", BudgetField.Limit to "AMOUNT_NOT_INTEGER"))
        assertEquals(RefusalKind.CurrencyInvalid, kind("VALIDATION_FAILED", BudgetField.Currency to "INVALID_CURRENCY"))
        assertEquals(RefusalKind.Immutable, kind("VALIDATION_FAILED", BudgetField.Category to "IMMUTABLE_FIELD"))
        assertEquals(RefusalKind.Immutable, kind("VALIDATION_FAILED", BudgetField.Period to "IMMUTABLE_FIELD", BudgetField.Limit to "OUT_OF_RANGE"))
        assertEquals(RefusalKind.Invalid, kind("VALIDATION_FAILED"))
        assertEquals(RefusalKind.Invalid, kind("VALIDATION_FAILED", BudgetField.Period to "INVALID_ENUM_VALUE"))
        assertEquals(RefusalKind.Invalid, kind("INVALID_ID"))
        assertEquals(RefusalKind.Other, kind("SOMETHING_NEW"))
        assertEquals(RefusalKind.Other, kind(null))
    }

    @Test
    fun theServersWordsNeverDecideTheKind() {
        // Only the code and the pointer's field are read: a field naming a code that means something else is not guessed.
        assertEquals(RefusalKind.Invalid, kind("VALIDATION_FAILED", BudgetField.Category to "OUT_OF_RANGE"))
        assertEquals(RefusalKind.Invalid, kind("VALIDATION_FAILED", BudgetField.Limit to "INVALID_ENUM_VALUE"))
    }

    @Test
    fun everyRefusalKindHasItsOwnWordingAndNeverTheGenericOne() {
        RefusalKind.entries.forEach { kind ->
            val res = kind.messageRes()
            assertNotEquals(Res.string.error_generic, res, kind.name)
            assertNotEquals(Res.string.error_generic_reference, res, kind.name)
        }
        assertEquals(RefusalKind.entries.size, RefusalKind.entries.map { it.messageRes() }.toSet().size)
        assertEquals(Res.string.budgets_refusal_category, RefusalKind.CategoryUnusable.messageRes())
        assertEquals(Res.string.budgets_refusal_not_verified, RefusalKind.NotVerified.messageRes())
        assertEquals(Res.string.budgets_refusal_gone, RefusalKind.NoLongerAvailable.messageRes())
        assertEquals(Res.string.budgets_refusal_limit, RefusalKind.LimitInvalid.messageRes())
        assertEquals(Res.string.budgets_refusal_currency, RefusalKind.CurrencyInvalid.messageRes())
        assertEquals(Res.string.budgets_refusal_immutable, RefusalKind.Immutable.messageRes())
        assertEquals(Res.string.budgets_refusal_invalid, RefusalKind.Invalid.messageRes())
        assertEquals(Res.string.budgets_refusal_other_plain, RefusalKind.Other.messageRes())
    }

    @Test
    fun onlyAnUnverifiedAddressCanBeRetriedUnchanged() {
        assertEquals(listOf(RefusalKind.NotVerified), RefusalKind.entries.filter { RefusalReason(it).canRetryUnchanged })
    }

    @Test
    fun everyFieldProblemMapsToAKey() {
        val problems = listOf(
            BudgetFieldProblem.LimitEmpty, BudgetFieldProblem.LimitInvalid, BudgetFieldProblem.LimitNotPositive,
            BudgetFieldProblem.LimitOutOfRange, BudgetFieldProblem.LimitDigits(2), BudgetFieldProblem.AlreadyBudgeted,
            BudgetFieldProblem.Server("X"),
        )
        assertEquals(problems.size, problems.map { it.messageRes(BudgetField.Limit) }.toSet().size, "distinct wording each")
    }

    @Test
    fun serverCodesAtAFieldReadAsThatField() {
        assertEquals(Res.string.budgets_immutable, BudgetFieldProblem.Server("IMMUTABLE_FIELD").messageRes(BudgetField.Category))
        assertEquals(Res.string.budgets_error_limit_range, BudgetFieldProblem.Server("OUT_OF_RANGE").messageRes(BudgetField.Limit))
        assertEquals(Res.string.budgets_error_limit_range, BudgetFieldProblem.Server("AMOUNT_NOT_INTEGER").messageRes(BudgetField.Limit))
        assertEquals(Res.string.budgets_refusal_category, BudgetFieldProblem.Server("INVALID_ENUM_VALUE").messageRes(BudgetField.Category))
        assertEquals(Res.string.budgets_error_category_required, BudgetFieldProblem.Server("REQUIRED").messageRes(BudgetField.Category))
        assertEquals(Res.string.budgets_error_field_invalid, BudgetFieldProblem.Server("INVALID_TYPE").messageRes(BudgetField.Limit))
        assertEquals(Res.string.budgets_already_budgeted, BudgetFieldProblem.AlreadyBudgeted.messageRes())
        assertEquals(Res.string.budgets_error_limit_digits, BudgetFieldProblem.LimitDigits(0).messageRes())
        assertEquals(Res.string.budgets_error_limit_empty, BudgetFieldProblem.LimitEmpty.messageRes())
        assertEquals(Res.string.budgets_error_limit_invalid, BudgetFieldProblem.LimitInvalid.messageRes())
        assertEquals(Res.string.budgets_error_limit_not_positive, BudgetFieldProblem.LimitNotPositive.messageRes())
        assertTrue(mapOf(BudgetField.Limit to "OUT_OF_RANGE").asProblems().getValue(BudgetField.Limit) is BudgetFieldProblem.Server)
    }
}
