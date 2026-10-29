package com.fares.saveme.feature.server

import com.fares.saveme.core.config.ServerAddressSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ServerAddressViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @BeforeTest fun setUp() = Dispatchers.setMain(dispatcher)
    @AfterTest fun tearDown() = Dispatchers.resetMain()

    private class FakeSettings(var stored: String? = null) : ServerAddressSettings {
        override val default = "192.168.1.14:8091"
        var restarts = 0
        override fun current() = stored ?: default
        override fun save(hostPort: String) { stored = hostPort }
        override fun reset() { stored = null }
        override fun restartApp() { restarts++ }
    }

    @Test
    fun startsWithTheCurrentAddress() {
        val vm = ServerAddressViewModel(FakeSettings("10.0.0.5:9000")) { true }
        assertEquals("10.0.0.5:9000", vm.state.value.input)
        assertEquals("192.168.1.14:8091", vm.state.value.default)
    }

    @Test
    fun saveStoresNormalisedAddressAndRestarts() {
        val settings = FakeSettings()
        val vm = ServerAddressViewModel(settings) { true }
        vm.onIntent(ServerAddressIntent.InputChanged("http://10.0.0.5:9000/"))
        vm.onIntent(ServerAddressIntent.SaveClicked)
        assertEquals("10.0.0.5:9000", settings.stored)
        assertEquals(1, settings.restarts)
    }

    @Test
    fun invalidInputIsNotSavedOrTested() {
        val settings = FakeSettings()
        var probed = false
        val vm = ServerAddressViewModel(settings) { probed = true; true }
        vm.onIntent(ServerAddressIntent.InputChanged("host/path"))
        vm.onIntent(ServerAddressIntent.SaveClicked)
        vm.onIntent(ServerAddressIntent.TestClicked)
        assertTrue(vm.state.value.invalid)
        assertEquals(null, settings.stored)
        assertEquals(0, settings.restarts)
        assertEquals(false, probed)
    }

    @Test
    fun testReportsReachableAndUnreachable() = runTest(dispatcher) {
        var answer = true
        val vm = ServerAddressViewModel(FakeSettings()) { answer }
        vm.onIntent(ServerAddressIntent.TestClicked)
        assertEquals(ServerTestResult.Testing, vm.state.value.test)
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(ServerTestResult.Reachable, vm.state.value.test)

        answer = false
        vm.onIntent(ServerAddressIntent.TestClicked)
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(ServerTestResult.Unreachable, vm.state.value.test)
    }

    @Test
    fun aThrowingProbeCountsAsUnreachable() = runTest(dispatcher) {
        val vm = ServerAddressViewModel(FakeSettings()) { error("boom") }
        vm.onIntent(ServerAddressIntent.TestClicked)
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(ServerTestResult.Unreachable, vm.state.value.test)
    }

    @Test
    fun resetClearsTheOverrideAndRestarts() {
        val settings = FakeSettings("10.0.0.5:9000")
        val vm = ServerAddressViewModel(settings) { true }
        vm.onIntent(ServerAddressIntent.ResetClicked)
        assertEquals(null, settings.stored)
        assertEquals(1, settings.restarts)
    }
}
