package com.fares.saveme.feature.sharedbudgets

import com.fares.saveme.core.network.AppJson
import com.fares.saveme.feature.sharedbudgets.data.SharedBudgetPayload
import com.fares.saveme.feature.sharedbudgets.data.dto.SharedBudgetInvitationMetaDto
import com.fares.saveme.feature.sharedbudgets.data.dto.SharedBudgetInvitationPreviewDto
import com.fares.saveme.feature.sharedbudgets.data.dto.SharedBudgetLineWriteRequest
import com.fares.saveme.feature.sharedbudgets.data.dto.SharedBudgetWriteRequest
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** What a Shared Budget sends and reads on the wire (shared-budgets.yaml). */
class SharedBudgetWireTest {
    @Test
    fun theWholeWritableBudgetIsSentAndANullLimitIsExplicit() {
        val json = SharedBudgetWriteRequest("Home", "EGP", 5, null, 3).toJson()
        assertEquals(setOf("name", "currency", "start_day", "limit_minor", "version"), json.keys)
        assertEquals(JsonNull, json["limit_minor"])
        assertEquals(JsonPrimitive(5), json["start_day"])
        assertEquals(JsonPrimitive(3), json["version"])
        assertEquals(JsonPrimitive(2_000_000L), SharedBudgetWriteRequest("Home", "EGP", 1, 2_000_000, 0).toJson()["limit_minor"])
    }

    @Test
    fun aLineSendsItsCategoryAndAnExplicitNullLimit() {
        val json = SharedBudgetLineWriteRequest("c1", null, 0).toJson()
        assertEquals(JsonPrimitive("c1"), json["category_id"])
        assertEquals(JsonNull, json["limit_minor"])
    }

    @Test
    fun aWaitingChangeKeepsWhatWasTypedAndNothingIsPrinted() {
        val payload = SharedBudgetPayload("Home", "EGP", 1, 500)
        val back = AppJson.decodeFromString(SharedBudgetPayload.serializer(), AppJson.encodeToString(SharedBudgetPayload.serializer(), payload))
        assertEquals(payload, back)
        assertFalse(payload.toString().contains("Home"))
        assertFalse(SharedBudgetWriteRequest("Home", "EGP", 1, 500, 0).toString().contains("Home"))
    }

    @Test
    fun thePreviewReadsExactlyWhatJoiningShows() {
        val preview = AppJson.decodeFromString(
            SharedBudgetInvitationPreviewDto.serializer(),
            """{"invitation_id":"i1","shared_budget_id":"b1","name":"Home","currency":"EGP","owner_display_name":null,"member_count":2,
            "expires_at":"2026-11-10T10:00:00Z","already_member":false,"something_new":1}""",
        )
        assertEquals("Home", preview.name)
        assertNull(preview.ownerDisplayName)
        assertEquals(2, preview.memberCount)
    }

    @Test
    fun theOneTimeLinkIsRedactedInPrint() {
        val meta = AppJson.decodeFromString(SharedBudgetInvitationMetaDto.serializer(), """{"share_url":"https://x.example/invite/shared-budget?token=SECRET"}""")
        assertTrue(meta.shareUrl!!.contains("SECRET"))
        assertFalse(meta.toString().contains("SECRET"))
    }
}
