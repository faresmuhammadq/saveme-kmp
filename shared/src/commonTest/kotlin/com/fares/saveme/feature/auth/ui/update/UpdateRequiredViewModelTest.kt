package com.fares.saveme.feature.auth.ui.update

import app.cash.turbine.test
import com.fares.saveme.core.presentation.runMviTest
import kotlin.test.Test
import kotlin.test.assertEquals

class UpdateRequiredViewModelTest {
    @Test
    fun namesTheVersionAndOpensTheStore() = runMviTest {
        val vm = UpdateRequiredViewModel("0.3.0")
        assertEquals("0.3.0", vm.state.value.minimumVersion)
        vm.effects.test {
            vm.onIntent(UpdateRequiredIntent.UpdateClicked)
            assertEquals(UpdateRequiredEffect.OpenStore(STORE_URL), awaitItem())
        }
    }
}
