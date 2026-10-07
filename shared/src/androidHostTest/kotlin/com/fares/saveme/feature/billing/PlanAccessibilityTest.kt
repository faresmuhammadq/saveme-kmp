package com.fares.saveme.feature.billing

import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import com.fares.saveme.designsystem.theme.AppTheme
import com.fares.saveme.feature.auth.domain.model.FeatureValue
import com.fares.saveme.feature.billing.domain.CurrentPlan
import com.fares.saveme.feature.billing.domain.EntitlementSet
import com.fares.saveme.feature.billing.domain.model.BillingPeriod
import com.fares.saveme.feature.billing.domain.model.CataloguePlan
import com.fares.saveme.feature.billing.domain.model.Subscription
import com.fares.saveme.feature.billing.domain.model.SubscriptionStatus
import com.fares.saveme.feature.billing.domain.play.PlayNotes
import com.fares.saveme.feature.billing.domain.play.RestoreMessagePart
import com.fares.saveme.feature.billing.ui.plan.CatalogueStatus
import com.fares.saveme.feature.billing.ui.plan.PlanScreen
import com.fares.saveme.feature.billing.ui.plan.PlanState
import com.fares.saveme.feature.billing.ui.plan.RestoreRefusal
import com.fares.saveme.feature.billing.ui.plan.RestoreUi
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
 * Spec 018 FR-023, FR-053, P-08: every status note and the restore message is a polite live region (announced, never colour
 * alone), carries its stable tag, and every action is a 48 dp target, also at 200% font.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w400dp-h3200dp")
class PlanAccessibilityTest {

    @get:Rule
    val compose = createComposeRule()

    private val free = EntitlementSet(mapOf("sharing.enabled" to FeatureValue.Flag(false), "wallets.max" to FeatureValue.Quota(null)))
    private val plus = EntitlementSet(mapOf("sharing.enabled" to FeatureValue.Flag(true), "wallets.max" to FeatureValue.Quota(null)))
    private val end = Instant.parse("2026-11-01T00:00:00Z")

    private fun state(restore: RestoreUi = RestoreUi.Idle, notes: PlayNotes = PlayNotes(), subscription: Subscription? = null) = PlanState(
        plan = CurrentPlan("plus", "Plus · Monthly", plus),
        subscription = subscription,
        settled = true,
        plans = listOf(
            CataloguePlan("free", "Free", BillingPeriod.Lifetime, true, free, null),
            CataloguePlan("plus", "Plus · Monthly", BillingPeriod.Monthly, false, plus, "plus_monthly"),
        ),
        catalogueStatus = CatalogueStatus.Loaded,
        timeZoneId = "Africa/Cairo",
        playSupported = true,
        restore = restore,
        notes = notes,
    )

    private fun playSubscription(status: SubscriptionStatus) = Subscription("plus", "Plus · Monthly", status, end, end, true, provider = "google_play")

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

    private fun SemanticsNode.liveRegion(): LiveRegionMode? =
        config.getOrNull(SemanticsProperties.LiveRegion) ?: children.firstNotNullOfOrNull { it.liveRegion() }

    private fun assertAnnounced(tag: String) {
        compose.onNodeWithTag(tag, useUnmergedTree = true).assertIsDisplayed()
        assertEquals("$tag is a polite live region", LiveRegionMode.Polite, compose.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().liveRegion())
    }

    @Test
    fun theRestoreResultIsAnnouncedAndItsActionsAreBigEnough() {
        compose.setContent {
            AppTheme {
                PlanScreen(
                    state(restore = RestoreUi.Result(listOf(RestoreMessagePart.Restored, RestoreMessagePart.AnotherAccount)), subscription = playSubscription(SubscriptionStatus.Active)),
                    onIntent = {},
                )
            }
        }
        listOf("plan.restore", "plan.enterCode", "plan.play.manage").forEach { compose.onNodeWithTag(it, useUnmergedTree = true).assertIsDisplayed() }
        assertAnnounced("plan.restore.result")
        assertTouchTargets()
    }

    @Test
    fun aRefusedRestoreIsAnnouncedWithItsRetryAndTheNotesAreAnnouncedAtDoubleFont() {
        RuntimeEnvironment.setFontScale(2f)
        compose.setContent {
            AppTheme {
                PlanScreen(
                    state(
                        restore = RestoreUi.Refused(RestoreRefusal.RateLimited, retryAfterSeconds = 600),
                        notes = PlayNotes(pendingProducts = setOf("plus_monthly"), anotherAccount = true),
                        subscription = playSubscription(SubscriptionStatus.Grace),
                    ),
                    onIntent = {},
                )
            }
        }
        listOf("plan.restore.result", "plan.play.pending", "plan.play.anotherAccount").forEach { assertAnnounced(it) }
        compose.onNodeWithTag("plan.restore.retry", useUnmergedTree = true).assertExists()
        assertTouchTargets()
    }
}
