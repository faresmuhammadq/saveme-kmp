package com.fares.saveme.navigation

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/** Raw URIs from the host (tapped mail links, dev paste screen). Parsed once in [AppNavHost]. */
class DeepLinkBus {
    private val mutableLinks = MutableSharedFlow<String>(replay = 1, extraBufferCapacity = 4)
    val links: SharedFlow<String> = mutableLinks.asSharedFlow()

    fun submit(uri: String) {
        mutableLinks.tryEmit(uri)
    }

    /**
     * Drops the replayed link once it has been handled. The replay only exists so a link
     * submitted before the collector starts (cold start) isn't lost; without this, the same link
     * would open again every time the collector restarts (for example after sign-in).
     */
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    fun consume() {
        mutableLinks.resetReplayCache()
    }
}
