package com.fares.saveme.devtools

import app.cash.turbine.test
import com.fares.saveme.core.presentation.runMviTest
import kotlin.test.Test
import kotlin.test.assertEquals

class DevToolsViewModelTest {
    @Test
    fun clicksBecomeNavigationEffects() = runMviTest {
        val vm = DevToolsViewModel()
        vm.effects.test {
            vm.onIntent(DevToolsIntent.ComponentsClicked)
            assertEquals(DevToolsEffect.NavigateCatalog, awaitItem())
            vm.onIntent(DevToolsIntent.PasteLinkClicked)
            assertEquals(DevToolsEffect.NavigatePasteLink, awaitItem())
            vm.onIntent(DevToolsIntent.Back)
            assertEquals(DevToolsEffect.NavigateBack, awaitItem())
        }
    }
}
