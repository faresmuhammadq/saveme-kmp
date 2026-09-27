package com.fares.saveme.devtools.catalog

import com.fares.saveme.core.presentation.MviViewModel

class ComponentCatalogViewModel :
    MviViewModel<ComponentCatalogState, ComponentCatalogIntent, ComponentCatalogEffect>(ComponentCatalogState()) {

    override fun onIntent(intent: ComponentCatalogIntent) {
        when (intent) {
            is ComponentCatalogIntent.LanguageSelected -> setState { reduce(it, ComponentCatalogChange.Language(intent.language)) }
            is ComponentCatalogIntent.FontScaleSelected -> setState { reduce(it, ComponentCatalogChange.FontScale(intent.scale)) }
            ComponentCatalogIntent.Back -> sendEffect(ComponentCatalogEffect.NavigateBack)
        }
    }
}
