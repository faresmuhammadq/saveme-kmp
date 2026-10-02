package com.fares.saveme.feature.transactions.domain

import com.fares.saveme.core.sync.ChangeState
import com.fares.saveme.core.sync.PendingChange
import com.fares.saveme.core.sync.Refusal
import com.fares.saveme.core.sync.ScopeKind
import com.fares.saveme.core.sync.SyncScope
import com.fares.saveme.feature.transactions.domain.model.RefusalKind
import com.fares.saveme.feature.transactions.domain.model.TransactionField
import com.fares.saveme.feature.transactions.domain.model.TransactionSyncState
import com.fares.saveme.feature.transactions.transactionJson
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

/** The queue row as the screens read it (FR-023 to FR-025, research R-07). */
class TransactionSyncStateTest {

    private fun change(
        state: ChangeState,
        clash: String? = null,
        refusal: Refusal? = null,
        delete: Boolean = false,
    ) = PendingChange(
        "transaction", "t1", "u1", SyncScope(ScopeKind.Wallet, "w1"), 2, "{}", delete, false, 0, 1, state, 0, null, clash, refusal,
        Instant.parse("2026-10-02T10:00:00Z"),
    )

    private fun refused(code: String, kind: String = "expense", vararg fields: Pair<String, String>) =
        change(ChangeState.Refused, refusal = Refusal(code, fields.toMap(), "req-9")).toSyncState(kind)

    @Test
    fun noRowMeansNoStateAndWaitingAndSendingBothMeanWaiting() {
        assertEquals(TransactionSyncState.None, (null as PendingChange?).toSyncState("expense"))
        assertEquals(TransactionSyncState.Waiting, change(ChangeState.Waiting).toSyncState("expense"))
        assertEquals(TransactionSyncState.Waiting, change(ChangeState.Sending).toSyncState("expense"))
    }

    @Test
    fun aLiveClashCarriesTheOtherDevicesCopyAndWhetherMineIsADeletion() {
        val edit = assertIs<TransactionSyncState.Clash>(change(ChangeState.Clash, clash = transactionJson(version = 6, amount = 9_000)).toSyncState("expense"))
        assertEquals(9_000, edit.theirs!!.amountMinor)
        assertEquals(false, edit.deletedElsewhere)
        assertEquals(false, edit.mineIsDeletion)
        val delete = assertIs<TransactionSyncState.Clash>(change(ChangeState.Clash, clash = transactionJson(version = 6), delete = true).toSyncState("expense"))
        assertTrue(delete.mineIsDeletion)
    }

    @Test
    fun aTombstoneClashIsDeletedElsewhere() {
        val clash = assertIs<TransactionSyncState.Clash>(
            change(ChangeState.Clash, clash = transactionJson(version = 6, deletedAt = "2026-10-01T00:00:00Z")).toSyncState("expense"),
        )
        assertTrue(clash.deletedElsewhere)
        assertNull(clash.theirs)
    }

    @Test
    fun everyRefusalReasonComesFromTheCodeAndThePointerNeverTheText() {
        fun kind(state: TransactionSyncState) = assertIs<TransactionSyncState.Refused>(state).reason.kind
        assertEquals(RefusalKind.NotVerified, kind(refused("EMAIL_NOT_VERIFIED")))
        assertEquals(RefusalKind.NoLongerAvailable, kind(refused("NOT_FOUND")))
        assertEquals(RefusalKind.NoRightHere, kind(refused("FORBIDDEN", "expense")))
        assertEquals(RefusalKind.NoRightHere, kind(refused("FORBIDDEN", "income")))
        assertEquals(RefusalKind.NoRightInOtherWallet, kind(refused("FORBIDDEN", "transfer")))
        assertEquals(RefusalKind.CategoryUnusable, kind(refused("VALIDATION_FAILED", "expense", "category" to "INVALID_ENUM_VALUE")))
        assertEquals(RefusalKind.DestinationUnavailable, kind(refused("VALIDATION_FAILED", "transfer", "destination" to "INVALID_ENUM_VALUE")))
        assertEquals(RefusalKind.DestinationUnavailable, kind(refused("VALIDATION_FAILED", "transfer", "destination" to "REQUIRED")))
        assertEquals(RefusalKind.CurrencyChanged, kind(refused("VALIDATION_FAILED", "expense", "currency" to "CURRENCY_MISMATCH")))
        assertEquals(RefusalKind.CurrenciesDiffer, kind(refused("VALIDATION_FAILED", "transfer", "destination" to "CURRENCY_MISMATCH")))
        assertEquals(RefusalKind.Invalid, kind(refused("VALIDATION_FAILED", "expense", "amount" to "OUT_OF_RANGE")))
        assertEquals(RefusalKind.Other, kind(refused("INTERNAL_ERROR")))
    }

    @Test
    fun theRequestIdAndTheFieldMapAreKept() {
        val state = assertIs<TransactionSyncState.Refused>(refused("VALIDATION_FAILED", "expense", "amount" to "OUT_OF_RANGE", "note" to "OUT_OF_RANGE", "bogus" to "X"))
        assertEquals("req-9", state.reason.requestId)
        assertEquals(mapOf(TransactionField.Amount to "OUT_OF_RANGE", TransactionField.Note to "OUT_OF_RANGE"), state.fields)
        assertTrue(assertIs<TransactionSyncState.Refused>(refused("EMAIL_NOT_VERIFIED")).reason.canRetryUnchanged)
        assertEquals(false, assertIs<TransactionSyncState.Refused>(refused("FORBIDDEN")).reason.canRetryUnchanged)
    }
}
