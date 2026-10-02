package com.fares.saveme.feature.billing.domain

import com.fares.saveme.core.network.AppJson
import com.fares.saveme.feature.billing.INSTAPAY_PRICE
import com.fares.saveme.feature.billing.PLAY_PRICE
import com.fares.saveme.feature.billing.PLUS_FEATURES
import com.fares.saveme.feature.billing.data.dto.PlanDto
import com.fares.saveme.feature.billing.domain.model.BillingPeriod
import com.fares.saveme.feature.billing.domain.model.Money
import com.fares.saveme.feature.billing.domain.model.toDomain
import com.fares.saveme.feature.billing.planJson
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CataloguePlanTest {

    private fun plan(period: String, days: Int? = null, prices: String = "[]", features: String = PLUS_FEATURES) =
        AppJson.decodeFromString(PlanDto.serializer(), planJson("p", "Plus", period, prices = prices, features = features, periodDays = days)).toDomain()

    @Test
    fun priceIsTheGooglePlayEntryOnly() {
        assertEquals(Money(14_900, "EGP"), plan("monthly", prices = "[$INSTAPAY_PRICE,$PLAY_PRICE]").price)
        assertNull(plan("monthly", prices = "[$INSTAPAY_PRICE]").price, "InstaPay is never shown")
        assertNull(plan("monthly").price)
    }

    @Test
    fun periodTypesMapAndUnknownIsNoPeriod() {
        assertEquals(BillingPeriod.Monthly, plan("monthly").period)
        assertEquals(BillingPeriod.Yearly, plan("yearly").period)
        assertEquals(BillingPeriod.Lifetime, plan("lifetime").period)
        assertEquals(BillingPeriod.EveryDays(90), plan("custom", days = 90).period)
        assertEquals(BillingPeriod.Unknown, plan("custom").period)
        assertEquals(BillingPeriod.Unknown, plan("fortnightly").period)
    }

    @Test
    fun featuresKeepNullQuotasAndIgnoreUnknownKeys() {
        val features = plan("monthly", features = """{"wallets.max":null,"future.thing":true,"sharing.enabled":true}""").features
        assertEquals(QuotaLimit.Unlimited, features.quota(QuotaFeature.Wallets))
        assertEquals(true, features.can(FlagFeature.SharingEnabled))
    }
}
