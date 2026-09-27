package com.fares.saveme.devtools.catalog

import app.cash.turbine.test
import com.fares.saveme.core.presentation.runMviTest
import kotlin.test.Test
import kotlin.test.assertEquals

class ComponentCatalogReducerTest {

    @Test
    fun languageAndFontScaleChanges() {
        val start = ComponentCatalogState()
        val arabic = reduce(start, ComponentCatalogChange.Language(CatalogLanguage.Arabic))
        assertEquals(CatalogLanguage.Arabic, arabic.language)
        assertEquals(2.0f, reduce(arabic, ComponentCatalogChange.FontScale(2.0f)).fontScale)
    }
}

class ComponentCatalogViewModelTest {

    @Test
    fun intentsUpdateStateAndBackIsAnEffect() = runMviTest {
        val vm = ComponentCatalogViewModel()
        vm.onIntent(ComponentCatalogIntent.LanguageSelected(CatalogLanguage.Arabic))
        vm.onIntent(ComponentCatalogIntent.FontScaleSelected(1.5f))
        assertEquals(ComponentCatalogState(CatalogLanguage.Arabic, 1.5f), vm.state.value)
        vm.effects.test {
            vm.onIntent(ComponentCatalogIntent.Back)
            assertEquals(ComponentCatalogEffect.NavigateBack, awaitItem())
        }
    }
}
