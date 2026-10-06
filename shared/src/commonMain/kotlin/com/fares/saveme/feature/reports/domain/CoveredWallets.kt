package com.fares.saveme.feature.reports.domain

import com.fares.saveme.feature.reports.domain.model.Covered
import com.fares.saveme.feature.reports.domain.model.ExcludedWallet
import com.fares.saveme.feature.reports.domain.model.ReportWallet

/** Which wallets a report covers and which it names as not included (FR-004, FR-013, FR-014). Pure. */
object CoveredWallets {
    /** The currency used when the phone knows neither a profile default nor a wallet. */
    const val FALLBACK_CURRENCY = "EGP"

    /**
     * A cross-wallet report covers every wallet on the phone in [currency] (archived included, any role, a wallet made on
     * this phone and not yet synced included); every other wallet is excluded, sorted by currency then name.
     */
    fun forCurrency(wallets: List<ReportWallet>, currency: String): Covered = Covered(
        wallets = wallets.filter { it.currency == currency },
        excluded = wallets.filter { it.currency != currency }
            .sortedWith(compareBy<ReportWallet> { it.currency }.thenBy { it.name }.thenBy { it.id })
            .map { ExcludedWallet(it.id, it.name, it.currency) },
        currency = currency,
    )

    /** A wallet report covers the one wallet, in its own currency, and excludes nothing. */
    fun forWallet(wallet: ReportWallet): Covered = Covered(listOf(wallet), emptyList(), wallet.currency)

    /** The currencies a person can choose: those of their wallets and the profile default, sorted. */
    fun currencies(wallets: List<ReportWallet>, profileDefault: String?): List<String> =
        (wallets.map { it.currency } + listOfNotNull(profileDefault)).distinct().sorted()

    fun defaultCurrency(profileDefault: String?): String = profileDefault ?: FALLBACK_CURRENCY
}
