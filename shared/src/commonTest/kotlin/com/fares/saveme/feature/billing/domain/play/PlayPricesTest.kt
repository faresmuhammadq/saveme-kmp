package com.fares.saveme.feature.billing.domain.play

import com.fares.saveme.feature.billing.data.play.PlayError
import com.fares.saveme.feature.billing.data.play.PlayProduct
import com.fares.saveme.feature.billing.data.play.PlayProductKind
import com.fares.saveme.feature.billing.data.play.PlayResult
import com.fares.saveme.feature.billing.domain.model.Money
import com.fares.saveme.feature.billing.play.FakePlayBillingClient
import com.fares.saveme.feature.billing.play.SchedulerClock
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.seconds

class PlayPricesTest {

    @Test
    fun microsConvertToMinorUnitsByTheCurrencysDigits() {
        assertEquals(Money(14_900, "EGP"), PlayPrices.toMoney(149_000_000, "EGP"))
        assertEquals(Money(1_490, "KWD"), PlayPrices.toMoney(1_490_000, "KWD"))
        assertEquals(Money(1_500, "JPY"), PlayPrices.toMoney(1_500_000_000, "JPY"))
        assertEquals(Money(14_990, "EGP"), PlayPrices.toMoney(149_900_000, "egp"))
    }

    /** US5 AS3: Google Play's 149.00 EGP reads the way the app writes money, in both languages, with Western digits. */
    @Test
    fun googlePlaysPriceIsWrittenByTheAppsFormatterInEnglishAndArabicWithWesternDigits() {
        val money = PlayPrices.toMoney(149_000_000, "EGP")!!
        assertEquals("EGP 149.00", com.fares.saveme.designsystem.money.MoneyFormatter.format(money.amountMinor, money.currency, "en"))
        assertEquals("\u2066149.00 \u062C.\u0645\u2069", com.fares.saveme.designsystem.money.MoneyFormatter.format(money.amountMinor, money.currency, "ar"))
    }

    @Test
    fun anInexactOrNonPositivePriceHasNoMoney() {
        assertNull(PlayPrices.toMoney(149_005_000, "EGP"), "finer than the currency's fraction")
        assertNull(PlayPrices.toMoney(1_490_001, "KWD"))
        assertNull(PlayPrices.toMoney(0, "EGP"))
        assertNull(PlayPrices.toMoney(-1_000_000, "EGP"))
        assertNull(PlayPrices.toMoney(1_000_000, ""))
    }

    @Test
    fun theExactIntegerHoldsAtTheTopOfTheRange() {
        // 9,000,000,000,000 micro-units (9 million units): no floating point is involved.
        assertEquals(Money(900_000_000, "EGP"), PlayPrices.toMoney(9_000_000_000_000, "EGP"))
    }

    private fun product(id: String, micros: Long, currency: String = "EGP") = PlayProduct(id, PlayProductKind.Subscription, micros, currency)

    @Test
    fun theLoaderReturnsGooglePlaysPricesAndCachesForAMinute() = runTest {
        val port = FakePlayBillingClient()
        port.productsResult = PlayResult.Ok(listOf(product("plus_monthly", 149_000_000), product("odd", 1_000_001)))
        val clock = SchedulerClock(testScheduler)
        val loader = PlayPriceLoader(port, clock)

        val first = loader.load(setOf("plus_monthly", "odd"))
        assertEquals(mapOf("plus_monthly" to Money(14_900, "EGP")), first)
        loader.load(setOf("plus_monthly", "odd"))
        assertEquals(1, port.productRequests.size, "at most once a minute for the same products")

        clock.skip(61.seconds)
        loader.load(setOf("plus_monthly", "odd"))
        assertEquals(2, port.productRequests.size)

        loader.load(setOf("plus_monthly"))
        assertEquals(3, port.productRequests.size, "a changed product set asks again")
    }

    @Test
    fun noPriceShowsWhenGooglePlayCannotAnswerAndFailuresAreNotCached() = runTest {
        val port = FakePlayBillingClient()
        port.productsResult = PlayResult.Failed(PlayError.BillingUnavailable)
        val loader = PlayPriceLoader(port, SchedulerClock(testScheduler))
        assertEquals(emptyMap(), loader.load(setOf("plus_monthly")))
        port.productsResult = PlayResult.Ok(listOf(product("plus_monthly", 149_000_000)))
        assertEquals(Money(14_900, "EGP"), loader.load(setOf("plus_monthly"))["plus_monthly"])
        assertEquals(emptyMap(), loader.load(emptySet()))
    }

    @Test
    fun clearDropsTheCache() = runTest {
        val port = FakePlayBillingClient()
        port.productsResult = PlayResult.Ok(listOf(product("plus_monthly", 149_000_000)))
        val loader = PlayPriceLoader(port, SchedulerClock(testScheduler))
        loader.load(setOf("plus_monthly"))
        loader.clear()
        loader.load(setOf("plus_monthly"))
        assertEquals(2, port.productRequests.size)
    }
}
