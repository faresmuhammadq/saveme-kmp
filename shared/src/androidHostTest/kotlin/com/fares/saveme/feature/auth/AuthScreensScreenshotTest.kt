package com.fares.saveme.feature.auth

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.LayoutDirection
import com.fares.saveme.designsystem.theme.AppTheme
import com.fares.saveme.devtools.pastelink.PasteLinkScreen
import com.fares.saveme.devtools.pastelink.PasteLinkState
import com.fares.saveme.feature.auth.domain.FieldError
import com.fares.saveme.feature.auth.ui.FieldProblem
import com.fares.saveme.feature.auth.ui.FormBanner
import com.fares.saveme.feature.auth.ui.checkinbox.CheckInboxKind
import com.fares.saveme.feature.auth.ui.checkinbox.CheckInboxScreen
import com.fares.saveme.feature.auth.ui.checkinbox.CheckInboxState
import com.fares.saveme.feature.auth.ui.home.HomeScreen
import com.fares.saveme.feature.auth.ui.home.HomeState
import com.fares.saveme.feature.auth.ui.password.forgot.ForgotPasswordScreen
import com.fares.saveme.feature.auth.ui.password.forgot.ForgotPasswordState
import com.fares.saveme.feature.auth.ui.password.reset.ResetPasswordScreen
import com.fares.saveme.feature.auth.ui.password.reset.ResetPasswordState
import com.fares.saveme.feature.auth.ui.password.reset.ResetStatus
import com.fares.saveme.feature.auth.ui.register.RegisterScreen
import com.fares.saveme.feature.auth.ui.register.RegisterState
import com.fares.saveme.feature.auth.ui.verify.ResendMessage
import com.fares.saveme.feature.auth.ui.verify.ResendState
import com.fares.saveme.feature.auth.ui.verify.VerifyEmailScreen
import com.fares.saveme.feature.auth.ui.verify.VerifyEmailState
import com.fares.saveme.feature.auth.ui.verify.VerifyStatus
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Each spec 003 screen state in English LTR, Arabic RTL and English at font scale 2.0 (P-14.4, FR-021). */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w400dp-h900dp")
class AuthScreensScreenshotTest(
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
        compose.onRoot().captureRoboImage("src/androidHostTest/screenshots/screen_${name}_$config.png")
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
        fun screens(): List<Array<Any>> = listOf(
            arrayOf("register", @Composable { RegisterScreen(RegisterState()) {} }),
            arrayOf("register_errors", @Composable {
                RegisterScreen(
                    RegisterState(
                        email = "sara",
                        emailError = FieldProblem.Client(FieldError.EmailInvalid),
                        passwordError = FieldProblem.Client(FieldError.PasswordTooShort),
                        banner = FormBanner.RateLimited(12),
                    ),
                ) {}
            }),
            arrayOf("register_already_registered", @Composable {
                RegisterScreen(RegisterState(email = "sara@example.com", alreadyRegistered = true)) {}
            }),
            arrayOf("check_inbox_verification", @Composable {
                CheckInboxScreen(CheckInboxState("sara@example.com", CheckInboxKind.Verification)) {}
            }),
            arrayOf("check_inbox_reset", @Composable { CheckInboxScreen(CheckInboxState("sara@example.com", CheckInboxKind.Reset)) {} }),
            arrayOf("verify_verifying", @Composable { VerifyEmailScreen(VerifyEmailState()) {} }),
            arrayOf("verify_done", @Composable { VerifyEmailScreen(VerifyEmailState(status = VerifyStatus.Verified)) {} }),
            arrayOf("verify_invalid_signed_in", @Composable {
                VerifyEmailScreen(
                    VerifyEmailState(status = VerifyStatus.LinkInvalid, signedIn = true, resend = ResendState(cooldownSeconds = 30, message = ResendMessage.Sent)),
                ) {}
            }),
            arrayOf("verify_invalid_signed_out", @Composable {
                VerifyEmailScreen(VerifyEmailState(status = VerifyStatus.LinkInvalid, signedIn = false)) {}
            }),
            arrayOf("home_verify_prompt", @Composable {
                HomeScreen(HomeState(email = "sara@example.com", emailVerified = false), {})
            }),
            arrayOf("home_due", @Composable {
                HomeScreen(
                    HomeState(
                        email = "sara@example.com", dueCount = 2,
                        firstDue = com.fares.saveme.feature.auth.domain.DueGlimpse("Rent", kotlinx.datetime.LocalDate(2026, 10, 1)),
                    ),
                    {},
                )
            }),
            arrayOf("forgot", @Composable { ForgotPasswordScreen(ForgotPasswordState(email = "sara@example.com")) {} }),
            arrayOf("reset", @Composable { ResetPasswordScreen(ResetPasswordState()) {} }),
            arrayOf("reset_done", @Composable { ResetPasswordScreen(ResetPasswordState(status = ResetStatus.Done)) {} }),
            arrayOf("reset_link_invalid", @Composable { ResetPasswordScreen(ResetPasswordState(status = ResetStatus.LinkInvalid)) {} }),
            arrayOf("paste_link_error", @Composable { PasteLinkScreen(PasteLinkState(parseError = true)) {} }),
        )
    }
}
