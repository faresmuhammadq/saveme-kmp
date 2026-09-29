package com.fares.saveme.core.locale

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidedValue

/**
 * Runtime locale override for Compose resources (Compose Multiplatform "change the app locale"
 * pattern). `null` restores the system locale.
 */
expect object LocalAppLocale {
    val current: String @Composable get

    @Composable
    infix fun provides(value: String?): ProvidedValue<*>
}
