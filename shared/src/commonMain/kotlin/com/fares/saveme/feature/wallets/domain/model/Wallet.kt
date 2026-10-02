package com.fares.saveme.feature.wallets.domain.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.time.Instant

/** The server's copy of a wallet, as last read. Money is `Long` minor units (P-03). */
data class Wallet(
    val id: String,
    /** The wire value, kept so an unknown type is never sent back changed. */
    val typeWire: String,
    val name: String,
    val currency: String,
    val openingBalanceMinor: Long,
    val balanceMinor: Long,
    val icon: String?,
    val color: String?,
    val archivedAt: Instant?,
    val currencyLocked: Boolean,
    val myRole: WalletRole,
    /** 0 = a wallet made on this phone that never reached the server. */
    val version: Long,
    val createdAt: Instant,
) {
    val type: WalletType get() = WalletType.fromWire(typeWire)

    fun toDraft() = WalletDraft(typeWire, name, currency, openingBalanceMinor, icon, color, archive = archivedAt != null)
}

/** The whole writable wallet, as the person edits it and as it waits to sync. */
@Serializable
data class WalletDraft(
    val type: String,
    val name: String,
    val currency: String,
    @SerialName("opening_balance_minor") val openingBalanceMinor: Long,
    val icon: String?,
    val color: String?,
    /** Archive after saving. Archiving is one-way, so a draft never sets this back to false once it is true. */
    val archive: Boolean = false,
) {
    val walletType: WalletType get() = WalletType.fromWire(type)

    /** Whether every writable member equals the stored wallet's, leaving [archive] out. */
    fun sameFieldsAs(wallet: Wallet): Boolean =
        type == wallet.typeWire && name == wallet.name && currency == wallet.currency &&
            openingBalanceMinor == wallet.openingBalanceMinor && icon == wallet.icon && color == wallet.color
}

/** One person in a wallet. The address is never requested or shown (FR-021). */
data class Member(
    val userId: String,
    val displayName: String?,
    val role: WalletRole,
    val isYou: Boolean,
)

data class CurrencyTotal(val currency: String, val totalMinor: Long, val walletCount: Int)
