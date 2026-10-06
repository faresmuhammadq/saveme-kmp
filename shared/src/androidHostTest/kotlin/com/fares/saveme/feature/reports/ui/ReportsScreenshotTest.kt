package com.fares.saveme.feature.reports.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.LayoutDirection
import com.fares.saveme.designsystem.component.LocalPickerToday
import com.fares.saveme.designsystem.theme.AppTheme
import com.fares.saveme.feature.reports.ui.categories.ReportCategoriesScreen
import com.fares.saveme.feature.reports.ui.reports.ReportsScreen
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The report screens' states in English LTR, Arabic RTL (mirrored) and English at font scale 2.0 (P-14.4, SC-006). Dates, periods and
 * "Updated" times are part of each state, so no golden depends on the real date.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w400dp-h3600dp")
class ReportsScreenshotTest(
    private val name: String,
    private val screen: @Composable () -> Unit,
) {
    @get:Rule
    val compose = createComposeRule()

    private fun capture(config: String, rtl: Boolean = false) {
        compose.setContent {
            CompositionLocalProvider(LocalLayoutDirection provides if (rtl) LayoutDirection.Rtl else LayoutDirection.Ltr) {
                AppTheme { screen() }
            }
        }
        compose.onRoot().captureRoboImage("src/androidHostTest/screenshots/screen_reports_${name}_$config.png")
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
        private val none: (Any) -> Unit = {}

        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun states(): List<Array<Any>> = listOf(
            arrayOf("ready", @Composable { ReportsScreen(ReportUiFixtures.ready(trend = 6), none) }),
            arrayOf("no_spending", @Composable { ReportsScreen(ReportUiFixtures.noSpending, none) }),
            arrayOf("no_wallets", @Composable { ReportsScreen(ReportUiFixtures.noWallets, none) }),
            arrayOf("loading", @Composable { ReportsScreen(ReportUiFixtures.loading, none) }),
            arrayOf("offline_waiting", @Composable { ReportsScreen(ReportUiFixtures.offlineWaiting, none) }),
            arrayOf("failed_refresh", @Composable { ReportsScreen(ReportUiFixtures.failedRefresh, none) }),
            arrayOf("wallet_cash", @Composable { ReportsScreen(ReportUiFixtures.walletCash, none) }),
            arrayOf("wallet_credit_card", @Composable { ReportsScreen(ReportUiFixtures.creditCard, none) }),
            arrayOf("trend_negative", @Composable { ReportsScreen(ReportUiFixtures.trendNegative, none) }),
            arrayOf("trend_three", @Composable { ReportsScreen(ReportUiFixtures.ready(trend = 3), none) }),
            arrayOf("excluded_usd", @Composable { ReportsScreen(ReportUiFixtures.crossCurrency, none) }),
            arrayOf("usd_report", @Composable { ReportsScreen(ReportUiFixtures.usdReport, none) }),
            arrayOf("scheduled", @Composable { ReportsScreen(ReportUiFixtures.scheduled, none) }),
            arrayOf("custom_problem", @Composable { CompositionLocalProvider(LocalPickerToday provides com.fares.saveme.feature.reports.ui.ReportUiFixtures.october.from) { ReportsScreen(ReportUiFixtures.customProblem, none) } }),
            arrayOf("unknown_period", @Composable { ReportsScreen(ReportUiFixtures.unknownPeriod, none) }),
            arrayOf("too_large", @Composable { ReportsScreen(ReportUiFixtures.tooLarge, none) }),
            arrayOf("trend_partly_unknown", @Composable { ReportsScreen(ReportUiFixtures.partlyUnknownTrend, none) }),
            arrayOf("categories_expenses", @Composable { ReportCategoriesScreen(ReportUiFixtures.categories(), none) }),
            arrayOf("categories_income", @Composable { ReportCategoriesScreen(ReportUiFixtures.categories(com.fares.saveme.feature.reports.ui.reports.CategoryGroupKind.Income), none) }),
            arrayOf("categories_scheduled", @Composable { ReportCategoriesScreen(ReportUiFixtures.categoriesScheduled, none) }),
            arrayOf("categories_empty", @Composable { ReportCategoriesScreen(ReportUiFixtures.categoriesEmpty, none) }),
        )
    }
}

/** The 12-period trend is a long page; it gets its own taller capture. */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w400dp-h7200dp")
class ReportsTrendTwelveScreenshotTest(
    private val name: String,
    private val screen: @Composable () -> Unit,
) {
    @get:Rule
    val compose = createComposeRule()

    private fun capture(config: String, rtl: Boolean = false) {
        compose.setContent {
            CompositionLocalProvider(LocalLayoutDirection provides if (rtl) LayoutDirection.Rtl else LayoutDirection.Ltr) {
                AppTheme { screen() }
            }
        }
        compose.onRoot().captureRoboImage("src/androidHostTest/screenshots/screen_reports_${name}_$config.png")
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
        fun states(): List<Array<Any>> = listOf(
            arrayOf("trend_twelve", @Composable { ReportsScreen(ReportUiFixtures.ready(trend = 12), {}) }),
        )
    }
}
