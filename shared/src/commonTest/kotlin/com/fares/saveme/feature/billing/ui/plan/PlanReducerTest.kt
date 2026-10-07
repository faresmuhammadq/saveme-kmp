package com.fares.saveme.feature.billing.ui.plan

import com.fares.saveme.feature.auth.domain.model.FeatureValue
import com.fares.saveme.feature.auth.domain.model.RefreshOutcome
import com.fares.saveme.feature.billing.EntitlementSetFixtures.free
import com.fares.saveme.feature.billing.EntitlementSetFixtures.plus
import com.fares.saveme.feature.billing.domain.CurrentPlan
import com.fares.saveme.feature.billing.domain.model.BillingPeriod
import com.fares.saveme.feature.billing.domain.model.CataloguePlan
import com.fares.saveme.feature.billing.domain.model.Money
import com.fares.saveme.feature.billing.domain.model.Subscription
import com.fares.saveme.feature.billing.domain.model.SubscriptionStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

private val freePlan = CurrentPlan("free-id", "Free", free)
private val plusPlan = CurrentPlan("plus-id", "Plus · Monthly", plus)
private val end = Instant.parse("2026-11-01T00:00:00Z")

private fun sub(status: SubscriptionStatus?, planId: String = "plus-id", periodEnd: Instant? = end, grace: Instant? = null, autoRenew: Boolean? = true) =
    Subscription(planId, "Plus · Monthly", status, periodEnd, grace, autoRenew)

private fun state(plan: CurrentPlan, subscription: Subscription?, settled: Boolean = true) =
    reduce(reduce(reduce(PlanState(), PlanChange.Entitlements(plan)), PlanChange.SubscriptionChanged(subscription)), PlanChange.RefreshFinished(RefreshOutcome.Done, RefreshOutcome.Done))
        .copy(settled = settled)

class PlanReducerTest {

    @Test
    fun freeHasNoStatusLine() {
        val current = state(freePlan, sub(null, planId = "free-id", periodEnd = null, autoRenew = null)).current!!
        assertEquals("Free", current.name)
        assertNull(current.status)
        assertFalse(current.statusLoading)
    }

    @Test
    fun activeRenewsOrEndsByAutoRenew() {
        assertEquals(PlanStatusUi.Renews(end), state(plusPlan, sub(SubscriptionStatus.Active)).current!!.status)
        assertEquals(PlanStatusUi.Ends(end), state(plusPlan, sub(SubscriptionStatus.Active, autoRenew = false)).current!!.status)
    }

    @Test
    fun lifetimeHasNoEndDate() {
        assertEquals(PlanStatusUi.NoEnd, state(plusPlan, sub(SubscriptionStatus.Active, periodEnd = null, autoRenew = null)).current!!.status)
    }

    @Test
    fun graceShowsTheGraceEnd() {
        val until = Instant.parse("2026-11-05T00:00:00Z")
        assertEquals(PlanStatusUi.Grace(until), state(plusPlan, sub(SubscriptionStatus.Grace, grace = until)).current!!.status)
    }

    @Test
    fun aSubscriptionOfAnotherPlanHidesTheStatus() {
        assertNull(state(freePlan, sub(SubscriptionStatus.Active)).current!!.status)
    }

    @Test
    fun placeholderUntilTheFirstSubscriptionAnswerSettles() {
        assertTrue(state(freePlan, null, settled = false).current!!.statusLoading)
        assertFalse(state(freePlan, null, settled = true).current!!.statusLoading)
    }

    @Test
    fun aFailedRefreshMarksStaleAndASuccessClearsIt() {
        val failed = reduce(PlanState(), PlanChange.RefreshFinished(RefreshOutcome.Offline, RefreshOutcome.Done))
        assertTrue(failed.stale)
        assertFalse(failed.refreshing)
        assertFalse(reduce(failed, PlanChange.RefreshFinished(RefreshOutcome.Done, RefreshOutcome.Done)).stale)
        assertTrue(reduce(PlanState(), PlanChange.RefreshFinished(RefreshOutcome.Failed, RefreshOutcome.Done)).stale)
    }

    private val catalogue = listOf(
        CataloguePlan("free-id", "Free", BillingPeriod.Lifetime, isDefault = true, features = free, playProductId = null),
        CataloguePlan("plus-id", "Plus · Monthly", BillingPeriod.Monthly, isDefault = false, features = plus, playProductId = "plus_monthly"),
        CataloguePlan("plus-y", "Plus · Yearly", BillingPeriod.Yearly, isDefault = false, features = plus, playProductId = "plus_yearly"),
    )

    @Test
    fun catalogueKeepsOrderMarksTheCurrentPlanAndHidesTheDefaultPeriod() {
        val s = reduce(state(freePlan, null), PlanChange.Catalogue(catalogue))
        assertEquals(CatalogueStatus.Loaded, s.catalogueStatus)
        assertEquals(listOf("Free", "Plus · Monthly", "Plus · Yearly"), s.cards.map { it.name })
        assertEquals(listOf(true, false, false), s.cards.map { it.isCurrent })
        assertNull(s.cards[0].period)
        assertEquals(BillingPeriod.Monthly, s.cards[1].period)
        assertNull(s.cards[1].price, "no Google Play answer, no price (FR-037)")
        assertNull(s.cards[2].price)
    }

    @Test
    fun aRetiredCurrentPlanMarksNoCard() {
        val s = reduce(state(CurrentPlan("retired", "Old", free), null), PlanChange.Catalogue(catalogue))
        assertEquals(listOf(false, false, false), s.cards.map { it.isCurrent })
    }

    @Test
    fun neverLoadedAndUnavailableShowsUnavailableButACachedOneStays() {
        assertEquals(CatalogueStatus.Unavailable, reduce(PlanState(), PlanChange.CatalogueUnavailable).catalogueStatus)
        val cached = reduce(PlanState(), PlanChange.Catalogue(catalogue))
        assertEquals(CatalogueStatus.Loaded, reduce(cached, PlanChange.CatalogueUnavailable).catalogueStatus)
        assertEquals(3, reduce(cached, PlanChange.RefreshStarted).cards.size, "cached cards stay while reloading")
    }

    @Test
    fun anEmptyStoredCatalogueDoesNotMarkItLoaded() {
        assertEquals(CatalogueStatus.Loading, reduce(PlanState(), PlanChange.Catalogue(null)).catalogueStatus)
    }

    @Test
    fun capabilityLinesFollowTheEntitlements() {
        assertEquals(capabilityLines(plus), state(plusPlan, null).current!!.capabilities)
        assertEquals(FeatureValue.Quota(null), plus.features["wallets.max"])
    }
}
