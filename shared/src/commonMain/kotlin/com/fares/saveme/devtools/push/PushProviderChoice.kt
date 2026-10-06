package com.fares.saveme.devtools.push

import com.fares.saveme.core.config.AppConfig
import com.fares.saveme.feature.notifications.domain.PushReceiver
import com.fares.saveme.feature.notifications.domain.PushTokenProvider
import com.fares.saveme.feature.notifications.domain.UnavailablePushTokenProvider
import org.koin.core.scope.Scope

/**
 * The provider a build binds until the gated Firebase phase: the fake one in development builds (`isDebugTools`), none in
 * staging and production (FR-036, FR-046). The receiver is resolved when a rotation happens, never while the graph is built.
 */
fun Scope.devOrUnavailablePushProvider(): PushTokenProvider =
    if (get<AppConfig>().isDebugTools) {
        DevFakePushTokenProvider(store = get(), onNewToken = { get<PushReceiver>().onNewToken() })
    } else {
        UnavailablePushTokenProvider
    }
