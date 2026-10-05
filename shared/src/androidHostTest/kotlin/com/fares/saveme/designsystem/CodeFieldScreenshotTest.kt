package com.fares.saveme.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.fares.saveme.designsystem.component.CodeField
import com.fares.saveme.designsystem.theme.AppTheme
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The code field empty, partial, complete and in error; the code stays left-to-right in four groups in Arabic (FR-004, P-14.4). */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w400dp-h300dp")
class CodeFieldScreenshotTest(
    private val name: String,
    private val value: String,
    private val error: String?,
) {
    @get:Rule
    val compose = createComposeRule()

    private fun capture(config: String, rtl: Boolean = false) {
        compose.setContent {
            CompositionLocalProvider(LocalLayoutDirection provides if (rtl) LayoutDirection.Rtl else LayoutDirection.Ltr) {
                AppTheme {
                    Box(Modifier.background(AppTheme.colors.background).padding(16.dp)) {
                        Field(rtl)
                    }
                }
            }
        }
        compose.onRoot().captureRoboImage("src/androidHostTest/screenshots/component_codefield_${name}_$config.png")
    }

    @Composable
    private fun Field(rtl: Boolean) = CodeField(
        value = value,
        onValueChange = {},
        label = if (rtl) "رمز الاسترداد" else "Redeem code",
        supportingText = if (rtl) "16 حرفًا ورقمًا كما في بريدك" else "16 letters and digits, as in your email",
        errorText = error?.let { if (rtl) "هذا الرمز غير صالح" else it },
    )

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
        fun states(): List<Array<Any?>> = listOf(
            arrayOf("empty", "", null),
            arrayOf("partial", "7KQ2MX", null),
            arrayOf("complete", "7KQ2MX4D9TRBH3WE", null),
            arrayOf("error", "7KQ2MX4D9TRBH3WE", "This code isn’t valid."),
        )
    }
}
