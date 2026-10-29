package com.fares.saveme.feature.categories.ui.list

import com.fares.saveme.feature.categories.domain.model.CategoriesOverview
import com.fares.saveme.feature.categories.domain.model.CategoryDisplay
import com.fares.saveme.feature.categories.domain.model.CategoryKind
import com.fares.saveme.feature.categories.domain.model.CategoryView
import com.fares.saveme.feature.categories.domain.model.RefusalReason
import com.fares.saveme.feature.categories.domain.model.SyncState
import kotlin.time.Instant

enum class CategoryStatusUi { Waiting, Changed, NotSaved, NotDeleted }

enum class CategoryCaptionUi { None, Default, Archived }

/** What one row shows; mapped from the domain so the screen holds no business rules. */
data class CategoryRowUi(
    val id: String,
    val name: String,
    val iconKey: String?,
    val colorHex: String?,
    val caption: CategoryCaptionUi,
    val status: CategoryStatusUi?,
    /** Default rows are not clickable (FR-011). */
    val clickable: Boolean,
)

data class CategoriesState(
    val kind: CategoryKind = CategoryKind.Expense,
    val own: List<CategoryRowUi> = emptyList(),
    val defaults: List<CategoryRowUi> = emptyList(),
    val archived: List<CategoryRowUi> = emptyList(),
    val refreshing: Boolean = false,
    val updatedAt: Instant? = null,
    val offline: Boolean = false,
    val refreshFailed: Boolean = false,
    val defaultsMissing: Boolean = false,
    /** False until a refresh has finished or something is on the phone: shows a skeleton, not the empty note. */
    val loaded: Boolean = false,
    val waitingCount: Int = 0,
    val timeZoneId: String = "UTC",
    /** Active categories of the other kind (own and defaults), for the count on its chip. */
    val otherKindCount: Int = 0,
) {
    /** Active categories of the chosen kind (own and defaults). */
    val kindCount: Int get() = own.size + defaults.size
}

sealed interface CategoriesIntent {
    data object Opened : CategoriesIntent
    data class KindChosen(val kind: CategoryKind) : CategoriesIntent
    data object Refresh : CategoriesIntent
    data object CreateClicked : CategoriesIntent
    data class CategoryClicked(val id: String) : CategoriesIntent
    data object Back : CategoriesIntent
}

sealed interface CategoriesEffect {
    /** A null [id] creates a category of [kind]. */
    data class OpenEditor(val id: String?, val kind: CategoryKind) : CategoriesEffect
    data object RequireVerification : CategoriesEffect
    data object NavigateBack : CategoriesEffect
}

sealed interface CategoriesChange {
    data class Stored(val overview: CategoriesOverview) : CategoriesChange
    data class Kind(val kind: CategoryKind) : CategoriesChange
    data class TimeZone(val id: String) : CategoriesChange
    data class Refreshing(val busy: Boolean) : CategoriesChange
    data class RefreshDone(val offline: Boolean, val failed: Boolean) : CategoriesChange
}

fun reduce(state: CategoriesState, change: CategoriesChange): CategoriesState = when (change) {
    is CategoriesChange.Stored -> {
        val own = change.overview.own.map { it.toRow(CategoryCaptionUi.None) }
        val archived = change.overview.archived.map { it.toRow(CategoryCaptionUi.Archived) }
        state.copy(
            own = own,
            defaults = change.overview.defaults.map { it.toRow() },
            archived = archived,
            updatedAt = change.overview.updatedAt,
            defaultsMissing = change.overview.defaultsMissing,
            otherKindCount = change.overview.otherKindCount,
            loaded = state.loaded || change.overview.loaded,
            waitingCount = (change.overview.own + change.overview.archived).count { it.syncState is SyncState.Waiting },
        )
    }
    is CategoriesChange.Kind -> if (change.kind == state.kind) state else state.copy(kind = change.kind)
    is CategoriesChange.TimeZone -> state.copy(timeZoneId = change.id)
    is CategoriesChange.Refreshing -> state.copy(refreshing = change.busy)
    is CategoriesChange.RefreshDone -> state.copy(refreshing = false, loaded = true, offline = change.offline, refreshFailed = change.failed)
}

internal fun CategoryView.toRow(caption: CategoryCaptionUi) = CategoryRowUi(
    id = id,
    name = shown.name,
    iconKey = shown.icon,
    colorHex = shown.color,
    caption = caption,
    status = when (val sync = syncState) {
        SyncState.None -> null
        SyncState.Waiting -> CategoryStatusUi.Waiting
        is SyncState.Clash -> CategoryStatusUi.Changed
        is SyncState.Refused -> if (sync.reason == RefusalReason.InUse) CategoryStatusUi.NotDeleted else CategoryStatusUi.NotSaved
    },
    clickable = true,
)

internal fun CategoryDisplay.toRow() = CategoryRowUi(
    id = id, name = name, iconKey = iconKey, colorHex = colorHex, caption = CategoryCaptionUi.Default, status = null, clickable = false,
)
