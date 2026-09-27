package com.fares.saveme.devtools.catalog

enum class CatalogLanguage { English, Arabic }

data class ComponentCatalogState(
    val language: CatalogLanguage = CatalogLanguage.English,
    val fontScale: Float = 1f,
)

sealed interface ComponentCatalogIntent {
    data class LanguageSelected(val language: CatalogLanguage) : ComponentCatalogIntent
    data class FontScaleSelected(val scale: Float) : ComponentCatalogIntent
    data object Back : ComponentCatalogIntent
}

sealed interface ComponentCatalogEffect {
    data object NavigateBack : ComponentCatalogEffect
}

sealed interface ComponentCatalogChange {
    data class Language(val language: CatalogLanguage) : ComponentCatalogChange
    data class FontScale(val scale: Float) : ComponentCatalogChange
}

fun reduce(state: ComponentCatalogState, change: ComponentCatalogChange): ComponentCatalogState = when (change) {
    is ComponentCatalogChange.Language -> state.copy(language = change.language)
    is ComponentCatalogChange.FontScale -> state.copy(fontScale = change.scale)
}
