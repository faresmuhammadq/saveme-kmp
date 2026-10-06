package com.fares.saveme.feature.account.ui.export

import app.cash.turbine.test
import com.fares.saveme.core.network.ApiErrorCode
import com.fares.saveme.core.presentation.runMviTest
import com.fares.saveme.feature.account.domain.export.DownloadLink
import com.fares.saveme.feature.account.domain.export.ExportScope
import com.fares.saveme.feature.account.domain.export.ExportStatus
import com.fares.saveme.feature.account.domain.export.LinkOutcome
import com.fares.saveme.feature.account.domain.export.WatchEvent
import com.fares.saveme.feature.account.ui.export.detail.ExportDetailEffect
import com.fares.saveme.feature.account.ui.export.detail.ExportDetailIntent
import com.fares.saveme.feature.account.ui.export.detail.ExportDetailMessage
import com.fares.saveme.feature.account.ui.export.detail.ExportDetailViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceTimeBy
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

class ExportDetailViewModelTest {

    private val link = DownloadLink("https://api.example/exports/e1/file?signature=SECRET")

    /** A watch that reads every 5 s on the test clock: queued, running, then ready; [reads] counts them. */
    private class FakeWatch {
        var reads = 0
        val starts = mutableListOf<Instant?>()
        fun flow(id: String, windowStart: Instant?): Flow<WatchEvent> {
            starts += windowStart
            return flow {
                val statuses = listOf(ExportStatus.Queued, ExportStatus.Running, ExportStatus.Ready)
                for (status in statuses) {
                    reads++
                    emit(WatchEvent.Status(anExport(id, status)))
                    if (!status.isActive) return@flow
                    delay(5.seconds)
                }
            }
        }
    }

    private fun vm(
        watch: FakeWatch = FakeWatch(),
        openableLink: suspend (String) -> LinkOutcome = { LinkOutcome.Open(link) },
        signedIn: Flow<Boolean> = MutableStateFlow(true),
        now: () -> Instant = { Instant.parse("2026-10-06T10:00:00Z") },
    ) = ExportDetailViewModel("e1", watch::flow, openableLink, signedIn = signedIn, now = now)

    @Test
    fun startedWatchesAndStoppedCancelsWithNoReadsAfter() = runMviTest {
        val watch = FakeWatch()
        val vm = vm(watch)
        vm.onIntent(ExportDetailIntent.Started)
        assertEquals(ExportStatus.Queued, vm.state.value.status)
        assertTrue(vm.state.value.polling)
        testScheduler.advanceTimeBy(6.seconds)
        assertEquals(ExportStatus.Running, vm.state.value.status)
        vm.onIntent(ExportDetailIntent.Stopped)
        assertFalse(vm.state.value.polling)
        val reads = watch.reads
        testScheduler.advanceTimeBy(60.seconds)
        assertEquals(reads, watch.reads)
    }

    @Test
    fun readyEndsThePolling() = runMviTest {
        val watch = FakeWatch()
        val vm = vm(watch)
        vm.onIntent(ExportDetailIntent.Started)
        testScheduler.advanceTimeBy(11.seconds)
        assertEquals(ExportStatus.Ready, vm.state.value.status)
        assertFalse(vm.state.value.polling)
        assertEquals(3, watch.reads)
    }

    @Test
    fun signOutCancelsAndClears() = runMviTest {
        val signedIn = MutableStateFlow(true)
        val watch = FakeWatch()
        val vm = vm(watch, signedIn = signedIn)
        vm.onIntent(ExportDetailIntent.Started)
        signedIn.value = false
        assertNull(vm.state.value.export)
        val reads = watch.reads
        testScheduler.advanceTimeBy(60.seconds)
        assertEquals(reads, watch.reads)
        vm.onIntent(ExportDetailIntent.Started)
        assertEquals(reads, watch.reads, "a signed-out screen never reads")
    }

    @Test
    fun stillPreparingStopsAndCheckAgainRestartsAFreshWindow() = runMviTest {
        val watch = object {
            val starts = mutableListOf<Instant?>()
            var mode = "still"
        }
        val vm = ExportDetailViewModel(
            "e1",
            watch = { id, start ->
                watch.starts += start
                if (watch.mode == "still") {
                    flowOf(WatchEvent.Status(anExport(id, ExportStatus.Running)), WatchEvent.StillPreparing(anExport(id, ExportStatus.Running)))
                } else {
                    flowOf(WatchEvent.Status(anExport(id, ExportStatus.Ready)))
                }
            },
            openableLink = { LinkOutcome.Open(link) },
            now = { Instant.parse("2026-10-06T10:20:00Z") },
        )
        vm.onIntent(ExportDetailIntent.Started)
        assertTrue(vm.state.value.stillPreparing)
        assertFalse(vm.state.value.polling)
        assertTrue(vm.state.value.canCheckAgain)
        watch.mode = "ready"
        vm.onIntent(ExportDetailIntent.CheckAgain)
        assertEquals(listOf(null, Instant.parse("2026-10-06T10:20:00Z")), watch.starts)
        assertFalse(vm.state.value.stillPreparing)
        assertEquals(ExportStatus.Ready, vm.state.value.status)
    }

    @Test
    fun openDownloadReadsAFreshLinkForEachTapAndNeverKeepsIt() = runMviTest {
        var reads = 0
        val vm = vm(FakeWatch(), openableLink = { reads++; LinkOutcome.Open(DownloadLink("https://x/file?signature=$reads")) })
        vm.onIntent(ExportDetailIntent.Started)
        testScheduler.advanceTimeBy(11.seconds)
        vm.effects.test {
            vm.onIntent(ExportDetailIntent.OpenDownload)
            assertEquals("https://x/file?signature=1", (awaitItem() as ExportDetailEffect.OpenDownload).link.value)
            vm.onIntent(ExportDetailIntent.OpenDownload)
            assertEquals("https://x/file?signature=2", (awaitItem() as ExportDetailEffect.OpenDownload).link.value)
        }
        assertEquals(2, reads)
        assertFalse(vm.state.value.toString().contains("signature"))
        assertFalse(ExportDetailEffect.OpenDownload(link).toString().contains("SECRET"))
    }

    @Test
    fun openDownloadIsOnlyForAReadyExport() = runMviTest {
        var reads = 0
        val vm = vm(FakeWatch(), openableLink = { reads++; LinkOutcome.Open(link) })
        vm.onIntent(ExportDetailIntent.Started)
        vm.onIntent(ExportDetailIntent.OpenDownload)
        assertEquals(0, reads, "queued has no download")
        vm.onIntent(ExportDetailIntent.Stopped)
    }

    @Test
    fun expiredOnTapUpdatesTheScreenWithoutOpeningAnything() = runMviTest {
        val vm = vm(FakeWatch(), openableLink = { LinkOutcome.NotReady(anExport("e1", ExportStatus.Expired)) })
        vm.onIntent(ExportDetailIntent.Started)
        testScheduler.advanceTimeBy(11.seconds)
        vm.effects.test {
            vm.onIntent(ExportDetailIntent.OpenDownload)
            expectNoEvents()
        }
        assertEquals(ExportStatus.Expired, vm.state.value.status)
        assertNull(vm.state.value.message)
    }

    @Test
    fun readyWithoutALinkKeepsReadyAndSaysSoAndOpensNothing() = runMviTest {
        val vm = vm(FakeWatch(), openableLink = { LinkOutcome.NotReady(anExport("e1", ExportStatus.Ready)) })
        vm.onIntent(ExportDetailIntent.Started)
        testScheduler.advanceTimeBy(11.seconds)
        vm.effects.test {
            vm.onIntent(ExportDetailIntent.OpenDownload)
            expectNoEvents()
        }
        assertEquals(ExportStatus.Ready, vm.state.value.status)
        assertEquals(ExportDetailMessage.LinkMissing, vm.state.value.message)
        assertFalse(vm.state.value.opening)
    }

    @Test
    fun goneAndOfflineAndFailureOnTheLinkRead() = runMviTest {
        suspend fun after(outcome: LinkOutcome): ExportDetailMessage? {
            val vm = vm(FakeWatch(), openableLink = { outcome })
            vm.onIntent(ExportDetailIntent.Started)
            testScheduler.advanceTimeBy(11.seconds)
            vm.onIntent(ExportDetailIntent.OpenDownload)
            return vm.state.value.message
        }
        assertEquals(ExportDetailMessage.NotFound, after(LinkOutcome.NotFound))
        assertEquals(ExportDetailMessage.Offline, after(LinkOutcome.Offline))
        assertEquals(ExportDetailMessage.Error(ApiErrorCode.INTERNAL_ERROR, "r"), after(LinkOutcome.Failed(ApiErrorCode.INTERNAL_ERROR, "r")))
    }

    @Test
    fun notFoundOnTheWatchStopsAndHidesTheExport() = runMviTest {
        val vm = ExportDetailViewModel("e1", watch = { _, _ -> flowOf(WatchEvent.NotFound) }, openableLink = { LinkOutcome.NotFound })
        vm.onIntent(ExportDetailIntent.Started)
        assertEquals(ExportDetailMessage.NotFound, vm.state.value.message)
        assertNull(vm.state.value.export)
        assertFalse(vm.state.value.polling)
    }

    @Test
    fun theBrowserFailureIsReportedWithoutTheLink() = runMviTest {
        val vm = vm()
        vm.onIntent(ExportDetailIntent.OpenFailed)
        assertEquals(ExportDetailMessage.OpenFailed, vm.state.value.message)
    }

    @Test
    fun failedAndExpiredOfferANewRequestOfTheSameScope() = runMviTest {
        val wallet = anExport("e1", ExportStatus.Failed, ExportScope.Wallet, "w9")
        val vm = ExportDetailViewModel("e1", watch = { _, _ -> flowOf(WatchEvent.Status(wallet)) }, openableLink = { LinkOutcome.NotFound })
        vm.onIntent(ExportDetailIntent.Started)
        vm.effects.test {
            vm.onIntent(ExportDetailIntent.RequestAgain)
            assertEquals(ExportDetailEffect.OpenRequest("w9"), awaitItem())
        }
    }

    @Test
    fun offlineWatchOffersCheckAgain() = runMviTest {
        val vm = ExportDetailViewModel("e1", watch = { _, _ -> flowOf(WatchEvent.Offline) }, openableLink = { LinkOutcome.Offline })
        vm.onIntent(ExportDetailIntent.Started)
        assertEquals(ExportDetailMessage.Offline, vm.state.value.message)
        assertTrue(vm.state.value.canCheckAgain)
    }
}
