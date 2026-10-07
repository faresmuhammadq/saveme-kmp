package com.fares.saveme.devtools.playbilling

import com.fares.saveme.core.config.AppConfig
import com.fares.saveme.feature.billing.data.play.PlayBillingClient
import com.fares.saveme.feature.billing.data.play.UnavailablePlayBillingClient
import org.koin.core.scope.Scope

/**
 * The Google Play port an Android build binds (spec 018 R-12): the build's own client, wrapped with the scripted one in development
 * builds only (`isDebugTools`). Until the Play Billing wiring phase the build's own client is the unavailable one.
 */
fun Scope.devOrRealPlayClient(real: PlayBillingClient = UnavailablePlayBillingClient(platformSupported = true)): PlayBillingClient =
    if (get<AppConfig>().isDebugTools) SwitchingPlayBillingClient(real, ScriptedPlayBillingClient()) else real
