package com.fares.saveme.core.presentation

import app.cash.turbine.test
import kotlin.test.Test
import kotlin.test.assertEquals

private data class CounterState(val count: Int = 0)

private sealed interface CounterIntent {
    data object Increment : CounterIntent
    data object Noop : CounterIntent
    data object Announce : CounterIntent
}

private sealed interface CounterEffect {
    data class Announced(val count: Int) : CounterEffect
}

private class CounterViewModel : MviViewModel<CounterState, CounterIntent, CounterEffect>(CounterState()) {
    override fun onIntent(intent: CounterIntent) {
        when (intent) {
            CounterIntent.Increment -> setState { it.copy(count = it.count + 1) }
            CounterIntent.Noop -> setState { it }
            CounterIntent.Announce -> sendEffect(CounterEffect.Announced(currentState.count))
        }
    }
}

class MviViewModelTest {

    @Test
    fun intentsReduceIntoNewStates() = runMviTest {
        val vm = CounterViewModel()
        vm.state.test {
            assertEquals(CounterState(0), awaitItem())
            vm.onIntent(CounterIntent.Increment)
            assertEquals(CounterState(1), awaitItem())
            vm.onIntent(CounterIntent.Noop)
            expectNoEvents()
        }
    }

    @Test
    fun effectsAreBufferedAndDeliveredOnceInOrder() = runMviTest {
        val vm = CounterViewModel()
        vm.onIntent(CounterIntent.Announce)
        vm.onIntent(CounterIntent.Increment)
        vm.onIntent(CounterIntent.Announce)
        vm.effects.test {
            assertEquals(CounterEffect.Announced(0), awaitItem())
            assertEquals(CounterEffect.Announced(1), awaitItem())
            expectNoEvents()
        }
    }
}
