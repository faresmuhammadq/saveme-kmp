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
}
