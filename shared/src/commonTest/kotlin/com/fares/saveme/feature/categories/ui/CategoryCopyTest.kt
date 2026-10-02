package com.fares.saveme.feature.categories.ui

import com.fares.saveme.feature.categories.domain.model.CategoryField
import com.fares.saveme.feature.categories.domain.model.CategoryFieldProblem
import com.fares.saveme.feature.categories.domain.model.RefusalReason
import saveme.shared.generated.resources.Res
import saveme.shared.generated.resources.category_error_field_invalid
import saveme.shared.generated.resources.category_error_kind_immutable
import saveme.shared.generated.resources.category_error_name_blank
import saveme.shared.generated.resources.category_error_name_long
import saveme.shared.generated.resources.error_generic
import saveme.shared.generated.resources.error_generic_reference
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

/** Every refusal reason and field error code has its own category copy, never a generic message (P-06.5). */
class CategoryCopyTest {
    @Test
    fun everyRefusalReasonMapsToCategoryCopy() {
        val reasons = listOf(
            RefusalReason.NotVerified, RefusalReason.NoLongerAvailable, RefusalReason.InUse, RefusalReason.Invalid, RefusalReason.Other("r"),
        )
        val messages = reasons.map { it.messageRes() }
        assertEquals(reasons.size, messages.toSet().size)
        messages.forEach {
            assertNotEquals(Res.string.error_generic, it)
            assertNotEquals(Res.string.error_generic_reference, it)
        }
    }

    @Test
    fun serverFieldCodesMapToTheirOwnMessages() {
        assertEquals(Res.string.category_error_kind_immutable, CategoryFieldProblem.Server("IMMUTABLE_FIELD").messageRes(CategoryField.Kind))
        assertEquals(Res.string.category_error_name_long, CategoryFieldProblem.Server("OUT_OF_RANGE").messageRes(CategoryField.Name))
        assertEquals(Res.string.category_error_name_blank, CategoryFieldProblem.Server("REQUIRED").messageRes(CategoryField.Name))
        assertEquals(Res.string.category_error_field_invalid, CategoryFieldProblem.Server("INVALID_ENUM_VALUE").messageRes(CategoryField.Kind))
        assertEquals(Res.string.category_error_field_invalid, CategoryFieldProblem.Server("SOMETHING_NEW").messageRes(CategoryField.Icon))
        listOf(CategoryFieldProblem.NameBlank, CategoryFieldProblem.NameTooLong).forEach { assertNotEquals(Res.string.error_generic, it.messageRes()) }
    }

    @Test
    fun serverFieldMapsBecomeProblems() {
        assertEquals(
            mapOf(CategoryField.Name to CategoryFieldProblem.Server("OUT_OF_RANGE")),
            mapOf(CategoryField.Name to "OUT_OF_RANGE").asProblems(),
        )
    }
}
