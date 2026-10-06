package com.fares.saveme.feature.account

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.LayoutDirection
import com.fares.saveme.core.network.ApiErrorCode
import com.fares.saveme.designsystem.theme.AppTheme
import com.fares.saveme.feature.account.domain.export.Export
import com.fares.saveme.feature.account.domain.export.ExportFailure
import com.fares.saveme.feature.account.domain.export.ExportScope
import com.fares.saveme.feature.account.domain.export.ExportStatus
import com.fares.saveme.feature.account.ui.export.detail.ExportDetailMessage
import com.fares.saveme.feature.account.ui.export.detail.ExportDetailScreen
import com.fares.saveme.feature.account.ui.export.detail.ExportDetailState
import com.fares.saveme.feature.account.ui.export.list.ExportsBanner
import com.fares.saveme.feature.account.ui.export.list.ExportsScreen
import com.fares.saveme.feature.account.ui.export.list.ExportsState
import com.fares.saveme.feature.account.ui.export.request.ExportRequestMessage
import com.fares.saveme.feature.account.ui.export.request.ExportRequestScreen
import com.fares.saveme.feature.account.ui.export.request.ExportRequestState
import com.fares.saveme.feature.account.ui.export.request.WalletChoice
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.time.Instant

/** The export screens' states in English LTR, Arabic RTL and English at font scale 2.0 (P-14.4, FR-038). */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w400dp-h900dp")
class ExportScreensScreenshotTest(
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
        compose.onRoot().captureRoboImage("src/androidHostTest/screenshots/screen_export_${name}_$config.png")
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
        private val at = Instant.parse("2026-10-06T10:00:00Z")
        private const val ZONE = "Africa/Cairo"

        private fun export(
            id: String, status: ExportStatus, scope: ExportScope = ExportScope.Account, walletId: String? = null,
            failure: ExportFailure? = null, size: Long? = null,
        ) = Export(
            id, scope, walletId, status, failure, at,
            completedAt = at.takeIf { status == ExportStatus.Ready || status == ExportStatus.Expired },
            expiresAt = Instant.parse("2026-10-07T10:00:41Z").takeIf { status == ExportStatus.Ready || status == ExportStatus.Expired },
            sizeBytes = size,
        )

        private fun detail(export: Export?, vararg change: (ExportDetailState) -> ExportDetailState): ExportDetailState =
            change.fold(ExportDetailState(export = export, loaded = true, timeZoneId = ZONE, walletNames = mapOf("w1" to "Home"))) { s, f -> f(s) }

        private val walletChoices = listOf(WalletChoice("w1", "Home", false), WalletChoice("w2", "Old savings", true))

        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun screens(): List<Array<Any>> = listOf(
            arrayOf("list", @Composable {
                ExportsScreen(
                    ExportsState(
                        items = listOf(
                            export("a", ExportStatus.Ready, ExportScope.Wallet, "w1", size = 184_320),
                            export("b", ExportStatus.Running),
                            export("c", ExportStatus.Queued),
                            export("d", ExportStatus.Failed, failure = ExportFailure.GenerationFailed),
                            export("e", ExportStatus.Expired, ExportScope.Wallet, "gone"),
                            export("f", ExportStatus.Unknown("archiving")),
                        ),
                        walletNames = mapOf("w1" to "Home"), hasMore = true, nextCursor = "c2", loaded = true, timeZoneId = ZONE,
                    ),
                ) {}
            }),
            arrayOf("list_empty", @Composable { ExportsScreen(ExportsState(loaded = true, timeZoneId = ZONE)) {} }),
            arrayOf("list_offline", @Composable { ExportsScreen(ExportsState(banner = ExportsBanner.Offline, loaded = true)) {} }),
            arrayOf("request_account", @Composable { ExportRequestScreen(ExportRequestState()) {} }),
            arrayOf("request_wallet", @Composable {
                ExportRequestScreen(ExportRequestState(scope = ExportScope.Wallet, walletId = "w1", wallets = walletChoices)) {}
            }),
            arrayOf("request_unsent", @Composable { ExportRequestScreen(ExportRequestState(unsentChanges = true)) {} }),
            arrayOf("request_in_progress", @Composable {
                ExportRequestScreen(ExportRequestState(message = ExportRequestMessage.InProgress("run1"))) {}
            }),
            arrayOf("request_limit_hours", @Composable {
                ExportRequestScreen(ExportRequestState(message = ExportRequestMessage.RateLimited(7_200), blockedUntil = at)) {}
            }),
            arrayOf("request_limit_minutes", @Composable {
                ExportRequestScreen(ExportRequestState(message = ExportRequestMessage.RateLimited(1_500), blockedUntil = at)) {}
            }),
            arrayOf("request_wallet_gone", @Composable {
                ExportRequestScreen(ExportRequestState(scope = ExportScope.Wallet, walletId = "w1", wallets = walletChoices, message = ExportRequestMessage.WalletGone)) {}
            }),
            arrayOf("request_failed", @Composable {
                ExportRequestScreen(ExportRequestState(message = ExportRequestMessage.Error(ApiErrorCode.INTERNAL_ERROR, "req-42"))) {}
            }),
            arrayOf("detail_queued", @Composable { ExportDetailScreen(detail(export("a", ExportStatus.Queued), { it.copy(polling = true) })) {} }),
            arrayOf("detail_running", @Composable { ExportDetailScreen(detail(export("a", ExportStatus.Running), { it.copy(polling = true) })) {} }),
            arrayOf("detail_ready", @Composable {
                ExportDetailScreen(detail(export("a", ExportStatus.Ready, ExportScope.Wallet, "w1", size = 184_320))) {}
            }),
            arrayOf("detail_ready_megabytes", @Composable {
                ExportDetailScreen(detail(export("a", ExportStatus.Ready, size = 5_400_000))) {}
            }),
            arrayOf("detail_expired", @Composable { ExportDetailScreen(detail(export("a", ExportStatus.Expired, size = 201_728))) {} }),
            arrayOf("detail_failed", @Composable {
                ExportDetailScreen(detail(export("a", ExportStatus.Failed, failure = ExportFailure.GenerationFailed))) {}
            }),
            arrayOf("detail_failed_wallet", @Composable {
                ExportDetailScreen(detail(export("a", ExportStatus.Failed, ExportScope.Wallet, "w1", ExportFailure.WalletUnavailable))) {}
            }),
            arrayOf("detail_still_preparing", @Composable {
                ExportDetailScreen(detail(export("a", ExportStatus.Running), { it.copy(stillPreparing = true) })) {}
            }),
            arrayOf("detail_not_found", @Composable {
                ExportDetailScreen(detail(null, { it.copy(message = ExportDetailMessage.NotFound) })) {}
            }),
            arrayOf("detail_offline", @Composable {
                ExportDetailScreen(detail(export("a", ExportStatus.Running), { it.copy(message = ExportDetailMessage.Offline) })) {}
            }),
            arrayOf("detail_open_failed", @Composable {
                ExportDetailScreen(detail(export("a", ExportStatus.Ready, size = 2048), { it.copy(message = ExportDetailMessage.OpenFailed) })) {}
            }),
        )
    }
}
