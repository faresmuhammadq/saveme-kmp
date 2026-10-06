package com.fares.saveme.navigation

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * The data of a tapped notification, handed over by the host: launcher extras of a background message or the app's own
 * pending intent (spec 017 R-10). Untrusted input: only [AppNavHost] parses it, and only to navigate (FR-029, FR-044).
 * Replays one tap so a cold start does not lose it; [consume] forgets it once handled.
 */
class PushTapBus {
    private val mutableTaps = MutableSharedFlow<Map<String, String>>(replay = 1, extraBufferCapacity = 4)
    val taps: SharedFlow<Map<String, String>> = mutableTaps.asSharedFlow()

    fun submit(data: Map<String, String>) {
        mutableTaps.tryEmit(data)
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    fun consume() {
        mutableTaps.resetReplayCache()
    }
}
