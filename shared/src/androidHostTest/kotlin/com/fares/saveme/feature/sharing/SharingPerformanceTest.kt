package com.fares.saveme.feature.sharing

import com.fares.saveme.core.network.AppJson
import com.fares.saveme.feature.sharing.data.dto.ActivityEntryDto
import com.fares.saveme.feature.sharing.domain.ActivityMapping
import com.fares.saveme.feature.sharing.domain.ActivityNames
import com.fares.saveme.feature.sharing.ui.members.MembersState
import com.fares.saveme.feature.sharing.ui.members.reduce
import com.fares.saveme.feature.sharing.ui.members.MembersChange
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.system.measureTimeMillis

/** Plan performance goals: the members screen's data within a second with 5 members and 20 invitations; an activity page of 50 mapped within 300 ms. */
class SharingPerformanceTest {

    @Test
    fun theMembersScreensDataWithFiveMembersAndTwentyInvitationsIsReadyWithinASecond() = runBlocking {
        val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Unconfined)
        val env = SharingTestEnv(scope)
        env.seedOwnerWallet(members = 5)
        for (i in 1..20) env.seed(invitationJson(id = "i$i", email = "person$i@example.com", createdAt = "2026-10-02T10:%02d:00Z".format(i)))
        // Warm the paths once, then measure a cold subscription.
        env.service.observeInvitations("w1").first()
        var state = MembersState()
        val elapsed = (1..3).minOf {
            measureTimeMillis {
                val members = env.service.observeMembers("w1").first()
                val invitations = env.service.observeInvitations("w1").first()
                val check = env.service.observeInviteCheck("w1").first()
                state = reduce(reduce(reduce(MembersState(), MembersChange.Members(members)), MembersChange.Invitations(invitations)), MembersChange.Check(check))
            }
        }
        assertEquals(5, state.members.size)
        assertEquals(20, state.invitations.size)
        assertTrue("members data took $elapsed ms", elapsed < 1_000)
    }

    @Test
    fun anActivityPageOfFiftyIsMappedWithinThreeHundredMilliseconds() {
        val entries = (1..50).map { i ->
            AppJson.decodeFromString(
                ActivityEntryDto.serializer(),
                activityJson("a$i", actorId = "u${i % 5}", actorName = if (i % 3 == 0) null else "Person ${i % 5}", action = listOf("created", "edited", "role_changed", "confirmed")[i % 4], subjectType = if (i % 4 == 2) "member" else "transaction", subjectId = "s$i"),
            )
        }
        val names = ActivityNames(members = (0..4).associate { "u$it" to "Person $it" }, transactions = (1..50).associate { "s$it" to "Note $it" })
        ActivityMapping.map(entries.first(), names)
        val elapsed = (1..5).minOf { measureTimeMillis { entries.mapNotNull { ActivityMapping.map(it, names) } } }
        assertEquals(50, entries.mapNotNull { ActivityMapping.map(it, names) }.size)
        assertTrue("mapping a page took $elapsed ms", elapsed < 300)
    }
}
