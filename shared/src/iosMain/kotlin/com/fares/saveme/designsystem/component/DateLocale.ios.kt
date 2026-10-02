package com.fares.saveme.designsystem.component

import androidx.compose.material3.CalendarLocale
import platform.Foundation.NSLocale

/** The language with Latin digits (`numbers=latn`), so Arabic dates and numbers print 0–9 (P-06.2). */
internal actual fun westernDigitsLocale(language: String): CalendarLocale = NSLocale("$language@numbers=latn")
