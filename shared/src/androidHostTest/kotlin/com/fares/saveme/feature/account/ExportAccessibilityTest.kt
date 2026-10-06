package com.fares.saveme.feature.account

import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import com.fares.saveme.designsystem.theme.AppTheme
import com.fares.saveme.feature.account.domain.export.Export
import com.fares.saveme.feature.account.domain.export.ExportScope
import com.fares.saveme.feature.account.domain.export.ExportStatus
import com.fares.saveme.feature.account.ui.deleteaccount.DeleteAccountScreen
import com.fares.saveme.feature.account.ui.deleteaccount.DeleteAccountState
import com.fares.saveme.feature.account.ui.export.detail.ExportDetailScreen
import com.fares.saveme.feature.account.ui.export.detail.ExportDetailState
import com.fares.saveme.feature.account.ui.export.request.ExportRequestScreen
import com.fares.saveme.feature.account.ui.export.request.ExportRequestState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import kotlin.time.Instant

/**
 * FR-038, P-08: status is a word and a polite live region (never the spinner alone or colour), the stable tags of
 * contracts/ui-and-e2e.md exist, every target is 48 dp, and nothing is lost at 200% font.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w400dp-h2400dp")
class ExportAccessibilityTest {

    @get:Rule
    val compose = createComposeRule()

    private val at = Instant.parse("2026-10-06T10:00:00Z")

    private fun export(status: ExportStatus) = Export(
        "e1", ExportScope.Account, null, status, null, at,
        completedAt = at.takeIf { status == ExportStatus.Ready }, expiresAt = Instant.parse("2026-10-07T10:00:41Z").takeIf { status == ExportStatus.Ready },
        sizeBytes = 184_320L.takeIf { status == ExportStatus.Ready },
    )

    private fun detail(status: ExportStatus, polling: Boolean = false) =
        ExportDetailState(export = export(status), loaded = true, polling = polling, timeZoneId = "Africa/Cairo")

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

    @Test
    fun aWaitingExportIsSaidInWordsInAPoliteLiveRegionBesideTheProgressIndicator() {
        compose.setContent { AppTheme { ExportDetailScreen(detail(ExportStatus.Queued, polling = true)) {} } }
        compose.onNodeWithText("Waiting to start").assertIsDisplayed()
        compose.onNodeWithTag("exportDetail.progress", useUnmergedTree = true).assertIsDisplayed()
        val live = compose.onNodeWithTag("exportDetail.status", useUnmergedTree = true).fetchSemanticsNode().config[SemanticsProperties.LiveRegion]
        assertEquals(LiveRegionMode.Polite, live)
    }

    @Test
    fun aReadyExportSaysReadyAndOffersOneBigOpenAction() {
        compose.setContent { AppTheme { ExportDetailScreen(detail(ExportStatus.Ready)) {} } }
        compose.onNodeWithText("Ready").assertIsDisplayed()
        compose.onNodeWithTag("exportDetail.size").assertIsDisplayed()
        compose.onNodeWithTag("exportDetail.until").assertIsDisplayed()
        compose.onNodeWithTag("exportDetail.open").assertIsDisplayed()
        assertTouchTargets()
    }

    @Test
    fun theRequestScreenCarriesItsTagsAndItsTargetsAreBigEnough() {
        compose.setContent { AppTheme { ExportRequestScreen(ExportRequestState()) {} } }
        listOf("exportRequest.scope.account", "exportRequest.scope.wallet", "exportRequest.explain", "exportRequest.submit").forEach {
            compose.onNodeWithTag(it, useUnmergedTree = true).assertIsDisplayed()
        }
        assertTouchTargets()
    }

    @Test
    fun theDeletionExplanationOffersExportAndNamesGooglePlayAtDoubleFont() {
        RuntimeEnvironment.setFontScale(2f)
        compose.setContent { AppTheme { DeleteAccountScreen(DeleteAccountState()) {} } }
        compose.onNodeWithTag("delete.explain.play", useUnmergedTree = true).assertExists()
        compose.onNodeWithTag("delete.explain.export", useUnmergedTree = true).assertExists()
        assertTouchTargets()
    }

    @Test
    fun theReadyExportKeepsEverythingAtDoubleFont() {
        RuntimeEnvironment.setFontScale(2f)
        compose.setContent { AppTheme { ExportDetailScreen(detail(ExportStatus.Ready)) {} } }
        compose.onNodeWithText("Ready").assertIsDisplayed()
        compose.onNodeWithTag("exportDetail.open").assertIsDisplayed()
        assertTouchTargets()
    }
}
