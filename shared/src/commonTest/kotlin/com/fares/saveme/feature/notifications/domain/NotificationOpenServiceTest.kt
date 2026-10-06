package com.fares.saveme.feature.notifications.domain

import com.fares.saveme.core.sync.FakeSyncRunner
import com.fares.saveme.core.sync.ReadOutcome
import com.fares.saveme.core.sync.ReadTarget
import com.fares.saveme.feature.notifications.BUDGET
import com.fares.saveme.feature.notifications.OCCURRENCE
import com.fares.saveme.feature.notifications.WALLET
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** FR-029, FR-032: only reads run, the screen opens at once when its record is here, and waits at most ten seconds. */
class NotificationOpenServiceTest {

    private class Rig(scope: TestScope, var present: Boolean = false) {
        val runner = FakeSyncRunner()
        val calls = mutableListOf<String>()
        val service = NotificationOpenService(
            runner = { runner },
            refreshEntitlements = { calls += "entitlements" },
            refreshAccount = { calls += "account" },
            presence = RecordPresence { present },
            scope = scope.backgroundScope,
        )
    }

    @Test
    fun eachReadRunsExactlyItsOwnForcedRead() = runTest {
        val rig = Rig(this)
        rig.service.open(PushRoute(PushRead.User, PushTarget.Budgets))
        rig.service.open(PushRoute(PushRead.Wallet(WALLET), PushTarget.Wallet(WALLET)))
        rig.service.open(PushRoute(PushRead.Everything, PushTarget.Wallets))
        rig.service.open(PushRoute(PushRead.Entitlements, PushTarget.Plan))
        rig.service.open(PushRoute(PushRead.Account, PushTarget.AccountDeletion))
        assertEquals(listOf<Pair<ReadTarget, Boolean>>(ReadTarget.User to true, ReadTarget.Wallet(WALLET) to true, ReadTarget.Everything to true), rig.runner.refreshes)
        assertEquals(listOf("entitlements", "account"), rig.calls)
    }

    @Test
    fun noReadMeansNoCallAtAll() = runTest {
        val rig = Rig(this)
        val target = rig.service.open(PushRoute(PushRead.None, PushTarget.InvitationNotice))
        assertEquals(PushTarget.InvitationNotice, target)
        assertTrue(rig.runner.refreshes.isEmpty() && rig.calls.isEmpty())
    }

    @Test
    fun aRecordAlreadyOnThePhoneOpensAtOnceWhileTheReadContinuesInTheBackground() = runTest {
        val rig = Rig(this, present = true)
        val gate = CompletableDeferred<Unit>()
        rig.runner.onRefresh = { _, _ -> gate.await() }
        val target = rig.service.open(PushRoute(PushRead.User, PushTarget.Budget(BUDGET)))
        assertEquals(PushTarget.Budget(BUDGET), target)
        testScheduler.runCurrent()
        assertEquals(1, rig.runner.refreshes.size, "the read was started")
        gate.complete(Unit)
    }

    @Test
    fun anAbsentRecordWaitsForItsRead() = runTest {
        val rig = Rig(this, present = false)
        val gate = CompletableDeferred<Unit>()
        rig.runner.onRefresh = { _, _ -> gate.await() }
        var opened: PushTarget? = null
        val job = backgroundScope.launch { opened = rig.service.open(PushRoute(PushRead.Wallet(WALLET), PushTarget.Occurrence(OCCURRENCE))) }
        testScheduler.runCurrent()
        assertEquals(null, opened, "still waiting for the read")
        gate.complete(Unit)
        testScheduler.runCurrent()
        assertEquals(PushTarget.Occurrence(OCCURRENCE), opened)
        job.cancel()
    }

    @Test
    fun offlineOrFailedStillOpensTheTarget() = runTest {
        val rig = Rig(this)
        rig.runner.outcome = ReadOutcome.Offline
        assertEquals(PushTarget.Budgets, rig.service.open(PushRoute(PushRead.User, PushTarget.Budgets)))
        rig.runner.outcome = ReadOutcome.Failed
        assertEquals(PushTarget.Budgets, rig.service.open(PushRoute(PushRead.User, PushTarget.Budgets)))
    }

    @Test
    fun aReadThatNeverEndsOpensTheTargetAfterTenSeconds() = runTest {
        val rig = Rig(this)
        rig.runner.onRefresh = { _, _ -> kotlinx.coroutines.awaitCancellation() }
        val before = currentTime
        assertEquals(PushTarget.Wallets, rig.service.open(PushRoute(PushRead.Everything, PushTarget.Wallets)))
        assertEquals(10_000L, currentTime - before)
    }
}
