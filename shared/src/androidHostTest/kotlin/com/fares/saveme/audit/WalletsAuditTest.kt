package com.fares.saveme.audit

import androidx.compose.ui.test.junit4.createComposeRule
import com.fares.saveme.designsystem.component.MainTab
import com.fares.saveme.feature.wallets.domain.model.CurrencyTotal
import com.fares.saveme.feature.wallets.domain.model.Member
import com.fares.saveme.feature.wallets.domain.model.RecentAuthor
import com.fares.saveme.feature.wallets.domain.model.RecentDirection
import com.fares.saveme.feature.wallets.domain.model.RecentKind
import com.fares.saveme.feature.wallets.domain.model.RecentTransaction
import com.fares.saveme.feature.wallets.domain.model.SyncState
import com.fares.saveme.feature.wallets.domain.model.Wallet
import com.fares.saveme.feature.wallets.domain.model.WalletRole
import com.fares.saveme.feature.wallets.domain.model.WalletType
import com.fares.saveme.feature.wallets.domain.model.WalletView
import com.fares.saveme.feature.wallets.ui.detail.WalletDetailScreen
import com.fares.saveme.feature.wallets.ui.detail.WalletDetailState
import com.fares.saveme.feature.wallets.ui.editor.WalletEditorScreen
import com.fares.saveme.feature.wallets.ui.editor.WalletEditorState
import com.fares.saveme.feature.wallets.ui.list.WalletCardUi
import com.fares.saveme.feature.wallets.ui.list.WalletsScreen
import com.fares.saveme.feature.wallets.ui.list.WalletsState
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.time.Clock
import kotlin.time.Instant

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w390dp-h844dp-xhdpi")
class WalletsAuditTest {
    @get:Rule val compose = createComposeRule()

    private fun card(id: String, name: String, type: WalletType, major: Long, icon: String?, accent: String?, shared: Boolean = false) =
        WalletCardUi(id, name, type, major * 100, "EGP", icon, accent, null, false, null, shared, WalletRole.Owner)

    @Test fun wallets() = compose.auditCapture("Wallets-B") {
        WithTabBar(MainTab.Wallets) {
            WalletsScreen(
                WalletsState(
                    active = listOf(
                        card("1", "CIB Checking", WalletType.Checking, 48_250, "bank", "#4C6FD6"),
                        card("2", "Emergency fund", WalletType.Savings, 120_000, "savings", "#2C7A5B"),
                        card("3", "Home expenses", WalletType.Checking, 9_700, null, null, shared = true),
                        card("4", "Cash", WalletType.Cash, 2_140, "cash", null),
                        card("5", "NBE Visa", WalletType.CreditCard, -6_380, "card", null),
                    ),
                    archived = listOf(card("6", "Old", WalletType.Cash, 0, null, null).copy(archived = true)),
                    totals = listOf(CurrencyTotal("EGP", 17_371_000, 5), CurrencyTotal("USD", 0, 0)),
                    loaded = true,
                ),
                {},
            )
        }
    }

    @Test fun walletDetail() {
        val wallet = Wallet(
            "w1", "checking", "Home expenses", "EGP", 970_000, 970_000, null, "#6B52B8", null, false, WalletRole.Owner, 3,
            Instant.parse("2026-10-01T10:00:00Z"),
        )
        val today = Clock.System.now().toLocalDateTime(TimeZone.UTC).date
        compose.auditCapture("WalletDetail-B") {
            WalletDetailScreen(
                WalletDetailState(
                    walletId = "w1",
                    wallet = WalletView(wallet, null, false, SyncState.None, 970_000),
                    members = listOf(
                        Member("u1", "Layla", WalletRole.Owner, true),
                        Member("u2", "Karim", WalletRole.Editor, false),
                    ),
                    pendingInvitations = 1,
                    recent = listOf(
                        RecentTransaction("t1", RecentKind.Expense, RecentDirection.Out, "Groceries", "cart", null, RecentAuthor.Member("Karim"), today, 82_000, "EGP"),
                        RecentTransaction("t2", RecentKind.Expense, RecentDirection.Out, "Electricity", "bolt", null, RecentAuthor.You, LocalDate(2026, 10, 15), 82_000, "EGP"),
                        RecentTransaction("t3", RecentKind.Transfer, RecentDirection.In, null, null, "CIB Checking", RecentAuthor.You, LocalDate(2026, 10, 1), 600_000, "EGP"),
                    ),
                ),
                {},
            )
        }
    }

    @Test fun walletEdit() = compose.auditCapture("WalletEdit-B") {
        WalletEditorScreen(
            WalletEditorState(
                loaded = true, type = WalletType.Savings, typeWire = "savings", name = "Emergency fund", currency = "EGP",
                amountText = "120,000.00", iconKey = "savings", accentKey = "#2C7A5B",
            ),
            {},
        )
    }
}
