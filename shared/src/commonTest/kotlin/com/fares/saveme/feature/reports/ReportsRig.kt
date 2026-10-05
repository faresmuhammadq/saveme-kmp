package com.fares.saveme.feature.reports

import com.fares.saveme.core.sync.FakeSyncRunner
import com.fares.saveme.feature.account.domain.model.ProfileView
import com.fares.saveme.feature.auth.AuthTestEnv
import com.fares.saveme.feature.auth.domain.AuthState
import com.fares.saveme.feature.auth.domain.model.RefreshOutcome
import com.fares.saveme.feature.budgets.budgetAccount
import com.fares.saveme.feature.budgets.profileView
import com.fares.saveme.feature.categories.domain.CategoriesService
import com.fares.saveme.feature.categories.domain.model.CategoriesOverview
import com.fares.saveme.feature.categories.domain.model.CategoryChoices
import com.fares.saveme.feature.categories.domain.model.CategoryDisplay
import com.fares.saveme.feature.categories.domain.model.CategoryDisplays
import com.fares.saveme.feature.categories.domain.model.CategoryDraft
import com.fares.saveme.feature.categories.domain.model.CategoryKind
import com.fares.saveme.feature.categories.domain.model.CategorySource
import com.fares.saveme.feature.categories.domain.model.CategoryView
import com.fares.saveme.feature.categories.domain.model.ReportLabels
import com.fares.saveme.feature.recurring.domain.ScheduledLine
import com.fares.saveme.feature.recurring.domain.ScheduledMoney
import com.fares.saveme.feature.reports.data.ReportsApi
import com.fares.saveme.feature.reports.domain.DefaultReportsService
import com.fares.saveme.feature.reports.domain.LearnedPeriods
import com.fares.saveme.feature.reports.domain.ProbeComputation
import com.fares.saveme.feature.reports.domain.ProbeRequest
import com.fares.saveme.feature.reports.domain.ReportChoicesStore
import com.fares.saveme.feature.reports.domain.ReportParityProbe
import com.fares.saveme.feature.reports.domain.model.Report
import com.fares.saveme.feature.reports.domain.model.ReportScope
import com.fares.saveme.feature.reports.domain.model.ReportState
import com.fares.saveme.feature.transactions.domain.LedgerSnapshot
import com.fares.saveme.feature.transactions.domain.ReportLedger
import com.fares.saveme.feature.transactions.domain.TransactionsService
import com.fares.saveme.feature.transactions.domain.model.CreateResult
import com.fares.saveme.feature.transactions.domain.model.SaveResult
import com.fares.saveme.feature.transactions.domain.model.Transaction
import com.fares.saveme.feature.transactions.domain.model.TransactionDraft
import com.fares.saveme.feature.transactions.domain.model.TransactionList
import com.fares.saveme.feature.transactions.domain.model.TransactionQuery
import com.fares.saveme.feature.transactions.domain.model.TransactionView
import com.fares.saveme.feature.transactions.domain.model.WalletChoice
import com.fares.saveme.feature.wallets.domain.WalletsService
import com.fares.saveme.feature.wallets.domain.model.CreateResult as WalletCreateResult
import com.fares.saveme.feature.wallets.domain.model.Member
import com.fares.saveme.feature.wallets.domain.model.SaveResult as WalletSaveResult
import com.fares.saveme.feature.wallets.domain.model.SyncState as WalletSyncState
import com.fares.saveme.feature.wallets.domain.model.TransferResult
import com.fares.saveme.feature.wallets.domain.model.Wallet
import com.fares.saveme.feature.wallets.domain.model.WalletDraft
import com.fares.saveme.feature.wallets.domain.model.WalletList
import com.fares.saveme.feature.wallets.domain.model.WalletRole
import com.fares.saveme.feature.wallets.domain.model.WalletView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlin.time.Instant

private val stamp = Instant.parse("2026-09-01T00:00:00Z")

fun reportWalletView(
    id: String,
    currency: String = "EGP",
    name: String = "Wallet $id",
    opening: Long = 0,
    type: String = "cash",
    archived: Boolean = false,
    waiting: Boolean = false,
) = WalletView(
    server = Wallet(id, type, name, currency, opening, opening, null, null, if (archived) stamp else null, false, WalletRole.Owner, 1, stamp),
    pending = null, pendingDelete = false, syncState = if (waiting) WalletSyncState.Waiting else WalletSyncState.None, displayedBalanceMinor = opening,
)

/** C4's service stand-in: a scripted wallet list (active and archived as the views say). */
class FakeReportWallets : WalletsService {
    val views = MutableStateFlow<List<WalletView>>(emptyList())
    val updatedAt = MutableStateFlow<Instant?>(null)
    override fun observeList(): Flow<WalletList> = combine(views, updatedAt) { list, at ->
        WalletList(list.filter { !it.archived }, list.filter { it.archived }, emptyList(), at)
    }
    override fun observeWallet(id: String): Flow<WalletView?> = views.map { list -> list.firstOrNull { it.id == id } }
    override fun observeMembers(id: String): Flow<List<Member>> = error("not used")
    override suspend fun canCreateAnother(): Boolean = error("not used")
    override suspend fun create(draft: WalletDraft): WalletCreateResult = error("not used")
    override suspend fun save(id: String, draft: WalletDraft, baseVersion: Long): WalletSaveResult = error("not used")
    override suspend fun archive(id: String): WalletSaveResult = error("not used")
    override suspend fun delete(id: String): WalletSaveResult = error("not used")
    override suspend fun keepMine(id: String) = error("not used")
    override suspend fun useTheirs(id: String) = error("not used")
    override suspend fun tryAgain(id: String) = error("not used")
    override suspend fun discard(id: String) = error("not used")
    override suspend fun refresh(force: Boolean): RefreshOutcome = RefreshOutcome.Done
    override suspend fun refreshMembers(id: String): RefreshOutcome = RefreshOutcome.Done
    override suspend fun transferOwnership(id: String, toUserId: String): TransferResult = error("not used")
}

/** A scripted snapshot whatever the asked range; records every ask. */
class FakeReportLedger(initial: LedgerSnapshot = LedgerSnapshot.Empty) : ReportLedger {
    val snapshot = MutableStateFlow(initial)
    /** Every ask as (wallets, first day of the extra window, last day of it): the window the trend needs. */
    val asks = mutableListOf<Triple<Set<String>, kotlinx.datetime.LocalDate, kotlinx.datetime.LocalDate>>()

    /** The range of every ask. */
    val rangeAsks = mutableListOf<Pair<kotlinx.datetime.LocalDate, kotlinx.datetime.LocalDate>>()
    override fun observe(walletIds: Set<String>, from: kotlinx.datetime.LocalDate, to: kotlinx.datetime.LocalDate, extraFrom: kotlinx.datetime.LocalDate, extraTo: kotlinx.datetime.LocalDate): Flow<LedgerSnapshot> {
        asks += Triple(walletIds, extraFrom, extraTo)
        rangeAsks += from to to
        return snapshot
    }
}

class FakeScheduledMoney : ScheduledMoney {
    val lines = MutableStateFlow<List<ScheduledLine>>(emptyList())
    val waiting = MutableStateFlow(false)
    override fun observe(walletIds: Set<String>, from: kotlinx.datetime.LocalDate, to: kotlinx.datetime.LocalDate): Flow<List<ScheduledLine>> = lines
    override fun observeWaiting(walletIds: Set<String>, from: kotlinx.datetime.LocalDate, to: kotlinx.datetime.LocalDate): Flow<Boolean> = waiting
}

fun display(id: String, name: String, icon: String? = "cart", color: String? = "#2E7D32") =
    CategoryDisplay(id, name, icon, color, "expense", CategorySource.Own, false)

/** C5's stand-in: platform ids map to system keys, names resolve from a scripted table. */
class FakeReportCategories : CategoriesService {
    val systemKeys = mutableMapOf<String, String>()
    val names = mutableMapOf<String, CategoryDisplay>()
    val labels: Flow<ReportLabels> = MutableStateFlow(
        ReportLabels(
            key = { id -> systemKeys[id] ?: id },
            displays = CategoryDisplays(lookup = { id -> names[id] ?: CategoryDisplay(id, "", null, null, null, CategorySource.Unavailable, false) }),
        ),
    )
    val labelAsks = mutableListOf<Set<String>>()
    override fun observeReportLabels(walletIds: Set<String>): Flow<ReportLabels> {
        labelAsks += walletIds
        return labels
    }
    override fun observeOverview(kind: CategoryKind): Flow<CategoriesOverview> = error("not used")
    override fun observeCategory(id: String): Flow<CategoryView?> = error("not used")
    override fun observeUsedLocally(): Flow<Set<String>> = flowOf(emptySet())
    override fun observeChoices(kind: CategoryKind, currentCategoryId: String?): Flow<CategoryChoices> = error("not used")
    override fun observeDisplays(walletId: String?): Flow<CategoryDisplays> = error("not used")
    override suspend fun create(draft: CategoryDraft): com.fares.saveme.feature.categories.domain.model.CreateResult = error("not used")
    override suspend fun save(id: String, draft: CategoryDraft, baseVersion: Long): com.fares.saveme.feature.categories.domain.model.SaveResult = error("not used")
    override suspend fun archive(id: String): com.fares.saveme.feature.categories.domain.model.SaveResult = error("not used")
    override suspend fun delete(id: String): com.fares.saveme.feature.categories.domain.model.SaveResult = error("not used")
    override suspend fun archiveInstead(id: String) = error("not used")
    override suspend fun keepCategory(id: String) = error("not used")
    override suspend fun keepMine(id: String) = error("not used")
    override suspend fun useTheirs(id: String) = error("not used")
    override suspend fun tryAgain(id: String) = error("not used")
    override suspend fun discard(id: String) = error("not used")
    override suspend fun refresh(force: Boolean): RefreshOutcome = RefreshOutcome.Done
    override suspend fun refreshReferences(walletId: String, force: Boolean): RefreshOutcome = RefreshOutcome.Done
}

/** C6's stand-in: only the feed read stamp is used. */
class FakeReportTransactions : TransactionsService {
    val readAt = MutableStateFlow<Instant?>(null)
    override fun observeFeedReadAt(walletId: String): Flow<Instant?> = readAt
    override fun observeList(walletId: String, query: TransactionQuery): Flow<TransactionList> = error("not used")
    override fun observeTransaction(id: String, fromWalletId: String): Flow<TransactionView?> = error("not used")
    override fun observeWritableWallets(): Flow<List<WalletChoice>> = error("not used")
    override suspend fun defaultWalletId(): String? = error("not used")
    override fun observeDestinations(sourceWalletId: String): Flow<List<WalletChoice>> = error("not used")
    override suspend fun create(draft: TransactionDraft): CreateResult = error("not used")
    override suspend fun save(id: String, draft: TransactionDraft, baseVersion: Long, lastSeen: Transaction?): SaveResult = error("not used")
    override suspend fun delete(id: String): SaveResult = error("not used")
    override suspend fun keepMine(id: String) = error("not used")
    override suspend fun useTheirs(id: String) = error("not used")
    override suspend fun tryAgain(id: String) = error("not used")
    override suspend fun discard(id: String) = error("not used")
    override suspend fun refresh(walletId: String, force: Boolean): RefreshOutcome = error("not used")
}

class RecordingProbe(override val enabled: Boolean = true) : ReportParityProbe {
    val compared = mutableListOf<Pair<ProbeRequest, ProbeComputation>>()
    override suspend fun compare(request: ProbeRequest, computed: ProbeComputation) {
        compared += request to computed
    }
}

/**
 * The reports domain over scripted features: a fixed instant (2026-10-02 10:00 UTC), one signed-in account `u1`, a profile
 * with start day 1 in Cairo, and an API that is offline unless the test gives it a handler.
 */
class ReportsRig(
    scope: CoroutineScope,
    probe: ReportParityProbe = com.fares.saveme.feature.reports.domain.NoParityProbe,
    handler: suspend io.ktor.client.engine.mock.MockRequestHandleScope.(io.ktor.client.request.HttpRequestData) -> io.ktor.client.request.HttpResponseData =
        { throw kotlinx.io.IOException("offline") },
) {
    val auth = AuthTestEnv(handler)
    val wallets = FakeReportWallets()
    val ledger = FakeReportLedger()
    val scheduled = FakeScheduledMoney()
    val categories = FakeReportCategories()
    val transactions = FakeReportTransactions()
    val profile = MutableStateFlow<ProfileView?>(profileView())
    val now = MutableStateFlow(Instant.parse("2026-10-02T10:00:00Z"))
    val choices = ReportChoicesStore()
    val learned = LearnedPeriods()
    val runner = FakeSyncRunner()
    val authState = MutableStateFlow<AuthState>(AuthState.SignedIn(budgetAccount))
    val service = DefaultReportsService(
        wallets = wallets, transactions = transactions, ledger = ledger, scheduled = scheduled, categories = categories,
        profile = profile, deviceZoneId = { "Africa/Cairo" }, authState = authState, choices = choices, learned = learned,
        api = ReportsApi(auth.client), runner = runner, probe = probe, now = now,
    )
    val requests get() = auth.requests

    /** The next emitted report that is ready. */
    suspend fun report(scope: ReportScope = ReportScope.AllWallets): Report =
        (service.observe(scope).first { it is ReportState.Ready } as ReportState.Ready).report
}

