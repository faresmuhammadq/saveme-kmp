package com.fares.saveme.feature.categories.domain

import com.fares.saveme.core.sync.ChangeState
import com.fares.saveme.core.sync.PendingChange
import com.fares.saveme.core.sync.Refusal
import com.fares.saveme.core.sync.ScopeKind
import com.fares.saveme.core.sync.SyncScope
import com.fares.saveme.feature.categories.categoryJson
import com.fares.saveme.feature.categories.domain.model.CategoryField
import com.fares.saveme.feature.categories.domain.model.RefusalReason
import com.fares.saveme.feature.categories.domain.model.SyncState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

/** The queue row to what the screens show (research R-07; FR-012 to FR-014). */
class CategorySyncStateTest {
    private fun change(state: ChangeState, clash: String? = null, refusal: Refusal? = null) = PendingChange(
        "category", "c1", "u1", SyncScope(ScopeKind.User, "u1"), 3, "{}", false, false, 0, 1, state, 0, null, clash, refusal,
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
        val state = assertIs<SyncState.Clash>(change(ChangeState.Clash, categoryJson(version = 6, name = "Web")).toSyncState())
        assertFalse(state.deletedElsewhere)
        assertEquals("Web", state.theirs!!.name)
        assertEquals(6L, state.theirs!!.version)
    }

    @Test
    fun aTombstoneClashIsDeletedElsewhere() {
        val state = assertIs<SyncState.Clash>(change(ChangeState.Clash, categoryJson(version = 7, deletedAt = "2026-10-01T00:00:00Z")).toSyncState())
        assertTrue(state.deletedElsewhere)
        assertNull(state.theirs)
    }

    @Test
    fun everyRefusalCodeHasItsReasonAndKeepsTheRequestId() {
        mapOf(
            "EMAIL_NOT_VERIFIED" to RefusalReason.NotVerified, "NOT_FOUND" to RefusalReason.NoLongerAvailable,
            "CATEGORY_IN_USE" to RefusalReason.InUse, "VALIDATION_FAILED" to RefusalReason.Invalid,
            "FORBIDDEN" to RefusalReason.Other("req-9"), "INTERNAL" to RefusalReason.Other("req-9"),
        ).forEach { (code, reason) ->
            val refused = assertIs<SyncState.Refused>(change(ChangeState.Refused, refusal = Refusal(code, requestId = "req-9")).toSyncState())
            assertEquals(reason, refused.reason, code)
        }
    }

    @Test
    fun theFieldMapKeepsOnlyKnownFields() {
        val refused = assertIs<SyncState.Refused>(
            change(ChangeState.Refused, refusal = Refusal("VALIDATION_FAILED", mapOf("name" to "OUT_OF_RANGE", "kind" to "IMMUTABLE_FIELD", "other" to "X"))).toSyncState(),
        )
        assertEquals(mapOf(CategoryField.Name to "OUT_OF_RANGE", CategoryField.Kind to "IMMUTABLE_FIELD"), refused.fields)
        assertTrue(RefusalReason.NotVerified.canRetryUnchanged)
        assertFalse(RefusalReason.InUse.canRetryUnchanged)
    }
}
