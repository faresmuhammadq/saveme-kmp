package com.fares.saveme.feature.wallets.domain

import com.fares.saveme.core.network.AppJson
import com.fares.saveme.core.sync.ChangeState
import com.fares.saveme.core.sync.PendingChange
import com.fares.saveme.feature.wallets.data.dto.WalletDto
import com.fares.saveme.feature.wallets.data.local.WalletEntity
import com.fares.saveme.feature.wallets.data.local.WalletMemberEntity
import com.fares.saveme.feature.wallets.domain.model.Member
import com.fares.saveme.feature.wallets.domain.model.RefusalKind
import com.fares.saveme.feature.wallets.domain.model.RefusalReason
import com.fares.saveme.feature.wallets.domain.model.SyncState
import com.fares.saveme.feature.wallets.domain.model.Wallet
import com.fares.saveme.feature.wallets.domain.model.WalletDraft
import com.fares.saveme.feature.wallets.domain.model.WalletField
import com.fares.saveme.feature.wallets.domain.model.WalletRole
import com.fares.saveme.feature.wallets.domain.model.WalletView
import kotlin.time.Instant

internal fun WalletEntity.toWallet() = Wallet(
    id = id, typeWire = type, name = name, currency = currency, openingBalanceMinor = openingBalanceMinor,
    balanceMinor = balanceMinor, icon = icon, color = color,
    archivedAt = archivedAt?.let { runCatching { Instant.parse(it) }.getOrNull() },
    currencyLocked = currencyLocked, myRole = WalletRole.fromWire(myRole), version = version,
    createdAt = runCatching { Instant.parse(createdAt) }.getOrDefault(Instant.fromEpochSeconds(0)),
)

internal fun WalletDto.toWallet() = Wallet(
    id = id, typeWire = type, name = name, currency = currency, openingBalanceMinor = openingBalanceMinor,
    balanceMinor = balanceMinor, icon = icon, color = color,
    archivedAt = archivedAt?.let { runCatching { Instant.parse(it) }.getOrNull() },
    currencyLocked = currencyLocked, myRole = WalletRole.fromWire(myRole), version = version,
    createdAt = runCatching { Instant.parse(createdAt) }.getOrDefault(Instant.fromEpochSeconds(0)),
)

internal fun WalletMemberEntity.toMember(currentUserId: String?) =
    Member(memberUserId, displayName, WalletRole.fromWire(role), isYou = memberUserId == currentUserId)

/** Maps the queue row (research R-07, R-08) to what the screens show. */
internal fun PendingChange?.toSyncState(): SyncState = when (this?.state) {
    null -> SyncState.None
    ChangeState.Waiting, ChangeState.Sending -> SyncState.Waiting
    ChangeState.Clash -> {
        val theirs = clashJson?.let { runCatching { AppJson.decodeFromString(WalletDto.serializer(), it) }.getOrNull() }
        if (theirs == null || theirs.deletedAt != null) SyncState.Clash(null, deletedElsewhere = true)
        else SyncState.Clash(theirs.toWallet(), deletedElsewhere = false)
    }
    ChangeState.Refused -> {
        val refusal = refusal
        SyncState.Refused(
            reason = RefusalReason(
                kind = when (refusal?.code) {
                    "EMAIL_NOT_VERIFIED" -> RefusalKind.NotVerified
                    "FEATURE_NOT_ENTITLED" -> RefusalKind.NotEntitled
                    "FORBIDDEN" -> RefusalKind.NotOwner
                    "NOT_FOUND" -> RefusalKind.NoLongerAvailable
                    "VALIDATION_FAILED" -> RefusalKind.Invalid
                    else -> RefusalKind.Other
                },
                requestId = refusal?.requestId,
            ),
            fields = refusal?.fields.orEmpty().mapNotNull { (name, code) ->
                WalletField.entries.firstOrNull { it.name == name }?.let { it to code }
            }.toMap(),
        )
    }
}

internal fun PendingChange?.pendingDraft(): WalletDraft? =
    this?.takeIf { !isDelete }?.let { runCatching { AppJson.decodeFromString(WalletDraft.serializer(), payloadJson) }.getOrNull() }

internal fun buildView(row: WalletEntity, change: PendingChange?, postingsDelta: Long, hasPostings: Boolean): WalletView {
    val server = row.toWallet()
    val pending = change.pendingDraft()
    return WalletView(
        server = server,
        pending = pending,
        pendingDelete = change?.isDelete == true,
        syncState = change.toSyncState(),
        displayedBalanceMinor = displayedBalance(server, pending, postingsDelta),
        hasPendingPostings = hasPostings,
    )
}
