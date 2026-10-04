package com.fares.saveme.feature.recurring.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.fares.saveme.designsystem.component.AppSelectField
import com.fares.saveme.designsystem.component.CategoryChoiceGroup
import com.fares.saveme.designsystem.component.CategoryChoiceItem
import com.fares.saveme.designsystem.component.CategoryChoiceSheet
import com.fares.saveme.designsystem.component.CategoryRow
import com.fares.saveme.designsystem.theme.AppTheme
import com.fares.saveme.feature.categories.domain.model.CategoryChoices
import com.fares.saveme.feature.categories.domain.model.CategoryDisplay
import com.fares.saveme.feature.categories.domain.model.CategorySource
import com.fares.saveme.feature.transactions.domain.model.TransactionKind
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import saveme.shared.generated.resources.Res
import saveme.shared.generated.resources.category_choice_current
import saveme.shared.generated.resources.category_choice_defaults
import saveme.shared.generated.resources.category_choice_empty
import saveme.shared.generated.resources.category_choice_own
import saveme.shared.generated.resources.category_choice_title
import saveme.shared.generated.resources.category_unavailable
import saveme.shared.generated.resources.tx_field_category
import saveme.shared.generated.resources.tx_kind_expense
import saveme.shared.generated.resources.tx_kind_income
import saveme.shared.generated.resources.tx_kind_transfer
import saveme.shared.generated.resources.tx_kind_unknown

/** A category's name, or "Category unavailable" when it can't be resolved on this phone. */
@Composable
internal fun CategoryDisplay.label(): String =
    if (source == CategorySource.Unavailable || name.isEmpty()) stringResource(Res.string.category_unavailable) else name

internal fun TransactionKind.kindLabelRes(): StringResource = when (this) {
    TransactionKind.Expense -> Res.string.tx_kind_expense
    TransactionKind.Income -> Res.string.tx_kind_income
    TransactionKind.Transfer -> Res.string.tx_kind_transfer
    TransactionKind.Unknown -> Res.string.tx_kind_unknown
}

/** The category field: a select field while none is chosen, else the chosen category's row; both open the sheet. */
@Composable
internal fun CategoryPickField(
    label: String,
    categoryId: String?,
    display: CategoryDisplay?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    errorText: String? = null,
    enabled: Boolean = true,
) {
    if (categoryId == null) {
        AppSelectField(label = label, value = "", onClick = onClick, modifier = modifier, errorText = errorText, enabled = enabled)
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.xxs)) {
        Text(label, style = AppTheme.type.label, color = AppTheme.colors.ink2)
        CategoryRow(
            name = display?.label() ?: stringResource(Res.string.category_unavailable),
            iconKey = display?.iconKey,
            colorHex = display?.colorHex,
            modifier = modifier.background(AppTheme.colors.surface, AppTheme.shapes.control),
            onClick = if (enabled) onClick else null,
        )
        if (errorText != null) Text(errorText, style = AppTheme.type.caption, color = AppTheme.colors.error)
    }
}

/** The sheet that lists the current, own and default categories of one kind. */
@Composable
internal fun RecurringCategorySheet(
    choices: CategoryChoices?,
    selectedId: String?,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
    title: String = stringResource(Res.string.category_choice_title),
) {
    val shown = choices ?: CategoryChoices(null, emptyList(), emptyList())
    val current = stringResource(Res.string.category_choice_current)
    val own = stringResource(Res.string.category_choice_own)
    val defaults = stringResource(Res.string.category_choice_defaults)
    val unavailable = stringResource(Res.string.category_unavailable)
    fun CategoryDisplay.item() = CategoryChoiceItem(id, name.ifEmpty { unavailable }, iconKey, colorHex)
    CategoryChoiceSheet(
        title = title,
        groups = listOf(
            CategoryChoiceGroup(current, listOfNotNull(shown.current?.item()), "categoryChoice.current", indexed = false),
            CategoryChoiceGroup(own, shown.own.map { it.item() }, "categoryChoice.own"),
            CategoryChoiceGroup(defaults, shown.defaults.map { it.item() }, "categoryChoice.default"),
        ),
        selectedId = selectedId,
        onSelect = onSelect,
        onDismiss = onDismiss,
        emptyMessage = stringResource(Res.string.category_choice_empty),
    )
}


/**
 * One choice out of a few with long words (rule type, amount basis, end): chips that wrap onto the next line, so
 * nothing clips at 200% text size (P-08.5) the way a fixed-height segmented control would.
 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
internal fun <T> ChoiceChips(
    options: List<T>,
    label: @Composable (T) -> String,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    optionModifier: (T) -> Modifier = { Modifier },
) {
    androidx.compose.foundation.layout.FlowRow(
        horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.xs),
        modifier = modifier,
    ) {
        options.forEach { option ->
            com.fares.saveme.designsystem.component.AppFilterChip(
                label = label(option),
                selected = option == selected,
                onClick = { onSelect(option) },
                modifier = optionModifier(option),
            )
        }
    }
}
