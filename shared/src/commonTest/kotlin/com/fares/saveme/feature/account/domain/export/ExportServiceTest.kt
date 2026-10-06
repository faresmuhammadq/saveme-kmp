package com.fares.saveme.feature.account.domain.export

import com.fares.saveme.core.network.ApiErrorCode
import com.fares.saveme.feature.account.jsonBody
import com.fares.saveme.feature.auth.jsonResponse
import com.fares.saveme.feature.auth.problemBody
import io.ktor.utils.io.errors.IOException
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ExportServiceTest {

    /** Not gated (FR-004): the service takes only the API and a clock, so no plan, feature or verification can be read. */
    @Test
    fun requestSendsOnePostAndReturnsTheCreatedExport() = runTest {
        val env = ExportTestEnv { jsonResponse(202, envelope(exportJson())) }
        env.signedIn()
        val outcome = assertIs<RequestOutcome.Created>(DefaultExportService(env.api).request(ExportScope.Account, null))
        assertEquals(ExportStatus.Queued, outcome.export.status)
        assertEquals(1, env.requests.size)
        assertEquals(mapOf("scope" to JsonPrimitive("account")), env.requests.single().jsonBody().toMap())
    }

    @Test
    fun aWalletRequestCarriesTheWallet() = runTest {
        val env = ExportTestEnv { jsonResponse(202, envelope(exportJson(scope = "wallet", walletId = "w1"))) }
        env.signedIn()
        DefaultExportService(env.api).request(ExportScope.Wallet, "w1")
        assertEquals(JsonPrimitive("w1"), env.requests.single().jsonBody()["wallet_id"])
    }

    @Test
    fun offlineAndTimeoutAreOutcomesNeverRetried() = runTest {
        val env = ExportTestEnv { throw IOException("offline") }
        env.signedIn()
        assertEquals(RequestOutcome.Offline, DefaultExportService(env.api).request(ExportScope.Account, null))
        assertEquals(1, env.requests.size)
    }

    @Test
    fun anUnreadableAnswerIsAFailureNotACrash() = runTest {
        val env = ExportTestEnv { jsonResponse(202, envelope(exportJson(requestedAt = "not a date"))) }
        env.signedIn()
        assertIs<RequestOutcome.Failed>(DefaultExportService(env.api).request(ExportScope.Account, null))
    }

    @Test
    fun pageMapsItemsAndTheCursor() = runTest {
        val env = ExportTestEnv {
            jsonResponse(200, """{"data":[${exportJson("a", "ready")},${exportJson("b", "expired")}],"meta":{"next_cursor":"c2","has_more":true,"limit":20}}""")
        }
        env.signedIn()
        val page = assertIs<PageOutcome.Page>(DefaultExportService(env.api).page(null))
        assertEquals(listOf("a", "b"), page.items.map { it.id })
        assertEquals("c2", page.nextCursor)
        assertTrue(page.hasMore)
    }

    @Test
    fun readMapsTheExportAndNotFound() = runTest {
        val ok = ExportTestEnv { jsonResponse(200, envelope(exportJson(status = "running"))) }
        ok.signedIn()
        assertEquals(ExportStatus.Running, assertIs<ReadOutcome.Read>(DefaultExportService(ok.api).read("e1")).export.status)
        val gone = ExportTestEnv { jsonResponse(404, problemBody(404, "NOT_FOUND")) }
        gone.signedIn()
        assertEquals(ReadOutcome.NotFound, DefaultExportService(gone.api).read("e1"))
    }

    @Test
    fun everyLinkRequestMakesAFreshRead() = runTest {
        var n = 0
        val env = ExportTestEnv { jsonResponse(200, envelope(exportJson(status = "ready", url = "$SECRET_URL&n=${++n}"))) }
        env.signedIn()
        val service = DefaultExportService(env.api)
        val first = assertIs<LinkOutcome.Open>(service.openableLink("e1"))
        val second = assertIs<LinkOutcome.Open>(service.openableLink("e1"))
        assertEquals(2, env.requests.size)
        assertTrue(first.link.value.endsWith("n=1"))
        assertTrue(second.link.value.endsWith("n=2"))
    }

    @Test
    fun notReadyOrNoLinkOpensNothing() = runTest {
        val expired = ExportTestEnv { jsonResponse(200, envelope(exportJson(status = "expired"))) }
        expired.signedIn()
        assertEquals(ExportStatus.Expired, assertIs<LinkOutcome.NotReady>(DefaultExportService(expired.api).openableLink("e1")).export.status)
        val noLink = ExportTestEnv { jsonResponse(200, envelope(exportJson(status = "ready", url = null))) }
        noLink.signedIn()
        assertEquals(ExportStatus.Ready, assertIs<LinkOutcome.NotReady>(DefaultExportService(noLink.api).openableLink("e1")).export.status)
    }

    @Test
    fun linkOutcomesForOfflineAndGone() = runTest {
        val offline = ExportTestEnv { throw IOException("offline") }
        offline.signedIn()
        assertEquals(LinkOutcome.Offline, DefaultExportService(offline.api).openableLink("e1"))
        val gone = ExportTestEnv { jsonResponse(404, problemBody(404, "NOT_FOUND")) }
        gone.signedIn()
        assertEquals(LinkOutcome.NotFound, DefaultExportService(gone.api).openableLink("e1"))
    }

    @Test
    fun aServerErrorOnReadIsAFailureWithItsCode() = runTest {
        val env = ExportTestEnv { jsonResponse(500, problemBody(500, "INTERNAL_ERROR")) }
        env.signedIn()
        val failed = assertIs<ReadOutcome.Failed>(DefaultExportService(env.api).read("e1"))
        assertEquals(ApiErrorCode.INTERNAL_ERROR, failed.code)
        assertEquals("req-42", failed.requestId)
    }
}
