package com.fares.saveme.feature.categories.domain

import com.fares.saveme.core.locale.AppLanguage
import com.fares.saveme.feature.categories.domain.model.CategoriesOverview
import com.fares.saveme.feature.categories.domain.model.CategoryKind
import com.fares.saveme.feature.categories.domain.model.CategoryView
import com.fares.saveme.feature.categories.domain.model.DefaultCategory
import kotlin.time.Instant

/**
 * The categories screen's data for one kind (FR-002): active own by name, active defaults in
 * platform order (names in [language]), archived own by name. Categories hidden by a waiting
 * deletion, retired defaults and other kinds are left out.
 */
internal fun buildOverview(
    kind: CategoryKind,
    views: List<CategoryView>,
    defaults: List<DefaultCategory>,
    language: AppLanguage,
    ownRefreshedAt: Instant?,
    defaultsRefreshedAt: Instant?,
): CategoriesOverview {
    val ofKind = views.filter { it.server.kind == kind && !it.hidden }
    val stamps = listOfNotNull(ownRefreshedAt, defaultsRefreshedAt)
    return CategoriesOverview(
        kind = kind,
        own = ofKind.filter { !it.archived }.sortedByName(),
        defaults = defaults
            .filter { it.kind == kind && it.retiredAt == null }
            .sortedBy { it.position }
            .map { it.toDisplay(language) },
        archived = ofKind.filter { it.archived }.sortedByName(),
        updatedAt = stamps.minOrNull(),
        loaded = stamps.isNotEmpty() || views.isNotEmpty(),
        defaultsMissing = defaults.isEmpty(),
        otherKindCount = views.count { it.server.kind != kind && !it.hidden && !it.archived } +
            defaults.count { it.kind != kind && it.retiredAt == null },
    )
}
