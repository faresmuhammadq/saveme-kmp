package com.fares.saveme.feature.categories.domain

import com.fares.saveme.core.locale.AppLanguage
import com.fares.saveme.feature.categories.domain.model.CategoryChoices
import com.fares.saveme.feature.categories.domain.model.CategoryKind
import com.fares.saveme.feature.categories.domain.model.CategoryReference
import com.fares.saveme.feature.categories.domain.model.CategoryView
import com.fares.saveme.feature.categories.domain.model.DefaultCategory

/** Own categories by name (case-insensitive), ties by creation (research R-11). */
internal fun List<CategoryView>.sortedByName(): List<CategoryView> =
    sortedWith(compareBy({ it.shown.name.lowercase() }, { it.server.createdAt }, { it.id }))

/**
 * The categories a person may pick for a new or re-categorised transaction of [kind] (FR-018):
 * active own, then active defaults. Never a partner's, an archived own, a retired default, one of
 * another or an unknown kind. The current category stays listed whatever its state.
 */
internal fun buildChoices(
    kind: CategoryKind,
    currentCategoryId: String?,
    own: List<CategoryView>,
    defaults: List<DefaultCategory>,
    references: List<CategoryReference>,
    language: AppLanguage,
): CategoryChoices {
    val ownChoices = own.filter { it.server.kind == kind && !it.archived && !it.hidden }.sortedByName().map { it.toDisplay() }
    val defaultChoices = defaults
        .filter { it.kind == kind && it.retiredAt == null }
        .sortedBy { it.position }
        .map { it.toDisplay(language) }
    val current = currentCategoryId
        ?.takeIf { id -> ownChoices.none { it.id == id } && defaultChoices.none { it.id == id } }
        ?.let { CategoryResolver(own, defaults, references, language).resolve(it) }
    return CategoryChoices(current = current, own = ownChoices, defaults = defaultChoices)
}
