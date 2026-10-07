package com.fares.saveme.feature.billing.play

import com.fares.saveme.feature.billing.data.play.AccountReference
import com.fares.saveme.feature.billing.data.play.PlayConnection
import com.fares.saveme.feature.billing.data.play.PlayError
import com.fares.saveme.feature.billing.data.play.PlayLaunchRequest
import com.fares.saveme.feature.billing.data.play.PlayLaunchResult
import com.fares.saveme.feature.billing.data.play.PlayResult
import com.fares.saveme.feature.billing.data.play.PlayUnavailable
import com.fares.saveme.feature.billing.data.play.UnavailablePlayBillingClient
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** FR-004, FR-055: where no real Google Play adapter is installed the port reports "unavailable" and holds nothing. */
class UnavailablePlayBillingClientTest {

    @Test
    fun androidBeforeTheAdapterIsWiredIsNotWiredAndIosIsNotSupported() = runTest {
        assertEquals(PlayConnection.Unavailable(PlayUnavailable.NotWired), UnavailablePlayBillingClient(platformSupported = true).connect())
        assertEquals(PlayConnection.Unavailable(PlayUnavailable.NotSupported), UnavailablePlayBillingClient(platformSupported = false).connect())
    }

    @Test
    fun itListsNothingPricesNothingAndDeliversNoUpdates() = runTest {
        val client = UnavailablePlayBillingClient(platformSupported = true)
        assertEquals(PlayResult.Failed(PlayError.BillingUnavailable), client.purchases())
        assertEquals(PlayResult.Failed(PlayError.BillingUnavailable), client.productDetails(setOf("plus_monthly")))
        assertTrue(client.purchaseUpdates.toList().isEmpty())
        assertIs<PlayLaunchResult.Failed>(client.launchPurchase(PlayLaunchRequest("plus_monthly", "offer", AccountReference("a".repeat(64)))))
    }
}
