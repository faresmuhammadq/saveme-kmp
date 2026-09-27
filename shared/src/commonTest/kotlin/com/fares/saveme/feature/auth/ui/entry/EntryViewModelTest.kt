package com.fares.saveme.feature.auth.ui.entry

import app.cash.turbine.test
import com.fares.saveme.core.config.AppConfig
import com.fares.saveme.core.config.Environment
import com.fares.saveme.core.presentation.runMviTest
import kotlin.test.Test
import kotlin.test.assertEquals

class EntryViewModelTest {

    private fun vm(debug: Boolean) = EntryViewModel(AppConfig("http://h", "h", Environment.Dev, isDebugTools = debug))

    @Test
    fun clicksBecomeNavigationEffects() = runMviTest {
        val vm = vm(debug = true)
        assertEquals(EntryState(isDebugTools = true), vm.state.value)
        vm.effects.test {
            vm.onIntent(EntryIntent.SignInClicked)
            assertEquals(EntryEffect.NavigateSignIn, awaitItem())
            vm.onIntent(EntryIntent.CreateAccountClicked)
            assertEquals(EntryEffect.NavigateRegister, awaitItem())
            vm.onIntent(EntryIntent.DevToolsClicked)
            assertEquals(EntryEffect.NavigateDevTools, awaitItem())
        }
    }

    @Test
    fun devToolsAreUnreachableOutsideDebugBuilds() = runMviTest {
        val vm = vm(debug = false)
        vm.effects.test {
            vm.onIntent(EntryIntent.DevToolsClicked)
            expectNoEvents()
        }
    }
}
