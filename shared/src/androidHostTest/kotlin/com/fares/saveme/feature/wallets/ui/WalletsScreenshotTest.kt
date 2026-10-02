package com.fares.saveme.feature.wallets.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
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
import com.fares.saveme.feature.wallets.domain.model.CurrencyTotal
import com.fares.saveme.feature.wallets.domain.model.Member
import com.fares.saveme.feature.wallets.domain.model.RefusalKind
import com.fares.saveme.feature.wallets.domain.model.RefusalReason
import com.fares.saveme.feature.wallets.domain.model.SyncState
import com.fares.saveme.feature.wallets.domain.model.Wallet
import com.fares.saveme.feature.wallets.domain.model.WalletDraft
import com.fares.saveme.feature.wallets.domain.model.WalletField
import com.fares.saveme.feature.wallets.domain.model.WalletFieldProblem
import com.fares.saveme.feature.wallets.domain.model.WalletRole
import com.fares.saveme.feature.wallets.domain.model.WalletType
import com.fares.saveme.feature.wallets.domain.model.WalletView
import com.fares.saveme.feature.wallets.ui.detail.ConfirmCard
import com.fares.saveme.feature.wallets.ui.detail.DetailConfirm
import com.fares.saveme.feature.wallets.ui.detail.TransferCandidates
import com.fares.saveme.feature.wallets.ui.detail.WalletDetailScreen
import com.fares.saveme.feature.wallets.ui.detail.WalletDetailState
import com.fares.saveme.feature.wallets.ui.editor.WalletEditorScreen
import com.fares.saveme.feature.wallets.ui.editor.WalletEditorState
import com.fares.saveme.feature.wallets.ui.list.WalletCardUi
import com.fares.saveme.feature.wallets.ui.list.WalletStatusUi
import com.fares.saveme.feature.wallets.ui.list.WalletsScreen
import com.fares.saveme.feature.wallets.ui.list.WalletsState
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.time.Instant

/** The wallet screens' states in English LTR, Arabic RTL and English at font scale 2.0 (P-14.4, FR-028). */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w400dp-h1600dp")
class WalletsScreenshotTest(
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
        compose.onRoot().captureRoboImage("src/androidHostTest/screenshots/screen_wallets_${name}_$config.png")
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
        private val created = Instant.parse("2026-09-01T00:00:00Z")
        private val updated = Instant.parse("2026-10-02T10:00:00Z")

        private fun wallet(
            name: String = "Pocket", type: String = "cash", balance: Long = 25_000, role: WalletRole = WalletRole.Owner,
            archived: Boolean = false, locked: Boolean = false, icon: String? = "cash", color: String? = "#2C7A5B",
        ) = Wallet(
            "w1", type, name, "EGP", balance, balance, icon, color, if (archived) Instant.parse("2026-09-05T00:00:00Z") else null,
            locked, role, 3, created,
        )

        private fun view(
            wallet: Wallet = wallet(), sync: SyncState = SyncState.None, pending: WalletDraft? = null,
            pendingDelete: Boolean = false, balance: Long = wallet.balanceMinor,
        ) = WalletView(wallet, pending, pendingDelete, sync, balance)

        private val owner = Member("u1", "Sara", WalletRole.Owner, true)
        private val editor = Member("u2", "Omar", WalletRole.Editor, false)
        private val viewer = Member("u3", null, WalletRole.Viewer, false)

        private fun card(
            name: String, amount: Long, type: WalletType = WalletType.Cash, status: WalletStatusUi? = null,
            archived: Boolean = false, role: WalletRole? = null, color: String? = "#2C7A5B", icon: String? = "cash",
        ) = WalletCardUi("c-$name", name, type, amount, "EGP", icon, color, status, archived, role)

        private val populated = WalletsState(
            active = listOf(
                card("Pocket", 250_000, status = WalletStatusUi.Waiting),
                card("Salary account", 1_450_000, WalletType.Checking, icon = "bank", color = "#4C6FD6"),
                card("Visa", -100_000, WalletType.CreditCard, icon = "card", color = "#A9503A"),
                card("Family jar", 90_000, WalletType.Savings, status = WalletStatusUi.Changed, role = WalletRole.Editor, icon = "savings", color = "#8F5E12"),
                card("Brokerage", 5_000, WalletType.Investment, status = WalletStatusUi.NotSaved, icon = null, color = null),
            ),
            archived = listOf(card("Old cash", 0, archived = true)),
            totals = listOf(CurrencyTotal("EGP", 1_695_000, 5), CurrencyTotal("USD", 120_000, 1)),
            loaded = true,
            updatedAt = updated,
        )

        private fun detailState(view: WalletView, members: List<Member> = listOf(owner), confirm: DetailConfirm = DetailConfirm.None) =
            WalletDetailState(walletId = "w1", wallet = view, members = members, confirm = confirm, timeZoneId = "Africa/Cairo")

        private val mine = WalletDraft("cash", "Pocket money", "EGP", 25_000, "cash", "#2C7A5B")

        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun screens(): List<Array<Any>> = listOf(
            arrayOf("editor_asset", @Composable {
                WalletEditorScreen(WalletEditorState(loaded = true, currency = "EGP", name = "Pocket", amountText = "250.00", iconKey = "cash", accentKey = "#2C7A5B"), {})
            }),
            arrayOf("editor_liability", @Composable {
                WalletEditorScreen(
                    WalletEditorState(loaded = true, type = WalletType.CreditCard, typeWire = "credit_card", currency = "EGP", name = "Visa", amountText = "1000.00"),
                    {},
                )
            }),
            arrayOf("editor_field_error", @Composable {
                WalletEditorScreen(
                    WalletEditorState(
                        loaded = true, currency = "EGP", name = "", amountText = "1.234",
                        errors = mapOf(WalletField.Name to WalletFieldProblem.NameBlank, WalletField.OpeningBalance to WalletFieldProblem.AmountDigits(2)),
                    ),
                    {},
                )
            }),
            arrayOf("editor_edit_locked", @Composable {
                WalletEditorScreen(
                    WalletEditorState(
                        walletId = "w1", loaded = true, type = WalletType.Savings, typeWire = "savings", currency = "EGP", currencyLocked = true,
                        name = "Family jar", amountText = "900.00", iconKey = "savings", accentKey = "#8F5E12", baseVersion = 3,
                        errors = mapOf(WalletField.Currency to WalletFieldProblem.Server("IMMUTABLE_FIELD")),
                    ),
                    {},
                )
            }),
            arrayOf("list_empty", @Composable { WalletsScreen(WalletsState(loaded = true), {}) }),
            arrayOf("list_one_waiting", @Composable {
                WalletsScreen(
                    WalletsState(
                        active = listOf(card("Pocket", 25_000, status = WalletStatusUi.Waiting)),
                        totals = listOf(CurrencyTotal("EGP", 25_000, 1)), loaded = true, offline = true, waitingCount = 1, updatedAt = updated,
                    ),
                    {},
                )
            }),
            arrayOf("list_totals_archived", @Composable { WalletsScreen(populated, {}) }),
            arrayOf("list_offline", @Composable { WalletsScreen(populated.copy(offline = true, waitingCount = 2, refreshFailed = false), {}) }),
            arrayOf("list_not_entitled", @Composable { WalletsScreen(populated.copy(notEntitledNotice = true, refreshFailed = true), {}) }),
            arrayOf("detail_owner", @Composable { WalletDetailScreen(detailState(view(), listOf(owner, editor, viewer)), {}) }),
            arrayOf("detail_viewer", @Composable { WalletDetailScreen(detailState(view(wallet(role = WalletRole.Viewer)), listOf(owner, viewer)), {}) }),
            arrayOf("detail_credit_card", @Composable {
                WalletDetailScreen(detailState(view(wallet(name = "Visa", type = "credit_card", balance = -100_000, icon = "card"))), {})
            }),
            arrayOf("detail_waiting", @Composable {
                WalletDetailScreen(detailState(view(sync = SyncState.Waiting, pending = mine)), {})
            }),
            arrayOf("detail_clash", @Composable {
                WalletDetailScreen(
                    detailState(view(sync = SyncState.Clash(wallet(name = "Wallet (web)"), false), pending = mine)), {},
                )
            }),
            arrayOf("detail_delete_clash", @Composable {
                WalletDetailScreen(
                    detailState(view(sync = SyncState.Clash(wallet(name = "Wallet (web)"), false), pending = mine, pendingDelete = true)), {},
                )
            }),
            arrayOf("detail_deleted_elsewhere", @Composable {
                WalletDetailScreen(detailState(view(sync = SyncState.Clash(null, true), pending = mine)), {})
            }),
            arrayOf("detail_not_saved", @Composable {
                WalletDetailScreen(
                    detailState(view(sync = SyncState.Refused(RefusalReason(RefusalKind.NotVerified), emptyMap()), pending = mine)), {},
                )
            }),
            arrayOf("detail_archived", @Composable { WalletDetailScreen(detailState(view(wallet(archived = true)), listOf(owner, editor)), {}) }),
            arrayOf("dialog_archive", @Composable {
                Column(Modifier.background(AppTheme.colors.background).padding(16.dp)) { ConfirmCard(detailState(view(), confirm = DetailConfirm.Archive), {}) }
            }),
            arrayOf("dialog_delete", @Composable {
                Column(Modifier.background(AppTheme.colors.background).padding(16.dp)) { ConfirmCard(detailState(view(), confirm = DetailConfirm.Delete), {}) }
            }),
            arrayOf("dialog_transfer", @Composable {
                Column(Modifier.background(AppTheme.colors.background).padding(16.dp)) {
                    ConfirmCard(detailState(view(), listOf(owner, editor), DetailConfirm.TransferTo(editor)), {})
                }
            }),
            arrayOf("sheet_transfer", @Composable {
                Column(Modifier.background(AppTheme.colors.surface).padding(16.dp)) { TransferCandidates(listOf(editor, viewer), {}) }
            }),
        )
    }
}
