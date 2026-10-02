package com.fares.saveme.feature.wallets.data

import com.fares.saveme.core.network.AppJson
import com.fares.saveme.core.sync.FeedApplier
import com.fares.saveme.core.sync.ScopeKind
import com.fares.saveme.core.sync.SyncScope
import com.fares.saveme.feature.wallets.data.dto.WalletDto
import com.fares.saveme.feature.wallets.data.dto.WalletMemberDto
import kotlinx.serialization.json.JsonElement

/**
 * The wallet record from its own feed (spec 009 FR-004, FR-005, FR-013): the balance, the currency
 * lock and the person's role come from here; the same version arrives again when only the balance
 * moved, and is applied.
 */
class WalletFeedApplier(private val repository: WalletRepository) : FeedApplier {
    override val scopeKind: ScopeKind = ScopeKind.Wallet
    override val rowType: String = ROW_TYPE

    override suspend fun apply(scope: SyncScope, records: List<JsonElement>) {
        for (record in records) repository.storeFeedCopy(AppJson.decodeFromJsonElement(WalletDto.serializer(), record))
    }

    /** A complete read from the start that never returned the wallet record: the wallet is gone, as a `404` says (FR-011). */
    override suspend fun sweep(scope: SyncScope, seenIds: Set<String>) = repository.sweepWallet(scope.id, seenIds)

    companion object {
        const val ROW_TYPE = "wallet"
    }
}

/** The members of one wallet from its feed; another member's deletion removes only that member (spec 009 FR-005). */
class WalletMemberFeedApplier(private val repository: WalletRepository) : FeedApplier {
    override val scopeKind: ScopeKind = ScopeKind.Wallet
    override val rowType: String = ROW_TYPE

    override suspend fun apply(scope: SyncScope, records: List<JsonElement>) {
        for (record in records) repository.storeMember(AppJson.decodeFromJsonElement(WalletMemberDto.serializer(), record))
    }

    override suspend fun sweep(scope: SyncScope, seenIds: Set<String>) = repository.sweepMembers(scope.id, seenIds)

    companion object {
        const val ROW_TYPE = "wallet_member"
    }
}

/**
 * The person's own memberships from their user feed (backend ADR-0017; spec 009 FR-010, FR-011). A
 * live row is kept, which makes the wallet followed (read from the start when it is new to the
 * phone); an ended one removes the wallet and everything of it from the phone, except a wallet made
 * on this phone and not yet accepted, and the wallet record itself while a change to it waits.
 */
class OwnMembershipFeedApplier(private val repository: WalletRepository) : FeedApplier {
    override val scopeKind: ScopeKind = ScopeKind.User
    override val rowType: String = WalletMemberFeedApplier.ROW_TYPE

    override suspend fun apply(scope: SyncScope, records: List<JsonElement>) {
        for (record in records) {
            val dto = AppJson.decodeFromJsonElement(WalletMemberDto.serializer(), record)
            // The user feed carries only the person's own memberships; anything else is not this applier's to act on.
            if (dto.memberUserId != scope.id) continue
            if (dto.deletedAt != null) repository.endOwnMembership(dto) else repository.storeMember(dto)
        }
    }

    /** A wallet whose own membership was not returned is not removed on that evidence: its own feed decides (FR-011). */
    override suspend fun sweep(scope: SyncScope, seenIds: Set<String>) = Unit
}
