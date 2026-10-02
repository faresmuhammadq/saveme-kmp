package com.fares.saveme.feature.categories.ui

import androidx.compose.runtime.Composable
import com.fares.saveme.designsystem.component.AppIconButton
import com.fares.saveme.designsystem.component.AppTopAppBar
import com.fares.saveme.designsystem.icon.AppIcons
import com.fares.saveme.feature.categories.domain.model.CategoryField
import com.fares.saveme.feature.categories.domain.model.CategoryFieldProblem
import com.fares.saveme.feature.categories.domain.model.RefusalReason
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import saveme.shared.generated.resources.Res
import saveme.shared.generated.resources.cd_back
import saveme.shared.generated.resources.category_error_field_invalid
import saveme.shared.generated.resources.category_error_kind_immutable
import saveme.shared.generated.resources.category_error_name_blank
import saveme.shared.generated.resources.category_error_name_long
import saveme.shared.generated.resources.category_refusal_gone
import saveme.shared.generated.resources.category_refusal_invalid
import saveme.shared.generated.resources.category_refusal_not_verified
import saveme.shared.generated.resources.category_refusal_other
import saveme.shared.generated.resources.category_refusal_other_plain
import saveme.shared.generated.resources.category_status_in_use

/** Copy for a refusal is chosen from its reason, never from the server's text (P-02.4, P-06.5). */
fun RefusalReason.messageRes(): StringResource = when (this) {
    RefusalReason.NotVerified -> Res.string.category_refusal_not_verified
    RefusalReason.NoLongerAvailable -> Res.string.category_refusal_gone
    RefusalReason.InUse -> Res.string.category_status_in_use
    RefusalReason.Invalid -> Res.string.category_refusal_invalid
    is RefusalReason.Other -> Res.string.category_refusal_other_plain
}

@Composable
fun RefusalReason.text(): String =
    if (this is RefusalReason.Other && requestId != null) stringResource(Res.string.category_refusal_other, requestId) else stringResource(messageRes())

/** One message per field problem, including the server's codes for that field (P-06.5). */
fun CategoryFieldProblem.messageRes(field: CategoryField? = null): StringResource = when (this) {
    CategoryFieldProblem.NameBlank -> Res.string.category_error_name_blank
    CategoryFieldProblem.NameTooLong -> Res.string.category_error_name_long
    is CategoryFieldProblem.Server -> when {
        code == "IMMUTABLE_FIELD" && field == CategoryField.Kind -> Res.string.category_error_kind_immutable
        code == "OUT_OF_RANGE" && field == CategoryField.Name -> Res.string.category_error_name_long
        code == "REQUIRED" && field == CategoryField.Name -> Res.string.category_error_name_blank
        else -> Res.string.category_error_field_invalid
    }
}

@Composable
fun CategoryFieldProblem.text(field: CategoryField? = null): String = stringResource(messageRes(field))

/** Server error codes the editor shows next to a field, from a refusal's field map. */
fun Map<CategoryField, String>.asProblems(): Map<CategoryField, CategoryFieldProblem> =
    mapValues { CategoryFieldProblem.Server(it.value) }

/** Top bar with a back arrow for the category screens (other features' bars are internal to them, P-10.4). */
@Composable
internal fun CategoriesBackBar(title: String, onBack: () -> Unit) {
    AppTopAppBar(
        title = title,
        navigationIcon = { AppIconButton(AppIcons.ArrowBack, stringResource(Res.string.cd_back), onBack) },
    )
}
