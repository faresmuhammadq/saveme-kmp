package com.fares.saveme.feature.billing

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.LayoutDirection
import com.fares.saveme.designsystem.theme.AppTheme
import com.fares.saveme.feature.billing.domain.AttemptKey
import com.fares.saveme.feature.billing.domain.RedeemConfirmation
import com.fares.saveme.feature.billing.ui.redeem.RedeemMessage
import com.fares.saveme.feature.billing.ui.redeem.RedeemPhase
import com.fares.saveme.feature.billing.ui.redeem.RedeemScreen
import com.fares.saveme.feature.billing.ui.redeem.RedeemState
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.time.Instant

/** The redeem screen's states in English LTR, Arabic RTL and English at font scale 2.0 (P-14.4, FR-030). */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w400dp-h900dp")
class RedeemScreenScreenshotTest(
    private val name: String,
    private val state: RedeemState,
) {
    @get:Rule
    val compose = createComposeRule()

    private fun capture(config: String, rtl: Boolean = false) {
        compose.setContent {
            CompositionLocalProvider(LocalLayoutDirection provides if (rtl) LayoutDirection.Rtl else LayoutDirection.Ltr) {
                AppTheme { RedeemScreen(state, onIntent = {}) }
            }
        }
        compose.onRoot().captureRoboImage("src/androidHostTest/screenshots/screen_redeem_${name}_$config.png")
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
        private val complete = RedeemState(code = "7KQ2MX4D9TRBH3WE", timeZoneId = "Africa/Cairo", signedInEmail = "sara@example.com")
        private fun refused(message: RedeemMessage, waitUntil: Instant? = null) = complete.copy(message = message, attempt = AttemptKey("k"), waitUntil = waitUntil)
        private fun done(confirmation: RedeemConfirmation) = complete.copy(code = "", phase = RedeemPhase.Done(confirmation))
        private val yearEnd = Instant.parse("2027-10-05T12:00:00Z")

        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun states(): List<Array<Any>> = listOf(
            arrayOf("empty", RedeemState(timeZoneId = "Africa/Cairo")),
            arrayOf("complete", complete),
            arrayOf("sending", complete.copy(phase = RedeemPhase.Sending, attempt = AttemptKey("k"))),
            arrayOf("unverified", complete.copy(verified = false)),
            arrayOf("done", done(RedeemConfirmation("Plus · Yearly", yearEnd, true, false, "Plus · Yearly", yearEnd, null))),
            arrayOf("done_current_stays", done(RedeemConfirmation("Plus · Lifetime", null, false, false, "Plus · Monthly", Instant.parse("2026-11-05T12:00:00Z"), null))),
            arrayOf("done_play_renews", done(RedeemConfirmation("Plus · Yearly", Instant.parse("2027-01-10T08:00:00Z"), false, true, "Plus · Monthly", Instant.parse("2026-11-05T12:00:00Z"), null))),
            arrayOf("done_starts_later", done(RedeemConfirmation("Plus · Yearly", yearEnd, true, false, "Plus · Yearly", yearEnd, Instant.parse("2026-12-01T00:00:00Z")))),
            arrayOf("not_valid", refused(RedeemMessage.NotValid)),
            arrayOf("used", refused(RedeemMessage.Used)),
            arrayOf("expired", refused(RedeemMessage.Expired)),
            arrayOf("other_address", refused(RedeemMessage.OtherAddress("sara@example.com"))),
            arrayOf("rate_limited", refused(RedeemMessage.RateLimited(12), Instant.parse("2026-10-06T10:12:00Z"))),
            arrayOf("offline", refused(RedeemMessage.Offline)),
            arrayOf("invalid_format", refused(RedeemMessage.InvalidFormat)),
        )
    }
}
