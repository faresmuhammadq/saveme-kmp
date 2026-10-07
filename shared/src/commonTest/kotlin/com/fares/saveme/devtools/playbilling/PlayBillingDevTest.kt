package com.fares.saveme.devtools.playbilling

import app.cash.turbine.test
import com.fares.saveme.core.presentation.runMviTest
import com.fares.saveme.feature.billing.data.play.PlayConnection
import com.fares.saveme.feature.billing.data.play.PlayPurchaseState
import com.fares.saveme.feature.billing.data.play.PlayPurchaseUpdate
import com.fares.saveme.feature.billing.data.play.PlayResult
import com.fares.saveme.feature.billing.data.play.PlayUnavailable
import com.fares.saveme.feature.billing.data.play.UnavailablePlayBillingClient
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class PlayBillingDevReducerTest {

    @Test
    fun heldStateAndFieldsReduce() {
        var state = PlayBillingDevState()
        state = reduce(state, PlayBillingDevChange.Held(enabled = true, unavailable = true, purchaseCount = 2))
        assertTrue(state.enabled && state.unavailable)
        assertEquals(2, state.purchaseCount)
        state = reduce(state, PlayBillingDevChange.PurchaseId("  e2e-us1-1  "))
        assertEquals("e2e-us1-1", state.purchaseId)
        state = reduce(state, PlayBillingDevChange.Price("14x9,0"))
        assertEquals("1490", state.priceMicros)
        assertTrue(state.canAdd)
        assertTrue(state.canSetPrice)
    }

    @Test
    fun addAndPriceNeedTheirInputs() {
        assertFalse(PlayBillingDevState().canAdd)
        assertFalse(PlayBillingDevState(purchaseId = "t", productId = "").canAdd)
        assertFalse(PlayBillingDevState(priceMicros = "").canSetPrice)
        assertFalse(PlayBillingDevState(priceMicros = "0").canSetPrice)
    }
}

class PlayBillingDevViewModelTest {

    private val client = SwitchingPlayBillingClient(UnavailablePlayBillingClient(platformSupported = true), ScriptedPlayBillingClient())

    @Test
    fun theRealClientAnswersUntilTheScriptedOneIsSwitchedOn() = runTest {
        assertIs<PlayConnection.Unavailable>(client.connect())
        client.setScriptedEnabled(true)
        assertEquals(PlayConnection.Ready, client.connect())
        client.setScriptedEnabled(false)
        assertEquals(PlayConnection.Unavailable(PlayUnavailable.NotWired), client.connect())
        assertTrue(client.platformSupported)
    }

    @Test
    fun addingAPurchaseListsItAndFlippingPendingToPurchasedReplacesIt() = runMviTest {
        val vm = PlayBillingDevViewModel(client)
        vm.onIntent(PlayBillingDevIntent.SetEnabled(true))
        vm.onIntent(PlayBillingDevIntent.PurchaseIdChanged("e2e-1"))
        vm.onIntent(PlayBillingDevIntent.PendingChanged(true))
        vm.onIntent(PlayBillingDevIntent.AddPurchase)
        assertEquals(1, vm.state.value.purchaseCount)
        val pending = (client.purchases() as PlayResult.Ok).value.single()
        assertEquals(PlayPurchaseState.Pending, pending.state)
        assertFalse(pending.acknowledged)

        vm.onIntent(PlayBillingDevIntent.PendingChanged(false))
        vm.onIntent(PlayBillingDevIntent.AddPurchase)
        assertEquals(1, vm.state.value.purchaseCount, "the same id replaces its entry")
        assertEquals(PlayPurchaseState.Purchased, (client.purchases() as PlayResult.Ok).value.single().state)

        vm.onIntent(PlayBillingDevIntent.ClearPurchases)
        assertEquals(0, vm.state.value.purchaseCount)
    }

    @Test
    fun anEmptyIdAddsNothing() = runMviTest {
        val vm = PlayBillingDevViewModel(client)
        vm.onIntent(PlayBillingDevIntent.AddPurchase)
        assertEquals(0, vm.state.value.purchaseCount)
    }

    @Test
    fun unavailableMakesTheScriptedPortRefuse() = runMviTest {
        val vm = PlayBillingDevViewModel(client)
        vm.onIntent(PlayBillingDevIntent.SetEnabled(true))
        vm.onIntent(PlayBillingDevIntent.SetUnavailable(true))
        assertTrue(vm.state.value.unavailable)
        assertIs<PlayConnection.Unavailable>(client.connect())
        assertIs<PlayResult.Failed>(client.purchases())
    }

    @Test
    fun aPriceIsServedForAskedProductsOnly() = runMviTest {
        val vm = PlayBillingDevViewModel(client)
        vm.onIntent(PlayBillingDevIntent.SetEnabled(true))
        vm.onIntent(PlayBillingDevIntent.ProductIdChanged("plus_monthly"))
        vm.onIntent(PlayBillingDevIntent.PriceChanged("149000000"))
        vm.onIntent(PlayBillingDevIntent.SetPrice)
        val answer = (client.productDetails(setOf("plus_monthly", "plus_yearly")) as PlayResult.Ok).value
        assertEquals(listOf("plus_monthly"), answer.map { it.productId })
        assertEquals(149_000_000L, answer.single().priceMicros)
    }

    @Test
    fun emitDeliversAPurchaseUpdateOnThePortsFlow() = runMviTest {
        val vm = PlayBillingDevViewModel(client)
        client.purchaseUpdates.test {
            vm.onIntent(PlayBillingDevIntent.EmitUpdate)
            assertIs<PlayPurchaseUpdate.Purchased>(awaitItem())
        }
    }

    @Test
    fun backNavigatesBack() = runMviTest {
        val vm = PlayBillingDevViewModel(client)
        vm.effects.test {
            vm.onIntent(PlayBillingDevIntent.Back)
            assertEquals(PlayBillingDevEffect.NavigateBack, awaitItem())
        }
    }
}
