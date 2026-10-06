package com.fares.saveme.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.fares.saveme.designsystem.component.TimeOfDayField
import com.fares.saveme.designsystem.component.TimeOfDayList
import com.fares.saveme.designsystem.theme.AppTheme
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The quarter-hour time field closed with a value, its sheet content scrolled to the value, disabled and in error: English LTR,
 * Arabic RTL and font scale 2.0 (spec 017 R-13, P-14.4). The time reads `HH:MM` left to right in Arabic too.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w400dp-h500dp")
class TimeOfDayFieldScreenshotTest(private val name: String) {

    @get:Rule
    val compose = createComposeRule()

    private fun capture(config: String, rtl: Boolean = false) {
        compose.setContent {
            CompositionLocalProvider(LocalLayoutDirection provides if (rtl) LayoutDirection.Rtl else LayoutDirection.Ltr) {
                AppTheme {
                    Column(Modifier.background(AppTheme.colors.background).padding(16.dp).fillMaxWidth()) { Content(rtl) }
                }
            }
        }
        compose.onRoot().captureRoboImage("src/androidHostTest/screenshots/component_timeofday_${name}_$config.png")
    }

    @Composable
    private fun Content(rtl: Boolean) {
        val label = if (rtl) "من" else "From"
        when (name) {
            "closed" -> TimeOfDayField(label, minutes = 22 * 60, onChange = {})
            "sheet" -> TimeOfDayList(options = (0 until 24 * 60 step 15).toList(), selected = 22 * 60 + 15, onSelect = {}, modifier = Modifier.height(320.dp))
            "disabled" -> TimeOfDayField(label, minutes = 8 * 60 + 45, onChange = {}, enabled = false)
            "error" -> TimeOfDayField(
                label, minutes = 22 * 60, onChange = {},
                errorText = if (rtl) "يجب أن تختلف بداية الساعات عن نهايتها." else "Start and end must be different.",
            )
        }
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

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun states(): List<Array<Any>> = listOf("closed", "sheet", "disabled", "error").map { arrayOf(it) }
    }
}
