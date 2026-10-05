package com.fares.saveme.feature.sharing.ui

import com.fares.saveme.feature.sharing.domain.model.InvitationChannel
import com.fares.saveme.feature.sharing.domain.model.InvitationPreview
import com.fares.saveme.feature.sharing.domain.model.InvitationRefusal
import com.fares.saveme.feature.sharing.domain.model.InvitationView
import com.fares.saveme.feature.sharing.domain.model.MemberView
import com.fares.saveme.feature.wallets.domain.model.SyncState
import com.fares.saveme.feature.wallets.domain.model.Wallet
import com.fares.saveme.feature.wallets.domain.model.WalletRole
import com.fares.saveme.feature.wallets.domain.model.WalletView
import kotlin.time.Instant

val uiNow: Instant = Instant.parse("2026-10-02T10:00:00Z")

fun uiMember(
    userId: String = "u2",
    name: String? = "Karim",
    role: String = "editor",
    isYou: Boolean = false,
    version: Long = 1,
) = MemberView(userId, name, role, version, isYou)

fun uiInvitation(
    id: String = "i1",
    email: String? = "karim@example.com",
    role: String = "viewer",
    expiresAt: Instant? = Instant.parse("2026-10-09T10:00:00Z"),
    waiting: Boolean = false,
    refusal: InvitationRefusal? = null,
    withdrawalRefused: Boolean = false,
    expired: Boolean = false,
    linkHeld: Boolean = false,
) = InvitationView(
    id = id, walletId = "w1", roleWire = role,
    channel = if (email == null) InvitationChannel.Link else InvitationChannel.Email,
    email = email, expiresAt = expiresAt, waiting = waiting, refusal = refusal, withdrawalRefused = withdrawalRefused,
    expired = expired, linkHeld = linkHeld,
)

fun uiWalletView(role: WalletRole = WalletRole.Owner, name: String = "Home", archived: Boolean = false) = WalletView(
    server = Wallet(
        "w1", "cash", name, "EGP", 0, 0, null, null, if (archived) Instant.parse("2026-09-30T00:00:00Z") else null, false, role, 3,
        Instant.parse("2026-09-01T00:00:00Z"),
    ),
    pending = null, pendingDelete = false, syncState = SyncState.None, displayedBalanceMinor = 0,
)

fun uiPreview(
    walletName: String = "Home",
    inviter: String? = "Mona",
    role: String = "editor",
    already: Boolean = false,
) = InvitationPreview("i1", "w1", walletName, role, inviter, Instant.parse("2026-10-09T10:00:00Z"), already)
