package com.fares.saveme.audit

import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.createComposeRule
import com.fares.saveme.core.sync.SyncStatus
import com.fares.saveme.designsystem.theme.AppTheme
import com.fares.saveme.feature.account.domain.deletion.Candidate
import com.fares.saveme.feature.account.domain.deletion.DeletionPlan
import com.fares.saveme.feature.account.domain.deletion.OwnedSharedWallet
import com.fares.saveme.feature.account.domain.deletion.PlanState
import com.fares.saveme.feature.account.domain.deletion.SuccessorReason
import com.fares.saveme.feature.account.domain.export.Export
import com.fares.saveme.feature.account.domain.export.ExportScope
import com.fares.saveme.feature.account.domain.export.ExportStatus
import com.fares.saveme.feature.account.domain.model.DeviceSession
import com.fares.saveme.feature.account.domain.model.Profile
import com.fares.saveme.feature.account.domain.model.ProfileView
import com.fares.saveme.feature.account.ui.account.AccountPlan
import com.fares.saveme.feature.account.ui.account.AccountPlanStatus
import com.fares.saveme.feature.account.ui.account.AccountScreen
import com.fares.saveme.feature.account.ui.account.AccountState
import com.fares.saveme.feature.account.ui.changepassword.ChangePasswordScreen
import com.fares.saveme.feature.account.ui.changepassword.ChangePasswordState
import com.fares.saveme.feature.account.ui.deleteaccount.DeleteAccountScreen
import com.fares.saveme.feature.account.ui.deleteaccount.DeleteAccountState
import com.fares.saveme.feature.account.ui.deleteaccount.DeleteStep
import com.fares.saveme.feature.account.ui.export.detail.ExportDetailScreen
import com.fares.saveme.feature.account.ui.export.detail.ExportDetailState
import com.fares.saveme.feature.account.ui.export.list.ExportsScreen
import com.fares.saveme.feature.account.ui.export.list.ExportsState
import com.fares.saveme.feature.account.ui.export.request.ExportRequestScreen
import com.fares.saveme.feature.account.ui.export.request.ExportRequestState
import com.fares.saveme.feature.account.ui.export.request.WalletChoice
import com.fares.saveme.feature.account.ui.profile.ProfileScreen
import com.fares.saveme.feature.account.ui.profile.ProfileState
import com.fares.saveme.feature.account.ui.sessions.ConfirmEnd
import com.fares.saveme.feature.account.ui.sessions.DeviceSessionsScreen
import com.fares.saveme.feature.account.ui.sessions.DeviceSessionsState
import com.fares.saveme.feature.wallets.domain.model.WalletRole
import com.fares.saveme.feature.wallets.domain.model.WalletType
import com.github.takahirom.roborazzi.captureRoboImage
import kotlinx.datetime.LocalDate
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.time.Instant

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w390dp-h844dp-xhdpi")
class AccountAuditTest {
    @get:Rule
    val compose = createComposeRule()

    private val now = Instant.parse("2026-10-18T09:12:00Z")

    @Test
    fun account() = compose.auditCapture("Account-B") {
        AccountScreen(
            AccountState(
                email = "layla@example.com",
                displayName = "Layla",
                plan = AccountPlan("Plus", AccountPlanStatus.Renews(Instant.parse("2026-11-12T00:00:00Z"))),
                sync = SyncStatus(waiting = 3),
                notificationsAvailable = true,
                deviceCount = 2,
            ),
        ) {}
    }

    @Test
    fun profile() = compose.auditCapture("Profile-B") {
        val server = Profile("Layla", "en", "Africa/Cairo", "EGP", true, 25, LocalDate(2026, 10, 25), 3)
        val other = server.copy(displayName = "Layla M.", budgetStartDay = 24)
        val view = ProfileView(server, server.toDraft().copy(budgetStartDay = 24), other, emptyMap(), now)
        ProfileScreen(ProfileState("layla@example.com", true, view, view.effective.copy(budgetStartDay = 25)), {})
    }

    @Test
    fun devices() = compose.auditCapture("Devices-B") {
        DeviceSessionsScreen(DeviceSessionsState(sessions, now, "Africa/Cairo"), {})
    }

    @Test
    fun devicesDialog() {
        compose.setContent {
            AppTheme {
                DeviceSessionsScreen(
                    DeviceSessionsState(sessions, now, "Africa/Cairo", confirm = ConfirmEnd.One("b", "Galaxy Tab S9")),
                    {},
                )
            }
        }
        compose.onNode(isDialog()).captureRoboImage("build/design-audit/Devices-B-dialog.png")
    }

    @Test
    fun changePassword() = compose.auditCapture("ChangePw-B") {
        ChangePasswordScreen(ChangePasswordState(current = "oldpass12", new = "calm-blue-river", email = "layla@example.com")) {}
    }

    @Test
    fun deleteAccount() = compose.auditCapture("DeleteAcct-B") {
        val t = Instant.parse("2026-09-02T00:00:00Z")
        val home = OwnedSharedWallet(
            "w1", "Home expenses", "u1", SuccessorReason.OldestEditor, choiceExpected = true, choiceOffered = true,
            candidates = listOf(
                Candidate("u1", "Karim", WalletRole.Editor, t),
                Candidate("u2", "Nour", WalletRole.Viewer, Instant.parse("2026-09-30T00:00:00Z")),
            ),
            walletColor = "#6B52B8",
        )
        val trip = OwnedSharedWallet(
            "w2", "Family trip", "u3", SuccessorReason.OnlyMember, choiceExpected = false, choiceOffered = false,
            candidates = listOf(Candidate("u3", "Mona", WalletRole.Editor, t)),
            walletColor = "#1F7F8C",
        )
        DeleteAccountScreen(
            DeleteAccountState(step = DeleteStep.NewOwners, plan = DeletionPlan(PlanState.Active, null, null, listOf(home, trip))),
        ) {}
    }

    @Test
    fun exports() = compose.auditCapture("Exports-B") {
        fun export(id: String, status: ExportStatus, wallet: String?, at: String) =
            Export(id, if (wallet == null) ExportScope.Account else ExportScope.Wallet, wallet, status, null, Instant.parse(at), null, null, null)
        ExportsScreen(
            ExportsState(
                items = listOf(
                    export("a", ExportStatus.Running, null, "2026-10-18T09:12:00Z"),
                    export("b", ExportStatus.Ready, "w2", "2026-10-16T09:00:00Z"),
                    export("c", ExportStatus.Expired, null, "2026-10-02T09:00:00Z"),
                    export("d", ExportStatus.Failed, "gone", "2026-09-28T09:00:00Z"),
                ),
                walletNames = mapOf("w2" to "Home expenses"),
                loaded = true,
                timeZoneId = "Africa/Cairo",
            ),
        ) {}
    }

    @Test
    fun exportNew() = compose.auditCapture("ExportNew-B") {
        ExportRequestScreen(
            ExportRequestState(
                scope = ExportScope.Wallet,
                walletId = "w2",
                wallets = listOf(
                    WalletChoice("w1", "CIB Checking", false, null, "#4C6FD6", WalletType.Checking),
                    WalletChoice("w2", "Home expenses", false, null, "#6B52B8", WalletType.Cash),
                    WalletChoice("w3", "Emergency fund", false, null, "#2C7A5B", WalletType.Savings),
                    WalletChoice("w4", "NBE Visa", false, null, "#A9503A", WalletType.CreditCard),
                ),
            ),
        ) {}
    }

    @Test
    fun exportDetail() = compose.auditCapture("ExportDetail-B") {
        ExportDetailScreen(
            ExportDetailState(
                export = Export("a", ExportScope.Account, null, ExportStatus.Running, null, now, null, null, null),
                stillPreparing = true,
                loaded = true,
                timeZoneId = "Africa/Cairo",
            ),
        ) {}
    }

    private val sessions = listOf(
        DeviceSession("a", "Xiaomi 14", "android", "1.0.0", Instant.parse("2026-09-02T09:00:00Z"), now, isCurrent = true),
        DeviceSession("b", "Galaxy Tab S9", "android", "1.0.0", Instant.parse("2026-09-10T09:00:00Z"), Instant.parse("2026-10-15T09:00:00Z"), isCurrent = false),
        DeviceSession("c", "iPhone 15", "ios", "1.0.0", Instant.parse("2026-10-10T09:00:00Z"), null, isCurrent = false),
    )
}
