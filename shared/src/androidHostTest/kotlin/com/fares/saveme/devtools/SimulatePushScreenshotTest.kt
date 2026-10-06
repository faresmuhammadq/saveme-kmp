package com.fares.saveme.devtools

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.LayoutDirection
import com.fares.saveme.designsystem.theme.AppTheme
import com.fares.saveme.devtools.push.PhoneIds
import com.fares.saveme.devtools.push.SimulatePayloads
import com.fares.saveme.devtools.push.SimulatePushScreen
import com.fares.saveme.devtools.push.SimulatePushState
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The development-only "Simulate a notification" tool (spec 017 FR-036): English LTR, Arabic RTL, font scale 2.0. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w400dp-h1100dp")
class SimulatePushScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    private fun capture(config: String, rtl: Boolean = false) {
        compose.setContent {
            CompositionLocalProvider(LocalLayoutDirection provides if (rtl) LayoutDirection.Rtl else LayoutDirection.Ltr) {
                AppTheme {
                    SimulatePushScreen(
                        SimulatePushState(rows = SimulatePayloads.rows(PhoneIds(walletId = "w", occurrenceId = null, budgetId = "b"))),
                        onIntent = {},
                    )
                }
            }
        }
        compose.onRoot().captureRoboImage("src/androidHostTest/screenshots/screen_push_simulate_$config.png")
    }

    @Test
    fun englishLtr() = capture("en")

    @Test
    @Config(qualifiers = "+ar-rEG")
    fun arabicRtl() = capture("ar", rtl = true)

    @Test
    fun englishFontScale2() {
        RuntimeEnvironment.setFontScale(2f)
        capture("en_fs2")
    }
}
