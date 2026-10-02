package com.fares.saveme.feature.categories.ui.list

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.fares.saveme.designsystem.component.AlertTone
import com.fares.saveme.designsystem.component.AppFab
import com.fares.saveme.designsystem.component.AppScreen
import com.fares.saveme.designsystem.component.CategoryRow
import com.fares.saveme.designsystem.component.CategoryRowStatus
import com.fares.saveme.designsystem.component.CategoryRowStatusKind
import com.fares.saveme.designsystem.component.InlineAlert
import com.fares.saveme.designsystem.component.SegmentedControl
import com.fares.saveme.designsystem.component.Skeleton
import com.fares.saveme.designsystem.component.SyncBanner
import com.fares.saveme.designsystem.component.SyncBannerState
import com.fares.saveme.designsystem.format.formatDateTime
import com.fares.saveme.designsystem.icon.AppIcons
import com.fares.saveme.designsystem.theme.AppTheme
import com.fares.saveme.feature.categories.domain.model.CategoryKind
import com.fares.saveme.feature.categories.ui.CategoriesBackBar
import org.jetbrains.compose.resources.stringResource
import saveme.shared.generated.resources.Res
import saveme.shared.generated.resources.categories_archived
import saveme.shared.generated.resources.categories_defaults
import saveme.shared.generated.resources.categories_defaults_missing
import saveme.shared.generated.resources.categories_new
import saveme.shared.generated.resources.categories_own
import saveme.shared.generated.resources.categories_own_empty
import saveme.shared.generated.resources.categories_refresh_failed
import saveme.shared.generated.resources.categories_title
import saveme.shared.generated.resources.categories_updated_at
import saveme.shared.generated.resources.category_archived
import saveme.shared.generated.resources.category_default
import saveme.shared.generated.resources.category_kind_expense
import saveme.shared.generated.resources.category_kind_income
import saveme.shared.generated.resources.category_status_clash
import saveme.shared.generated.resources.category_status_in_use
import saveme.shared.generated.resources.category_status_not_saved
import saveme.shared.generated.resources.category_status_waiting
import kotlin.time.Instant

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoriesScreen(state: CategoriesState, onIntent: (CategoriesIntent) -> Unit) {
    Box(Modifier.fillMaxSize().testTag("categories.list")) {
        AppScreen(
            scrollable = false,
            topBar = { CategoriesBackBar(stringResource(Res.string.categories_title)) { onIntent(CategoriesIntent.Back) } },
        ) {
            PullToRefreshBox(
                isRefreshing = state.refreshing,
                onRefresh = { onIntent(CategoriesIntent.Refresh) },
                modifier = Modifier.weight(1f).fillMaxWidth(),
            ) {
                CategoriesContent(state, onIntent)
            }
        }
        AppFab(
            icon = AppIcons.Plus,
            contentDescription = stringResource(Res.string.categories_new),
            onClick = { onIntent(CategoriesIntent.CreateClicked) },
            text = stringResource(Res.string.categories_new),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .navigationBarsPadding()
                .padding(AppTheme.spacing.m)
                .testTag("categories.create"),
        )
    }
}

@Composable
private fun CategoriesContent(state: CategoriesState, onIntent: (CategoriesIntent) -> Unit) {
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.xs),
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 88.dp),
    ) {
        if (state.offline && state.waitingCount > 0) item { SyncBanner(SyncBannerState.Offline(state.waitingCount)) }
        if (state.refreshFailed) {
            item { InlineAlert(AlertTone.Warning, stringResource(Res.string.categories_refresh_failed), Modifier.testTag("categories.refreshFailed")) }
        }
        item {
            SegmentedControl(
                options = CategoryKind.entries,
                label = { stringResource(if (it == CategoryKind.Expense) Res.string.category_kind_expense else Res.string.category_kind_income) },
                selected = state.kind,
                onSelect = { onIntent(CategoriesIntent.KindChosen(it)) },
                optionModifier = { Modifier.testTag("categories.kind.${it.wire}") },
            )
        }
        if (!state.loaded) {
            items3Skeleton()
        } else {
            item { SectionTitle(stringResource(Res.string.categories_own)) }
            if (state.own.isEmpty()) {
                item {
                    Text(
                        stringResource(Res.string.categories_own_empty),
                        style = AppTheme.type.body,
                        color = AppTheme.colors.ink2,
                        modifier = Modifier.padding(horizontal = AppTheme.spacing.xs).testTag("categories.ownEmpty"),
                    )
                }
            }
            itemsIndexed(state.own, key = { _, row -> "own-${row.id}" }) { index, row -> Card(row, "categories.own.$index", onIntent) }
            item { SectionTitle(stringResource(Res.string.categories_defaults)) }
            if (state.defaults.isEmpty() && state.defaultsMissing) {
                item {
                    Text(
                        stringResource(Res.string.categories_defaults_missing),
                        style = AppTheme.type.body,
                        color = AppTheme.colors.ink2,
                        modifier = Modifier.padding(horizontal = AppTheme.spacing.xs).testTag("categories.defaultsMissing"),
                    )
                }
            }
            itemsIndexed(state.defaults, key = { _, row -> "default-${row.id}" }) { index, row -> Card(row, "categories.default.$index", onIntent) }
            if (state.archived.isNotEmpty()) {
                item { SectionTitle(stringResource(Res.string.categories_archived), Modifier.testTag("categories.archivedHeader")) }
                itemsIndexed(state.archived, key = { _, row -> "archived-${row.id}" }) { index, row -> Card(row, "categories.archived.$index", onIntent) }
            }
        }
        if (state.offline && state.updatedAt != null) {
            item {
                Text(
                    stringResource(Res.string.categories_updated_at, formatDateTime(state.updatedAt, state.timeZoneId)),
                    style = AppTheme.type.caption,
                    color = AppTheme.colors.caption,
                    modifier = Modifier.testTag("categories.updatedAt"),
                )
            }
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.items3Skeleton() {
    items(3) { Skeleton(Modifier.fillMaxWidth().height(72.dp), AppTheme.shapes.card) }
}

@Composable
private fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = AppTheme.type.label,
        color = AppTheme.colors.ink2,
        modifier = modifier.padding(top = AppTheme.spacing.xs).semantics { heading() },
    )
}

@Composable
private fun Card(row: CategoryRowUi, tag: String, onIntent: (CategoriesIntent) -> Unit) {
    val status = row.status?.let { status ->
        when (status) {
            CategoryStatusUi.Waiting -> CategoryRowStatus(CategoryRowStatusKind.Waiting, stringResource(Res.string.category_status_waiting))
            CategoryStatusUi.Changed -> CategoryRowStatus(CategoryRowStatusKind.Changed, stringResource(Res.string.category_status_clash))
            CategoryStatusUi.NotSaved -> CategoryRowStatus(CategoryRowStatusKind.NotSaved, stringResource(Res.string.category_status_not_saved))
            CategoryStatusUi.NotDeleted -> CategoryRowStatus(CategoryRowStatusKind.NotSaved, stringResource(Res.string.category_status_in_use))
        }
    }
    CategoryRow(
        name = row.name,
        iconKey = row.iconKey,
        colorHex = row.colorHex,
        caption = when (row.caption) {
            CategoryCaptionUi.None -> null
            CategoryCaptionUi.Default -> stringResource(Res.string.category_default)
            CategoryCaptionUi.Archived -> stringResource(Res.string.category_archived)
        },
        status = status,
        onClick = if (row.clickable) ({ onIntent(CategoriesIntent.CategoryClicked(row.id)) }) else null,
        modifier = Modifier
            .clip(AppTheme.shapes.card)
            .background(AppTheme.colors.surface, AppTheme.shapes.card)
            .testTag(tag),
        statusModifier = Modifier.testTag("$tag.status"),
    )
}

@Preview
@Composable
private fun CategoriesScreenPreview() {
    AppTheme {
        CategoriesScreen(
            CategoriesState(
                own = listOf(
                    CategoryRowUi("1", "Coffee", "restaurant", "#1F7F8C", CategoryCaptionUi.None, CategoryStatusUi.Waiting, true),
                ),
                defaults = listOf(
                    CategoryRowUi("d1", "Groceries", "cart", "#2E7D32", CategoryCaptionUi.Default, null, false),
                ),
                loaded = true,
                updatedAt = Instant.parse("2026-10-02T10:00:00Z"),
            ),
            {},
        )
    }
}
