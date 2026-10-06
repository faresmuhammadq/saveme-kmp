package com.fares.saveme.feature.reports.ui

import androidx.compose.foundation.layout.RowScope
import androidx.compose.runtime.Composable
import com.fares.saveme.designsystem.component.AppIconButton
import com.fares.saveme.designsystem.component.AppTopAppBar
import com.fares.saveme.designsystem.format.formatDate
import com.fares.saveme.designsystem.format.formatDayMonth
import com.fares.saveme.designsystem.icon.AppIcons
import com.fares.saveme.feature.categories.domain.model.CategorySource
import com.fares.saveme.feature.categories.domain.model.ReportLabels
import com.fares.saveme.feature.reports.domain.Shares
import com.fares.saveme.feature.reports.domain.model.CategoryGroup
import com.fares.saveme.feature.reports.domain.model.Share
import kotlinx.datetime.LocalDate
import org.jetbrains.compose.resources.stringResource
import saveme.shared.generated.resources.Res
import saveme.shared.generated.resources.cd_back
import saveme.shared.generated.resources.reports_share_under_one

/**
 * One category row as both reports screens draw it, mapped from the domain so the screens hold no rules (FR-006, FR-007).
 * [name] is empty while the category cannot be resolved: the screen then says "Category unavailable". Amounts are the
 * person's own and never printed (P-09.2).
 */
data class CategoryRowUi(
    val categoryId: String,
    val name: String,
    val iconKey: String?,
    val colorHex: String?,
    val valueMinor: Long,
    val plannedMinor: Long?,
    val share: Share,
    val fraction: Float,
    val currency: String,
) {
    override fun toString() = "CategoryRowUi"
}

/** The rows of one group in the server's order, with shares computed here from the group's total. */
fun categoryRows(group: CategoryGroup, labels: ReportLabels, currency: String): List<CategoryRowUi> = group.items.map { item ->
    val display = labels.display(item.categoryId)
    val resolved = display.source != CategorySource.Unavailable
    CategoryRowUi(
        categoryId = item.categoryId,
        name = if (resolved) display.name else "",
        iconKey = display.iconKey,
        colorHex = display.colorHex,
        valueMinor = item.valueMinor,
        plannedMinor = item.plannedMinor,
        share = Shares.of(item.valueMinor, group.totalMinor),
        fraction = Shares.fraction(item.valueMinor, group.totalMinor),
        currency = currency,
    )
}

/** "64%", "<1%" or nothing when the total is zero. */
@Composable
internal fun shareText(share: Share): String = when (share) {
    is Share.Percent -> "${share.percent}%"
    Share.UnderOne -> stringResource(Res.string.reports_share_under_one)
    Share.None -> ""
}

/** "25 Sep – 24 Oct", with the year when the range is not within [thisYear]; Western digits in both languages. */
@Composable
internal fun rangeText(from: LocalDate, to: LocalDate, thisYear: Int): String =
    if (from.year == thisYear && to.year == thisYear) {
        "${formatDayMonth(from)} – ${formatDayMonth(to)}"
    } else {
        "${formatDate(from)} – ${formatDate(to)}"
    }

@Composable
internal fun ReportsBackBar(title: String, onBack: () -> Unit, actions: @Composable RowScope.() -> Unit = {}) {
    AppTopAppBar(
        title = title,
        navigationIcon = { AppIconButton(AppIcons.ArrowBack, stringResource(Res.string.cd_back), onBack) },
        actions = actions,
    )
}
