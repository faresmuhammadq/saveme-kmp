package com.fares.saveme.designsystem.component

import androidx.compose.material3.CalendarLocale
import java.util.Locale

/** The language with Latin digits (`-u-nu-latn`), so Arabic dates and numbers print 0–9 (P-06.2). */
internal actual fun westernDigitsLocale(language: String): CalendarLocale = Locale.forLanguageTag("$language-u-nu-latn")
