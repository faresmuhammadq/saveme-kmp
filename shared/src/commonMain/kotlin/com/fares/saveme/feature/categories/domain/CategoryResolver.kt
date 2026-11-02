package com.fares.saveme.feature.categories.domain

import com.fares.saveme.core.locale.AppLanguage
import com.fares.saveme.feature.categories.domain.model.CategoryDisplay
import com.fares.saveme.feature.categories.domain.model.CategoryReference
import com.fares.saveme.feature.categories.domain.model.CategorySource
import com.fares.saveme.feature.categories.domain.model.CategoryView
import com.fares.saveme.feature.categories.domain.model.DefaultCategory

/** The display of an own category with its waiting change applied. */
internal fun CategoryView.toDisplay() = CategoryDisplay(
    id = id, name = shown.name, iconKey = shown.icon, colorHex = shown.color, kindWire = server.kindWire,
    source = CategorySource.Own, archived = archived,
)

/** A default's display; its name follows the app language (FR-003), a retired one still resolves. */
internal fun DefaultCategory.toDisplay(language: AppLanguage) = CategoryDisplay(
    id = id, name = if (language == AppLanguage.Ar) nameAr else nameEn, iconKey = icon, colorHex = color,
    kindWire = kindWire, source = CategorySource.Default, archived = retiredAt != null, systemKey = systemKey,
)

internal fun CategoryReference.toDisplay() = CategoryDisplay(
    id = categoryId, name = name, iconKey = icon, colorHex = color, kindWire = kindWire,
    source = CategorySource.Partner, archived = false,
)

internal fun unavailableDisplay(id: String) = CategoryDisplay(
    id = id, name = "", iconKey = null, colorHex = null, kindWire = null, source = CategorySource.Unavailable, archived = false,
)

/**
 * Resolves a category id for display, offline, in this order: the person's own categories
 * (archived and waiting-deletion included), the default set (retired included), the wallet's
 * references, then the "unavailable" placeholder (research R-10, FR-019).
 */
internal class CategoryResolver(
    own: List<CategoryView>,
    defaults: List<DefaultCategory>,
    references: List<CategoryReference>,
    private val language: AppLanguage,
) {
    private val ownById = own.associateBy { it.id }
    private val defaultsById = defaults.associateBy { it.id }
    private val referencesById = references.associateBy { it.categoryId }

    fun resolve(id: String): CategoryDisplay =
        ownById[id]?.toDisplay()
            ?: defaultsById[id]?.toDisplay(language)
            ?: referencesById[id]?.toDisplay()
            ?: unavailableDisplay(id)
}
