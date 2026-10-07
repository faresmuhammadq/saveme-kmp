package com.fares.saveme.feature.billing

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.LayoutDirection
import com.fares.saveme.designsystem.theme.AppTheme
import com.fares.saveme.feature.auth.domain.model.FeatureValue
import com.fares.saveme.feature.billing.domain.CurrentPlan
import com.fares.saveme.feature.billing.domain.EntitlementSet
import com.fares.saveme.feature.billing.domain.model.BillingPeriod
import com.fares.saveme.feature.billing.domain.model.CataloguePlan
import com.fares.saveme.feature.billing.domain.model.Money
import com.fares.saveme.feature.billing.domain.model.Subscription
import com.fares.saveme.feature.billing.domain.model.SubscriptionStatus
import com.fares.saveme.feature.billing.domain.play.PlayNotes
import com.fares.saveme.feature.billing.domain.play.RestoreMessagePart
import com.fares.saveme.feature.billing.ui.plan.CatalogueStatus
import com.fares.saveme.feature.billing.ui.plan.PlanScreen
import com.fares.saveme.feature.billing.ui.plan.PlanState
import com.fares.saveme.feature.billing.ui.plan.RestoreRefusal
import com.fares.saveme.feature.billing.ui.plan.RestoreUi
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.time.Instant

/** The Plan screen's states in English LTR, Arabic RTL and English at font scale 2.0 (P-14.4, spec 005 FR-019, spec 018 FR-053). */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w400dp-h1400dp")
class PlanScreenScreenshotTest(
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
        compose.onRoot().captureRoboImage("src/androidHostTest/screenshots/screen_plan_${name}_$config.png")
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
        private val free = EntitlementSet(
            mapOf(
                "sharing.enabled" to FeatureValue.Flag(false),
                "sharing.max_members_per_wallet" to FeatureValue.Quota(1),
                "wallets.max" to FeatureValue.Quota(null),
            ),
        )
        private val plus = EntitlementSet(
            mapOf(
                "sharing.enabled" to FeatureValue.Flag(true),
                "sharing.max_members_per_wallet" to FeatureValue.Quota(5),
                "export.enabled" to FeatureValue.Flag(true),
                "reports.advanced" to FeatureValue.Flag(true),
                "wallets.max" to FeatureValue.Quota(null),
            ),
        )
        private val end = Instant.parse("2026-11-01T00:00:00Z")
        private val freePlan = CurrentPlan("free", "Free", free)
        private val plusPlan = CurrentPlan("plus", "Plus · Monthly", plus)

        private fun catalogue(arabic: Boolean = false) = listOf(
            CataloguePlan("free", if (arabic) "المجانية" else "Free", BillingPeriod.Lifetime, true, free, null),
            CataloguePlan("plus", if (arabic) "بلس · شهري" else "Plus · Monthly", BillingPeriod.Monthly, false, plus, "plus_monthly"),
            CataloguePlan("plus-y", if (arabic) "بلس · سنوي" else "Plus · Yearly", BillingPeriod.Yearly, false, plus, "plus_yearly"),
        )

        private val googlePlayPrices = mapOf("plus_monthly" to Money(14_900, "EGP"), "plus_yearly" to Money(149_900, "EGP"))

        private fun state(
            plan: CurrentPlan,
            subscription: Subscription?,
            plans: List<CataloguePlan>? = catalogue(),
            stale: Boolean = false,
            status: CatalogueStatus = CatalogueStatus.Loaded,
            prices: Map<String, Money> = emptyMap(),
            restore: RestoreUi = RestoreUi.Idle,
            notes: PlayNotes = PlayNotes(),
        ) = PlanState(
            plan, subscription, settled = true, plans = plans, catalogueStatus = status, stale = stale, timeZoneId = "Africa/Cairo",
            playSupported = true, restore = restore, playPrices = prices, notes = notes,
        )

        private fun playSubscription(status: SubscriptionStatus, grace: Instant? = null) =
            Subscription("plus", "Plus · Monthly", status, end, grace, true, provider = "google_play")

        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun screens(): List<Array<Any>> = listOf(
            arrayOf("free", @Composable {
                PlanScreen(state(freePlan, Subscription("free", "Free", null, null, null, null)), onIntent = {})
            }),
            arrayOf("active_with_prices", @Composable {
                PlanScreen(state(plusPlan, Subscription("plus", "Plus · Monthly", SubscriptionStatus.Active, end, null, true), prices = googlePlayPrices), onIntent = {})
            }),
            arrayOf("grace", @Composable {
                PlanScreen(state(plusPlan, Subscription("plus", "Plus · Monthly", SubscriptionStatus.Grace, end, Instant.parse("2026-11-05T00:00:00Z"), true)), onIntent = {})
            }),
            arrayOf("stale_unavailable", @Composable {
                PlanScreen(state(freePlan, null, plans = null, stale = true, status = CatalogueStatus.Unavailable), onIntent = {})
            }),
            arrayOf("arabic_names", @Composable {
                PlanScreen(state(freePlan, Subscription("free", "Free", null, null, null, null), catalogue(arabic = true), prices = googlePlayPrices), onIntent = {})
            }),
            arrayOf("restore_running", @Composable {
                PlanScreen(state(freePlan, null, restore = RestoreUi.Running), onIntent = {})
            }),
            arrayOf("restore_result", @Composable {
                PlanScreen(
                    state(plusPlan, playSubscription(SubscriptionStatus.Active), restore = RestoreUi.Result(listOf(RestoreMessagePart.Restored, RestoreMessagePart.AnotherAccount, RestoreMessagePart.Pending))),
                    onIntent = {},
                )
            }),
            arrayOf("restore_refused", @Composable {
                PlanScreen(state(freePlan, null, restore = RestoreUi.Refused(RestoreRefusal.RateLimited, retryAfterSeconds = 600)), onIntent = {})
            }),
            arrayOf("restore_unavailable", @Composable {
                PlanScreen(state(freePlan, null, restore = RestoreUi.Refused(RestoreRefusal.GooglePlayUnavailable)), onIntent = {})
            }),
            arrayOf("play_grace", @Composable {
                PlanScreen(state(plusPlan, playSubscription(SubscriptionStatus.Grace, Instant.parse("2026-11-05T00:00:00Z"))), onIntent = {})
            }),
            arrayOf("play_hold", @Composable {
                PlanScreen(state(freePlan, null, notes = PlayNotes(heldProducts = setOf("plus_monthly"))), onIntent = {})
            }),
            arrayOf("play_paused", @Composable {
                PlanScreen(state(freePlan, null, notes = PlayNotes(pausedProducts = setOf("plus_monthly"))), onIntent = {})
            }),
            arrayOf("play_pending", @Composable {
                PlanScreen(state(freePlan, null, notes = PlayNotes(pendingProducts = setOf("plus_monthly"))), onIntent = {})
            }),
            arrayOf("play_another_account", @Composable {
                PlanScreen(state(freePlan, null, notes = PlayNotes(anotherAccount = true)), onIntent = {})
            }),
            arrayOf("play_manage_test", @Composable {
                PlanScreen(state(plusPlan, playSubscription(SubscriptionStatus.Active), prices = googlePlayPrices, notes = PlayNotes(testProducts = setOf("plus_monthly"))), onIntent = {})
            }),
        )
    }
}
