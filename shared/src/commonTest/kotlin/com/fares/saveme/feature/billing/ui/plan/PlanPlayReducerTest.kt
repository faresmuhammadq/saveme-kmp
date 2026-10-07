package com.fares.saveme.feature.billing.ui.plan

import com.fares.saveme.feature.billing.EntitlementSetFixtures.free
import com.fares.saveme.feature.billing.EntitlementSetFixtures.plus
import com.fares.saveme.feature.billing.domain.CurrentPlan
import com.fares.saveme.feature.billing.domain.model.BillingPeriod
import com.fares.saveme.feature.billing.domain.model.CataloguePlan
import com.fares.saveme.feature.billing.domain.model.Money
import com.fares.saveme.feature.billing.domain.model.Subscription
import com.fares.saveme.feature.billing.domain.model.SubscriptionStatus
import com.fares.saveme.feature.billing.domain.play.PlayNotes
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

/** Spec 018: the Plan screen's Google Play state (notes, links, prices, restore) as pure reductions. */
class PlanPlayReducerTest {

    private val end = Instant.parse("2026-11-01T00:00:00Z")
    private val graceEnd = Instant.parse("2026-11-05T00:00:00Z")

    private val catalogue = listOf(
        CataloguePlan("free-id", "Free", BillingPeriod.Lifetime, true, free, playProductId = null),
        CataloguePlan("plus-m", "Plus · Monthly", BillingPeriod.Monthly, false, plus, playProductId = "plus_monthly"),
        CataloguePlan("plus-y", "Plus · Yearly", BillingPeriod.Yearly, false, plus, playProductId = "plus_yearly"),
    )

    private fun play(provider: String? = "google_play", status: SubscriptionStatus? = SubscriptionStatus.Active, grace: Instant? = null) =
        Subscription("plus-m", "Plus · Monthly", status, end, grace, true, provider = provider)

    private fun state(
        subscription: Subscription? = play(),
        supported: Boolean = true,
        notes: PlayNotes = PlayNotes(),
        plans: List<CataloguePlan>? = catalogue,
    ) = PlanState(
        plan = CurrentPlan("plus-m", "Plus · Monthly", plus),
        subscription = subscription,
        plans = plans,
        catalogueStatus = CatalogueStatus.Loaded,
        playSupported = supported,
        notes = notes,
        timeZoneId = "Africa/Cairo",
    )

    @Test
    fun manageShowsOnlyForAGooglePlaySubscriptionOnAPhoneWithGooglePlay() {
        assertTrue(state().showManage)
        assertFalse(state(play(provider = "instapay")).showManage)
        assertFalse(state(play(provider = "manual")).showManage)
        assertFalse(state(play(provider = null, status = null)).showManage)
        assertFalse(state(supported = false).showManage)
        assertFalse(state(null).showManage)
    }

    @Test
    fun theManagedProductIsTheCataloguePlansGooglePlayProduct() {
        assertEquals("plus_monthly", state().manageProductId)
        assertNull(state(plans = null).manageProductId)
        assertNull(state(Subscription("retired", "Old", SubscriptionStatus.Active, end, null, true, provider = "google_play")).manageProductId)
    }

    @Test
    fun aGracePeriodShowsTheWarningWithTheGraceEnd() {
        val problems = state(play(status = SubscriptionStatus.Grace, grace = graceEnd)).problems
        assertEquals(listOf<PaymentProblemUi>(PaymentProblemUi.Grace(graceEnd, "Plus · Monthly")), problems)
    }

    @Test
    fun graceOfAnotherProviderOrWithoutGooglePlayShowsNothing() {
        assertTrue(state(play(provider = "instapay", status = SubscriptionStatus.Grace, grace = graceEnd)).problems.isEmpty())
        assertTrue(state(play(status = SubscriptionStatus.Grace, grace = graceEnd), supported = false).problems.isEmpty())
        assertTrue(state(play(status = SubscriptionStatus.Grace, grace = null)).problems.isEmpty())
    }

    @Test
    fun holdAndPausedShowOnlyAfterAVerifyAnswerInThisRun() {
        assertTrue(state(null).problems.isEmpty())
        val held = state(null, notes = PlayNotes(heldProducts = setOf("plus_monthly"))).problems
        assertEquals(listOf<PaymentProblemUi>(PaymentProblemUi.Hold("Plus · Monthly", "plus_monthly")), held)
        val paused = state(null, notes = PlayNotes(pausedProducts = setOf("plus_yearly"))).problems
        assertEquals(listOf<PaymentProblemUi>(PaymentProblemUi.Paused("plus_yearly")), paused)
    }

    @Test
    fun aHoldWithoutACataloguePlanForItsProductWaitsForTheCatalogue() {
        assertTrue(state(null, notes = PlayNotes(heldProducts = setOf("plus_monthly")), plans = null).problems.isEmpty())
    }

    @Test
    fun pendingAndAnotherAccountFollowTheNotes() {
        assertFalse(state().showPending)
        assertTrue(state(notes = PlayNotes(pendingProducts = setOf("plus_monthly"))).showPending)
        assertFalse(state(notes = PlayNotes(pendingProducts = setOf("plus_monthly")), supported = false).showPending)
        assertTrue(state(notes = PlayNotes(anotherAccount = true)).showAnotherAccount)
        assertFalse(state().showAnotherAccount)
        val cleared = reduce(state(notes = PlayNotes(anotherAccount = true)), PlanChange.Notes(PlayNotes()))
        assertFalse(cleared.showAnotherAccount, "a note disappears when its condition no longer holds (FR-035)")
    }

    @Test
    fun theTestLabelShowsOnlyForTheGoverningGooglePlaySubscriptionsOwnProduct() {
        assertTrue(state(notes = PlayNotes(testProducts = setOf("plus_monthly"))).showTestLabel)
        assertFalse(state(notes = PlayNotes(testProducts = setOf("plus_yearly"))).showTestLabel)
        assertFalse(state(play(provider = "instapay"), notes = PlayNotes(testProducts = setOf("plus_monthly"))).showTestLabel)
        assertFalse(state().showTestLabel)
    }

    @Test
    fun cardPricesComeOnlyFromGooglePlay() {
        val without = state().cards
        assertTrue(without.all { it.price == null }, "no Google Play answer, no price, no placeholder")
        val withPrices = reduce(state(), PlanChange.PlayPricesLoaded(mapOf("plus_monthly" to Money(14_900, "EGP")))).cards
        assertNull(withPrices[0].price)
        assertEquals(Money(14_900, "EGP"), withPrices[1].price)
        assertNull(withPrices[2].price)
    }

    @Test
    fun restoreChangesReplaceTheRestoreState() {
        val running = reduce(state(), PlanChange.Restore(RestoreUi.Running))
        assertEquals(RestoreUi.Running, running.restore)
        assertEquals(RestoreUi.Idle, reduce(running, PlanChange.Restore(RestoreUi.Idle)).restore)
    }

    @Test
    fun theBillingActionsAreExactlyRestoreAndEnterCodeAndNoSubscribeExists() {
        // FR-001, FR-040: while the switch is off nothing models a Subscribe offer: the card and the intents have no such member.
        val intents = listOf(
            PlanIntent.Opened, PlanIntent.Refresh, PlanIntent.Back, PlanIntent.EnterCode, PlanIntent.RestorePurchases, PlanIntent.RetryRestore,
            PlanIntent.DismissRestoreResult, PlanIntent.OpenManage, PlanIntent.OpenFixPayment,
        )
        assertTrue(intents.none { it.toString().contains("Subscribe", ignoreCase = true) })
        assertTrue(state().cards.none { it.toString().contains("subscribe", ignoreCase = true) })
    }
}
