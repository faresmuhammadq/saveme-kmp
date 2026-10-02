package com.fares.saveme.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.fares.saveme.designsystem.catalog.BarsSection
import com.fares.saveme.designsystem.catalog.BudgetsSection
import com.fares.saveme.designsystem.catalog.ButtonsSection
import com.fares.saveme.designsystem.catalog.CardsSection
import com.fares.saveme.designsystem.catalog.CategoriesSection
import com.fares.saveme.designsystem.catalog.DialogsSection
import com.fares.saveme.designsystem.catalog.RowsSection
import com.fares.saveme.designsystem.catalog.EmptySection
import com.fares.saveme.designsystem.catalog.FeedbackSection
import com.fares.saveme.designsystem.catalog.FieldsSection
import com.fares.saveme.designsystem.catalog.ListsSection
import com.fares.saveme.designsystem.catalog.MembersSection
import com.fares.saveme.designsystem.catalog.MoneySection
import com.fares.saveme.designsystem.catalog.PlansSection
import com.fares.saveme.designsystem.catalog.SelectionSection
import com.fares.saveme.designsystem.catalog.SheetSection
import com.fares.saveme.designsystem.catalog.WalletsSection
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
 * One image per catalogue section in English LTR, Arabic RTL and English at font scale 2.0
 * (P-14.4). Record with `recordRoborazziAndroidHostTest`, verify with `verifyRoborazziAndroidHostTest`.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w400dp-h2000dp")
class ComponentScreenshotTest(
    private val name: String,
    private val section: @Composable () -> Unit,
) {

    @get:Rule
    val compose = createComposeRule()

    private fun capture(config: String, rtl: Boolean = false) {
        compose.setContent {
            // The library's test manifest has no supportsRtl, so direction is set explicitly.
            CompositionLocalProvider(LocalLayoutDirection provides if (rtl) LayoutDirection.Rtl else LayoutDirection.Ltr) {
                AppTheme {
                    Box(Modifier.background(AppTheme.colors.background).padding(16.dp)) { section() }
                }
            }
        }
        compose.onRoot().captureRoboImage("src/androidHostTest/screenshots/${name}_$config.png")
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
        fun sections(): List<Array<Any>> = listOf(
            arrayOf("buttons", @Composable { ButtonsSection() }),
            arrayOf("bars", @Composable { BarsSection() }),
            arrayOf("selection", @Composable { SelectionSection() }),
            arrayOf("fields", @Composable { FieldsSection() }),
            arrayOf("money", @Composable { MoneySection() }),
            arrayOf("cards", @Composable { CardsSection() }),
            arrayOf("plans", @Composable { PlansSection() }),
            arrayOf("wallets", @Composable { WalletsSection() }),
            arrayOf("categories", @Composable { CategoriesSection() }),
            arrayOf("lists", @Composable { ListsSection() }),
            arrayOf("budgets", @Composable { BudgetsSection() }),
            arrayOf("members", @Composable { MembersSection() }),
            arrayOf("feedback", @Composable { FeedbackSection() }),
            arrayOf("sheet", @Composable { SheetSection() }),
            arrayOf("empty", @Composable { EmptySection() }),
            arrayOf("rows", @Composable { RowsSection() }),
            arrayOf("dialogs", @Composable { DialogsSection() }),
        )
    }
}
