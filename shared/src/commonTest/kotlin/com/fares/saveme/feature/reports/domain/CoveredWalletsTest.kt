package com.fares.saveme.feature.reports.domain

import com.fares.saveme.feature.reports.reportWallet
import kotlin.test.Test
import kotlin.test.assertEquals

/** FR-004, FR-013, FR-014. */
class CoveredWalletsTest {

    private val wallets = listOf(
        reportWallet("cash", "Cash", "EGP"),
        reportWallet("old", "Old", "EGP", archived = true),
        reportWallet("savings", "Savings", "USD"),
        reportWallet("trip", "Trip", "KWD"),
        reportWallet("abroad", "Abroad", "USD"),
        reportWallet("new", "New", "EGP", waiting = true),
    )

    @Test
    fun aCrossWalletReportCoversEveryWalletInTheCurrencyArchivedAndWaitingIncluded() {
        val covered = CoveredWallets.forCurrency(wallets, "EGP")
        assertEquals(setOf("cash", "old", "new"), covered.walletIds)
        assertEquals("EGP", covered.currency)
    }

    @Test
    fun otherWalletsAreNamedSortedByCurrencyThenName() {
        val covered = CoveredWallets.forCurrency(wallets, "EGP")
        assertEquals(listOf("KWD" to "Trip", "USD" to "Abroad", "USD" to "Savings"), covered.excluded.map { it.currency to it.name })
    }

    @Test
    fun noWalletInTheCurrencyIsAnEmptyCoveredSetAndEveryWalletExcluded() {
        val covered = CoveredWallets.forCurrency(wallets, "EUR")
        assertEquals(emptySet(), covered.walletIds)
        assertEquals(wallets.size, covered.excluded.size)
    }

    @Test
    fun aWalletReportCoversThatWalletInItsOwnCurrencyAndExcludesNothing() {
        val covered = CoveredWallets.forWallet(wallets[2])
        assertEquals(setOf("savings"), covered.walletIds)
        assertEquals("USD", covered.currency)
        assertEquals(emptyList(), covered.excluded)
    }

    @Test
    fun theCurrenciesAreTheWalletsPlusTheProfileDefaultSorted() {
        assertEquals(listOf("EGP", "EUR", "KWD", "USD"), CoveredWallets.currencies(wallets, "EUR"))
        assertEquals(listOf("EGP", "KWD", "USD"), CoveredWallets.currencies(wallets, null))
    }

    @Test
    fun theDefaultCurrencyIsTheProfilesElseEgp() {
        assertEquals("USD", CoveredWallets.defaultCurrency("USD"))
        assertEquals("EGP", CoveredWallets.defaultCurrency(null))
    }
}
