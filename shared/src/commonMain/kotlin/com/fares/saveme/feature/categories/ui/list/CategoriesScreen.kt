package com.fares.saveme.feature.categories.ui.list

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fares.saveme.designsystem.component.AlertTone
import com.fares.saveme.designsystem.component.CalmCircleButton
import com.fares.saveme.designsystem.component.CalmScreen
import com.fares.saveme.designsystem.component.CalmSectionTitle
import com.fares.saveme.designsystem.component.CalmTitle
import com.fares.saveme.designsystem.component.CategoryRowStatus
import com.fares.saveme.designsystem.component.CategoryRowStatusKind
import com.fares.saveme.designsystem.component.CategoryTile
import com.fares.saveme.designsystem.component.InlineAlert
import com.fares.saveme.designsystem.component.Skeleton
import com.fares.saveme.designsystem.component.SyncBanner
import com.fares.saveme.designsystem.component.SyncBannerState
import com.fares.saveme.designsystem.format.formatDateTime
import com.fares.saveme.designsystem.icon.AppIcons
import com.fares.saveme.designsystem.theme.AppTheme
import com.fares.saveme.feature.categories.domain.model.CategoryKind
import org.jetbrains.compose.resources.stringResource
import saveme.shared.generated.resources.Res
import saveme.shared.generated.resources.categories_archived
import saveme.shared.generated.resources.categories_calm_add
import saveme.shared.generated.resources.categories_calm_defaults
import saveme.shared.generated.resources.categories_defaults_missing
import saveme.shared.generated.resources.categories_new
import saveme.shared.generated.resources.categories_calm_yours
import saveme.shared.generated.resources.categories_own_empty
import saveme.shared.generated.resources.categories_refresh_failed
import saveme.shared.generated.resources.categories_title
import saveme.shared.generated.resources.categories_updated_at
import saveme.shared.generated.resources.category_archived
import saveme.shared.generated.resources.category_kind_expense
import saveme.shared.generated.resources.category_kind_income
import saveme.shared.generated.resources.category_status_clash
import saveme.shared.generated.resources.category_status_in_use
import saveme.shared.generated.resources.category_status_not_saved
import saveme.shared.generated.resources.category_status_waiting
import saveme.shared.generated.resources.cd_back
import kotlin.time.Instant

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoriesScreen(state: CategoriesState, onIntent: (CategoriesIntent) -> Unit) {
    Box(Modifier.fillMaxSize().testTag("categories.list")) {
        CalmScreen(
            title = null,
            spacing = 0.dp,
            titleSlot = { CalmTitle(stringResource(Res.string.categories_title), Modifier.padding(top = 12.dp)) },
            onBack = { onIntent(CategoriesIntent.Back) },
            backDescription = stringResource(Res.string.cd_back),
            scrollable = false,
            actions = {
                CalmCircleButton(
                    AppIcons.Plus, stringResource(Res.string.categories_new), { onIntent(CategoriesIntent.CreateClicked) },
                    tint = AppTheme.colors.onPrimary,
                    container = AppTheme.colors.primary,
                    bordered = false,
                    modifier = Modifier.testTag("categories.create"),
                )
            },
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.xs), modifier = Modifier.padding(top = 6.dp).selectableGroup()) {
                CategoryKind.entries.forEach { kind ->
                    val name = stringResource(if (kind == CategoryKind.Expense) Res.string.category_kind_expense else Res.string.category_kind_income)
                    KindChip(
                        label = "$name · ${if (kind == state.kind) state.kindCount else state.otherKindCount}",
                        selected = kind == state.kind,
                        onClick = { onIntent(CategoriesIntent.KindChosen(kind)) },
                        modifier = Modifier.testTag("categories.kind.${kind.wire}"),
                    )
                }
            }
            PullToRefreshBox(
                isRefreshing = state.refreshing,
                onRefresh = { onIntent(CategoriesIntent.Refresh) },
                modifier = Modifier.weight(1f).fillMaxWidth(),
            ) {
                CategoriesContent(state, onIntent)
            }
        }
    }
}

/** The kind switch: a 36dp pill, 16dp inside, ink-filled when chosen; the touch area stays 48dp. */
@Composable
private fun KindChip(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier.heightIn(min = 48.dp).selectable(selected = selected, role = Role.Button, onClick = onClick),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .height(36.dp)
                .background(if (selected) AppTheme.colors.ink else AppTheme.colors.surface, AppTheme.shapes.pill)
                .then(if (selected) Modifier else Modifier.border(1.dp, AppTheme.colors.outlineVariant, AppTheme.shapes.pill))
                .padding(horizontal = 16.dp),
        ) {
            Text(
                label,
                style = AppTheme.type.label.copy(fontSize = 13.5.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold),
                color = if (selected) AppTheme.colors.background else AppTheme.colors.ink,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun CategoriesContent(state: CategoriesState, onIntent: (CategoriesIntent) -> Unit) {
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(0.dp),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = AppTheme.spacing.l),
    ) {
        if (state.offline && state.waitingCount > 0) item { SyncBanner(SyncBannerState.Offline(state.waitingCount)) }
        if (state.refreshFailed) {
            item { InlineAlert(AlertTone.Warning, stringResource(Res.string.categories_refresh_failed), Modifier.testTag("categories.refreshFailed")) }
        }
        if (!state.loaded) {
            items(3) { Skeleton(Modifier.fillMaxWidth().height(96.dp), AppTheme.shapes.hero) }
        } else {
            item { CalmSectionTitle(stringResource(Res.string.categories_calm_yours)) }
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
            tileRows(state.own, "own", onIntent, withAdd = true)
            item { CalmSectionTitle(stringResource(Res.string.categories_calm_defaults)) }
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
            tileRows(state.defaults, "default", onIntent)
            if (state.archived.isNotEmpty()) {
                item {
                    CalmSectionTitle(
                        stringResource(Res.string.categories_archived),
                        Modifier.padding(start = 2.dp).testTag("categories.archivedHeader"),
                    )
                }
                tileRows(state.archived, "archived", onIntent)
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

/** Category tiles three to a row; [withAdd] ends the grid with the dashed "Add" tile. */
private fun LazyListScope.tileRows(rows: List<CategoryRowUi>, group: String, onIntent: (CategoriesIntent) -> Unit, withAdd: Boolean = false) {
    val cells: List<CategoryRowUi?> = if (withAdd) rows + listOf(null) else rows
    val indexed = cells.withIndex().toList().chunked(3)
    itemsIndexed(indexed, key = { _, chunk -> "$group-${chunk.first().value?.id ?: "add"}" }) { position, chunk ->
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.padding(top = if (position == 0) 0.dp else 10.dp).height(IntrinsicSize.Min),
        ) {
            chunk.forEach { (index, row) ->
                val cell = Modifier.weight(1f).fillMaxHeight()
                if (row == null) AddTile(cell, onIntent) else CategoryTileCard(row, "categories.$group.$index", onIntent, cell)
            }
            repeat(3 - chunk.size) { Spacer(Modifier.weight(1f)) }
        }
    }
}

@Composable
private fun CategoryTileCard(row: CategoryRowUi, tag: String, onIntent: (CategoriesIntent) -> Unit, modifier: Modifier = Modifier) {
    val status = row.status?.let { status ->
        when (status) {
            CategoryStatusUi.Waiting -> CategoryRowStatus(CategoryRowStatusKind.Waiting, stringResource(Res.string.category_status_waiting))
            CategoryStatusUi.Changed -> CategoryRowStatus(CategoryRowStatusKind.Changed, stringResource(Res.string.category_status_clash))
            CategoryStatusUi.NotSaved -> CategoryRowStatus(CategoryRowStatusKind.NotSaved, stringResource(Res.string.category_status_not_saved))
            CategoryStatusUi.NotDeleted -> CategoryRowStatus(CategoryRowStatusKind.NotSaved, stringResource(Res.string.category_status_in_use))
        }
    }
    val caption = when (row.caption) {
        CategoryCaptionUi.None -> null
        CategoryCaptionUi.Default -> null
        CategoryCaptionUi.Archived -> stringResource(Res.string.category_archived)
    }
    val shape = RoundedCornerShape(20.dp)
    Column(
        verticalArrangement = Arrangement.spacedBy(14.dp),
        modifier = modifier
            .testTag(tag)
            .clip(shape)
            .background(AppTheme.colors.surface, shape)
            .border(1.dp, AppTheme.colors.outlineVariant, shape)
            .then(
                if (row.clickable) {
                    Modifier.clickable(role = Role.Button, onClick = { onIntent(CategoriesIntent.CategoryClicked(row.id)) })
                } else {
                    Modifier.semantics(mergeDescendants = true) {}
                },
            )
            .padding(14.dp),
    ) {
        CategoryTile(row.iconKey, row.colorHex)
        Column {
            Text(
                row.name,
                style = AppTheme.type.label.copy(fontSize = 13.5.sp, fontWeight = FontWeight.Bold),
                color = AppTheme.colors.primary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (caption != null) Text(caption, style = AppTheme.type.caption.copy(fontSize = 11.sp), color = AppTheme.colors.caption)
            if (status != null) {
                Text(
                    status.label,
                    style = AppTheme.type.caption.copy(fontSize = 11.sp),
                    color = if (status.kind == CategoryRowStatusKind.NotSaved) AppTheme.colors.error else AppTheme.money.pending,
                    modifier = Modifier.testTag("$tag.status"),
                )
            }
        }
    }
}

@Composable
private fun AddTile(modifier: Modifier, onIntent: (CategoriesIntent) -> Unit) {
    val shape = RoundedCornerShape(20.dp)
    val stroke = AppTheme.colors.caption
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterVertically),
        modifier = modifier
            .heightIn(min = 96.dp)
            .clip(shape)
            .drawBehind {
                drawRoundRect(
                    color = stroke,
                    cornerRadius = CornerRadius(20.dp.toPx()),
                    style = Stroke(1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 3.dp.toPx()))),
                )
            }
            .clickable(role = Role.Button, onClick = { onIntent(CategoriesIntent.CreateClicked) })
            .padding(14.dp),
    ) {
        Icon(AppIcons.Plus, contentDescription = null, tint = AppTheme.colors.ink2)
        Text(
            stringResource(Res.string.categories_calm_add),
            style = AppTheme.type.label.copy(fontSize = 13.5.sp, fontWeight = FontWeight.Bold),
            color = AppTheme.colors.ink2,
        )
    }
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
