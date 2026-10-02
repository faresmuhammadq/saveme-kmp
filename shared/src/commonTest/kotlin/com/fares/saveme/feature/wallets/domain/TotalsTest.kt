package com.fares.saveme.feature.wallets.domain

import com.fares.saveme.feature.wallets.domain.model.CurrencyTotal
import com.fares.saveme.feature.wallets.domain.model.SyncState
import com.fares.saveme.feature.wallets.domain.model.Wallet
import com.fares.saveme.feature.wallets.domain.model.WalletDraft
import com.fares.saveme.feature.wallets.domain.model.WalletRole
import com.fares.saveme.feature.wallets.domain.model.WalletView
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

class TotalsTest {
    private fun view(
        id: String, currency: String, balance: Long, type: String = "cash", archived: Boolean = false,
        hidden: Boolean = false,
    ): WalletView {
        val wallet = Wallet(
            id, type, id, currency, balance, balance, null, null,
            if (archived) Instant.parse("2026-09-02T00:00:00Z") else null, false, WalletRole.Owner, 1, Instant.parse("2026-09-01T00:00:00Z"),
        )
        return WalletView(wallet, null, pendingDelete = hidden, syncState = if (hidden) SyncState.Waiting else SyncState.None, displayedBalanceMinor = balance)
    }

    @Test
    fun sumsPerCurrencyInLongWithLiabilitiesNegative() {
        val totals = totalsOf(
            listOf(view("a", "EGP", 100_000), view("b", "EGP", -25_000, "credit_card"), view("c", "USD", 5_000)),
            defaultCurrency = "EGP",
        )
        assertEquals(listOf(CurrencyTotal("EGP", 75_000, 2), CurrencyTotal("USD", 5_000, 1)), totals)
    }

    @Test
    fun archivedAndHiddenWalletsAreLeftOut() {
        val totals = totalsOf(
            listOf(view("a", "EGP", 100), view("b", "EGP", 900, archived = true), view("c", "EGP", 70, hidden = true)),
            defaultCurrency = "EGP",
        )
        assertEquals(listOf(CurrencyTotal("EGP", 100, 1)), totals)
    }

    @Test
    fun defaultCurrencyFirstThenByCode() {
        val totals = totalsOf(listOf(view("a", "USD", 1), view("b", "EUR", 1), view("c", "EGP", 1)), defaultCurrency = "USD")
        assertEquals(listOf("USD", "EGP", "EUR"), totals.map { it.currency })
    }

    @Test
    fun noSumAcrossCurrenciesEvenForTinyAmounts() {
        val totals = totalsOf(listOf(view("a", "KWD", 1), view("b", "JPY", 1)), defaultCurrency = null)
        assertEquals(2, totals.size)
        assertEquals(listOf(1L, 1L), totals.map { it.totalMinor })
    }
}

class DisplayedBalanceTest {
    private val server = Wallet("w", "cash", "W", "EGP", 10_000, 15_000, null, null, null, false, WalletRole.Owner, 2, Instant.parse("2026-09-01T00:00:00Z"))

    @Test
    fun serverBalanceWhenNothingWaits() = assertEquals(15_000, displayedBalance(server, null, 0))

    @Test
    fun anOpeningBalanceEditMovesTheBalanceByTheDifference() {
        val pending = WalletDraft("cash", "W", "EGP", 12_500, null, null)
        assertEquals(17_500, displayedBalance(server, pending, 0))
    }

    @Test
    fun unsentPostingsAreAdded() = assertEquals(14_000, displayedBalance(server, null, -1_000))

    @Test
    fun aPlaceholderShowsItsOpeningBalance() {
        val placeholder = server.copy(version = 0, balanceMinor = 10_000)
        assertEquals(10_000, displayedBalance(placeholder, WalletDraft("cash", "W", "EGP", 10_000, null, null), 0))
    }
}
