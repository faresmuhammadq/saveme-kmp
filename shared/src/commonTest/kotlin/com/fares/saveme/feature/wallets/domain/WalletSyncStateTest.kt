package com.fares.saveme.feature.wallets.domain

import com.fares.saveme.core.sync.ChangeState
import com.fares.saveme.core.sync.PendingChange
import com.fares.saveme.core.sync.Refusal
import com.fares.saveme.core.sync.ScopeKind
import com.fares.saveme.core.sync.SyncScope
import com.fares.saveme.feature.wallets.domain.model.RefusalKind
import com.fares.saveme.feature.wallets.domain.model.SyncState
import com.fares.saveme.feature.wallets.walletJson
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

/** The queue row to what the screens show (research R-07, R-08; FR-016 to FR-018). */
class WalletSyncStateTest {
    private fun change(state: ChangeState, clash: String? = null, refusal: Refusal? = null) = PendingChange(
        "wallet", "w1", "u1", SyncScope(ScopeKind.Wallet, "w1"), 3, "{}", false, false, 0, 1, state, 0, null, clash, refusal,
        Instant.parse("2026-10-02T10:00:00Z"),
    )

    @Test
    fun noRowIsNone() = assertEquals(SyncState.None, (null as PendingChange?).toSyncState())

    @Test
    fun waitingAndSendingAreWaiting() {
        assertEquals(SyncState.Waiting, change(ChangeState.Waiting).toSyncState())
        assertEquals(SyncState.Waiting, change(ChangeState.Sending).toSyncState())
    }

    @Test
    fun aLiveClashCarriesTheOtherDevicesCopy() {
        val state = assertIs<SyncState.Clash>(change(ChangeState.Clash, walletJson(version = 6, name = "Web")).toSyncState())
        assertFalse(state.deletedElsewhere)
        assertEquals("Web", state.theirs!!.name)
        assertEquals(6L, state.theirs!!.version)
    }

    @Test
    fun aTombstoneClashIsDeletedElsewhere() {
        val state = assertIs<SyncState.Clash>(change(ChangeState.Clash, walletJson(version = 7, deletedAt = "2026-10-01T00:00:00Z")).toSyncState())
        assertTrue(state.deletedElsewhere)
        assertNull(state.theirs)
    }

    @Test
    fun everyRefusalCodeHasItsReason() {
        mapOf(
            "EMAIL_NOT_VERIFIED" to RefusalKind.NotVerified, "FEATURE_NOT_ENTITLED" to RefusalKind.NotEntitled,
            "FORBIDDEN" to RefusalKind.NotOwner, "NOT_FOUND" to RefusalKind.NoLongerAvailable,
            "VALIDATION_FAILED" to RefusalKind.Invalid, "INTERNAL" to RefusalKind.Other,
        ).forEach { (code, kind) ->
            val refused = assertIs<SyncState.Refused>(change(ChangeState.Refused, refusal = Refusal(code, requestId = "req-9")).toSyncState())
            assertEquals(kind, refused.reason.kind, code)
            assertEquals("req-9", refused.reason.requestId)
        }
    }
}
