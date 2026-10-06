package com.fares.saveme.feature.notifications.ui.open

import androidx.lifecycle.viewModelScope
import com.fares.saveme.core.presentation.MviViewModel
import com.fares.saveme.feature.notifications.domain.PushRoute
import com.fares.saveme.feature.notifications.domain.PushTarget
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** Waits for [open] (a read of at most 10 s), then asks the nav host to open the target in this screen's place. */
class NotificationOpenViewModel(
    private val route: PushRoute,
    private val open: suspend (PushRoute) -> PushTarget,
) : MviViewModel<NotificationOpenState, NotificationOpenIntent, NotificationOpenEffect>(NotificationOpenState()) {

    private var job: Job? = null

    override fun onIntent(intent: NotificationOpenIntent) {
        when (intent) {
            NotificationOpenIntent.Started -> if (job == null) {
                setState { reduce(it, NotificationOpenChange.Waiting) }
                job = viewModelScope.launch {
                    val target = open(route)
                    setState { reduce(it, NotificationOpenChange.Finished) }
                    sendEffect(NotificationOpenEffect.Replace(target))
                }
            }
            NotificationOpenIntent.Cancel -> {
                // The read may finish in the background; nothing opens afterwards (FR-032).
                job?.cancel()
                setState { reduce(it, NotificationOpenChange.Finished) }
                sendEffect(NotificationOpenEffect.Close)
            }
        }
    }
}
