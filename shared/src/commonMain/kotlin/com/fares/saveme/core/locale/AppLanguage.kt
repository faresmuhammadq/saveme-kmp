package com.fares.saveme.core.locale

import androidx.compose.ui.unit.LayoutDirection

/** The two languages the app ships (P-06). */
enum class AppLanguage(val tag: String, val layoutDirection: LayoutDirection) {
    Ar("ar", LayoutDirection.Rtl),
    En("en", LayoutDirection.Ltr);

    companion object {
        fun fromTag(tag: String?): AppLanguage? = entries.firstOrNull { it.tag == tag }
    }
}
