package com.fares.saveme.feature.billing.ui.plan

import app.cash.turbine.test
import com.fares.saveme.core.presentation.runMviTest
import com.fares.saveme.feature.auth.domain.model.RefreshOutcome
import com.fares.saveme.feature.billing.EntitlementSetFixtures.free
import com.fares.saveme.feature.billing.domain.CurrentPlan
import com.fares.saveme.feature.billing.domain.model.BillingPeriod
import com.fares.saveme.feature.billing.domain.model.CataloguePlan
import com.fares.saveme.feature.billing.domain.model.Subscription
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PlanViewModelTest {

    private val plan = MutableStateFlow<CurrentPlan?>(CurrentPlan("free-id", "Free", free))
    private val subscription = MutableStateFlow<Subscription?>(null)
    private val catalogue = MutableStateFlow<List<CataloguePlan>?>(null)
    private val zone = MutableStateFlow("Africa/Cairo")
    private val order = mutableListOf<String>()
    private var refreshOutcome = RefreshOutcome.Done
    private var catalogueOutcome = RefreshOutcome.Done
    private var refreshes = 0

    private fun vm() = PlanViewModel(
        plan = plan, subscription = subscription, catalogue = catalogue, timeZoneId = zone,
        loadStored = { order += "stored" },
        refresh = { order += "refresh"; refreshes++; refreshOutcome },
        loadCatalogue = { order += "catalogue"; catalogueOutcome },
    )

    @Test
    fun cachedEntitlementsRenderBeforeAnyRefreshAndTheZoneIsTheProfiles() = runMviTest {
        val vm = vm()
        assertEquals("Free", vm.state.value.current!!.name)
        assertEquals("Africa/Cairo", vm.state.value.timeZoneId)
        assertEquals(0, refreshes)
    }

    @Test
    fun openedLoadsStoredThenRefreshesOnceAndSettles() = runMviTest {
        val vm = vm()
        vm.onIntent(PlanIntent.Opened)
        vm.onIntent(PlanIntent.Opened)

        assertEquals(listOf("stored", "refresh", "catalogue"), order)
        assertFalse(vm.state.value.refreshing)
        assertTrue(vm.state.value.settled)
        assertFalse(vm.state.value.stale)
    }

    @Test
    fun refreshRunsBothReadsAndClearsRefreshing() = runMviTest {
        val vm = vm()
        vm.onIntent(PlanIntent.Refresh)
        assertEquals(listOf("refresh", "catalogue"), order)
        assertEquals(1, refreshes)
        assertFalse(vm.state.value.refreshing)
        assertTrue(vm.state.value.settled)
    }

    @Test
    fun offlineMarksStaleAndKeepsTheCachedPlan() = runMviTest {
        refreshOutcome = RefreshOutcome.Offline
        catalogueOutcome = RefreshOutcome.Offline
        val vm = vm()
        vm.onIntent(PlanIntent.Opened)
        assertTrue(vm.state.value.stale)
        assertEquals("Free", vm.state.value.current!!.name)
        assertEquals(CatalogueStatus.Unavailable, vm.state.value.catalogueStatus)
    }

    @Test
    fun aRetryAfterUnavailableLoadsTheCatalogue() = runMviTest {
        catalogueOutcome = RefreshOutcome.Offline
        val vm = vm()
        vm.onIntent(PlanIntent.Opened)
        catalogueOutcome = RefreshOutcome.Done
        catalogue.value = listOf(CataloguePlan("free-id", "Free", BillingPeriod.Lifetime, true, free, null))
        vm.onIntent(PlanIntent.Refresh)
        assertEquals(CatalogueStatus.Loaded, vm.state.value.catalogueStatus)
        assertEquals(2, order.count { it == "catalogue" })
    }

    @Test
    fun backNavigatesBack() = runMviTest {
        val vm = vm()
        vm.effects.test {
            vm.onIntent(PlanIntent.Back)
            assertEquals(PlanEffect.NavigateBack, awaitItem())
        }
    }

    @Test
    fun enterCodeOpensTheRedeemScreen() = runMviTest {
        val vm = vm()
        vm.effects.test {
            vm.onIntent(PlanIntent.EnterCode)
            assertEquals(PlanEffect.OpenRedeem, awaitItem())
        }
    }
}
