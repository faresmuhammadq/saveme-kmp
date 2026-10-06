package com.fares.saveme.feature.reports.domain

import com.fares.saveme.core.sync.ReadOutcome
import com.fares.saveme.core.sync.ReadTarget
import com.fares.saveme.feature.auth.domain.model.RefreshOutcome
import com.fares.saveme.feature.reports.ReportsRig
import com.fares.saveme.feature.reports.reportWalletView
import com.fares.saveme.feature.reports.domain.model.ReportScope
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

/** FR-020: open reads the covered scopes, a pull forces it, "Updated" is the phone's own oldest read stamp. */
class ReportsServiceRefreshTest {

    @Test
    fun aCrossWalletOpenReadsEverythingOnceAndAPullForcesIt() = runTest {
        val rig = ReportsRig(backgroundScope)
        assertEquals(RefreshOutcome.Done, rig.service.refresh(ReportScope.AllWallets, force = false))
        assertEquals(RefreshOutcome.Done, rig.service.refresh(ReportScope.AllWallets, force = true))
        assertEquals(listOf<Pair<ReadTarget, Boolean>>(ReadTarget.Everything to false, ReadTarget.Everything to true), rig.runner.refreshes)
    }

    @Test
    fun aWalletReportReadsTheWalletThenThePersonsOwnScope() = runTest {
        val rig = ReportsRig(backgroundScope)
        rig.service.refresh(ReportScope.Wallet("w1"), force = false)
        assertEquals(listOf<Pair<ReadTarget, Boolean>>(ReadTarget.Wallet("w1") to false, ReadTarget.User to false), rig.runner.refreshes)
    }

    @Test
    fun offlineAndFailedReadsMapToTheirOutcomes() = runTest {
        val rig = ReportsRig(backgroundScope)
        rig.runner.outcome = ReadOutcome.Offline
        assertEquals(RefreshOutcome.Offline, rig.service.refresh(ReportScope.AllWallets))
        rig.runner.outcome = ReadOutcome.Failed
        assertEquals(RefreshOutcome.Failed, rig.service.refresh(ReportScope.AllWallets))
        assertEquals(RefreshOutcome.Failed, rig.service.refresh(ReportScope.Wallet("w1")))
        rig.runner.outcome = ReadOutcome.Done
        assertEquals(RefreshOutcome.Done, rig.service.refresh(ReportScope.Wallet("w1")))
    }

    @Test
    fun theUpdatedTimeIsTheOldestCoveredReadStamp() = runTest {
        val rig = ReportsRig(backgroundScope)
        rig.wallets.views.value = listOf(reportWalletView("w1"))
        assertEquals(null, rig.report().updatedAt)
        rig.wallets.updatedAt.value = Instant.parse("2026-10-02T09:00:00Z")
        assertEquals(Instant.parse("2026-10-02T09:00:00Z"), rig.report().updatedAt)
        rig.transactions.readAt.value = Instant.parse("2026-10-01T08:00:00Z")
        assertEquals(Instant.parse("2026-10-01T08:00:00Z"), rig.report(ReportScope.Wallet("w1")).updatedAt)
    }

    @Test
    fun nothingIsReadWhileSignedOut() = runTest {
        val rig = ReportsRig(backgroundScope)
        rig.authState.value = com.fares.saveme.feature.auth.domain.AuthState.SignedOut(com.fares.saveme.feature.auth.domain.SignOutReason.None)
        assertEquals(RefreshOutcome.Done, rig.service.refresh(ReportScope.AllWallets))
        assertEquals(emptyList(), rig.runner.refreshes)
    }
}
