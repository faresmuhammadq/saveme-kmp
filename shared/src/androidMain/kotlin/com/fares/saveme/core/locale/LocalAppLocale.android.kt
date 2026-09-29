package com.fares.saveme.core.locale

import android.content.res.Configuration
import android.content.res.Resources
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidedValue
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import java.util.Locale

actual object LocalAppLocale {
    actual val current: String
        @Composable get() = Locale.getDefault().toString()

    @Suppress("DEPRECATION")
    @Composable
    actual infix fun provides(value: String?): ProvidedValue<*> {
        val system = Resources.getSystem().configuration.locales[0]
        val locale = value?.let { Locale.forLanguageTag(it) } ?: system
        Locale.setDefault(locale)
        // A copy, so every reader of LocalConfiguration sees a new value and recomposes.
        val configuration = Configuration(LocalConfiguration.current).apply { setLocale(locale) }
        val resources = LocalContext.current.resources
        resources.updateConfiguration(configuration, resources.displayMetrics)
        return LocalConfiguration provides configuration
    }
}
