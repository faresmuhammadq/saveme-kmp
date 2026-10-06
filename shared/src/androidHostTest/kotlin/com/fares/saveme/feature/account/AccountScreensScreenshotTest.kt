package com.fares.saveme.feature.account

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.LayoutDirection
import com.fares.saveme.core.network.ApiErrorCode
import com.fares.saveme.core.sync.SyncStatus
import com.fares.saveme.designsystem.theme.AppTheme
import com.fares.saveme.feature.account.domain.model.DeviceSession
import com.fares.saveme.feature.account.domain.model.Profile
import com.fares.saveme.feature.account.domain.model.ProfileField
import com.fares.saveme.feature.account.domain.model.ProfileView
import com.fares.saveme.feature.account.ui.AccountBanner
import com.fares.saveme.feature.account.ui.account.AccountScreen
import com.fares.saveme.feature.account.ui.account.AccountState
import com.fares.saveme.feature.account.ui.changepassword.ChangePasswordScreen
import com.fares.saveme.feature.account.ui.changepassword.ChangePasswordState
import com.fares.saveme.feature.account.ui.changepassword.CurrentPasswordProblem
import com.fares.saveme.feature.account.ui.changepassword.WaitReason
import com.fares.saveme.feature.account.ui.deleteaccount.DeleteAccountScreen
import com.fares.saveme.feature.account.ui.deleteaccount.DeleteAccountState
import com.fares.saveme.feature.account.ui.deleteaccount.DeletePasswordProblem
import com.fares.saveme.feature.account.ui.deleteaccount.DeleteStep
import com.fares.saveme.feature.account.ui.profile.ProfileScreen
import com.fares.saveme.feature.account.ui.profile.ProfileState
import com.fares.saveme.feature.account.ui.sessions.DeviceSessionsScreen
import com.fares.saveme.feature.account.ui.sessions.DeviceSessionsState
import com.fares.saveme.feature.auth.ui.home.HomeScreen
import com.fares.saveme.feature.auth.ui.home.HomeState
import com.github.takahirom.roborazzi.captureRoboImage
import kotlinx.datetime.LocalDate
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.time.Instant

/** Each account screen state in English LTR, Arabic RTL and English at font scale 2.0 (P-14.4, FR-025). */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w400dp-h900dp")
class AccountScreensScreenshotTest(
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
        private val at = Instant.parse("2026-09-28T09:00:00Z")
        private val sessions = listOf(
            DeviceSession("a", "Pixel 8", "android", "1.0.0", at, at, isCurrent = true),
            DeviceSession("b", "Galaxy S24", "android", "1.0.0", Instant.parse("2026-09-20T08:30:00Z"), null, isCurrent = false),
        )
        private val server = Profile("Sara", "en", "Africa/Cairo", "EGP", false, 1, LocalDate(2026, 10, 1), 3)
        private fun profile(view: ProfileView) = ProfileState("sara@example.com", true, view, view.effective)

        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun screens(): List<Array<Any>> = listOf(
            arrayOf("home_pending_deletion", @Composable {
                HomeScreen(HomeState(email = "sara@example.com", deletionPendingOn = Instant.parse("2026-10-28T10:00:00Z")), {})
            }),
            arrayOf("account", @Composable { AccountScreen(AccountState("sara@example.com", emailVerified = false)) {} }),
            arrayOf("account_sync_waiting", @Composable {
                AccountScreen(AccountState("sara@example.com", sync = SyncStatus(waiting = 2, needsDecision = 1, lastSyncedAt = at))) {}
            }),
            arrayOf("account_synced", @Composable {
                AccountScreen(AccountState("sara@example.com", sync = SyncStatus(lastSyncedAt = at))) {}
            }),
            arrayOf("sign_out_pending", @Composable {
                AccountScreen(AccountState("sara@example.com", confirmSignOutVisible = true, sync = SyncStatus(waiting = 2))) {}
            }),
            arrayOf("sessions", @Composable {
                DeviceSessionsScreen(DeviceSessionsState(sessions, at, "Africa/Cairo"), {})
            }),
            arrayOf("sessions_offline_alone", @Composable {
                DeviceSessionsScreen(DeviceSessionsState(sessions.take(1), at, "Africa/Cairo", banner = AccountBanner.Offline), {})
            }),
            arrayOf("change_password_errors", @Composable {
                ChangePasswordScreen(ChangePasswordState(current = "secret", currentError = CurrentPasswordProblem.Incorrect)) {}
            }),
            arrayOf("change_password_locked", @Composable {
                ChangePasswordScreen(ChangePasswordState(waitMinutes = 15, waitReason = WaitReason.Locked)) {}
            }),
            arrayOf("change_password_done", @Composable { ChangePasswordScreen(ChangePasswordState(done = true)) {} }),
            arrayOf("profile", @Composable {
                ProfileScreen(profile(ProfileView(server, null, null, emptyMap(), at)), {})
            }),
            arrayOf("profile_pending_locked", @Composable {
                ProfileScreen(profile(ProfileView(server.copy(currencyLocked = true), server.toDraft().copy(displayName = "Sara M"), null, emptyMap(), at)), {})
            }),
            arrayOf("profile_conflict", @Composable {
                val view = ProfileView(server, server.toDraft().copy(displayName = "Sara M"), server.copy(displayName = "Sara (web)", locale = "ar", version = 6), emptyMap(), at)
                ProfileScreen(profile(view), {})
            }),
            arrayOf("profile_rejected", @Composable {
                ProfileScreen(profile(ProfileView(server, server.toDraft().copy(defaultCurrency = "USD"), null, mapOf(ProfileField.DefaultCurrency to "IMMUTABLE_FIELD"), at)), {})
            }),
            arrayOf("delete_explain", @Composable { DeleteAccountScreen(DeleteAccountState()) {} }),
            arrayOf("delete_confirm_error", @Composable {
                DeleteAccountScreen(DeleteAccountState(step = DeleteStep.Confirm, password = "x", passwordError = DeletePasswordProblem.Incorrect)) {}
            }),
            arrayOf("delete_scheduled", @Composable {
                DeleteAccountScreen(DeleteAccountState(step = DeleteStep.Scheduled(Instant.parse("2026-10-28T10:00:00Z"), emptyList()))) {}
            }),
            arrayOf("delete_service_error", @Composable {
                DeleteAccountScreen(DeleteAccountState(step = DeleteStep.Confirm, banner = com.fares.saveme.feature.account.ui.deleteaccount.DeleteBanner.Error(ApiErrorCode.INTERNAL_ERROR, "req-42"))) {}
            }),
        )
    }
}
