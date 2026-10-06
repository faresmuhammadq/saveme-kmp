package com.fares.saveme.feature.notifications.ui.open

import com.fares.saveme.feature.notifications.domain.PushTarget

/** The "Opening…" screen while a tap's read runs (FR-032). */
data class NotificationOpenState(val waiting: Boolean = false)

sealed interface NotificationOpenIntent {
    data object Started : NotificationOpenIntent
    data object Cancel : NotificationOpenIntent
}

sealed interface NotificationOpenEffect {
    /** The nav host swaps this screen for the target's. */
    data class Replace(val target: PushTarget) : NotificationOpenEffect
    data object Close : NotificationOpenEffect
}

sealed interface NotificationOpenChange {
    data object Waiting : NotificationOpenChange
    data object Finished : NotificationOpenChange
}

fun reduce(state: NotificationOpenState, change: NotificationOpenChange): NotificationOpenState = when (change) {
    NotificationOpenChange.Waiting -> state.copy(waiting = true)
    NotificationOpenChange.Finished -> state.copy(waiting = false)
}
