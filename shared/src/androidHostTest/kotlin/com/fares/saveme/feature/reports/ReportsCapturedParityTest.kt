package com.fares.saveme.feature.reports

import android.content.Context
import androidx.room.Room
import androidx.sqlite.driver.AndroidSQLiteDriver
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.testing.WorkManagerTestInitHelper
import co.touchlab.kermit.Logger
import co.touchlab.kermit.LogWriter
import co.touchlab.kermit.Severity
import com.fares.saveme.appFeatureModules
import com.fares.saveme.core.config.AppConfig
import com.fares.saveme.core.config.Environment
import com.fares.saveme.core.database.AppDatabase
import com.fares.saveme.core.di.androidPlatformModule
import com.fares.saveme.core.di.initKoin
import com.fares.saveme.core.network.AppJson
import com.fares.saveme.core.network.createHttpClient
import com.fares.saveme.core.storage.InMemorySecureStore
import com.fares.saveme.core.storage.SecureStore
import com.fares.saveme.core.sync.ReadOutcome
import com.fares.saveme.core.sync.ReadTarget
import com.fares.saveme.core.sync.SyncRunner
import com.fares.saveme.feature.account.domain.ProfileSyncer
import com.fares.saveme.feature.auth.data.dto.SignInResultDto
import com.fares.saveme.feature.auth.domain.SessionManager
import com.fares.saveme.feature.categories.domain.CategoriesService
import com.fares.saveme.feature.categories.domain.model.CategorySource
import com.fares.saveme.feature.recurring.domain.ScheduledMoney
import com.fares.saveme.feature.reports.CapturedReportsResponses.Captured
import com.fares.saveme.feature.reports.data.ReportsApi
import com.fares.saveme.feature.reports.data.dto.ReportByCategoryDto
import com.fares.saveme.feature.reports.data.dto.ReportSummaryDto
import com.fares.saveme.feature.reports.data.dto.ReportTrendDto
import com.fares.saveme.feature.reports.domain.DefaultReportsService
import com.fares.saveme.feature.reports.domain.DevReportParityProbe
import com.fares.saveme.feature.reports.domain.LearnedPeriods
import com.fares.saveme.feature.reports.domain.ReportChoicesStore
import com.fares.saveme.feature.reports.domain.ReportMapping
import com.fares.saveme.feature.reports.domain.ReportParityProbe
import com.fares.saveme.feature.reports.domain.model.RangeSelection
import com.fares.saveme.feature.reports.domain.model.Report
import com.fares.saveme.feature.reports.domain.model.ReportChoices
import com.fares.saveme.feature.reports.domain.model.ReportScope
import com.fares.saveme.feature.reports.domain.model.ReportState
import com.fares.saveme.feature.reports.domain.model.TrendSlot
import com.fares.saveme.feature.transactions.domain.ReportLedger
import com.fares.saveme.feature.transactions.domain.TransactionsService
import com.fares.saveme.feature.wallets.domain.WalletsService
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.GlobalContext
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.robolectric.RobolectricTestRunner
import kotlin.time.Instant
import com.fares.saveme.feature.reports.CapturedReportsResponses as R

/**
 * SC-002 and SC-004 against the live backend (spec 014 T089/T090): the real feed pages of three QA accounts (an owner with
 * eight wallets, a shared wallet with a partner, an archived wallet, a deleted wallet, a USD wallet, scheduled rules; the
 * partner; and an account with a pending start day change) go through the app's real graph (sync runner, puller, wallet,
 * transaction, category and recurring appliers, an in-memory Room database, `TransactionReportLedger`, `ScheduledMoney`,
 * labels, `ReportPeriods`, `ReportCalculator`). Every figure of every recorded report answer must equal the phone's
 * for the same wallets, range, currency and switch. The one recorded difference is backend gap 2 (see [gapTwo]).
 */
@RunWith(RobolectricTestRunner::class)
class ReportsCapturedParityTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var db: AppDatabase

    /** What the mock server answers: the person's feeds and the recorded lookups and refusals. */
    private class Persona(
        val uid: String,
        val userFeed: String,
        val heads: String,
        val wallets: Map<String, List<String>>,
        val reports: Map<String, Captured>,
    )

    private class Phone(val persona: Persona, val service: DefaultReportsService, val choices: ReportChoicesStore, val learned: LearnedPeriods, val runner: SyncRunner, val api: ReportsApi)

    private val signInTemplate = { uid: String ->
        """{"credential":"test-credential","session":{"id":"0192f7a0-1c3d-7abc-9f01-2b3c4d5e6f70","user_id":"$uid","version":1,"device_name":"QA host","platform":"android","app_version":"1.0.0","state":"active","end_reason":null,"signed_in_at":"2026-10-06T00:00:00Z","last_used_at":null,"ended_at":null,"current":true},"account":{"id":"$uid","email":"qa@example.com","email_verified":true,"state":"active","deletion_scheduled_for":null},"entitlements":{"plan":{"id":"p","name":"Plus"},"features":{"wallet_sharing":true,"wallets_max":10}},"deletion_cancelled":false}"""
    }

    @After
    fun tearDown() {
        db.close()
        stopKoin()
    }

    private val personaA get() = Persona(R.USER_A, R.FEED_A_USER, R.HEADS_A, R.FEEDS_A_WALLETS, CapturedReportsA.all + CapturedReportsRefusals.all)
    private val personaB get() = Persona(R.USER_B, R.FEED_B_USER, R.HEADS_B, R.FEEDS_B_WALLETS, CapturedReportsB.all)
    private val personaD get() = Persona(R.USER_D, R.FEED_D_USER, R.HEADS_D, R.FEEDS_D_WALLETS, CapturedReportsD.all)

    private fun nextCursor(page: String): String? =
        (AppJson.parseToJsonElement(page).jsonObject.getValue("meta").jsonObject["next_cursor"] as? JsonPrimitive)?.takeIf { it.isString }?.content

    private fun feedPage(pages: List<String>, cursor: String?): String {
        if (cursor == null) return pages.first()
        val index = pages.indexOfFirst { nextCursor(it) == cursor }
        if (index >= 0 && index + 1 < pages.size) return pages[index + 1]
        return """{"data":[],"meta":{"next_cursor":null,"has_more":false,"limit":200,"sync_cursor":"$cursor"}}"""
    }

    private fun requestKey(request: HttpRequestData): String {
        val query = request.url.parameters.entries().sortedBy { it.key }.joinToString("&") { (k, v) -> v.joinToString("&") { "$k=$it" } }
        return request.url.encodedPath.substringAfter("/api/v1/") + if (query.isEmpty()) "" else "?$query"
    }

    private fun normalized(path: String): String {
        val base = path.substringBefore('?')
        val query = path.substringAfter('?', "")
        if (query.isEmpty()) return base
        return base + "?" + query.split('&').sorted().joinToString("&")
    }

    private fun answerFor(persona: Persona, request: HttpRequestData): Pair<Int, String> {
        val path = request.url.encodedPath.substringAfter("/api/v1/")
        val cursor = request.url.parameters["cursor"]
        return when {
            path == "sync/wallets/heads" -> 200 to persona.heads
            path == "sync/user/changes" -> 200 to feedPage(listOf(persona.userFeed), cursor)
            path.startsWith("sync/wallets/") && path.endsWith("/changes") -> {
                val id = path.removePrefix("sync/wallets/").removeSuffix("/changes")
                200 to feedPage(persona.wallets.getValue(id), cursor)
            }
            path == "platform-categories" -> 200 to R.PLATFORM
            path.contains("reports/") -> {
                val key = requestKey(request)
                val captured = persona.reports.values.firstOrNull { normalized(it.path) == key }
                    ?: persona.reports.values.firstOrNull { it.status != 200 && it.path.substringBefore('?') == path.substringBefore('?') }
                if (captured != null) captured.status to captured.body
                else 404 to """{"type":"t","title":"x","status":404,"detail":"no recorded answer for $key","code":"NOT_FOUND","request_id":"qa"}"""
            }
            else -> 404 to """{"type":"t","title":"x","status":404,"detail":"no recorded answer for $path","code":"NOT_FOUND","request_id":"qa"}"""
        }
    }

    /** Starts the app's real graph on an in-memory database and the recorded answers, signs the person in and reads everything. */
    private fun startPhone(persona: Persona, probe: ((ReportsApi) -> ReportParityProbe)? = null): Phone {
        WorkManagerTestInitHelper.initializeTestWorkManager(context, Configuration.Builder().build())
        initKoin(
            config = AppConfig(baseUrl = "http://localhost", linkHost = "localhost", environment = Environment.Dev, isDebugTools = true),
            platformModule = androidPlatformModule(context, "test"),
            featureModules = appFeatureModules + module {
                single<AppDatabase> { Room.inMemoryDatabaseBuilder<AppDatabase>(context).setDriver(AndroidSQLiteDriver()).build() }
                single<SecureStore> { InMemorySecureStore() }
                single {
                    createHttpClient(
                        engine = MockEngine { request ->
                            val (status, body) = answerFor(persona, request)
                            respond(body, HttpStatusCode.fromValue(status), headersOf(HttpHeaders.ContentType, "application/json"))
                        },
                        config = get(), clientInfo = get(), credentialProvider = { "test-credential" }, languageProvider = { "en" }, requestTimeoutMillis = null,
                    )
                }
            },
        ) { androidContext(context) }
        val koin = GlobalContext.get()
        db = koin.get()
        val sessions = koin.get<SessionManager>()
        val runner = koin.get<SyncRunner>()
        runBlocking {
            sessions.establish(AppJson.decodeFromString(SignInResultDto.serializer(), signInTemplate(persona.uid)))
            assertEquals(ReadOutcome.Done, koin.get<CategoriesService>().refresh(force = true).let { if (it == com.fares.saveme.feature.auth.domain.model.RefreshOutcome.Done) ReadOutcome.Done else ReadOutcome.Failed })
            assertEquals(ReadOutcome.Done, runner.refresh(ReadTarget.Everything, force = true))
        }
        val choices = ReportChoicesStore()
        val api = koin.get<ReportsApi>()
        val learned = LearnedPeriods()
        val service = DefaultReportsService(
            wallets = koin.get<WalletsService>(), transactions = koin.get<TransactionsService>(), ledger = koin.get<ReportLedger>(),
            scheduled = koin.get<ScheduledMoney>(), categories = koin.get<CategoriesService>(), profile = koin.get<ProfileSyncer>().view,
            deviceZoneId = { "Africa/Cairo" }, authState = sessions.state, choices = choices, learned = learned, api = api, runner = runner,
            probe = probe?.invoke(api) ?: com.fares.saveme.feature.reports.domain.NoParityProbe,
            // 2026-10-06 03:30 in Cairo: the day the answers were recorded (the backend generates each rule's occurrences to today + 35 days).
            now = MutableStateFlow(Instant.parse("2026-10-06T00:30:00Z")),
        )
        return Phone(persona, service, choices, learned, runner, api)
    }

    // ---- cases ---------------------------------------------------------------------------------------------------

    private data class Case(
        val label: String,
        val scope: ReportScope,
        val kind: String,
        val from: LocalDate?,
        val to: LocalDate?,
        val months: Int?,
        val date: LocalDate?,
        val planned: Boolean,
        val currency: String?,
        val captured: Captured,
    )

    private fun parse(label: String, captured: Captured): Case? {
        val path = captured.path.substringBefore('?')
        val query = captured.path.substringAfter('?', "").split('&').filter { it.isNotEmpty() }.associate { it.substringBefore('=') to it.substringAfter('=') }
        val walletId = Regex("^wallets/([^/]+)/reports/").find(path)?.groupValues?.get(1)
        val kind = path.substringAfterLast("reports/")
        return Case(
            label, if (walletId == null) ReportScope.AllWallets else ReportScope.Wallet(walletId), kind,
            query["from"]?.let(LocalDate::parse), query["to"]?.let(LocalDate::parse), query["months"]?.toInt(), query["date"]?.let(LocalDate::parse),
            query["include_planned"] == "true", query["currency"], captured,
        )
    }

    private fun comparable(all: Map<String, Captured>): List<Case> = all.mapNotNull { (label, captured) ->
        // Lookups (months and date only), refusals, wallet reads and the wallet-scoped currency probe are not figure comparisons.
        if (captured.status != 200 || "/lookup/" in label || "/wallet/" in label) null else parse(label, captured)
    }

    private fun choicesFor(case: Case): ReportChoices {
        val range = when {
            case.from != null && case.to != null -> RangeSelection.Custom(case.from, case.to)
            case.date != null -> RangeSelection.PeriodContaining(case.date)
            else -> RangeSelection.CurrentPeriod
        }
        return ReportChoices(range = range, trendLength = case.months ?: ReportChoices.DEFAULT_TREND_LENGTH, currency = case.currency, includeScheduled = case.planned)
    }

    private suspend fun reportFor(phone: Phone, case: Case): Report {
        phone.choices.update(phone.persona.uid, case.scope) { choicesFor(case) }
        // Each case learns its periods the way a fresh open does: every unknown slot in one lookup.
        phone.learned.clear()
        // An unknown Budget Period is learned from GET /reports/trend with only months and date (FR-019), as the app does after a read.
        phone.service.refresh(case.scope, force = true)
        return (phone.service.observe(case.scope).first { it is ReportState.Ready } as ReportState.Ready).report
    }

    private fun body(case: Case): JsonObject = AppJson.parseToJsonElement(case.captured.body).jsonObject

    private fun excluded(case: Case): List<com.fares.saveme.feature.reports.data.dto.ExcludedWalletDto> =
        (body(case)["meta"] as? JsonObject)?.get("excluded_wallets")?.let {
            AppJson.decodeFromJsonElement(kotlinx.serialization.builtins.ListSerializer(com.fares.saveme.feature.reports.data.dto.ExcludedWalletDto.serializer()), it)
        } ?: emptyList()

    /** The differing members of one case (empty when the phone's figures equal the server's). */
    private suspend fun differences(phone: Phone, case: Case): Pair<Report, List<String>> {
        val report = reportFor(phone, case)
        return report to differences(report, case)
    }

    private fun differences(report: Report, case: Case): List<String> {
        val data = body(case).getValue("data")
        val members = mutableListOf<String>()
        when (case.kind) {
            "summary" -> {
                val dto = AppJson.decodeFromJsonElement(ReportSummaryDto.serializer(), data)
                val range = report.range ?: return listOf("range unknown")
                if (range.from.toString() != dto.range.from || range.to.toString() != dto.range.to) members += "range"
                members += ReportMapping.differingSummaryMembers(report.summary!!, report.planned, dto)
                if (case.scope is ReportScope.AllWallets && ReportMapping.differingExcluded(report.covered.excluded.map { it.walletId to it.currency }.toSet(), excluded(case))) members += "excluded_wallets"
                if (report.currency != dto.currency) members += "currency"
            }
            "by-category" -> {
                val dto = AppJson.decodeFromJsonElement(ReportByCategoryDto.serializer(), data)
                if (report.range == null) return listOf("range unknown")
                members += ReportMapping.differingCategoryMembers(report.breakdown!!, dto)
            }
            "trend" -> {
                val dto = AppJson.decodeFromJsonElement(ReportTrendDto.serializer(), data)
                val points = report.trend.map { (it as? TrendSlot.Known)?.point }
                if (points.any { it == null }) return listOf("periods unknown")
                members += ReportMapping.differingTrendMembers(points.filterNotNull(), dto, case.planned)
                if (case.scope is ReportScope.AllWallets && ReportMapping.differingExcluded(report.covered.excluded.map { it.walletId to it.currency }.toSet(), excluded(case))) members += "excluded_wallets"
            }
            else -> error("unknown kind ${case.kind}")
        }
        return members
    }

    private fun compareAll(persona: Persona, hasGapTwo: Boolean = false) = runBlocking {
        val phone = startPhone(persona)
        val cases = comparable(persona.reports)
        assertTrue("recorded cases", cases.size > 20)
        val unexpected = mutableListOf<String>()
        var gap = 0
        for (case in cases.sortedBy { it.label }) {
            val (report, diff) = differences(phone, case)
            val expected = hasGapTwo && isGapTwo(case, report)
            when {
                diff.isEmpty() && !expected -> Unit
                diff == listOf("planned.transfers_in") && expected -> gap++
                else -> unexpected += "${case.label}: $diff (gap 2 expected: $expected)"
            }
        }
        assertEquals("every recorded figure equals the phone's (${cases.size} reports)", emptyList<String>(), unexpected)
        if (hasGapTwo) assertTrue("gap 2 shows in $gap summaries", gap > 0)
        cases.size
    }

    @Test
    fun theOwnersEveryRecordedReportEqualsThePhonesFigures() {
        compareAll(personaA, hasGapTwo = true)
    }

    @Test
    fun thePartnersEveryRecordedReportEqualsThePhonesFigures() {
        // The partner owns both ends of the transfer rule into the shared wallet, so nothing is hidden from this phone.
        compareAll(personaB)
    }

    @Test
    fun theStartDayChangeStillAheadGivesEveryPeriodFromTheServerAndTheFiguresEqual() {
        compareAll(personaD)
    }

    /**
     * Backend gap 2 (spec 014 §9), settled by the live QA: the partner's scheduled transfer from a wallet the owner is not
     * in into the shared wallet lives on the partner's rule, which no feed the owner reads carries (the shared wallet's
     * feed carries the partner's transactions, rules and occurrences made in it, and transactions in another wallet that
     * name it as the destination, but not another wallet's rule). The server counts its 30,000 in the planned transfers in
     * of the shared wallet's report and of the owner's cross-wallet report; the phone cannot. Only planned transfers in
     * differ, only where the range contains 22 October, and by exactly that amount.
     */
    @Test
    fun gapTwoTheScheduledTransferInFromAWalletThePersonIsNotInDoesNotReachThePhone() = runBlocking {
        val phone = startPhone(personaA)
        val case = comparable(personaA.reports).single { it.label == "A/shared/summary/oct/true" }
        val report = reportFor(phone, case)
        val server = AppJson.decodeFromJsonElement(ReportSummaryDto.serializer(), body(case).getValue("data"))
        assertEquals(30_000L, server.planned!!.transfersIn.amountMinor)
        assertEquals(0L, report.planned!!.transfersIn)
        assertEquals(listOf("planned.transfers_in"), ReportMapping.differingSummaryMembers(report.summary!!, report.planned, server))
        // The transfer made (not scheduled) in the same direction is on the phone: the shared wallet's feed carries it.
        assertEquals(60_000L, report.summary!!.transfersIn)
        assertEquals(60_000L, server.transfersIn.amountMinor)
    }

    @Test
    fun theSharedWalletReportCountsEveryMembersTransactionsAndALeftOutWalletsTransferLandsInItsDirection() = runBlocking {
        val phone = startPhone(personaA)
        val report = reportFor(phone, comparable(personaA.reports).single { it.label == "A/shared/summary/oct/false" })
        // The owner's 1,000.00 and the partner's 150.00 + 500.00 groceries and snacks; the partner's deleted 770.00 does not count.
        assertEquals(165_000L, report.summary!!.expenses)
        assertEquals(8_000L, report.summary!!.income)
        // The partner's transfer from their own wallet (not a wallet of this phone) and the transfer out to it.
        assertEquals(60_000L, report.summary!!.transfersIn)
        assertEquals(25_000L, report.summary!!.transfersOut)
        val cross = reportFor(phone, comparable(personaA.reports).single { it.label == "A/all/summary/oct/false" })
        // 800.00 in: the partner's 600.00 into Household and the 200.00 transfer from the deleted wallet into Cash.
        assertEquals(80_000L, cross.summary!!.transfersIn)
        // 300.00 out: the shared wallet's 250.00 to the partner's wallet and the partner's 50.00 from the family wallet (this person views it).
        assertEquals(30_000L, cross.summary!!.transfersOut)
        assertEquals(listOf("USD"), cross.covered.excluded.map { it.currency })
    }

    @Test
    fun theCategoriesOfTheLiveSetResolveToNamesOnThePhone() = runBlocking {
        val phone = startPhone(personaA)
        val case = comparable(personaA.reports).single { it.label == "A/all/by-category/oct/false" }
        val report = reportFor(phone, case)
        val labels = phone.service.observeLabels(ReportScope.AllWallets).first()
        val displays = (report.breakdown!!.expenses.items + report.breakdown!!.income.items).map { it to labels.display(it.categoryId) }
        // Platform categories by their system key, the person's own categories by name; the partner's category in a shared wallet by its reference.
        assertTrue(displays.none { (_, display) -> display.source == CategorySource.Unavailable })
        assertTrue(displays.any { (item, display) -> item.systemKey == null && display.name == "Gym" })
        assertTrue(displays.any { (item, display) -> item.systemKey == null && display.name == "Groceries" })
        assertTrue(displays.any { (item, display) -> item.systemKey == "groceries" })
        val shared = phone.service.observeLabels(ReportScope.Wallet(R.WALLET_SHARED)).first()
        val sharedReport = reportFor(phone, comparable(personaA.reports).single { it.label == "A/shared/by-category/oct/false" })
        val names = sharedReport.breakdown!!.expenses.items.map { shared.display(it.categoryId).name }
        assertTrue("$names", "B Snacks" in names)
    }

    @Test
    fun theRecordedRefusalsAreHandledByCodeAndDoNotHideAnythingOnThePhone() = runBlocking {
        val phone = startPhone(personaA)
        val codes = CapturedReportsRefusals.all.values.associate { it.path to it.status }
        assertEquals(setOf(404, 422), codes.values.toSet())
        // The client never sends a malformed range, month count or currency (it refuses first); an unknown wallet id answers 404, a malformed one 422.
        val unknown = phone.api.summary(LocalDate(2026, 10, 1), LocalDate(2026, 10, 31), walletId = R.WALLET_BOWN)
        assertTrue(unknown is com.fares.saveme.core.network.ApiResult.Failure && unknown.problem.code == "NOT_FOUND")
        val malformed = phone.api.summary(LocalDate(2026, 10, 1), LocalDate(2026, 10, 31), walletId = "not-a-uuid")
        assertTrue(malformed is com.fares.saveme.core.network.ApiResult.Failure && malformed.problem.code == "INVALID_ID")
    }

    // ---- the development probe against the recorded answers (FR-028) ------------------------------------------------------

    private class Captures : LogWriter() {
        val lines = mutableListOf<String>()
        override fun log(severity: Severity, message: String, tag: String, throwable: Throwable?) {
            if (tag == "reports") lines += message
        }
    }

    @Test
    fun theDevelopmentProbeFindsTheOwnersSyncedFiguresEqualToTheRecordedAnswers() = runBlocking {
        val captures = Captures()
        val previous = Logger.config.logWriterList
        Logger.setLogWriters(captures)
        try {
            val phone = startPhone(personaA, probe = { api -> DevReportParityProbe(api) })
            phone.service.refresh(ReportScope.AllWallets, force = true)
            phone.service.refresh(ReportScope.Wallet(R.WALLET_SHARED), force = true)
            val probes = captures.lines.filter { it.startsWith("report.parity") }
            // Summary and by-category of the owner's cross-wallet report and the shared wallet equal the recorded answers; trends too.
            assertTrue(probes.toString(), probes.any { it == "report.parity ok kind=summary scope=all" })
            assertTrue(probes.toString(), probes.any { it == "report.parity ok kind=by-category scope=all" })
            assertTrue(probes.toString(), probes.any { it == "report.parity ok kind=trend scope=all" })
            assertTrue(probes.toString(), probes.none { "mismatch" in it })
            // No amount, share, category or wallet name is in a log line (FR-025).
            assertTrue(captures.lines.none { Regex("\\d{4,}").containsMatchIn(it.replace(Regex("[0-9a-f]{8}-[0-9a-f-]{27}"), "")) && "request=" !in it })
        } finally {
            Logger.setLogWriters(*previous.toTypedArray())
        }
    }

    /** The one recorded difference: planned transfers in of a summary whose range contains 22 October and counts the shared wallet. */
    private fun isGapTwo(case: Case, report: Report): Boolean {
        val range = report.range ?: return false
        val countsShared = case.scope == ReportScope.AllWallets && report.currency == "EGP" || case.scope == ReportScope.Wallet(R.WALLET_SHARED)
        return case.kind == "summary" && case.planned && countsShared && LocalDate(2026, 10, 22) in range.from..range.to
    }
}
