package com.fares.saveme.feature.billing.ui.plan

import app.cash.turbine.test
import com.fares.saveme.core.presentation.runMviTest
import com.fares.saveme.feature.auth.domain.model.RefreshOutcome
import com.fares.saveme.feature.billing.EntitlementSetFixtures.free
import com.fares.saveme.feature.billing.EntitlementSetFixtures.plus
import com.fares.saveme.feature.billing.domain.CurrentPlan
import com.fares.saveme.feature.billing.domain.model.BillingPeriod
import com.fares.saveme.feature.billing.domain.model.CataloguePlan
import com.fares.saveme.feature.billing.domain.model.Money
import com.fares.saveme.feature.billing.domain.model.Subscription
import com.fares.saveme.feature.billing.domain.model.SubscriptionStatus
import com.fares.saveme.feature.billing.domain.play.PlanActivated
import com.fares.saveme.feature.billing.domain.play.PlayNotes
import com.fares.saveme.feature.billing.domain.play.PlayStoreLinks
import com.fares.saveme.feature.billing.domain.play.RestoreMessagePart
import com.fares.saveme.feature.billing.domain.play.RestoreOutcome
import com.fares.saveme.feature.billing.domain.play.RestoreResult
import com.fares.saveme.feature.billing.domain.play.RestoreRetry
import com.fares.saveme.feature.billing.domain.play.RestoreSummary
import com.fares.saveme.feature.billing.domain.play.testRetry
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

/** Spec 018: the Plan store's Google Play intents against fake domain lambdas. */
class PlanPlayViewModelTest {

    private val plan = MutableStateFlow<CurrentPlan?>(CurrentPlan("plus-m", "Plus · Monthly", plus))
    private val subscription = MutableStateFlow<Subscription?>(
        Subscription("plus-m", "Plus · Monthly", SubscriptionStatus.Grace, Instant.parse("2026-11-01T00:00:00Z"), Instant.parse("2026-11-05T00:00:00Z"), true, provider = "google_play"),
    )
    private val catalogue = MutableStateFlow<List<CataloguePlan>?>(
        listOf(
            CataloguePlan("free-id", "Free", BillingPeriod.Lifetime, true, free, null),
            CataloguePlan("plus-m", "Plus · Monthly", BillingPeriod.Monthly, false, plus, "plus_monthly"),
        ),
    )
    private val notes = MutableStateFlow(PlayNotes())
    private var passes = 0
    private val priceRequests = mutableListOf<Set<String>>()
    private val restoreCalls = mutableListOf<RestoreRetry?>()
    private var restoreAnswer: RestoreResult = RestoreResult.Done(RestoreSummary(1, mapOf(RestoreOutcome.Linked to 1)))
    private var pendingActivated: PlanActivated? = null
    private var opened = 0
    private var closed = 0

    private fun vm(supported: Boolean = true) = PlanViewModel(
        plan = plan, subscription = subscription, catalogue = catalogue, timeZoneId = MutableStateFlow("Africa/Cairo"),
        loadStored = {}, refresh = { RefreshOutcome.Done }, loadCatalogue = { RefreshOutcome.Done },
        playSupported = supported,
        notes = notes,
        runPurchasePass = { passes++ },
        loadPlayPrices = { ids -> priceRequests += ids; mapOf("plus_monthly" to Money(14_900, "EGP")) },
        restorePurchases = { retry -> restoreCalls += retry; restoreAnswer },
        subscriptionsUrl = { product -> PlayStoreLinks.subscriptions("com.fares.saveme", product) },
        consumeActivated = { pendingActivated.also { pendingActivated = null } },
        screenOpened = { opened++ },
        screenClosed = { closed++ },
    )

    private fun retryToken(): RestoreRetry = testRetry()

    @Test
    fun openingRunsAPurchasePassAndRequestsGooglePlaysPricesOnce() = runMviTest {
        val vm = vm()
        vm.onIntent(PlanIntent.Opened)
        vm.onIntent(PlanIntent.Opened)
        assertEquals(1, passes)
        assertTrue(priceRequests.all { it == setOf("plus_monthly") })
        assertEquals(Money(14_900, "EGP"), vm.state.value.cards[1].price)
        assertEquals(1, opened, "the store tells the notes holder the screen is showing")
    }

    @Test
    fun refreshRunsAnotherPass() = runMviTest {
        val vm = vm()
        vm.onIntent(PlanIntent.Opened)
        vm.onIntent(PlanIntent.Refresh)
        assertEquals(2, passes)
    }

    @Test
    fun noPricesAreRequestedWhereThereIsNoGooglePlay() = runMviTest {
        val vm = vm(supported = false)
        vm.onIntent(PlanIntent.Opened)
        assertTrue(priceRequests.isEmpty())
        assertTrue(vm.state.value.cards.all { it.price == null })
        assertFalse(vm.state.value.playSupported)
    }

    @Test
    fun aChangedProductSetRequestsPricesAgain() = runMviTest {
        val vm = vm()
        vm.onIntent(PlanIntent.Opened)
        val before = priceRequests.size
        catalogue.value = catalogue.value!! + CataloguePlan("plus-y", "Plus · Yearly", BillingPeriod.Yearly, false, plus, "plus_yearly")
        assertEquals(before + 1, priceRequests.size)
        assertEquals(setOf("plus_monthly", "plus_yearly"), priceRequests.last())
    }

    @Test
    fun restoreRunsThenShowsTheCombinedResult() = runMviTest {
        val gate = CompletableDeferred<Unit>()
        restoreAnswer = RestoreResult.Done(RestoreSummary(2, mapOf(RestoreOutcome.Linked to 1, RestoreOutcome.LinkedElsewhere to 1)))
        val vm = PlanViewModel(
            plan = plan, subscription = subscription, catalogue = catalogue, timeZoneId = MutableStateFlow("UTC"),
            loadStored = {}, refresh = { RefreshOutcome.Done }, loadCatalogue = { RefreshOutcome.Done },
            playSupported = true,
            restorePurchases = { gate.await(); restoreAnswer },
        )
        vm.onIntent(PlanIntent.RestorePurchases)
        assertEquals(RestoreUi.Running, vm.state.value.restore)
        vm.onIntent(PlanIntent.RestorePurchases)
        gate.complete(Unit)
        assertEquals(RestoreUi.Result(listOf(RestoreMessagePart.Restored, RestoreMessagePart.AnotherAccount)), vm.state.value.restore)
        vm.onIntent(PlanIntent.DismissRestoreResult)
        assertEquals(RestoreUi.Idle, vm.state.value.restore)
    }

    @Test
    fun anEmptyOutcomeShowsNoMessage() = runMviTest {
        restoreAnswer = RestoreResult.Done(RestoreSummary(1, mapOf(RestoreOutcome.Unknown to 1)))
        val vm = vm()
        vm.onIntent(PlanIntent.RestorePurchases)
        assertEquals(RestoreUi.Idle, vm.state.value.restore)
    }

    @Test
    fun googlePlayUnavailableShowsItsOwnRefusalWithoutRetry() = runMviTest {
        restoreAnswer = RestoreResult.GooglePlayUnavailable
        val vm = vm()
        vm.onIntent(PlanIntent.RestorePurchases)
        assertEquals(RestoreUi.Refused(RestoreRefusal.GooglePlayUnavailable), vm.state.value.restore)
    }

    @Test
    fun aSignedOutRestoreShowsNothing() = runMviTest {
        restoreAnswer = RestoreResult.SignedOut
        val vm = vm()
        vm.onIntent(PlanIntent.RestorePurchases)
        assertEquals(RestoreUi.Idle, vm.state.value.restore)
    }

    @Test
    fun eachRefusalMapsAndTryAgainReusesTheAttempt() = runMviTest {
        val retry = retryToken()
        val cases = listOf(
            RestoreResult.Offline(retry) to RestoreUi.Refused(RestoreRefusal.Offline),
            RestoreResult.RateLimited(600, retry) to RestoreUi.Refused(RestoreRefusal.RateLimited, retryAfterSeconds = 600),
            RestoreResult.ServiceUnavailable(retry) to RestoreUi.Refused(RestoreRefusal.Busy),
            RestoreResult.InProgress(retry) to RestoreUi.Refused(RestoreRefusal.Busy),
            RestoreResult.Failed("req-9", retry) to RestoreUi.Refused(RestoreRefusal.Failed, requestId = "req-9"),
        )
        cases.forEach { (answer, expected) ->
            restoreCalls.clear()
            restoreAnswer = answer
            val vm = vm()
            vm.onIntent(PlanIntent.RestorePurchases)
            assertEquals(expected, vm.state.value.restore)
            restoreAnswer = RestoreResult.Done(RestoreSummary(1, mapOf(RestoreOutcome.Linked to 1)))
            vm.onIntent(PlanIntent.RetryRestore)
            assertEquals(listOf(null, retry), restoreCalls, "Try again carries the refused attempt")
            assertEquals(RestoreUi.Result(listOf(RestoreMessagePart.Restored)), vm.state.value.restore)
        }
    }

    @Test
    fun manageAndFixPaymentOpenGooglePlaysSubscriptionCentreForTheProduct() = runMviTest {
        val vm = vm()
        vm.effects.test {
            vm.onIntent(PlanIntent.OpenManage)
            assertEquals(
                PlanEffect.OpenUrl("https://play.google.com/store/account/subscriptions?package=com.fares.saveme&sku=plus_monthly"),
                awaitItem(),
            )
            vm.onIntent(PlanIntent.OpenFixPayment)
            assertEquals(
                PlanEffect.OpenUrl("https://play.google.com/store/account/subscriptions?package=com.fares.saveme&sku=plus_monthly"),
                awaitItem(),
            )
        }
    }

    @Test
    fun fixPaymentForAHoldOpensTheHeldProduct() = runMviTest {
        subscription.value = null
        notes.value = PlayNotes(heldProducts = setOf("plus_monthly"))
        val vm = vm()
        vm.effects.test {
            vm.onIntent(PlanIntent.OpenFixPayment)
            assertEquals("https://play.google.com/store/account/subscriptions?package=com.fares.saveme&sku=plus_monthly", (awaitItem() as PlanEffect.OpenUrl).url)
        }
    }

    @Test
    fun theActivatedNoteBecomesAOneShotEffect() = runMviTest {
        val vm = vm()
        vm.effects.test {
            pendingActivated = PlanActivated("Plus · Monthly")
            notes.value = PlayNotes(activated = PlanActivated("Plus · Monthly"))
            assertEquals(PlanEffect.ShowActivated("Plus · Monthly"), awaitItem())
            assertNull(pendingActivated)
        }
    }

    @Test
    fun noteChangesReachTheState() = runMviTest {
        val vm = vm()
        assertFalse(vm.state.value.showPending)
        notes.value = PlayNotes(pendingProducts = setOf("plus_monthly"))
        assertTrue(vm.state.value.showPending)
        notes.value = PlayNotes()
        assertFalse(vm.state.value.showPending)
    }
}
