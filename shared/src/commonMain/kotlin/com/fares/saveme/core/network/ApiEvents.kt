package com.fares.saveme.core.network

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/** Cross-cutting outcomes any call can produce. The only channel from `core` to features (P-10.7). */
sealed interface ApiEvent {
    /** 401 on a request that carried a credential: the session was ended elsewhere. */
    data object SessionEnded : ApiEvent

    /** 426: this app version is below the server's minimum. */
    data class UpdateRequired(val minimumVersion: String?) : ApiEvent

    /** 403 EMAIL_NOT_VERIFIED: the action needs a verified address. */
    data object EmailNotVerified : ApiEvent
}

class ApiEvents {
    private val mutableEvents = MutableSharedFlow<ApiEvent>(extraBufferCapacity = 8)
    val events: SharedFlow<ApiEvent> = mutableEvents.asSharedFlow()

    fun publish(event: ApiEvent) {
        mutableEvents.tryEmit(event)
    }
}
