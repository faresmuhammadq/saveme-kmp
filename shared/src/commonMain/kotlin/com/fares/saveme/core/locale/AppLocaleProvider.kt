package com.fares.saveme.core.locale

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalLayoutDirection

/** Changing it invalidates the whole subtree, so every string re-resolves; state is kept. */
private val LocalAppLanguage = staticCompositionLocalOf { AppLanguage.En }

/** Applies [override] (or the device language when null) to resources and layout direction. */
@Composable
fun AppLocaleProvider(override: AppLanguage?, effective: AppLanguage, content: @Composable () -> Unit) {
    CompositionLocalProvider(
        LocalAppLocale provides override?.tag,
        LocalAppLanguage provides effective,
        LocalLayoutDirection provides effective.layoutDirection,
        content = content,
    )
}
