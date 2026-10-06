package com.fares.saveme.feature.account

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.LayoutDirection
import com.fares.saveme.core.network.ApiErrorCode
import com.fares.saveme.designsystem.theme.AppTheme
import com.fares.saveme.feature.account.domain.deletion.Candidate
import com.fares.saveme.feature.account.domain.deletion.DeletionPlan
import com.fares.saveme.feature.account.domain.deletion.OwnedSharedWallet
import com.fares.saveme.feature.account.domain.deletion.PlanState
import com.fares.saveme.feature.account.domain.deletion.SuccessorReason
import com.fares.saveme.feature.account.domain.model.ScheduledOwner
import com.fares.saveme.feature.account.ui.deleteaccount.DeleteAccountScreen
import com.fares.saveme.feature.account.ui.deleteaccount.DeleteAccountState
import com.fares.saveme.feature.account.ui.deleteaccount.DeleteBanner
import com.fares.saveme.feature.account.ui.deleteaccount.DeleteStep
import com.fares.saveme.feature.auth.ui.home.HomeScreen
import com.fares.saveme.feature.auth.ui.home.HomeState
import com.fares.saveme.feature.wallets.domain.model.WalletRole
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.time.Instant

/** The Delete account flow's new steps in English LTR, Arabic RTL and English at font scale 2.0 (P-14.4, FR-038). */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w400dp-h900dp")
class DeleteAccountScreenshotTest(
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
        compose.onRoot().captureRoboImage("src/androidHostTest/screenshots/screen_delete_${name}_$config.png")
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
        private val date = Instant.parse("2026-11-05T10:00:00Z")
        private val now = Instant.parse("2026-10-06T10:00:00Z")

        private val fixed = OwnedSharedWallet(
            "w1", "Home", "u2", SuccessorReason.OnlyMember, choiceExpected = false, choiceOffered = false,
            candidates = listOf(Candidate("u2", "Omar", WalletRole.Viewer, Instant.parse("2026-09-01T00:00:00Z"))),
        )
        private val choice = OwnedSharedWallet(
            "w3", "Trip", "u4", SuccessorReason.OldestEditor, choiceExpected = true, choiceOffered = true,
            candidates = listOf(
                Candidate("u4", "Mona", WalletRole.Editor, Instant.parse("2026-09-10T00:00:00Z")),
                Candidate("u5", null, WalletRole.Viewer, Instant.parse("2026-09-12T00:00:00Z")),
            ),
        )
        private val unknownWallet = OwnedSharedWallet(
            "w9", null, "ghost", SuccessorReason.Unknown("x"), choiceExpected = true, choiceOffered = false,
            candidates = listOf(Candidate("ghost", null, null, null, unknown = true)),
        )
        private fun plan(vararg wallets: OwnedSharedWallet, pending: Boolean = false) =
            DeletionPlan(if (pending) PlanState.PendingDeletion else PlanState.Active, null, date.takeIf { pending }, wallets.toList())

        private val withOwners = plan(fixed, choice, unknownWallet)

        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun screens(): List<Array<Any>> = listOf(
            arrayOf("explain_offline", @Composable { DeleteAccountScreen(DeleteAccountState(planBanner = DeleteBanner.Offline)) {} }),
            arrayOf("loading", @Composable { DeleteAccountScreen(DeleteAccountState(step = DeleteStep.LoadingPlan)) {} }),
            arrayOf("owners", @Composable {
                DeleteAccountScreen(DeleteAccountState(step = DeleteStep.NewOwners, plan = withOwners, picks = mapOf("w3" to "u5"))) {}
            }),
            arrayOf("owners_changed", @Composable {
                DeleteAccountScreen(DeleteAccountState(step = DeleteStep.NewOwners, plan = plan(choice), successorsChanged = true)) {}
            }),
            arrayOf("review", @Composable { DeleteAccountScreen(DeleteAccountState(step = DeleteStep.Review, plan = withOwners)) {} }),
            arrayOf("review_none", @Composable { DeleteAccountScreen(DeleteAccountState(step = DeleteStep.Review, plan = plan())) {} }),
            arrayOf("scheduled_owners", @Composable {
                DeleteAccountScreen(
                    DeleteAccountState(
                        step = DeleteStep.Scheduled(date, listOf(ScheduledOwner("w1", "u2", SuccessorReason.OnlyMember), ScheduledOwner("w3", "u5", SuccessorReason.Chosen))),
                        plan = withOwners,
                    ),
                ) {}
            }),
            arrayOf("pending", @Composable {
                DeleteAccountScreen(DeleteAccountState(step = DeleteStep.Pending, plan = plan(fixed, choice.copy(successorUserId = "u5", reason = SuccessorReason.Chosen), pending = true), now = now)) {}
            }),
            arrayOf("pending_saved", @Composable {
                DeleteAccountScreen(DeleteAccountState(step = DeleteStep.Pending, plan = plan(choice.copy(successorUserId = "u5", reason = SuccessorReason.Chosen), pending = true), ownersSaved = true, now = now)) {}
            }),
            arrayOf("pending_plan_unavailable", @Composable {
                DeleteAccountScreen(DeleteAccountState(step = DeleteStep.Pending, pendingOn = date, planBanner = DeleteBanner.Error(ApiErrorCode.INTERNAL_ERROR, "req-42"), now = now)) {}
            }),
            arrayOf("pending_being_deleted", @Composable {
                DeleteAccountScreen(DeleteAccountState(step = DeleteStep.Pending, plan = plan(fixed, pending = true), now = Instant.parse("2026-11-06T10:00:00Z"))) {}
            }),
            arrayOf("cancelled_dropped", @Composable { DeleteAccountScreen(DeleteAccountState(step = DeleteStep.Cancelled(choicesDropped = true))) {} }),
            arrayOf("home_being_deleted", @Composable {
                HomeScreen(HomeState(email = "sara@example.com", deletionPendingOn = date, now = Instant.parse("2026-11-06T10:00:00Z")), {})
            }),
        )
    }
}
