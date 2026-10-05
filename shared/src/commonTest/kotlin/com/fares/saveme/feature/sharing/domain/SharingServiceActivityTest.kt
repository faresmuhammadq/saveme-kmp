package com.fares.saveme.feature.sharing.domain

import com.fares.saveme.core.sync.ReadTarget
import com.fares.saveme.feature.auth.jsonResponse
import com.fares.saveme.feature.auth.problemBody
import com.fares.saveme.feature.sharing.SharingTestEnv
import com.fares.saveme.feature.sharing.activityJson
import com.fares.saveme.feature.sharing.activityPageBody
import com.fares.saveme.feature.sharing.domain.model.ActivityPage
import com.fares.saveme.feature.sharing.domain.model.Actor
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** Online activity pages resolved against the phone's records (spec 013 FR-024-FR-026). */
class SharingServiceActivityTest {

    @Test
    fun aPageIsMappedNamedAndSkipsWhatItDoesNotKnow() = runTest {
        val body = activityPageBody(
            listOf(
                activityJson("a1", action = "created", subjectId = "t1"),
                activityJson("a2", action = "from_the_future"),
                activityJson("a3", actorId = null, actorName = null, action = "confirmed", subjectType = "occurrence", subjectId = "o1"),
            ),
            next = "c2",
        )
        val env = SharingTestEnv(backgroundScope) { jsonResponse(200, body) }
        env.signedIn()
        env.names.names = ActivityNames(transactions = mapOf("t1" to "Lunch"))
        val page = assertIs<ActivityPage.Loaded>(env.service.activityPage("w1", null))
        assertEquals(listOf("a1", "a3"), page.items.map { it.id })
        assertEquals("Lunch", page.items[0].subject.name)
        assertEquals(Actor.Automatic, page.items[1].actor)
        assertEquals("c2", page.nextCursor)
        assertEquals(listOf("w1"), env.names.asked)
        assertTrue(env.requests.single().url.parameters["limit"] == "50")
    }

    @Test
    fun aRefusedPositionRestartsFromTheFirstPageOnceThenFails() = runTest {
        var calls = 0
        val env = SharingTestEnv(backgroundScope) { request ->
            calls++
            if (request.url.parameters["cursor"] != null) {
                jsonResponse(400, problemBody(400, "INVALID_CURSOR"))
            } else {
                jsonResponse(200, activityPageBody(listOf(activityJson("a1"))))
            }
        }
        env.signedIn()
        val page = assertIs<ActivityPage.Loaded>(env.service.activityPage("w1", "stale"))
        assertTrue(page.restarted)
        assertEquals(2, calls)

        val always = SharingTestEnv(backgroundScope) { jsonResponse(400, problemBody(400, "INVALID_CURSOR")) }
        always.signedIn()
        assertIs<ActivityPage.Failed>(always.service.activityPage("w1", "stale"))
        assertEquals(2, always.requests.size, "once, not forever")
    }

    @Test
    fun aMissingWalletForcesAReadAndIsGone() = runTest {
        val env = SharingTestEnv(backgroundScope) { jsonResponse(404, problemBody(404, "NOT_FOUND")) }
        env.signedIn()
        assertEquals(ActivityPage.Gone, env.service.activityPage("w1", null))
        assertEquals(listOf<Pair<ReadTarget, Boolean>>(ReadTarget.Wallet("w1") to true), env.runner.refreshes)
    }

    @Test
    fun offlineAndFailureAreNamed() = runTest {
        val offline = SharingTestEnv(backgroundScope)
        offline.signedIn()
        assertEquals(ActivityPage.Offline, offline.service.activityPage("w1", null))
        val broken = SharingTestEnv(backgroundScope) { jsonResponse(500, problemBody(500, "INTERNAL_ERROR")) }
        broken.signedIn()
        assertEquals(ActivityPage.Failed("req-42"), broken.service.activityPage("w1", null))
    }

    @Test
    fun theNewestRoleChangeAboutThePersonDecidesThePlanNote() = runTest {
        val limited = activityPageBody(
            listOf(
                activityJson("a1", actorId = null, actorName = null, action = "role_limited_by_plan", subjectType = "member", subjectId = "u1", role = "viewer"),
                activityJson("a2", action = "role_changed", subjectType = "member", subjectId = "u1", role = "editor"),
            ),
        )
        val env = SharingTestEnv(backgroundScope) { jsonResponse(200, limited) }
        env.signedIn()
        env.service.activityPage("w1", null)
        assertTrue(env.planNotes.observe("w1").first())

        val byOwner = activityPageBody(
            listOf(
                activityJson("a1", action = "role_changed", subjectType = "member", subjectId = "u1", role = "viewer"),
                activityJson("a2", actorId = null, actorName = null, action = "role_limited_by_plan", subjectType = "member", subjectId = "u1", role = "viewer"),
            ),
        )
        val other = SharingTestEnv(backgroundScope) { jsonResponse(200, byOwner) }
        other.signedIn()
        other.planNotes.record("w1", true)
        other.service.activityPage("w1", null)
        assertFalse(other.planNotes.observe("w1").first())
    }
}
