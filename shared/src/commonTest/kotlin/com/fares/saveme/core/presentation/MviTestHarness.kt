package com.fares.saveme.core.presentation

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain

/**
 * Runs a store test with `Dispatchers.Main` (which `viewModelScope` uses) bound to the test
 * scheduler, so intents handled in `viewModelScope.launch` run eagerly and deterministically.
 */
@OptIn(ExperimentalCoroutinesApi::class)
fun runMviTest(block: suspend TestScope.() -> Unit) = runTest {
    Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
    try {
        block()
    } finally {
        Dispatchers.resetMain()
    }
}
