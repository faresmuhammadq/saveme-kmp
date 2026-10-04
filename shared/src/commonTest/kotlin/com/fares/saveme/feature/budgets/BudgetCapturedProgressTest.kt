package com.fares.saveme.feature.budgets

import com.fares.saveme.core.network.AppJson
import com.fares.saveme.core.sync.CapturedSyncResponses
import com.fares.saveme.core.sync.CapturedSyncServer
import com.fares.saveme.core.sync.FakeHandler
import com.fares.saveme.core.sync.FakePendingChangeDao
import com.fares.saveme.core.sync.FakeSyncScheduler
import com.fares.saveme.core.sync.FeedPuller
import com.fares.saveme.core.sync.ScopeKind
import com.fares.saveme.core.sync.ScopeRead
import com.fares.saveme.core.sync.SyncEngine
import com.fares.saveme.core.sync.SyncLock
import com.fares.saveme.core.sync.SyncScope
import com.fares.saveme.feature.account.data.dto.ProfileDto
import com.fares.saveme.feature.account.domain.model.Profile
import com.fares.saveme.feature.account.domain.model.ProfileView
import com.fares.saveme.feature.auth.jsonResponse
import com.fares.saveme.feature.budgets.data.BudgetFeedApplier
import com.fares.saveme.feature.budgets.data.CapturedBudgetResponses as C
import com.fares.saveme.feature.budgets.data.dto.BudgetProgressDto
import com.fares.saveme.feature.budgets.domain.BudgetMath
import com.fares.saveme.feature.budgets.domain.DefaultBudgetsService
import com.fares.saveme.feature.budgets.domain.model.BudgetView
import com.fares.saveme.feature.budgets.domain.model.FigureSource
import com.fares.saveme.feature.budgets.domain.model.PeriodState
import com.fares.saveme.feature.transactions.TransactionTestEnv
import com.fares.saveme.feature.transactions.data.TransactionFeedApplier
import com.fares.saveme.feature.transactions.data.TransactionRepository
import com.fares.saveme.feature.transactions.domain.TransactionOwnSpending
import com.fares.saveme.feature.wallets.data.OwnMembershipFeedApplier
import com.fares.saveme.feature.wallets.data.WalletFeedApplier
import com.fares.saveme.feature.wallets.data.WalletMemberFeedApplier
import com.fares.saveme.feature.wallets.data.WalletRepository
import com.fares.saveme.feature.wallets.domain.DefaultWalletScopes
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

/**
 * SC-003 and SC-004 against the live backend (spec 012 contract QA): the person's real feed pages (budgets, wallets,
 * transactions incl. a partner's, a transfer, an income, a deleted expense, other currencies, recurring confirmations
 * attributed to the rule's author) go through the real puller and appliers; the phone's own figures, computed by the
 * real [TransactionOwnSpending] and calculators, must equal `GET /budget-progress` for the same period.
 */
class BudgetCapturedProgressTest {

    private class World(scope: CoroutineScope, val person: String, userFeed: String, walletFeeds: Map<String, String>, progress: (String) -> String?, profileJson: String) {
        val server = CapturedSyncServer().also {
            it.user = { _ -> CapturedSyncResponses.Answer(200, userFeed) }
            it.wallet = { id, _ -> CapturedSyncResponses.Answer(200, walletFeeds.getValue(id)) }
        }
        val tx = TransactionTestEnv(scope)
        val budgets = BudgetTestEnv(scope) { request ->
            val body = progress(request.url.encodedPath)
            if (body == null) jsonResponse(404, """{"status":404,"code":"NOT_FOUND"}""") else jsonResponse(200, body)
        }
        val walletRepository = WalletRepository(
            tx.walletApi, tx.walletDao, tx.memberDao, tx.scopeDao, accountId = { person }, hasQueuedChange = { false },
            onWalletRemoved = { }, clock = tx.clock, onWalletCopy = { _, _ -> },
        )
        val transactionRepository = TransactionRepository(
            tx.transactionDao, tx.scopeDao, accountId = { person }, hasQueuedChange = { false }, clock = tx.clock,
            walletChangeSeq = { id -> tx.walletDao.get(id)?.changeSeq },
        )
        val walletScopes = DefaultWalletScopes(walletRepository) { person }
        val puller = FeedPuller(
            server.api, tx.scopeDao,
            {
                listOf(
                    OwnMembershipFeedApplier(walletRepository), WalletFeedApplier(walletRepository), WalletMemberFeedApplier(walletRepository),
                    TransactionFeedApplier(transactionRepository), BudgetFeedApplier(budgets.repository),
                )
            },
            { walletScopes }, SyncLock(), { person }, tx.clock,
        )
        val engine = SyncEngine(FakePendingChangeDao(), tx.scopeDao, listOf(FakeHandler("transaction")), FakeSyncScheduler(), currentAccountId = { person }, clock = tx.clock)
        val spending = TransactionOwnSpending({ engine }, transactionRepository, accountId = { person }, memberWalletIds = { tx.walletDao.rows.map { it.keys } }, ruleAuthorOf = { null })
        val profile = MutableStateFlow<ProfileView?>(profileView(json = profileJson))
        val now = MutableStateFlow(Instant.parse("2026-10-04T23:30:00Z"))
        val service = DefaultBudgetsService(
            repository = budgets.repository, progress = budgets.progress, api = budgets.api, engine = { budgets.engine }, authState = budgets.authState,
            categories = budgets.categories, wallets = budgets.wallets, spending = spending, profile = profile, deviceZoneId = { "Africa/Cairo" },
            runner = budgets.runner, now = now, clock = budgets.clock, newId = { "x" },
        )

        suspend fun read(scope: SyncScope) = assertIs<ScopeRead.Done>(puller.read(scope))
        suspend fun overview() = service.observeOverview().first()
        suspend fun active(id: String): BudgetView = overview().active.single { it.id == id }
    }

    companion object {
        private fun profileView(json: String): ProfileView {
            val dto = AppJson.decodeFromString(ProfileDto.serializer(), json)
            return ProfileView(
                Profile(dto.displayName, dto.locale, dto.timezone, dto.defaultCurrency, false, dto.budgetPeriodStartDay, dto.budgetPeriodStartDayFrom?.let { LocalDate.parse(it) }, dto.version),
                pending = null, conflict = null, rejection = emptyMap(), refreshedAt = null,
            )
        }

        /** The `profile` row of a captured feed page. */
        fun profileOf(feed: String): String =
            AppJson.parseToJsonElement(feed).jsonObject.getValue("data").jsonArray.map { it.jsonObject }.single { it.getValue("type").toString() == "\"profile\"" }.getValue("record").toString()

        fun serverProgress(page: String): List<BudgetProgressDto> =
            AppJson.parseToJsonElement(page).jsonObject.getValue("data").jsonArray.map { AppJson.decodeFromJsonElement(BudgetProgressDto.serializer(), it) }
    }

    private val walletFeedsA = mapOf(C.WALLET_CASH to C.FEED_WALLET_CASH, C.WALLET_BANK to C.FEED_WALLET_BANK, C.WALLET_USD to C.FEED_WALLET_USD, C.WALLET_SHARED to C.FEED_WALLET_SHARED)

    private fun worldA(scope: CoroutineScope) = World(
        scope, C.USER_A, C.FEED_USER_A, walletFeedsA,
        { path -> if (path.endsWith("/budget-progress")) C.PROGRESS_A_200 else null }, profileOf(C.FEED_USER_A),
    )

    private suspend fun World.syncA() {
        server.signedIn()
        read(SyncScope(ScopeKind.User, person))
        for (id in walletScopes.followed()) read(SyncScope(ScopeKind.Wallet, id))
    }

    @Test
    fun thePhonesOwnFiguresEqualTheServersForEveryBudgetOfTheLiveSet() = runTest {
        val w = worldA(backgroundScope)
        w.syncA()
        assertEquals(setOf(C.WALLET_CASH, C.WALLET_BANK, C.WALLET_USD, C.WALLET_SHARED), w.walletScopes.followed().toSet())

        val overview = w.overview()
        val period = (overview.period as PeriodState.Known).period
        val server = serverProgress(C.PROGRESS_A_200)
        assertEquals(5, server.size)
        assertEquals(5, overview.active.size, "an archived and two deleted budgets are not active")
        assertEquals(listOf(C.BUDGET_USD_ARCHIVED), overview.archived.map { it.id })

        // SC-004: the period the phone derives from the profile equals the server's.
        val serverPeriod = server.first().period
        assertEquals(LocalDate.parse(serverPeriod.start), period.start)
        assertEquals(LocalDate.parse(serverPeriod.end), period.end)
        assertEquals(serverPeriod.daysInPeriod, period.daysInPeriod)
        assertEquals(serverPeriod.daysElapsed, period.daysElapsed)

        // SC-003: spent, remaining, over limit, projection, share and other currencies.
        for (s in server) {
            val figures = assertNotNull(w.active(s.budgetId).figures)
            assertEquals(FigureSource.Phone, figures.source, "no server figure stored yet: these are the phone's own")
            assertEquals(s.spent.amountMinor, figures.spentMinor, "spent ${s.budgetId}")
            assertEquals(s.limit.amountMinor, figures.limitMinor)
            assertEquals(s.remaining!!.amountMinor, figures.remainingMinor, "remaining ${s.budgetId}")
            assertEquals(s.overLimit, figures.overLimit)
            assertEquals(s.otherCurrencies, figures.otherCurrencies, "other currencies ${s.budgetId}")
            assertEquals(s.projectedEnd?.amountMinor, figures.projectedMinor, "projection ${s.budgetId}")
            assertEquals(BudgetMath.percent(s.spent.amountMinor, s.limit.amountMinor), figures.percent)
        }
        // The attribution cases, by name: the author's rule confirmed by the partner counts for the author only.
        assertEquals(70_550L, w.active(C.BUDGET_GROCERIES).figures!!.spentMinor)
        assertEquals(73_550L, w.active(C.BUDGET_ALL).figures!!.spentMinor)
    }

    @Test
    fun aProgressReadStoresTheServersFiguresAndTheSyncedSumsEqualThemAndTheyAreShown() = runTest {
        val w = worldA(backgroundScope)
        w.syncA()
        w.service.refresh(force = true)
        for (s in serverProgress(C.PROGRESS_A_200)) {
            val stored = w.budgets.progressDao.rows.value.getValue(s.budgetId)
            assertEquals(s.spent.amountMinor, stored.spentMinor)
            assertEquals(stored.spentMinor, stored.localSpentMinor, "the phone's synced-only sum equals the server's at the read (${s.budgetId})")
            assertEquals(stored.otherCurrencies, stored.localOtherCurrencies)
            val figures = w.active(s.budgetId).figures!!
            assertEquals(FigureSource.Server, figures.source)
            assertEquals(s.spent.amountMinor, figures.spentMinor)
        }
    }

    @Test
    fun aStartDayChangeStillAheadKeepsTheServersPeriodAndTheFiguresStillEqualTheServers() = runTest {
        val pending = World(
            backgroundScope, C.USER_B, C.FEED_USER_B_PENDING, mapOf(C.WALLET_SHARED to C.FEED_WALLET_SHARED_FOR_B),
            { path -> if (path.endsWith("/budget-progress")) C.PROGRESS_B_PENDING_200 else null }, profileOf(C.FEED_USER_B_PENDING),
        )
        pending.server.signedIn()
        pending.read(SyncScope(ScopeKind.User, C.USER_B))
        for (id in pending.walletScopes.followed()) pending.read(SyncScope(ScopeKind.Wallet, id))
        val profile = pending.profile.value!!.server
        assertEquals(25, profile.budgetStartDay)
        assertEquals(LocalDate(2026, 10, 25), profile.budgetStartDayFrom)

        // Before any read the phone cannot know the period under the old day: "Progress appears after sync".
        val before = pending.overview()
        assertIs<PeriodState.Unknown>(before.period)
        assertNull(before.active.first().figures)
        assertEquals(LocalDate(2026, 10, 25), before.pendingStartDay!!.from)

        pending.service.refresh(force = true)
        val after = pending.overview()
        val server = serverProgress(C.PROGRESS_B_PENDING_200)
        val period = (after.period as PeriodState.Known).period
        assertEquals(LocalDate.parse(server.first().period.start), period.start)
        assertEquals(LocalDate.parse(server.first().period.end), period.end)
        assertEquals(24, period.daysInPeriod)
        assertEquals(5, period.daysElapsed)
        for (s in server) {
            val stored = pending.budgets.progressDao.rows.value.getValue(s.budgetId)
            assertEquals(s.spent.amountMinor, stored.localSpentMinor, "the phone's own sum for the server's truncated period equals the server's")
            assertEquals(s.spent.amountMinor, pending.active(s.budgetId).figures!!.spentMinor)
        }
    }

    @Test
    fun leavingTheSharedWalletStopsItsSpendingCountingExactlyAsTheServerStopsIt() = runTest {
        val w = World(
            backgroundScope, C.USER_B, C.FEED_USER_B_PENDING, mapOf(C.WALLET_SHARED to C.FEED_WALLET_SHARED_FOR_B),
            { path -> if (path.endsWith("/budget-progress")) C.PROGRESS_B_PENDING_200 else null }, profileOf(C.FEED_USER_B_PENDING),
        )
        w.server.signedIn()
        w.read(SyncScope(ScopeKind.User, C.USER_B))
        for (id in w.walletScopes.followed()) w.read(SyncScope(ScopeKind.Wallet, id))
        w.service.refresh(force = true)
        assertEquals(11_000L, w.active(C.BUDGET_B_GROCERIES).figures!!.spentMinor)

        w.server.user = { _ -> CapturedSyncResponses.Answer(200, C.FEED_USER_B_AFTER_LEAVING) }
        w.read(SyncScope(ScopeKind.User, C.USER_B))
        val afterLeaving = serverProgress(C.PROGRESS_B_AFTER_LEAVING_200)
        for (s in afterLeaving) {
            val figures = w.active(s.budgetId).figures!!
            assertEquals(FigureSource.Phone, figures.source, "the stored server figure is stale, so the phone's own is shown")
            assertEquals(s.spent.amountMinor, figures.spentMinor, "after leaving, ${s.budgetId}")
        }
        assertTrue(afterLeaving.all { it.spent.amountMinor == 0L })
    }
}
