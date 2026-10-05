package com.fares.saveme.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.fares.saveme.designsystem.component.ExcludedWalletsNotice
import com.fares.saveme.designsystem.component.NoticeAction
import com.fares.saveme.designsystem.component.ShareBarRow
import com.fares.saveme.designsystem.component.TrendBarChart
import com.fares.saveme.designsystem.component.TrendChartLabels
import com.fares.saveme.designsystem.component.TrendPointUi
import com.fares.saveme.designsystem.component.TrendRowUi
import com.fares.saveme.designsystem.component.TrendTable
import com.fares.saveme.designsystem.component.TrendTableLabels
import com.fares.saveme.designsystem.component.formatMoney
import com.fares.saveme.designsystem.theme.AppTheme
import com.github.takahirom.roborazzi.captureRoboImage
import org.jetbrains.compose.resources.stringResource
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import saveme.shared.generated.resources.Res
import saveme.shared.generated.resources.reports_expenses
import saveme.shared.generated.resources.reports_income
import saveme.shared.generated.resources.reports_net
import saveme.shared.generated.resources.reports_scheduled_legend
import saveme.shared.generated.resources.reports_trend_summary

/**
 * The report charts in the states a person meets them (spec 014 T024, P-14.4): no data, one period, twelve periods, a negative net,
 * scheduled money and long labels, in English LTR, Arabic RTL (mirrored) and English at font scale 2.0.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w400dp-h1400dp")
class ReportChartsScreenshotTest(
    private val name: String,
    private val content: @Composable () -> Unit,
) {
    @get:Rule
    val compose = createComposeRule()

    private fun capture(config: String, rtl: Boolean = false) {
        compose.setContent {
            CompositionLocalProvider(LocalLayoutDirection provides if (rtl) LayoutDirection.Rtl else LayoutDirection.Ltr) {
                AppTheme { Box(Modifier.background(AppTheme.colors.background).padding(16.dp)) { content() } }
            }
        }
        compose.onRoot().captureRoboImage("src/androidHostTest/screenshots/reports_component_${name}_$config.png")
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
        private fun months(vararg values: Triple<Long, Long, Pair<Long?, Long?>>) =
            values.mapIndexed { i, (income, expenses, planned) ->
                TrendPointUi("${1 + i} Mar", income, expenses, planned.first, planned.second)
            }

        private val twelve = (0 until 12).map { i ->
            TrendPointUi("${1 + i} ${listOf("Nov", "Dec", "Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct")[i]}", 900_000L + i * 40_000L, 700_000L + (i % 4) * 150_000L)
        }

        @Composable
        private fun Chart(points: List<TrendPointUi>, rows: List<TrendRowUi>, currency: String = "EGP") {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                TrendBarChart(
                    points, currency,
                    TrendChartLabels(
                        stringResource(Res.string.reports_income), stringResource(Res.string.reports_expenses), stringResource(Res.string.reports_scheduled_legend),
                        stringResource(Res.string.reports_trend_summary, points.size),
                    ),
                )
                TrendTable(
                    rows, currency,
                    TrendTableLabels(
                        stringResource(Res.string.reports_income), stringResource(Res.string.reports_expenses), stringResource(Res.string.reports_net),
                        stringResource(Res.string.reports_scheduled_legend),
                    ),
                )
            }
        }

        private fun rowsOf(points: List<TrendPointUi>) =
            points.map { TrendRowUi("${it.periodLabel} – end", it.incomeMinor, it.expensesMinor, it.incomeMinor - it.expensesMinor, it.plannedIncomeMinor, it.plannedExpensesMinor) }

        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun states(): List<Array<Any>> {
            val zero = listOf(TrendPointUi("1 Aug", 0, 0), TrendPointUi("1 Sep", 0, 0), TrendPointUi("1 Oct", 0, 0))
            val single = listOf(TrendPointUi("1 Oct", 1_000_000, 350_000))
            val negative = listOf(TrendPointUi("1 Sep", 1_000_000, 1_200_000), TrendPointUi("1 Oct", 400_000, 650_000), TrendPointUi("1 Nov", 0, 90_000))
            val scheduled = listOf(
                TrendPointUi("1 Aug", 1_000_000, 900_000), TrendPointUi("1 Sep", 1_000_000, 300_000, plannedIncomeMinor = 0, plannedExpensesMinor = 500_000),
                TrendPointUi("1 Oct", 1_000_000, 0, plannedIncomeMinor = 120_000, plannedExpensesMinor = 500_000),
            )
            val large = listOf(TrendPointUi("25 Sep", 123_456_789_012, 98_765_432_109), TrendPointUi("25 Oct", 5_000_000_000, 4_000_000_000))
            return listOf(
                arrayOf("zero", @Composable { Chart(zero, rowsOf(zero)) }),
                arrayOf("single", @Composable { Chart(single, rowsOf(single)) }),
                arrayOf("twelve", @Composable { Chart(twelve, rowsOf(twelve)) }),
                arrayOf("negative_net", @Composable { Chart(negative, rowsOf(negative)) }),
                arrayOf("scheduled", @Composable { Chart(scheduled, rowsOf(scheduled)) }),
                arrayOf("large_amounts", @Composable { Chart(large, rowsOf(large), "KWD") }),
                arrayOf(
                    "share_rows",
                    @Composable {
                        Column {
                            ShareBarRow("A very long category name that must wrap onto two lines without clipping anything at all", "cart", "#2E7D32", formatMoney(350_000, "EGP"), "64%", 0.64f)
                            ShareBarRow("Tiny", "bus", "#1F7F8C", formatMoney(1, "EGP"), "<1%", 0.001f, scheduledText = "Scheduled: ${formatMoney(500_000, "EGP")}")
                            ShareBarRow("Zero", null, null, formatMoney(0, "EGP"), "0%", 0f)
                        }
                    },
                ),
                arrayOf(
                    "excluded_notice",
                    @Composable {
                        ExcludedWalletsNotice(
                            "Not included: Savings (USD), Holiday fund with a long name (KWD)",
                            listOf(NoticeAction("See USD report", "x.usd"), NoticeAction("See KWD report", "x.kwd")),
                            onAction = {},
                        )
                    },
                ),
            )
        }
    }
}
