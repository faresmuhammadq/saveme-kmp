package com.fares.saveme.feature.notifications.domain

import com.fares.saveme.core.sync.FakeSyncRunner
import com.fares.saveme.core.sync.ReadTarget
import com.fares.saveme.feature.notifications.BUDGET
import com.fares.saveme.feature.notifications.FakeDisplay
import com.fares.saveme.feature.notifications.FakeScheduler
import com.fares.saveme.feature.notifications.NOTIFICATION
import com.fares.saveme.feature.notifications.NotificationsTestEnv
import com.fares.saveme.feature.notifications.OCCURRENCE
import com.fares.saveme.feature.notifications.WALLET
import com.fares.saveme.feature.notifications.data.ShownNotificationsStore
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Research R-09 (FR-023 to FR-028): exactly the received text, collapsed, de-duplicated, then a background read. */
class PushReceiverTest {

    private class Rig(scope: TestScope) {
        val env = NotificationsTestEnv(scope.backgroundScope)
        val display = FakeDisplay()
        val runner = FakeSyncRunner()
        val scheduler = FakeScheduler()
        val coordinator = PushCoordinator(env.sessions, scheduler, scope.backgroundScope)
        val receiver = PushReceiver(
            sessions = env.sessions,
            shown = ShownNotificationsStore(env.records),
            display = display,
            labels = { ChannelLabels(emptyMap(), "New notification") },
            coordinator = { coordinator },
            runner = { runner },
            scope = scope.backgroundScope,
        )
    }

    private fun data(type: String, vararg extra: Pair<String, String>, id: String = NOTIFICATION) =
        mapOf("schema" to "1", "type" to type, "category" to "budget_exceeded", "notification_id" to id) + extra

    @Test
    fun signedOutIsDropped() = runTest {
        val rig = Rig(this)
        rig.receiver.onMessage(data("budget_exceeded", "budget_id" to BUDGET), "Title", "Body")
        assertTrue(rig.display.posted.isEmpty())
        assertTrue(rig.runner.refreshes.isEmpty())
    }

    @Test
    fun blankTitleOrBodyIsDropped() = runTest {
        val rig = Rig(this)
        rig.env.signIn()
        rig.receiver.onMessage(data("budget_exceeded", "budget_id" to BUDGET), null, "Body")
        rig.receiver.onMessage(data("budget_exceeded", "budget_id" to BUDGET), "Title", "  ")
        assertTrue(rig.display.posted.isEmpty())
    }

    @Test
    fun aKnownTypeIsPostedWithTheReceivedTextTheCollapseKeyAndOnlyTheContractKeys() = runTest {
        val rig = Rig(this)
        rig.env.signIn()
        rig.receiver.onMessage(data("budget_exceeded", "budget_id" to BUDGET, "evil" to "x", "google.message_id" to "m"), "Budget alert", "A budget went over its limit.")
        testScheduler.runCurrent()
        val posted = rig.display.posted.single()
        assertEquals("budget_exceeded", posted.channel)
        assertEquals("budget:$BUDGET", posted.tag)
        assertEquals("Budget alert", posted.title)
        assertEquals("A budget went over its limit.", posted.body)
        assertEquals(setOf("schema", "type", "category", "notification_id", "budget_id"), posted.tap.keys)
        assertEquals(listOf("channels", "post"), rig.display.log, "channels are ensured before posting")
        assertEquals(listOf<Pair<ReadTarget, Boolean>>(ReadTarget.User to true), rig.runner.refreshes)
    }

    @Test
    fun theReadIsTheRoutesScopeForced() = runTest {
        val rig = Rig(this)
        rig.env.signIn()
        rig.receiver.onMessage(
            data("occurrence_due", "wallet_id" to WALLET, "occurrence_id" to OCCURRENCE, id = "id-1"), "T", "B",
        )
        rig.receiver.onMessage(data("occurrences_due_summary", id = "id-2"), "T", "B")
        rig.receiver.onMessage(data("member_removed", id = "id-3"), "T", "B")
        testScheduler.runCurrent()
        assertEquals(
            listOf<Pair<ReadTarget, Boolean>>(ReadTarget.Wallet(WALLET) to true, ReadTarget.Everything to true, ReadTarget.Everything to true),
            rig.runner.refreshes,
        )
    }

    @Test
    fun noReadForEntitlementsAccountInvitationOrExport() = runTest {
        val rig = Rig(this)
        rig.env.signIn()
        listOf("subscription_ending", "deletion_reminder", "invitation_received").forEachIndexed { i, type ->
            rig.receiver.onMessage(data(type, id = "id-$i"), "T", "B")
        }
        testScheduler.runCurrent()
        assertEquals(3, rig.display.posted.size)
        assertTrue(rig.runner.refreshes.isEmpty())
    }

    @Test
    fun anUnknownTypeWithTextIsPostedUnderAnOtherTagWithNoRead() = runTest {
        val rig = Rig(this)
        rig.env.signIn()
        rig.receiver.onMessage(data("future_type"), "News", "Something new")
        testScheduler.runCurrent()
        val posted = rig.display.posted.single()
        assertEquals("other:$NOTIFICATION", posted.tag)
        assertEquals("News", posted.title)
        assertTrue(rig.runner.refreshes.isEmpty())
    }

    @Test
    fun aMalformedKnownTypeIsPostedWithoutARead() = runTest {
        val rig = Rig(this)
        rig.env.signIn()
        rig.receiver.onMessage(data("budget_exceeded"), "T", "B")
        testScheduler.runCurrent()
        assertEquals(1, rig.display.posted.size)
        assertTrue(rig.runner.refreshes.isEmpty())
    }

    @Test
    fun theSameNotificationIdTwiceIsShownOnce() = runTest {
        val rig = Rig(this)
        rig.env.signIn()
        rig.receiver.onMessage(data("budget_exceeded", "budget_id" to BUDGET), "T", "B")
        rig.receiver.onMessage(data("budget_exceeded", "budget_id" to BUDGET), "T", "B")
        testScheduler.runCurrent()
        assertEquals(1, rig.display.posted.size)
        assertEquals(1, rig.runner.refreshes.size)
        // Another id about the same subject is shown, and replaces the first by its tag.
        rig.receiver.onMessage(data("budget_exceeded", "budget_id" to BUDGET, id = "another-id"), "T", "B")
        assertEquals(2, rig.display.posted.size)
        assertEquals(rig.display.posted[0].tag, rig.display.posted[1].tag)
    }

    @Test
    fun aNewAddressIsHandedToTheCoordinator() = runTest {
        val rig = Rig(this)
        rig.receiver.onNewToken()
        assertEquals(listOf(ReconcileTrigger.TokenChanged), rig.scheduler.requests)
    }

    @Test
    fun theRedactedTypesNeverPrintTheirContent() {
        val posted = PostedNotification("sharing", "wallet:x", "Secret title", "Secret body", mapOf("type" to "x"))
        assertFalse(posted.toString().contains("Secret"))
    }
}
