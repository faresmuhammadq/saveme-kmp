package com.fares.saveme.feature.sharing.domain

import com.fares.saveme.core.network.AppJson
import com.fares.saveme.feature.sharing.activityJson
import com.fares.saveme.feature.sharing.data.dto.ActivityEntryDto
import com.fares.saveme.feature.sharing.domain.model.ActivityAction
import com.fares.saveme.feature.sharing.domain.model.Actor
import com.fares.saveme.feature.sharing.domain.model.SubjectKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ActivityMappingTest {

    private fun entry(json: String): ActivityEntryDto = AppJson.decodeFromString(ActivityEntryDto.serializer(), json)

    private val names = ActivityNames(
        members = mapOf("u2" to "Karim", "u3" to null),
        transactions = mapOf("t1" to "Groceries", "t2" to null),
        rules = mapOf("r1" to "Rent"),
        occurrences = mapOf("o1" to "Rent"),
        walletName = "Home",
    )

    @Test
    fun everyActionAndSubjectKindOfTheContractMapsToWords() {
        val actions = listOf(
            "created" to "transaction", "edited" to "wallet", "deleted" to "recurring_rule", "restored" to "transaction",
            "archived" to "wallet", "ownership_transferred" to "wallet", "confirmed" to "occurrence", "skipped" to "occurrence",
            "joined" to "member", "left" to "member", "removed" to "member", "role_changed" to "member",
            "role_limited_by_plan" to "member", "invited" to "invitation", "invitation_revoked" to "invitation",
        )
        for ((action, subject) in actions) {
            val item = ActivityMapping.map(entry(activityJson(action = action, subjectType = subject)), names)
            assertNotNull(item, "$action/$subject")
            assertEquals(action, item.action.wire)
            assertEquals(subject, item.subject.kind.wire)
        }
        assertEquals(15, ActivityAction.entries.size)
        assertEquals(6, SubjectKind.entries.size)
    }

    @Test
    fun anUnknownActionOrSubjectTypeIsSkipped() {
        assertNull(ActivityMapping.map(entry(activityJson(action = "teleported")), names))
        assertNull(ActivityMapping.map(entry(activityJson(subjectType = "spaceship")), names))
        assertNull(ActivityMapping.map(entry(activityJson(at = "not a time")), names))
    }

    @Test
    fun theActorIsTheServersNameThenThePhonesMemberNameThenAFormerMemberAndNullIsAutomatic() {
        assertEquals(Actor.Named("Karim S."), ActivityMapping.map(entry(activityJson(actorId = "u2", actorName = "Karim S.")), names)!!.actor)
        assertEquals(Actor.Named("Karim"), ActivityMapping.map(entry(activityJson(actorId = "u2", actorName = null)), names)!!.actor)
        assertEquals(Actor.UnnamedMember, ActivityMapping.map(entry(activityJson(actorId = "u3", actorName = null)), names)!!.actor)
        assertEquals(Actor.FormerMember, ActivityMapping.map(entry(activityJson(actorId = "u9", actorName = null)), names)!!.actor)
        assertEquals(Actor.Automatic, ActivityMapping.map(entry(activityJson(actorId = null, actorName = null)), names)!!.actor)
    }

    @Test
    fun subjectsAreNamedFromThePhonesCopyOrLeftToTheKindsWords() {
        fun subject(type: String, id: String) = ActivityMapping.map(entry(activityJson(subjectType = type, subjectId = id)), names)!!.subject
        assertEquals("Groceries", subject("transaction", "t1").name)
        assertTrue(subject("transaction", "t1").onPhone)
        assertNull(subject("transaction", "t2").name)
        assertTrue(subject("transaction", "t2").onPhone, "on the phone with nothing to name it by")
        assertNull(subject("transaction", "t-deleted").name)
        assertFalse(subject("transaction", "t-deleted").onPhone)
        assertEquals("Rent", subject("recurring_rule", "r1").name)
        assertEquals("Rent", subject("occurrence", "o1").name)
        assertEquals("Karim", subject("member", "u2").name)
        assertEquals("Home", subject("wallet", "w1").name)
        assertNull(subject("invitation", "i1").name)
    }

    @Test
    fun theRoleOfAnInvitationEntryIsKept() {
        val item = ActivityMapping.map(entry(activityJson(action = "invited", subjectType = "invitation", role = "editor")), names)!!
        assertEquals("editor", item.roleWire)
    }

    @Test
    fun onlyAnOnPhoneTransactionOpens() {
        assertEquals("t1", ActivityMapping.map(entry(activityJson(subjectId = "t1")), names)!!.transactionId)
        assertNull(ActivityMapping.map(entry(activityJson(subjectId = "gone")), names)!!.transactionId)
        assertNull(ActivityMapping.map(entry(activityJson(subjectType = "wallet", subjectId = "w1")), names)!!.transactionId)
    }

    @Test
    fun theNewestRoleChangeAboutThePersonDecidesWhetherThePlanDidIt() {
        val limited = entry(activityJson("a1", action = "role_limited_by_plan", subjectType = "member", subjectId = "u1"))
        val changed = entry(activityJson("a2", action = "role_changed", subjectType = "member", subjectId = "u1"))
        val other = entry(activityJson("a3", action = "role_limited_by_plan", subjectType = "member", subjectId = "u2"))
        assertEquals(true, ActivityMapping.limitedByPlan(listOf(other, limited, changed), "u1"))
        assertEquals(false, ActivityMapping.limitedByPlan(listOf(changed, limited), "u1"))
        assertNull(ActivityMapping.limitedByPlan(listOf(other), "u1"))
    }
}
