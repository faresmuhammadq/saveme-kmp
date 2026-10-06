package com.fares.saveme.feature.notifications

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.fares.saveme.designsystem.theme.AppTheme
import com.fares.saveme.feature.account.ui.account.AccountScreen
import com.fares.saveme.feature.account.ui.account.AccountState
import com.fares.saveme.feature.notifications.domain.NotificationCategory
import com.fares.saveme.feature.notifications.domain.NotificationPreferences
import com.fares.saveme.feature.notifications.domain.QuarterHour
import com.fares.saveme.feature.notifications.domain.QuietHours
import com.fares.saveme.feature.notifications.ui.PermissionStatus
import com.fares.saveme.feature.notifications.ui.invitation.InvitationNoticeScreen
import com.fares.saveme.feature.notifications.ui.invitation.InvitationNoticeState
import com.fares.saveme.feature.notifications.ui.open.NotificationOpenScreen
import com.fares.saveme.feature.notifications.ui.prompt.PromptContent
import com.fares.saveme.feature.notifications.ui.settings.LoadMessage
import com.fares.saveme.feature.notifications.ui.settings.NotificationSettingsScreen
import com.fares.saveme.feature.notifications.ui.settings.NotificationSettingsState
import com.fares.saveme.feature.notifications.ui.settings.SettingsMessage
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Every new screen of spec 017 in English LTR, Arabic RTL and English at font scale 2.0 (P-14.4, FR-040): the Notifications
 * screen in each state of contracts/ui-and-e2e.md §9, "Opening…", the invitation notice, the explanation sheet's content, and
 * the Account screen with its two new rows.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w400dp-h1100dp")
class NotificationsScreenshotTest(
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
        compose.onRoot().captureRoboImage("src/androidHostTest/screenshots/screen_push_${name}_$config.png")
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
        private val defaults = NotificationPreferences(
            version = 1,
            categories = NotificationCategory.entries.associateWith { true },
            quietHours = QuietHours(true, QuarterHour(22 * 60), QuarterHour(8 * 60)),
        )
        private val ready = NotificationSettingsState(loading = false, read = defaults, edited = defaults, timeZoneId = "Africa/Cairo")
        private val edited = defaults.copy(
            categories = defaults.categories + (NotificationCategory.BudgetExceeded to false),
            quietHours = QuietHours(true, QuarterHour(23 * 60), QuarterHour(7 * 60)),
        )
        private val dirty = ready.copy(edited = edited)

        private fun screen(content: @Composable () -> Unit): @Composable () -> Unit = content

        private fun settings(state: NotificationSettingsState): @Composable () -> Unit = { NotificationSettingsScreen(state, onIntent = {}) }

        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun states(): List<Array<Any>> = listOf(
            arrayOf("settings_loading", settings(NotificationSettingsState())),
            arrayOf("settings_not_loaded", settings(NotificationSettingsState(loading = false, notLoaded = LoadMessage.Offline))),
            arrayOf("settings_defaults", settings(ready)),
            arrayOf("settings_edited", settings(dirty)),
            arrayOf("settings_invalid_range", settings(ready.copy(edited = defaults.copy(quietHours = QuietHours(true, QuarterHour(480), QuarterHour(480)))))),
            arrayOf("settings_saving", settings(dirty.copy(saving = true))),
            arrayOf("settings_conflict", settings(ready.copy(message = SettingsMessage.Conflict))),
            arrayOf("settings_rate_limited", settings(dirty.copy(message = SettingsMessage.RateLimited(1500)))),
            arrayOf("settings_phone_off_askable", settings(ready.copy(phone = PermissionStatus.Askable))),
            arrayOf("settings_phone_off_blocked", settings(ready.copy(phone = PermissionStatus.Blocked))),
            arrayOf("settings_discard", settings(dirty.copy(discardConfirmVisible = true))),
            arrayOf("opening", screen { NotificationOpenScreen(onIntent = {}) }),
            arrayOf("invitation_notice", screen { InvitationNoticeScreen(InvitationNoticeState("sara@example.com"), onIntent = {}) }),
            arrayOf(
                "prompt_sheet",
                screen {
                    Column(Modifier.fillMaxWidth().background(AppTheme.colors.surface).padding(16.dp)) { PromptContent(onTurnOn = {}, onNotNow = {}) }
                },
            ),
            arrayOf(
                "account_rows",
                screen { AccountScreen(AccountState(email = "sara@example.com", notificationsAvailable = true, devToolsAvailable = true), onIntent = {}) },
            ),
        )
    }
}
