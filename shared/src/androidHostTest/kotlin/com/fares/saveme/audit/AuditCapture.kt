package com.fares.saveme.audit

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.onRoot
import com.fares.saveme.designsystem.component.AppTabBar
import com.fares.saveme.designsystem.component.MainTab
import com.fares.saveme.designsystem.theme.AppTheme
import com.github.takahirom.roborazzi.captureRoboImage

/**
 * Design audit: renders [content] at 390×844dp (xhdpi, so 780×1688px like the mock-up PNGs) into build/design-audit/[name].png.
 * Not a regression test; it asserts nothing.
 */
fun ComposeContentTestRule.auditCapture(name: String, content: @Composable () -> Unit) {
    setContent { AppTheme { content() } }
    onRoot().captureRoboImage("build/design-audit/$name.png")
}

/** The app draws the tab bar around the tab screens (AppNavHost); the audit does the same. */
@Composable
fun WithTabBar(selected: MainTab, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f)) { content() }
        AppTabBar(
            selected = selected,
            onSelect = {},
            onAdd = {},
            labels = mapOf(MainTab.Home to "Home", MainTab.Wallets to "Wallets", MainTab.Budgets to "Budgets", MainTab.Reports to "Reports"),
            addDescription = "Add transaction",
        )
    }
}
