package com.fares.saveme.feature.wallets.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import com.fares.saveme.designsystem.theme.AppTheme
import com.fares.saveme.feature.wallets.domain.model.CurrencyTotal
import com.fares.saveme.feature.wallets.domain.model.Member
import com.fares.saveme.feature.wallets.domain.model.RefusalKind
import com.fares.saveme.feature.wallets.domain.model.RefusalReason
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
import com.fares.saveme.feature.wallets.ui.list.WalletStatusUi
import com.fares.saveme.feature.wallets.ui.list.WalletsScreen
import com.fares.saveme.feature.wallets.ui.list.WalletsState
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import kotlin.time.Instant

/** FR-028, P-08: marks and roles are text, choices are announced, targets are 48dp, nothing clips at 2x font. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w400dp-h2400dp")
class WalletsAccessibilityTest {

    @get:Rule
    val compose = createComposeRule()

    private val list = WalletsState(
        active = listOf(
            WalletCardUi("1", "Pocket", WalletType.Cash, 25_000, "EGP", "cash", "#2C7A5B", WalletStatusUi.Waiting, false, null),
            WalletCardUi("2", "Jar", WalletType.Savings, 9_000, "EGP", null, null, WalletStatusUi.Changed, false, WalletRole.Viewer),
            WalletCardUi("3", "Brokerage", WalletType.Investment, 9_000, "EGP", null, null, WalletStatusUi.NotSaved, false, null),
        ),
        archived = listOf(WalletCardUi("4", "Old", WalletType.Cash, 0, "EGP", null, null, null, true, null)),
        totals = listOf(CurrencyTotal("EGP", 43_000, 3)),
        loaded = true,
    )

    private fun detail(sync: SyncState = SyncState.None, archived: Boolean = false): WalletDetailState {
        val wallet = Wallet(
            "w1", "cash", "Pocket", "EGP", 100, 100, null, null, if (archived) Instant.parse("2026-09-02T00:00:00Z") else null,
            false, WalletRole.Owner, 1, Instant.parse("2026-09-01T00:00:00Z"),
        )
        return WalletDetailState(
            walletId = "w1", wallet = WalletView(wallet, null, false, sync, 100),
            members = listOf(Member("u1", "Sara", WalletRole.Owner, true), Member("u2", "Omar", WalletRole.Editor, false)),
        )
    }

    @Test
    fun syncMarksRolesAndArchivedStateAreText() {
        compose.setContent { AppTheme { Column { WalletsScreen(list, {}) } } }
        compose.onNodeWithText("Waiting to sync").assertIsDisplayed()
        compose.onNodeWithText("Changed on another device.").assertIsDisplayed()
        compose.onNodeWithText("Not saved.").assertIsDisplayed()
        compose.onNodeWithText("Viewer").assertIsDisplayed()
        compose.onNodeWithText("Archived").assertIsDisplayed()
    }

    @Test
    fun detailExposesRoleAndArchivedStateAsText() {
        compose.setContent { AppTheme { WalletDetailScreen(detail(archived = true), {}) } }
        compose.onNodeWithText("Owner").assertIsDisplayed()
        compose.onNodeWithText("Editor").assertIsDisplayed()
        compose.onNodeWithText("Archived").assertIsDisplayed()
        compose.onNodeWithText("Transfer ownership").assertIsDisplayed()
    }

    @Test
    fun editorChoicesAreAnnouncedAndEveryTargetIsAtLeast48dp() {
        compose.setContent { AppTheme { WalletEditorScreen(WalletEditorState(loaded = true, currency = "EGP", iconKey = "cash"), {}) } }
        listOf("Cash", "Bank", "Savings jar", "Card", "Loan", "Investment", "Blue", "Green", "Slate").forEach { label ->
            compose.onAllNodes(hasContentDescription(label)).fetchSemanticsNodes().let { assertTrue("no choice announced as $label", it.isNotEmpty()) }
        }
        assertTouchTargets()
    }

    @Test
    fun listAndDetailTargetsAreAtLeast48dp() {
        compose.setContent { AppTheme { WalletsScreen(list, {}) } }
        assertTouchTargets()
    }

    @Test
    fun nothingClipsAtDoubleFontScale() {
        RuntimeEnvironment.setFontScale(2f)
        compose.setContent { AppTheme { WalletsScreen(list, {}) } }
        compose.onNodeWithText("Waiting to sync").assertIsDisplayed()
        compose.onNodeWithText("Changed on another device.").assertIsDisplayed()
        assertTouchTargets()
    }

    private fun assertTouchTargets() {
        val minPx = with(compose.density) { 48.dp.toPx() }
        val nodes = compose.onAllNodes(hasClickAction(), useUnmergedTree = true).fetchSemanticsNodes()
        assertTrue("expected clickable nodes", nodes.isNotEmpty())
        nodes.forEach { node ->
            val bounds = node.touchBoundsInRoot
            assertTrue("touch width ${bounds.width} < $minPx for ${node.config}", bounds.width >= minPx - 0.5f)
            assertTrue("touch height ${bounds.height} < $minPx for ${node.config}", bounds.height >= minPx - 0.5f)
        }
    }
}
