package com.fares.saveme.feature.budgets.domain

import co.touchlab.kermit.Logger
import com.fares.saveme.core.network.ApiErrorCode
import com.fares.saveme.core.network.ApiResult
import com.fares.saveme.core.sync.PendingChange
import com.fares.saveme.core.sync.ReadOutcome
import com.fares.saveme.core.sync.ReadTarget
import com.fares.saveme.core.sync.ScopeKind
import com.fares.saveme.core.sync.SyncEngine
import com.fares.saveme.core.sync.SyncRunner
import com.fares.saveme.core.sync.SyncScope
import com.fares.saveme.feature.account.domain.model.ProfileView
import com.fares.saveme.feature.auth.domain.AuthState
import com.fares.saveme.feature.auth.domain.model.RefreshOutcome
import com.fares.saveme.feature.budgets.data.BudgetApi
import com.fares.saveme.feature.budgets.data.BudgetProgressRepository
import com.fares.saveme.feature.budgets.data.BudgetRepository
import com.fares.saveme.feature.budgets.data.BudgetSyncHandler
import com.fares.saveme.feature.budgets.data.dto.BudgetProgressDto
import com.fares.saveme.feature.budgets.data.local.BudgetEntity
import com.fares.saveme.feature.budgets.data.local.BudgetProgressEntity
import com.fares.saveme.feature.budgets.data.toStored
import com.fares.saveme.feature.budgets.domain.model.Budget
import com.fares.saveme.feature.budgets.domain.model.BudgetChoices
import com.fares.saveme.feature.budgets.domain.model.BudgetDraft
import com.fares.saveme.feature.budgets.domain.model.BudgetField
import com.fares.saveme.feature.budgets.domain.model.BudgetFieldProblem
import com.fares.saveme.feature.budgets.domain.model.BudgetFigures
import com.fares.saveme.feature.budgets.domain.model.BudgetPeriod
import com.fares.saveme.feature.budgets.domain.model.BudgetView
import com.fares.saveme.feature.budgets.domain.model.BudgetsOverview
import com.fares.saveme.feature.budgets.domain.model.CreateResult
import com.fares.saveme.feature.budgets.domain.model.PendingStartDay
import com.fares.saveme.feature.budgets.domain.model.PeriodState
import com.fares.saveme.feature.budgets.domain.model.SaveResult
import com.fares.saveme.feature.budgets.domain.model.StoredProgress
import com.fares.saveme.feature.budgets.domain.model.SyncState
import com.fares.saveme.feature.categories.domain.CategoriesService
import com.fares.saveme.feature.categories.domain.model.CategoryDisplays
import com.fares.saveme.feature.categories.domain.model.CategoryKind
import com.fares.saveme.feature.transactions.domain.OwnSpending
import com.fares.saveme.feature.transactions.domain.SpendingSnapshot
import com.fares.saveme.feature.wallets.domain.WalletsService
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlin.uuid.Uuid

/**
 * The budgets domain API (contracts/budgets-domain.md). Other features use only this package (P-10.4); the screens'
 * stores take its functions as lambdas.
 */
interface BudgetsService {
    /** The budgets screen (FR-016): active and archived views of the current period, the period state and its pending start day. */
    fun observeOverview(): Flow<BudgetsOverview>

    /** Null when the budget is gone or hidden by a deletion that waits to sync. */
    fun observeBudget(id: String): Flow<BudgetView?>

    /** The server's budget start day for the editor's period line (1 until the profile is on the phone). */
    fun observeStartDay(): Flow<Int>

    /** FR-003, FR-004: expense categories, currencies and the categories already budgeted in [currency]. */
    fun observeChoices(currency: String, currentCategoryId: String?): Flow<BudgetChoices>

    suspend fun create(draft: BudgetDraft): CreateResult

    /** [baseVersion] is the version shown when editing began (FR-005); category, currency and period are never changed. */
    suspend fun saveLimit(id: String, limitMinor: Long, baseVersion: Long): SaveResult

    suspend fun archive(id: String): SaveResult

    suspend fun delete(id: String): SaveResult

    suspend fun keepMine(id: String)

    /** Also "Accept the deletion". */
    suspend fun useTheirs(id: String)

    suspend fun tryAgain(id: String)

    suspend fun discard(id: String)

    /**
     * Sends what waits, reads the person's changes and wallets (cheap since spec 011), then walks every page of
     * `GET /budget-progress` (research R-09); without [force] a walk within the last 60 seconds is skipped.
     */
    suspend fun refresh(force: Boolean = false): RefreshOutcome

    /** Reads one budget's progress when the server holds it (FR-018); without [force] a read within the last 60 seconds is skipped. */
    suspend fun refreshOne(id: String, force: Boolean = false): RefreshOutcome
}

class DefaultBudgetsService(
    private val repository: BudgetRepository,
    private val progress: BudgetProgressRepository,
    private val api: BudgetApi,
    private val engine: () -> SyncEngine,
    private val authState: StateFlow<AuthState>,
    private val categories: CategoriesService,
    private val wallets: WalletsService,
    private val spending: OwnSpending,
    private val profile: Flow<ProfileView?>,
    private val deviceZoneId: () -> String,
    private val runner: SyncRunner,
    private val now: Flow<Instant> = DayTicker().ticks(),
    private val clock: Clock = Clock.System,
    private val newId: () -> String = { Uuid.generateV7().toString() },
    private val readFloor: Duration = 60.seconds,
) : BudgetsService {

    private val log = Logger.withTag("budgets")
    private val refreshLock = Mutex()
    private var lastWalk: Instant? = null
    private val oneLock = Mutex()
    private val lastOne = mutableMapOf<String, Instant>()

    /** The account the 60 second floors belong to: a different person signing in never inherits them. */
    private var floorsAccount: String? = null

    private val accountId: String? get() = (authState.value as? AuthState.SignedIn)?.account?.userId

    private val emailVerified: Boolean get() = (authState.value as? AuthState.SignedIn)?.account?.emailVerified == true

    // ---- reads ------------------------------------------------------------------------------------------------

    private class Raw(val row: BudgetEntity, val change: PendingChange?)

    private class Context(val period: PeriodState, val today: LocalDate?, val pendingStartDay: PendingStartDay?, val startDay: Int)

    private class Built(val context: Context, val views: List<BudgetView>, val progressRows: List<BudgetProgressEntity>)

    private fun accountFlow(): Flow<String?> = authState.map { (it as? AuthState.SignedIn)?.account?.userId }.distinctUntilChanged()

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun rawViews(): Flow<List<Raw>> = accountFlow().flatMapLatest { id ->
        if (id == null) {
            flowOf(emptyList())
        } else {
            combine(repository.observeAll(id), engine().observeAll(BudgetSyncHandler.RECORD_TYPE)) { rows, changes ->
                val byId = changes.associateBy { it.recordId }
                rows.map { Raw(it, byId[it.id]) }
            }
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun progressRows(): Flow<List<BudgetProgressEntity>> = accountFlow().flatMapLatest { id ->
        if (id == null) flowOf(emptyList()) else progress.observeAll(id)
    }

    /** Today, the period and the pending start day, re-derived when the day turns (US5-S6) and when the profile changes. */
    private fun clockContext(): Flow<Context> = combine(profile, progressRows().map { rows -> rows.mapNotNull { it.toStoredProgress()?.period }.distinct() }, now) { view, stored, instant ->
        val server = view?.server
        val zone = runCatching { TimeZone.of(server?.timezoneId ?: deviceZoneId()) }.getOrElse { TimeZone.UTC }
        val today = instant.toLocalDateTime(zone).date
        val startDay = server?.budgetStartDay
        val from = server?.budgetStartDayFrom
        Context(
            period = BudgetPeriods.current(today, startDay, from, stored),
            today = today,
            pendingStartDay = if (startDay != null && from != null && from > today) PendingStartDay(startDay, from) else null,
            startDay = startDay ?: BudgetPeriods.DEFAULT_START_DAY,
        )
    }.distinctUntilChanged { a, b ->
        a.period == b.period && a.today == b.today && a.pendingStartDay == b.pendingStartDay && a.startDay == b.startDay
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun built(): Flow<Built> = clockContext().flatMapLatest { ctx ->
        val known = (ctx.period as? PeriodState.Known)?.period
        val snapshot: Flow<SpendingSnapshot?> = if (known == null) flowOf(null) else spending.observe(known.start, known.end)
        combine(rawViews(), progressRows(), categories.observeDisplays(), snapshot) { raws, stored, displays, snap ->
            val storedById = stored.associateBy { it.budgetId }
            Built(ctx, raws.map { viewOf(it, known, snap, storedById[it.row.id], displays) }, stored)
        }
    }

    private fun viewOf(raw: Raw, period: BudgetPeriod?, snapshot: SpendingSnapshot?, stored: BudgetProgressEntity?, displays: CategoryDisplays): BudgetView {
        val budget = raw.row.toBudget()
        val pending = raw.change.pendingDraft()
        val limit = pending?.limitMinor ?: budget.limitMinor
        val figures = if (period == null || snapshot == null || !budget.isMonthly) {
            null
        } else {
            figuresOf(budget, limit, period, snapshot, stored?.toStoredProgress())
        }
        return BudgetView(
            server = budget,
            pending = pending,
            pendingDelete = raw.change?.isDelete == true,
            syncState = raw.change.toSyncState(),
            category = budget.categoryId?.let { displays.resolve(it) },
            figures = figures,
        )
    }

    private fun figuresOf(budget: Budget, limitMinor: Long, period: BudgetPeriod, snapshot: SpendingSnapshot, stored: StoredProgress?): BudgetFigures {
        val phone = SpendingTotals.withWaiting(snapshot, budget.categoryId, budget.currency, period)
        val synced = SpendingTotals.syncedOnly(snapshot, budget.categoryId, budget.currency, period)
        val chosen = ProgressSelection.select(stored, period, budget.currency, limitMinor, phone, synced)
        return BudgetMath.figures(chosen.spentMinor, limitMinor, period, chosen.otherCurrencies, chosen.source, chosen.includesWaiting)
    }

    override fun observeOverview(): Flow<BudgetsOverview> = built().map { built ->
        val visible = built.views.filter { !it.hidden }
        BudgetsOverview(
            period = built.context.period,
            today = built.context.today,
            pendingStartDay = built.context.pendingStartDay,
            startDay = built.context.startDay,
            active = visible.filter { !it.archived },
            archived = visible.filter { it.archived },
            updatedAt = built.progressRows.mapNotNull { runCatching { Instant.parse(it.readAt) }.getOrNull() }.maxOrNull(),
            loaded = visible.isNotEmpty() || built.progressRows.isNotEmpty(),
        )
    }

    override fun observeBudget(id: String): Flow<BudgetView?> =
        built().map { built -> built.views.firstOrNull { it.id == id }?.takeUnless { it.hidden } }

    override fun observeStartDay(): Flow<Int> = clockContext().map { it.startDay }.distinctUntilChanged()

    override fun observeChoices(currency: String, currentCategoryId: String?): Flow<BudgetChoices> = combine(
        categories.observeChoices(CategoryKind.Expense, currentCategoryId),
        wallets.observeList(),
        profile,
        rawViews(),
    ) { choices, walletList, view, raws ->
        val defaultCurrency = view?.effective?.defaultCurrency
        val walletCurrencies = (walletList.active + walletList.archived).map { it.server.currency }
        val all = (listOfNotNull(defaultCurrency) + walletCurrencies.sorted()).distinct()
        BudgetChoices(
            current = choices.current,
            own = choices.own,
            defaults = choices.defaults,
            forCurrency = currency,
            currencies = all,
            defaultCurrency = defaultCurrency ?: all.firstOrNull() ?: FALLBACK_CURRENCY,
            alreadyBudgeted = activeKeys(raws, currency),
            startDay = view?.server?.budgetStartDay ?: BudgetPeriods.DEFAULT_START_DAY,
        )
    }

    /** The categories (or all expenses) that have an active budget in [currency]: waiting archives and deletions count as gone. */
    private fun activeKeys(raws: List<Raw>, currency: String): Set<String> = raws
        .filter { it.row.currency == currency }
        .filter { raw ->
            val archived = raw.row.archivedAt != null || raw.change.pendingDraft()?.archive == true
            val hidden = raw.change?.isDelete == true && raw.change.toSyncState() == SyncState.Waiting
            hidden.not() && archived.not()
        }
        .map { BudgetValidation.keyOf(it.row.categoryId) }
        .toSet()

    // ---- writes -----------------------------------------------------------------------------------------------

    override suspend fun create(draft: BudgetDraft): CreateResult {
        val account = accountId ?: return CreateResult.NotVerified
        if (!emailVerified) return CreateResult.NotVerified
        val errors = mutableMapOf<BudgetField, BudgetFieldProblem>()
        BudgetValidation.checkLimit(draft.limitMinor)?.let { errors[BudgetField.Limit] = it }
        if (!CURRENCY.matches(draft.currency)) errors[BudgetField.Currency] = BudgetFieldProblem.Server("INVALID_CURRENCY")
        if (errors.isNotEmpty()) return CreateResult.Invalid(errors)
        if (BudgetValidation.keyOf(draft.categoryId) in activeKeys(currentRaws(account), draft.currency)) return CreateResult.AlreadyBudgeted
        val id = newId()
        val clean = draft.copy(archive = false)
        repository.insertPlaceholder(id, account, clean.categoryId, clean.currency, clean.limitMinor)
        engine().enqueue(BudgetSyncHandler.RECORD_TYPE, id, scopeOf(account), 0, clean.toPayloadJson())
        runner.requestUpload()
        return CreateResult.Created(id)
    }

    override suspend fun saveLimit(id: String, limitMinor: Long, baseVersion: Long): SaveResult {
        val account = accountId ?: return SaveResult.NotVerified
        if (!emailVerified) return SaveResult.NotVerified
        val row = repository.get(id) ?: return SaveResult.NoLongerAvailable
        if (row.periodType != Budget.MONTHLY) return SaveResult.ReadOnly
        BudgetValidation.checkLimit(limitMinor)?.let { return SaveResult.Invalid(mapOf(BudgetField.Limit to it)) }
        val waitingArchive = engine().get(BudgetSyncHandler.RECORD_TYPE, id).pendingDraft()?.archive == true
        // Category and currency are the stored ones: they never change after creation (FR-005).
        val draft = BudgetDraft(row.categoryId, row.currency, limitMinor, archive = waitingArchive)
        engine().enqueue(BudgetSyncHandler.RECORD_TYPE, id, scopeOf(account), baseVersion, draft.toPayloadJson())
        runner.requestUpload()
        return SaveResult.Saved
    }

    override suspend fun archive(id: String): SaveResult {
        val account = accountId ?: return SaveResult.NotVerified
        if (!emailVerified) return SaveResult.NotVerified
        val row = repository.get(id) ?: return SaveResult.NoLongerAvailable
        if (row.periodType != Budget.MONTHLY) return SaveResult.ReadOnly
        val waiting = engine().get(BudgetSyncHandler.RECORD_TYPE, id).pendingDraft()
        if (row.archivedAt != null || waiting?.archive == true) return SaveResult.Saved
        val shown = waiting ?: row.toBudget().toDraft()
        engine().enqueue(BudgetSyncHandler.RECORD_TYPE, id, scopeOf(account), row.version, shown.copy(archive = true).toPayloadJson())
        runner.requestUpload()
        return SaveResult.Saved
    }

    override suspend fun delete(id: String): SaveResult {
        val account = accountId ?: return SaveResult.NotVerified
        if (!emailVerified) return SaveResult.NotVerified
        val row = repository.get(id) ?: return SaveResult.NoLongerAvailable
        engine().enqueueDelete(BudgetSyncHandler.RECORD_TYPE, id, scopeOf(account), row.version)
        // A budget that never reached the server has nothing to delete there (FR-006): nothing is sent.
        if (row.version == 0L) repository.removePlaceholder(id) else runner.requestUpload()
        return SaveResult.Saved
    }

    override suspend fun keepMine(id: String) {
        engine().keepMine(BudgetSyncHandler.RECORD_TYPE, id)
        runner.requestUpload()
    }

    override suspend fun useTheirs(id: String) = engine().useTheirs(BudgetSyncHandler.RECORD_TYPE, id)

    override suspend fun tryAgain(id: String) {
        val change = engine().get(BudgetSyncHandler.RECORD_TYPE, id) ?: return
        if (change.isDelete) {
            engine().enqueueDelete(change.recordType, id, change.scope, change.baseVersion)
        } else {
            engine().enqueue(change.recordType, id, change.scope, change.baseVersion, change.payloadJson)
        }
        runner.requestUpload()
    }

    override suspend fun discard(id: String) = engine().discard(BudgetSyncHandler.RECORD_TYPE, id)

    private suspend fun currentRaws(account: String): List<Raw> {
        val changes = engine().observeAll(BudgetSyncHandler.RECORD_TYPE).first().associateBy { it.recordId }
        return repository.observeAll(account).first().map { Raw(it, changes[it.id]) }
    }

    private fun scopeOf(account: String) = SyncScope(ScopeKind.User, account)

    // ---- progress reads (research R-09) ---------------------------------------------------------------------------

    private suspend fun forgetFloorsOfAnotherAccount() {
        val account = accountId
        if (account == floorsAccount) return
        floorsAccount = account
        lastWalk = null
        oneLock.withLock { lastOne.clear() }
    }

    override suspend fun refresh(force: Boolean): RefreshOutcome = refreshLock.withLock {
        forgetFloorsOfAnotherAccount()
        if (force) engine().syncNow()
        val feeds = runner.refresh(ReadTarget.Everything, force).toOutcome()
        val last = lastWalk
        if (!force && last != null && clock.now() - last < readFloor) return@withLock feeds
        val walk = walkProgress()
        if (walk == RefreshOutcome.Done) lastWalk = clock.now()
        combineOutcomes(feeds, walk)
    }

    private suspend fun walkProgress(): RefreshOutcome {
        val account = accountId ?: return RefreshOutcome.Done
        var restarted = false
        while (true) {
            val rows = mutableListOf<BudgetProgressDto>()
            var pages = 0
            var cursor: String? = null
            var restart = false
            while (true) {
                when (val answer = api.progressPage(cursor)) {
                    is ApiResult.Success -> {
                        pages++
                        rows += answer.value.items
                        if (!answer.value.hasMore) break
                        cursor = answer.value.nextCursor
                        if (cursor == null) {
                            log.w { "budget progress: a page claims more but gives no next cursor" }
                            return RefreshOutcome.Failed
                        }
                    }
                    is ApiResult.Failure -> {
                        if (answer.code == ApiErrorCode.INVALID_CURSOR && !restarted) {
                            restarted = true
                            restart = true
                            break
                        }
                        log.w { "budget progress refused (request ${answer.problem.requestId}): ${answer.problem.code.ifEmpty { "status ${answer.problem.status}" }}" }
                        return RefreshOutcome.Failed
                    }
                    ApiResult.Offline, ApiResult.Timeout -> {
                        log.i { "budget progress: no answer after $pages pages" }
                        return RefreshOutcome.Offline
                    }
                }
            }
            if (restart) continue
            if (accountId != account) return RefreshOutcome.Failed
            store(account, rows)
            log.i { "budget progress: ${rows.size} rows, $pages pages" }
            return RefreshOutcome.Done
        }
    }

    /**
     * Stores a completed walk with the phone's synced-only figures from one snapshot taken after the reads (R-06, R-09).
     * Every page of a walk shares one period, so one snapshot serves them all.
     */
    private suspend fun store(account: String, rows: List<BudgetProgressDto>) {
        val budgets = repository.getAll().associateBy { it.id }
        val readAt = clock.now().toString()
        val entities = localFigures(rows, budgets).map { (dto, local) -> dto.toStored(account, local.first, local.second, readAt) }
        progress.replaceWalk(account, entities, budgets.values.filter { it.archivedAt != null }.map { it.id })
    }

    /** The phone's synced-only sums for each row's budget, from one snapshot per period; zero for a budget not on the phone. */
    private suspend fun localFigures(rows: List<BudgetProgressDto>, budgets: Map<String, BudgetEntity>): List<Pair<BudgetProgressDto, Pair<Long, List<String>>>> {
        val snapshots = mutableMapOf<Pair<LocalDate, LocalDate>, SpendingSnapshot>()
        return rows.map { dto ->
            val start = runCatching { LocalDate.parse(dto.period.start) }.getOrNull()
            val end = runCatching { LocalDate.parse(dto.period.end) }.getOrNull()
            val budget = budgets[dto.budgetId]
            if (start == null || end == null || budget == null) return@map dto to (0L to emptyList())
            val snapshot = snapshots.getOrPut(start to end) { spending.observe(start, end).first() }
            val period = BudgetPeriod(start, end, dto.period.daysInPeriod, dto.period.daysElapsed)
            val sums = SpendingTotals.syncedOnly(snapshot, budget.categoryId, budget.currency, period)
            dto to (sums.spentMinor to sums.otherCurrencies)
        }
    }

    override suspend fun refreshOne(id: String, force: Boolean): RefreshOutcome {
        val account = accountId ?: return RefreshOutcome.Done
        forgetFloorsOfAnotherAccount()
        val row = repository.get(id)
        // A budget the server does not hold yet has no progress there (404); the phone's estimate stands (FR-018).
        if (row == null || row.version == 0L) return RefreshOutcome.Done
        oneLock.withLock {
            val last = lastOne[id]
            if (!force && last != null && clock.now() - last < readFloor) return RefreshOutcome.Done
            // The attempt counts even when it fails, so an editor reopened offline asks once a minute.
            lastOne[id] = clock.now()
        }
        return when (val answer = api.progressOne(id)) {
            is ApiResult.Success -> {
                if (accountId == account) {
                    val local = localFigures(listOf(answer.value), mapOf(id to row)).single().second
                    progress.store(answer.value.toStored(account, local.first, local.second, clock.now().toString()))
                }
                RefreshOutcome.Done
            }
            is ApiResult.Failure -> {
                if (answer.code == ApiErrorCode.NOT_FOUND) {
                    RefreshOutcome.Done
                } else {
                    log.w { "budget progress refused (request ${answer.problem.requestId}): ${answer.problem.code.ifEmpty { "status ${answer.problem.status}" }}" }
                    RefreshOutcome.Failed
                }
            }
            ApiResult.Offline, ApiResult.Timeout -> RefreshOutcome.Offline
        }
    }

    private fun combineOutcomes(a: RefreshOutcome, b: RefreshOutcome) = when {
        a == RefreshOutcome.Offline || b == RefreshOutcome.Offline -> RefreshOutcome.Offline
        a == RefreshOutcome.Failed || b == RefreshOutcome.Failed -> RefreshOutcome.Failed
        else -> RefreshOutcome.Done
    }

    private fun ReadOutcome.toOutcome(): RefreshOutcome = when (this) {
        ReadOutcome.Done -> RefreshOutcome.Done
        ReadOutcome.Offline -> RefreshOutcome.Offline
        ReadOutcome.Failed -> RefreshOutcome.Failed
    }

    companion object {
        private val CURRENCY = Regex("^[A-Z]{3}$")

        /** Used only when the phone knows neither a profile default nor a wallet (an assumed default, plan.md Implementation notes). */
        const val FALLBACK_CURRENCY = "EGP"
    }
}
