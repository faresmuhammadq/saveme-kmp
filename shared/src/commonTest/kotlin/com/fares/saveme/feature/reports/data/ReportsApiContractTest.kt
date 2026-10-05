package com.fares.saveme.feature.reports.data

import com.fares.saveme.core.network.ApiErrorCode
import com.fares.saveme.core.network.ApiResult
import com.fares.saveme.feature.auth.AuthTestEnv
import com.fares.saveme.feature.auth.jsonResponse
import com.fares.saveme.feature.auth.problemBody
import com.fares.saveme.feature.reports.data.dto.ReportByCategoryDto
import com.fares.saveme.feature.reports.data.dto.ReportSummaryDto
import com.fares.saveme.feature.reports.data.dto.ReportTrendDto
import io.ktor.http.HttpMethod
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Every operation and handled code of contracts/api-client.md, with the examples of reports.yaml v1.11 (P-14.3). */
class ReportsApiContractTest {

    private val wallet = "0192f79e-0000-7000-8000-000000000001"

    private fun money(minor: Long) = """{"amount_minor":$minor,"currency":"EGP"}"""

    /** reports.yaml `currentPeriod` (wallet summary): scheduled rent included. */
    private val currentPeriod = """
        {"data":{"wallet_id":"$wallet","range":{"from":"2026-10-01","to":"2026-10-31"},"currency":"EGP",
         "income":${money(1_000_000)},"expenses":${money(350_000)},"net":${money(650_000)},"transfers_in":${money(0)},
         "transfers_out":${money(200_000)},"balance_start":${money(80_000)},"balance_end":${money(530_000)},
         "planned":{"income":${money(0)},"expenses":${money(500_000)},"transfers_in":${money(0)},"transfers_out":${money(0)}}},
         "meta":{}}
    """.trimIndent()

    /** `creditCard`: planned null, negative balances. */
    private val creditCard = """
        {"data":{"wallet_id":"0192f79e-0000-7000-8000-000000000003","range":{"from":"2026-09-01","to":"2026-09-30"},"currency":"EGP",
         "income":${money(0)},"expenses":${money(420_000)},"net":${money(-420_000)},"transfers_in":${money(300_000)},"transfers_out":${money(0)},
         "balance_start":${money(-300_000)},"balance_end":${money(-420_000)},"planned":null},"meta":{}}
    """.trimIndent()

    /** Cross-wallet `egp` summary with a USD wallet excluded. */
    private val egpSummary = """
        {"data":{"wallet_id":null,"range":{"from":"2026-10-01","to":"2026-10-31"},"currency":"EGP","income":${money(1_000_000)},
         "expenses":${money(770_000)},"net":${money(230_000)},"transfers_in":${money(0)},"transfers_out":${money(50_000)},
         "balance_start":${money(2_480_000)},"balance_end":${money(2_660_000)},"planned":null},
         "meta":{"excluded_wallets":[{"wallet_id":"0192f79e-0000-7000-8000-000000000004","currency":"USD"}]}}
    """.trimIndent()

    private val byCategory = """
        {"data":{"wallet_id":"$wallet","range":{"from":"2026-10-01","to":"2026-10-31"},"currency":"EGP",
         "expenses":{"total":${money(550_000)},"planned":null,"items":[
           {"label":"groceries","category_id":"0192f7c0-0000-7000-8000-000000000101","system_key":"groceries","value":${money(350_000)},"planned":null},
           {"label":"transport","category_id":"0192f7c0-0000-7000-8000-000000000105","system_key":"transport","value":${money(120_000)},"planned":null},
           {"label":"0192f7c1-0000-7000-8000-0000000000a7","category_id":"0192f7c1-0000-7000-8000-0000000000a7","system_key":null,"value":${money(80_000)},"planned":null}]},
         "income":{"total":${money(1_000_000)},"planned":null,"items":[
           {"label":"salary","category_id":"0192f7c0-0000-7000-8000-000000000120","system_key":"salary","value":${money(1_000_000)},"planned":null,"a_later_member":1}]}},
         "meta":{}}
    """.trimIndent()

    private val withPlanned = """
        {"data":{"wallet_id":"$wallet","range":{"from":"2026-10-01","to":"2026-10-31"},"currency":"EGP",
         "expenses":{"total":${money(0)},"planned":${money(500_000)},"items":[
           {"label":"rent","category_id":"0192f7c0-0000-7000-8000-000000000102","system_key":"rent","value":${money(0)},"planned":${money(500_000)}}]},
         "income":{"total":${money(0)},"planned":${money(0)},"items":[]}},"meta":{}}
    """.trimIndent()

    private fun series(vararg values: Pair<String, Long>) = values.joinToString(",", "[", "]") { (label, minor) -> """{"label":"$label","value":${money(minor)}}""" }

    /** `threeMonths`: wallet trend, planned null. */
    private val threeMonths = """
        {"data":{"wallet_id":"$wallet","currency":"EGP","periods":[
           {"label":"2026-08-01","from":"2026-08-01","to":"2026-08-31"},{"label":"2026-09-01","from":"2026-09-01","to":"2026-09-30"},
           {"label":"2026-10-01","from":"2026-10-01","to":"2026-10-31"}],
         "income":${series("2026-08-01" to 0, "2026-09-01" to 1_000_000, "2026-10-01" to 1_000_000)},
         "expenses":${series("2026-08-01" to 0, "2026-09-01" to 1_200_000, "2026-10-01" to 350_000)},
         "net":${series("2026-08-01" to 0, "2026-09-01" to -200_000, "2026-10-01" to 650_000)},
         "planned_income":null,"planned_expenses":null},"meta":{}}
    """.trimIndent()

    /** `twoPeriodsPlanned`: cross-wallet trend from the 25th with scheduled sums. */
    private val twoPeriodsPlanned = """
        {"data":{"wallet_id":null,"currency":"EGP","periods":[
           {"label":"2026-08-25","from":"2026-08-25","to":"2026-09-24"},{"label":"2026-09-25","from":"2026-09-25","to":"2026-10-24"}],
         "income":${series("2026-08-25" to 1_000_000, "2026-09-25" to 1_000_000)},"expenses":${series("2026-08-25" to 900_000, "2026-09-25" to 300_000)},
         "net":${series("2026-08-25" to 100_000, "2026-09-25" to 700_000)},
         "planned_income":${series("2026-08-25" to 0, "2026-09-25" to 0)},"planned_expenses":${series("2026-08-25" to 0, "2026-09-25" to 500_000)}},
         "meta":{"excluded_wallets":[]}}
    """.trimIndent()

    private fun env(handler: suspend io.ktor.client.engine.mock.MockRequestHandleScope.(io.ktor.client.request.HttpRequestData) -> io.ktor.client.request.HttpResponseData) = AuthTestEnv(handler)

    private val first = LocalDate(2026, 10, 1)
    private val last = LocalDate(2026, 10, 31)

    @Test
    fun theWalletSummaryAsksForAnExplicitRangeAndPlannedAndDecodesTheExamples() = runTest {
        val env = env { jsonResponse(200, currentPeriod) }
        val answer = assertIs<ApiResult.Success<ReportAnswer<ReportSummaryDto>>>(env.apiOf().summary(first, last, walletId = wallet, includePlanned = true)).value
        val request = env.requests.single()
        assertEquals(HttpMethod.Get, request.method)
        assertTrue(request.url.encodedPath.endsWith("/wallets/$wallet/reports/summary"))
        assertEquals(setOf("from", "to", "include_planned"), request.url.parameters.names())
        assertEquals("2026-10-01", request.url.parameters["from"])
        assertEquals("2026-10-31", request.url.parameters["to"])
        assertEquals("true", request.url.parameters["include_planned"])
        assertEquals("android", request.headers["X-Client-Platform"])
        assertEquals(1_000_000, answer.data.income.amountMinor)
        assertEquals(650_000, answer.data.net.amountMinor)
        assertEquals(500_000, answer.data.planned!!.expenses.amountMinor)
        assertEquals(wallet, answer.data.walletId)
        assertEquals(emptyList(), answer.excludedWallets, "a wallet report's meta is {}")
    }

    @Test
    fun aNullPlannedAndNegativeBalancesDecode() = runTest {
        val env = env { jsonResponse(200, creditCard) }
        val answer = assertIs<ApiResult.Success<ReportAnswer<ReportSummaryDto>>>(env.apiOf().summary(first, last, walletId = wallet)).value
        assertNull(answer.data.planned)
        assertEquals(-300_000, answer.data.balanceStart.amountMinor)
        assertEquals("false", env.requests.single().url.parameters["include_planned"])
    }

    @Test
    fun theCrossWalletSummarySendsTheCurrencyAndDecodesExcludedWallets() = runTest {
        val env = env { jsonResponse(200, egpSummary) }
        val answer = assertIs<ApiResult.Success<ReportAnswer<ReportSummaryDto>>>(env.apiOf().summary(first, last, includePlanned = false, currency = "EGP")).value
        val request = env.requests.single()
        assertTrue(request.url.encodedPath.endsWith("/reports/summary") && !request.url.encodedPath.contains("/wallets/"))
        assertEquals("EGP", request.url.parameters["currency"])
        assertNull(answer.data.walletId)
        assertEquals(listOf("USD"), answer.excludedWallets.map { it.currency })
        assertEquals("0192f79e-0000-7000-8000-000000000004", answer.excludedWallets.single().walletId)
    }

    @Test
    fun byCategoryDecodesItemsInOrderWithPlatformAndPersonalCategoriesAndIgnoresAnUnknownMember() = runTest {
        val env = env { jsonResponse(200, byCategory) }
        val answer = assertIs<ApiResult.Success<ReportAnswer<ReportByCategoryDto>>>(env.apiOf().byCategory(first, last, walletId = wallet)).value.data
        assertTrue(env.requests.single().url.encodedPath.endsWith("/wallets/$wallet/reports/by-category"))
        assertEquals(listOf("groceries", "transport", "0192f7c1-0000-7000-8000-0000000000a7"), answer.expenses.items.map { it.label })
        assertEquals("groceries", answer.expenses.items.first().systemKey)
        assertNull(answer.expenses.items.last().systemKey)
        assertEquals(550_000, answer.expenses.total.amountMinor)
        assertNull(answer.expenses.planned)
        assertEquals(1_000_000, answer.income.items.single().value.amountMinor)
    }

    @Test
    fun plannedSumsDecodeOnItemsAndGroups() = runTest {
        val env = env { jsonResponse(200, withPlanned) }
        val answer = assertIs<ApiResult.Success<ReportAnswer<ReportByCategoryDto>>>(env.apiOf().byCategory(first, last, walletId = wallet, includePlanned = true)).value.data
        assertEquals(500_000, answer.expenses.planned!!.amountMinor)
        assertEquals(500_000, answer.expenses.items.single().planned!!.amountMinor)
        assertEquals(0, answer.expenses.items.single().value.amountMinor)
        assertEquals(0, answer.income.planned!!.amountMinor)
    }

    @Test
    fun theTrendSendsMonthsAndDateAndDecodesPlannedNullAndSeries() = runTest {
        val env = env { jsonResponse(200, threeMonths) }
        val answer = assertIs<ApiResult.Success<ReportAnswer<ReportTrendDto>>>(env.apiOf().trend(3, last, walletId = wallet, includePlanned = false)).value.data
        val request = env.requests.single()
        assertTrue(request.url.encodedPath.endsWith("/wallets/$wallet/reports/trend"))
        assertEquals("3", request.url.parameters["months"])
        assertEquals("2026-10-31", request.url.parameters["date"])
        assertEquals("false", request.url.parameters["include_planned"])
        assertEquals(3, answer.periods.size)
        assertEquals(listOf(0L, -200_000L, 650_000L).drop(0), answer.net.map { it.value.amountMinor })
        assertNull(answer.plannedIncome)
    }

    @Test
    fun theCrossWalletTrendDecodesPlannedSeries() = runTest {
        val env = env { jsonResponse(200, twoPeriodsPlanned) }
        val answer = assertIs<ApiResult.Success<ReportAnswer<ReportTrendDto>>>(env.apiOf().trend(2, LocalDate(2026, 10, 2), includePlanned = true, currency = "EGP")).value
        assertEquals(listOf("2026-08-25", "2026-09-25"), answer.data.periods.map { it.from })
        assertEquals(500_000, answer.data.plannedExpenses!!.last().value.amountMinor)
        assertEquals(emptyList(), answer.excludedWallets)
    }

    @Test
    fun thePeriodLookupSendsOnlyMonthsAndDate() = runTest {
        val env = env { jsonResponse(200, twoPeriodsPlanned) }
        env.apiOf().trend(2, LocalDate(2026, 10, 2))
        val request = env.requests.single()
        assertEquals(setOf("months", "date"), request.url.parameters.names(), "neither currency nor include_planned (FR-019)")
        assertTrue(request.url.encodedPath.endsWith("/reports/trend"))
    }

    @Test
    fun noReportDtoHasAShareMember() {
        listOf(ReportSummaryDto.serializer(), ReportByCategoryDto.serializer(), ReportTrendDto.serializer()).forEach { serializer ->
            val names = (0 until serializer.descriptor.elementsCount).map { serializer.descriptor.getElementName(it) }
            assertTrue(names.none { "share" in it || "percent" in it }, "$names")
        }
        val item = com.fares.saveme.feature.reports.data.dto.CategoryItemDto.serializer().descriptor
        assertTrue((0 until item.elementsCount).map { item.getElementName(it) }.none { "share" in it || "percent" in it })
    }

    @Test
    fun theClientRefusesQueriesTheServerWouldRefuse() = runTest {
        val env = env { request -> jsonResponse(200, if (request.url.encodedPath.endsWith("/trend")) threeMonths else currentPeriod) }
        val api = env.apiOf()
        assertFailsWith<IllegalArgumentException> { api.summary(last, first) }
        assertFailsWith<IllegalArgumentException> { api.summary(LocalDate(2026, 1, 1), LocalDate(2027, 1, 2)) }
        assertFailsWith<IllegalArgumentException> { api.trend(13, last) }
        assertFailsWith<IllegalArgumentException> { api.trend(0, last) }
        assertFailsWith<IllegalArgumentException> { api.summary(first, last, currency = "egp") }
        assertFailsWith<IllegalArgumentException> { api.summary(LocalDate(1899, 12, 31), last) }
        assertEquals(emptyList(), env.requests, "nothing was sent")
        // The limits themselves are accepted.
        assertIs<ApiResult.Success<*>>(api.summary(LocalDate(2026, 1, 1), LocalDate(2027, 1, 1)))
        assertIs<ApiResult.Success<*>>(api.trend(12, last))
        assertIs<ApiResult.Success<*>>(api.trend(1, last))
    }

    @Test
    fun everyRefusalIsMappedByItsCode() = runTest {
        val cases = listOf(
            Triple(400, "INVALID_QUERY_PARAMETER", ApiErrorCode.UNKNOWN),
            Triple(401, "UNAUTHENTICATED", ApiErrorCode.UNAUTHENTICATED),
            Triple(404, "NOT_FOUND", ApiErrorCode.NOT_FOUND),
            Triple(422, "INVALID_ID", ApiErrorCode.UNKNOWN),
            Triple(426, "CLIENT_VERSION_UNSUPPORTED", ApiErrorCode.CLIENT_VERSION_UNSUPPORTED),
            Triple(500, "INTERNAL_ERROR", ApiErrorCode.INTERNAL_ERROR),
        )
        cases.forEach { (status, code, mapped) ->
            val env = env { jsonResponse(status, problemBody(status, code)) }
            val failure = assertIs<ApiResult.Failure>(env.apiOf().summary(first, last, walletId = wallet))
            assertEquals(code, failure.problem.code, "raw code of $status")
            assertEquals(mapped, failure.code, "mapped code of $status")
            assertEquals("req-42", failure.problem.requestId)
        }
    }

    private fun AuthTestEnv.apiOf() = ReportsApi(client)
}
