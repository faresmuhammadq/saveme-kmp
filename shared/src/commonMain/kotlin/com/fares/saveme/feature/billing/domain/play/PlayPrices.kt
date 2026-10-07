package com.fares.saveme.feature.billing.domain.play

import com.fares.saveme.designsystem.money.MoneyFormatter
import com.fares.saveme.feature.billing.data.play.PlayBillingClient
import com.fares.saveme.feature.billing.data.play.PlayResult
import com.fares.saveme.feature.billing.domain.model.Money
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

object PlayPrices {
    private const val MICROS_DIGITS = 6

    /**
     * Google Play's micro-units to integer minor units of [currency] (P-03): exact only, no floating point. Null when the amount
     * is not positive, has a finer fraction than the currency allows, or the currency has more than six digits.
     */
    fun toMoney(priceMicros: Long, currency: String): Money? {
        if (priceMicros <= 0 || currency.isBlank()) return null
        val digits = MoneyFormatter.digitsFor(currency)
        if (digits > MICROS_DIGITS) return null
        var divisor = 1L
        repeat(MICROS_DIGITS - digits) { divisor *= 10 }
        if (priceMicros % divisor != 0L) return null
        return Money(priceMicros / divisor, currency.uppercase())
    }
}

/**
 * Google Play's prices for the plans the catalogue sells there (FR-036–FR-038): requested when the Plan screen opens and when the
 * product set changes, at most once a minute, held in memory only and dropped at sign-out. Nothing shows until Google Play answers.
 */
class PlayPriceLoader(
    private val port: PlayBillingClient,
    private val clock: Clock = Clock.System,
) {
    private val lock = Mutex()
    private var cachedFor: Set<String> = emptySet()
    private var cachedAt: Instant? = null
    private var cached: Map<String, Money> = emptyMap()

    suspend fun load(productIds: Set<String>): Map<String, Money> {
        if (productIds.isEmpty()) return emptyMap()
        return lock.withLock {
            val at = cachedAt
            if (at != null && cachedFor == productIds && clock.now() - at < 1.minutes) return@withLock cached
            when (val result = port.productDetails(productIds)) {
                is PlayResult.Failed -> emptyMap()
                is PlayResult.Ok -> result.value
                    .mapNotNull { product -> PlayPrices.toMoney(product.priceMicros, product.currency)?.let { product.productId to it } }
                    .toMap()
                    .also {
                        cached = it
                        cachedFor = productIds
                        cachedAt = clock.now()
                    }
            }
        }
    }

    fun clear() {
        cached = emptyMap()
        cachedFor = emptySet()
        cachedAt = null
    }
}
