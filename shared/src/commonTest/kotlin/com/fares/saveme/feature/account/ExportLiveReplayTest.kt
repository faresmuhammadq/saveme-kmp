package com.fares.saveme.feature.account

import app.cash.turbine.test
import com.fares.saveme.core.network.ApiErrorCode
import com.fares.saveme.core.presentation.runMviTest
import com.fares.saveme.feature.account.CapturedExportResponses as C
import com.fares.saveme.feature.account.domain.export.DefaultExportService
import com.fares.saveme.feature.account.domain.export.ExportScope
import com.fares.saveme.feature.account.domain.export.ExportStatus
import com.fares.saveme.feature.account.domain.export.ExportTestEnv
import com.fares.saveme.feature.account.domain.export.LinkOutcome
import com.fares.saveme.feature.account.domain.export.PageOutcome
import com.fares.saveme.feature.account.domain.export.ReadOutcome
import com.fares.saveme.feature.account.domain.export.RequestOutcome
import com.fares.saveme.feature.account.domain.export.WatchEvent
import com.fares.saveme.feature.account.ui.export.detail.ExportDetailEffect
import com.fares.saveme.feature.account.ui.export.detail.ExportDetailIntent
import com.fares.saveme.feature.account.ui.export.detail.ExportDetailViewModel
import com.fares.saveme.feature.account.ui.export.list.ExportsIntent
import com.fares.saveme.feature.account.ui.export.list.ExportsViewModel
import com.fares.saveme.feature.account.ui.export.request.ExportRequestEffect
import com.fares.saveme.feature.account.ui.export.request.ExportRequestIntent
import com.fares.saveme.feature.account.ui.export.request.ExportRequestMessage
import com.fares.saveme.feature.account.ui.export.request.ExportRequestViewModel
import com.fares.saveme.feature.account.ui.export.request.WalletChoice
import com.fares.saveme.feature.account.ui.export.waitText
import com.fares.saveme.feature.account.ui.export.WaitText
import com.fares.saveme.feature.auth.jsonResponse
import com.fares.saveme.feature.billing.Captured
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpMethod
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/**
 * The answers captured from the live local backend (CapturedExportResponses, qa-report.md section 3) replayed through the real
 * `ApiClient` -> `ExportApi` -> `DefaultExportService` and through the three export stores (T074).
 */
class ExportLiveReplayTest {

    private fun answer(c: Captured): suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData =
        { jsonResponse(c.status, c.body, *c.headers.toTypedArray()) }

    private fun answers(vararg list: Captured): suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData {
        var index = 0
        return { val c = list[minOf(index++, list.lastIndex)]; jsonResponse(c.status, c.body, *c.headers.toTypedArray()) }
    }

    private suspend fun serviceOf(c: Captured): Pair<DefaultExportService, ExportTestEnv> {
        val env = ExportTestEnv(answer(c))
        env.signedIn()
        return DefaultExportService(env.api, now = { Instant.parse("2026-10-06T00:20:00Z") }) to env
    }

    private suspend fun request(c: Captured, scope: ExportScope = ExportScope.Account, walletId: String? = null): RequestOutcome =
        serviceOf(c).first.request(scope, walletId)

    private val aliceWallet = "01a10e94-7f9b-7b48-bc3e-6fb34d109977"

    // --- requests ---

    @Test
    fun theLiveAcceptedRequestsAreQueuedExportsOfTheAskedScope() = runTest {
        val account = assertIs<RequestOutcome.Created>(request(C.requestAccountQueued)).export
        assertEquals(ExportStatus.Queued, account.status)
        assertEquals(ExportScope.Account, account.scope)
        assertNull(account.walletId)
        assertNull(account.sizeBytes)
        val wallet = assertIs<RequestOutcome.Created>(request(C.requestWalletQueued, ExportScope.Wallet, aliceWallet)).export
        assertEquals(ExportScope.Wallet, wallet.scope)
        assertEquals(aliceWallet, wallet.walletId)
        val viewer = assertIs<RequestOutcome.Created>(request(C.requestWalletAsViewer, ExportScope.Wallet, aliceWallet)).export
        assertEquals(ExportStatus.Queued, viewer.status, "a viewer of the wallet may export it (spec 022)")
        val whilePending = assertIs<RequestOutcome.Created>(request(C.requestWhilePending)).export
        assertEquals(ExportStatus.Queued, whilePending.status, "an account pending deletion may export (FR-004)")
    }

    @Test
    fun theLiveConflictCarriesTheExportAlreadyBeingPrepared() = runTest {
        val first = assertIs<RequestOutcome.Created>(request(C.requestAccountQueued)).export
        val again = assertIs<RequestOutcome.InProgress>(request(C.conflictAccountAgain))
        assertEquals(first.id, again.current?.id)
        assertEquals(ExportScope.Account, again.current?.scope)
        assertTrue(again.current?.status?.isActive == true)
        val wallet = assertIs<RequestOutcome.InProgress>(request(C.conflictWalletWhileActive, ExportScope.Wallet, aliceWallet))
        assertEquals(first.id, wallet.current?.id, "any scope meets the one active export")
    }

    @Test
    fun theLiveSixthRequestIsRateLimitedWithTheWaitInTheBodyAndTheHeader() = runTest {
        val limited = assertIs<RequestOutcome.RateLimited>(request(C.rateLimitedSixth))
        assertEquals(86386L, limited.retryAfterSeconds)
        assertEquals("86386", C.rateLimitedSixth.headers.single { it.first == "Retry-After" }.second)
        assertEquals(WaitText.Hours(24), waitText(limited.retryAfterSeconds), "24 hours, rounded up, never 23")
    }

    @Test
    fun theLiveRefusalsMapToTheirOwnOutcomes() = runTest {
        assertEquals(RequestOutcome.WalletGone, request(C.walletNotFoundUnknown, ExportScope.Wallet, "w"))
        assertEquals(RequestOutcome.WalletGone, request(C.walletNotFoundNonMember, ExportScope.Wallet, aliceWallet), "a non-member gets the same 404 as a missing wallet")
        for (refusal in listOf(C.invalidScope, C.walletMissing, C.emptyBody, C.badWalletId)) {
            val failed = assertIs<RequestOutcome.Failed>(request(refusal))
            assertEquals(ApiErrorCode.VALIDATION_FAILED, failed.code)
            assertTrue(failed.requestId?.isNotBlank() == true, "the general failure shows the request id")
        }
        assertEquals(ApiErrorCode.UNAUTHENTICATED, assertIs<RequestOutcome.Failed>(request(C.unauthenticated)).code)
    }

    @Test
    fun aRequestIsOneCallWithOnlyTheScopeAndNeverRetried() = runTest {
        val (service, env) = serviceOf(C.conflictAccountAgain)
        service.request(ExportScope.Account, null)
        val request = env.requests.single()
        assertEquals(HttpMethod.Post, request.method)
        assertNull(request.headers["Idempotency-Key"])
    }

    // --- reads and links ---

    @Test
    fun theLiveReadsFollowQueuedRunningReadyAndExpired() = runTest {
        assertEquals(ExportStatus.Queued, assertIs<ReadOutcome.Read>(serviceOf(C.getAccountQueued).first.read("e")).export.status)
        assertEquals(ExportStatus.Running, assertIs<ReadOutcome.Read>(serviceOf(C.getWalletRunning).first.read("e")).export.status)
        val ready = assertIs<ReadOutcome.Read>(serviceOf(C.getWalletReady).first.read("e")).export
        assertEquals(ExportStatus.Ready, ready.status)
        assertTrue((ready.sizeBytes ?: 0) > 0)
        assertEquals(86400L, ready.expiresAt!!.epochSeconds - ready.completedAt!!.epochSeconds, "kept for 24 hours")
        val expired = assertIs<ReadOutcome.Read>(serviceOf(C.getWalletExpired).first.read("e")).export
        assertEquals(ExportStatus.Expired, expired.status)
        assertEquals(ReadOutcome.NotFound, serviceOf(C.getUnknown).first.read("e"))
        assertEquals(ReadOutcome.NotFound, serviceOf(C.getForeign).first.read("e"), "someone else's export is not found")
        assertEquals(ApiErrorCode.UNKNOWN, assertIs<ReadOutcome.Failed>(serviceOf(C.getBadId).first.read("nope")).code, "INVALID_ID is not a code the app branches on: the general failure")
    }

    @Test
    fun theLiveReadyExportOpensWithAFreshLinkAndAnExpiredOneNeverDoes() = runTest {
        val open = assertIs<LinkOutcome.Open>(serviceOf(C.getWalletReady).first.openableLink("e"))
        assertTrue(open.link.value.contains("/exports/") && open.link.value.contains("signature="))
        assertEquals("DownloadLink(***)", open.link.toString())
        assertFalse(open.toString().contains("signature"), "the outcome prints without the link")
        val expired = assertIs<LinkOutcome.NotReady>(serviceOf(C.getWalletExpired).first.openableLink("e"))
        assertEquals(ExportStatus.Expired, expired.export.status)
        assertEquals(LinkOutcome.NotFound, serviceOf(C.getUnknown).first.openableLink("e"))
    }

    // --- list ---

    @Test
    fun theLiveListIsNewestFirstWithAnOpaqueCursorAndTheLastPageEndsIt() = runTest {
        val two = assertIs<PageOutcome.Page>(serviceOf(C.listTwo).first.page(null))
        assertEquals(listOf(ExportScope.Wallet, ExportScope.Account), two.items.map { it.scope }, "newest first")
        assertFalse(two.hasMore)
        assertNull(two.nextCursor)
        val one = assertIs<PageOutcome.Page>(serviceOf(C.listPageOne).first.page(null))
        assertTrue(one.hasMore)
        assertTrue(one.nextCursor?.isNotBlank() == true)
        val next = assertIs<PageOutcome.Page>(serviceOf(C.listPageTwo).first.page(one.nextCursor))
        assertFalse(next.hasMore)
        assertEquals(1, next.items.size)
        assertNotEqualsId(one.items.single().id, next.items.single().id)
        assertEquals(PageOutcome.CursorInvalid, serviceOf(C.listInvalidCursor).first.page("garbage"))
        assertEquals(5, assertIs<PageOutcome.Page>(serviceOf(C.listFiveReady).first.page(null)).items.size)
        val mixed = assertIs<PageOutcome.Page>(serviceOf(C.listReadyAndExpired).first.page(null))
        assertEquals(listOf(ExportStatus.Expired, ExportStatus.Ready), mixed.items.map { it.status })
    }

    private fun assertNotEqualsId(a: String, b: String) = assertTrue(a != b, "the second page continues after the first")

    @Test
    fun aListedPageSendsTwentyAndTheCursorAndTheServerClampsOrRefusesOthers() = runTest {
        val (service, env) = serviceOf(C.listPageTwo)
        service.page("c1")
        assertEquals("20", env.requests.single().url.parameters["limit"])
        assertEquals("c1", env.requests.single().url.parameters["cursor"])
        assertEquals(ApiErrorCode.UNKNOWN, assertIs<PageOutcome.Failed>(serviceOf(C.listLimitZero).first.page(null)).code, "INVALID_QUERY_PARAMETER: the app never sends 0")
        assertEquals(PageOutcome.Page::class, serviceOf(C.listLimitHuge).first.page(null)::class)
    }

    // --- watch ---

    @Test
    fun theLiveSequenceQueuedRunningReadyEndsThePollingAfterThreeReadsFiveSecondsApart() = runTest {
        val env = ExportTestEnv(answers(C.getWalletQueued, C.getWalletRunning, C.getWalletReady))
        env.signedIn()
        val service = DefaultExportService(env.api, now = { Instant.parse("2026-10-06T00:20:00Z") })
        val events = service.watch("e", null).toList()
        assertEquals(
            listOf(ExportStatus.Queued, ExportStatus.Running, ExportStatus.Ready),
            events.map { assertIs<WatchEvent.Status>(it).export.status },
        )
        assertEquals(3, env.requests.size)
        assertEquals(10_000L, testScheduler.currentTime, "two waits of five seconds, none after the final state")
    }

    // --- stores ---

    @Test
    fun theDetailStoreWatchesTheLiveSequenceAndHandsOnlyAFreshRedactedLinkToTheBrowser() = runMviTest {
        val env = ExportTestEnv(answers(C.getWalletQueued, C.getWalletRunning, C.getWalletReady, C.getWalletReady))
        env.signedIn()
        val service = DefaultExportService(env.api, now = { Instant.parse("2026-10-06T00:20:00Z") })
        val vm = ExportDetailViewModel("e", service::watch, service::openableLink, now = { Instant.parse("2026-10-06T00:20:00Z") })
        vm.onIntent(ExportDetailIntent.Started)
        vm.state.first { it.status == ExportStatus.Ready && !it.polling }
        assertEquals(3, env.requests.size)
        assertEquals(10_000L, testScheduler.currentTime, "two waits of five seconds, none after the final state")
        vm.effects.test {
            vm.onIntent(ExportDetailIntent.OpenDownload)
            val effect = assertIs<ExportDetailEffect.OpenDownload>(awaitItem())
            assertTrue(effect.link.value.startsWith("http://saveme-backend.test/exports/"))
            assertFalse(effect.toString().contains("signature"))
        }
        assertFalse(vm.state.value.toString().contains("signature"), "the state holds no link")
        assertEquals(4, env.requests.size, "three watch reads and one fresh read for the tap")
    }

    @Test
    fun theDetailStoreShowsAnExpiredExportWithoutOpeningTheBrowser() = runMviTest {
        val env = ExportTestEnv(answers(C.getWalletReady, C.getWalletExpired))
        env.signedIn()
        val service = DefaultExportService(env.api, now = { Instant.parse("2026-10-06T00:20:00Z") })
        val vm = ExportDetailViewModel("e", service::watch, service::openableLink, now = { Instant.parse("2026-10-06T00:20:00Z") })
        vm.onIntent(ExportDetailIntent.Started)
        vm.state.first { it.status == ExportStatus.Ready }
        vm.effects.test {
            vm.onIntent(ExportDetailIntent.OpenDownload)
            vm.state.first { it.status == ExportStatus.Expired }
            expectNoEvents()
        }
        assertEquals(2, env.requests.size)
    }

    @Test
    fun theRequestStoreOpensTheLiveCurrentExportAndBlocksOnTheLiveRateLimit() = runMviTest {
        val created = assertIs<RequestOutcome.Created>(request(C.requestAccountQueued)).export
        val conflict = ExportTestEnv(answer(C.conflictAccountAgain)).also { it.signedIn() }
        val vm = ExportRequestViewModel(request = DefaultExportService(conflict.api)::request, wallets = flowOf(listOf(WalletChoice(aliceWallet, "QA Alpha", false))))
        vm.effects.test {
            vm.onIntent(ExportRequestIntent.Submit)
            vm.state.first { it.message != null }
            assertEquals(ExportRequestMessage.InProgress(created.id), vm.state.value.message)
            vm.onIntent(ExportRequestIntent.SeeIt)
            assertEquals(ExportRequestEffect.OpenDetail(created.id), awaitItem())
        }
        val limited = ExportTestEnv(answer(C.rateLimitedSixth)).also { it.signedIn() }
        val limitedVm = ExportRequestViewModel(request = DefaultExportService(limited.api)::request, now = { Instant.parse("2026-10-06T00:30:00Z") })
        limitedVm.onIntent(ExportRequestIntent.Submit)
        limitedVm.state.first { it.blockedUntil != null }
        assertEquals(ExportRequestMessage.RateLimited(86386L), limitedVm.state.value.message)
        assertEquals(Instant.parse("2026-10-06T00:30:00Z") + 86386.seconds, limitedVm.state.value.blockedUntil)
        assertFalse(limitedVm.state.value.canSubmit)
        limitedVm.onIntent(ExportRequestIntent.Submit)
        assertEquals(1, limited.requests.size, "nothing is sent while the wait is on")
        limitedVm.state.first { it.blockedUntil == null }
        assertTrue(limitedVm.state.value.canSubmit, "the wait ends by itself")
    }

    @Test
    fun theListStoreShowsTheLivePagesAndRestartsFromTheFirstOnABadCursor() = runMviTest {
        val env = ExportTestEnv(answers(C.listPageOne, C.listInvalidCursor, C.listPageOne))
        env.signedIn()
        val vm = ExportsViewModel(DefaultExportService(env.api)::page)
        vm.onIntent(ExportsIntent.Opened)
        vm.state.first { it.loaded }
        assertEquals(1, vm.state.value.items.size)
        assertTrue(vm.state.value.hasMore)
        vm.onIntent(ExportsIntent.ShowMore)
        vm.state.first { env.requests.size == 3 && !it.loadingMore && !it.loading }
        assertEquals(1, vm.state.value.items.size, "the refused cursor restarted from the first page")
        assertNull(env.requests.last().url.parameters["cursor"])
        assertEquals("20", env.requests.first().url.parameters["limit"])
    }
}
