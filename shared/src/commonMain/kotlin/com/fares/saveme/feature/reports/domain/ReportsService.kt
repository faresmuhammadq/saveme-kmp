package com.fares.saveme.feature.reports.domain

import co.touchlab.kermit.Logger
import com.fares.saveme.core.network.ApiResult
import com.fares.saveme.core.sync.ReadOutcome
import com.fares.saveme.core.sync.ReadTarget
import com.fares.saveme.core.sync.SyncRunner
import com.fares.saveme.feature.account.domain.model.ProfileView
import com.fares.saveme.feature.auth.domain.AuthState
import com.fares.saveme.feature.auth.domain.model.RefreshOutcome
import com.fares.saveme.feature.budgets.domain.DayTicker
import com.fares.saveme.feature.categories.domain.CategoriesService
import com.fares.saveme.feature.categories.domain.model.ReportLabels
import com.fares.saveme.feature.recurring.domain.ScheduledLine
import com.fares.saveme.feature.recurring.domain.ScheduledMoney
import com.fares.saveme.feature.reports.data.ReportsApi
import com.fares.saveme.feature.reports.domain.model.BudgetPeriodSpan
import com.fares.saveme.feature.reports.domain.model.Covered
import com.fares.saveme.feature.reports.domain.model.HomePeriod
import com.fares.saveme.feature.reports.domain.model.PeriodLookup
import com.fares.saveme.feature.reports.domain.model.RangeChoice
import com.fares.saveme.feature.reports.domain.model.RangeKind
import com.fares.saveme.feature.reports.domain.model.RangeProblem
import com.fares.saveme.feature.reports.domain.model.RangeSelection
import com.fares.saveme.feature.reports.domain.model.Report
import com.fares.saveme.feature.reports.domain.model.ReportChoices
import com.fares.saveme.feature.reports.domain.model.ReportRange
import com.fares.saveme.feature.reports.domain.model.ReportScope
import com.fares.saveme.feature.reports.domain.model.ReportState
import com.fares.saveme.feature.reports.domain.model.ReportWallet
import com.fares.saveme.feature.reports.domain.model.TrendSlot
import com.fares.saveme.feature.transactions.domain.LedgerSnapshot
import com.fares.saveme.feature.transactions.domain.ReportLedger
import com.fares.saveme.feature.transactions.domain.TransactionsService
import com.fares.saveme.feature.wallets.domain.WalletsService
import com.fares.saveme.feature.wallets.domain.model.SyncState
import com.fares.saveme.feature.wallets.domain.model.WalletView
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

/**
 * The reports domain API (contracts/domain-apis.md). Every figure is computed on the phone from its own rows with waiting
 * changes overlaid (FR-001); the server's report figures are never shown. Other features use only this package (P-10.4);
 * the screens' stores take its functions as lambdas.
 */
interface ReportsService {
    /** The report for a scope under the session's choices; recomputed on any change of rows, queue, profile, labels or day. */
    fun observe(scope: ReportScope): Flow<ReportState>

    fun observeChoices(scope: ReportScope): Flow<ReportChoices>

    /** The currencies of the person's wallets and the profile default, sorted. */
    fun observeCurrencies(): Flow<List<String>>

    /** Changes the range; an unknown period is looked up from the server when online (FR-019). */
    suspend fun setRange(scope: ReportScope, range: RangeChoice)

    /** 3, 6 or 12; anything else is ignored. */
    suspend fun setTrendLength(scope: ReportScope, length: Int)

    /** Cross-wallet only. */
    fun setCurrency(currency: String)

    fun setIncludeScheduled(scope: ReportScope, on: Boolean)

    fun validateCustom(from: LocalDate, to: LocalDate): RangeProblem?

    /** Sends what waits, reads the covered scopes (60 second floors in the runner; [force] skips them), then looks up periods. */
    suspend fun refresh(scope: ReportScope, force: Boolean = false): RefreshOutcome

    /** Names, icons and colours for the categories of the scope's wallets. */
    fun observeLabels(scope: ReportScope): Flow<ReportLabels>

    /** Home's figures: the current period over every wallet in the profile's currency, whatever the Reports screen chose. */
    fun observeHomePeriod(): Flow<HomePeriod> = flowOf()
}

class DefaultReportsService(
    private val wallets: WalletsService,
    private val transactions: TransactionsService,
    private val ledger: ReportLedger,
    private val scheduled: ScheduledMoney,
    private val categories: CategoriesService,
    private val profile: Flow<ProfileView?>,
    private val deviceZoneId: () -> String,
    private val authState: StateFlow<AuthState>,
    private val choices: ReportChoicesStore,
    private val learned: LearnedPeriods,
    private val api: ReportsApi,
    private val runner: SyncRunner,
    private val probe: ReportParityProbe = NoParityProbe,
    private val now: Flow<Instant> = DayTicker().ticks(),
) : ReportsService {

    private val log = Logger.withTag("reports")
    private val attempted = HashSet<String>()

    private val accountId: String? get() = (authState.value as? AuthState.SignedIn)?.account?.userId

    private fun accountFlow(): Flow<String?> = authState.map { (it as? AuthState.SignedIn)?.account?.userId }.distinctUntilChanged()

    // ---- context ---------------------------------------------------------------------------------------------------

    /** Today in the profile's zone (the device's until the profile is read), the period rule and the profile's currency. */
    private data class Base(val today: LocalDate, val profile: PeriodProfile, val defaultCurrency: String?)

    private class Ctx(val base: Base, val learned: List<BudgetPeriodSpan>) {
        val today: LocalDate get() = base.today
        val profile: PeriodProfile get() = base.profile
    }

    private fun baseFlow(): Flow<Base> = combine(profile, now) { view, instant ->
        val server = view?.server
        val zone = runCatching { TimeZone.of(server?.timezoneId ?: deviceZoneId()) }.getOrElse { TimeZone.UTC }
        Base(
            today = instant.toLocalDateTime(zone).date,
            // A start day changed on this phone does not count until the server's copy carries it (FR-011).
            profile = if (server == null) PeriodProfile.None else PeriodProfile(server.budgetStartDay, server.budgetStartDayFrom),
            defaultCurrency = view?.effective?.defaultCurrency,
        )
    }.distinctUntilChanged()

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun ctxFlow(account: String): Flow<Ctx> =
        baseFlow().flatMapLatest { base -> learned.observe(account, base.profile).map { Ctx(base, it) } }

    private suspend fun ctxNow(account: String): Ctx {
        val base = baseFlow().first()
        return Ctx(base, learned.get(account, base.profile))
    }

    // ---- ranges ----------------------------------------------------------------------------------------------------

    /** [anchor] is the date the trend ends on while the range's period cannot be named. */
    private class Resolved(val range: ReportRange?, val anchor: LocalDate)

    private fun resolve(selection: RangeSelection, ctx: Ctx): Resolved = when (selection) {
        RangeSelection.CurrentPeriod -> period(ctx.today, ctx)
        is RangeSelection.PeriodContaining -> period(selection.anchor, ctx)
        is RangeSelection.Custom -> Resolved(ReportRange(selection.from, selection.to, RangeKind.Custom), selection.to)
    }

    private fun period(date: LocalDate, ctx: Ctx): Resolved = when (val lookup = ReportPeriods.containing(date, ctx.profile, ctx.learned)) {
        is PeriodLookup.Known -> Resolved(ReportRange(lookup.span.start, lookup.span.end, RangeKind.Period, lookup.source), date)
        PeriodLookup.Unknown -> Resolved(null, date)
    }

    // ---- reads -----------------------------------------------------------------------------------------------------

    /** Everything the figures depend on that is not a row: scope, wallets, range, trend periods, currency and switch. */
    private data class Plan(
        val scope: ReportScope,
        val title: String?,
        val isLiability: Boolean,
        val currency: String,
        val currencies: List<String>,
        val covered: Covered,
        val range: ReportRange?,
        val canGoNext: Boolean,
        val slots: List<PeriodLookup>,
        val window: Pair<LocalDate, LocalDate>?,
        val includeScheduled: Boolean,
        val knownUntil: LocalDate?,
        val choices: ReportChoices,
        val today: LocalDate,
    )

    private sealed interface Planned {
        data class Ready(val plan: Plan) : Planned
        data class Terminal(val state: ReportState) : Planned
    }

    private fun reportWallet(view: WalletView) = ReportWallet(
        id = view.id,
        name = view.shown.name,
        currency = view.shown.currency,
        openingMinor = view.shown.openingBalanceMinor,
        isLiability = view.isLiability,
        archived = view.archived,
        waiting = view.syncState is SyncState.Waiting,
    )

    private fun plansFor(account: String, scope: ReportScope): Flow<Planned> = when (scope) {
        ReportScope.AllWallets -> combine(wallets.observeList(), ctxFlow(account), choices.observe(account, scope)) { list, ctx, chosen ->
            val all = (list.active + list.archived).map(::reportWallet)
            if (all.isEmpty()) {
                Planned.Terminal(ReportState.NoWallets)
            } else {
                val currency = chosen.currency ?: CoveredWallets.defaultCurrency(ctx.base.defaultCurrency)
                val covered = CoveredWallets.forCurrency(all, currency)
                val currencies = (CoveredWallets.currencies(all, ctx.base.defaultCurrency) + currency).distinct().sorted()
                Planned.Ready(plan(scope, null, false, covered, currencies, chosen, ctx))
            }
        }
        is ReportScope.Wallet -> combine(wallets.observeWallet(scope.walletId), ctxFlow(account), choices.observe(account, scope)) { view, ctx, chosen ->
            if (view == null) {
                Planned.Terminal(ReportState.WalletGone)
            } else {
                val wallet = reportWallet(view)
                Planned.Ready(plan(scope, wallet.name, wallet.isLiability, CoveredWallets.forWallet(wallet), emptyList(), chosen, ctx))
            }
        }
    }.distinctUntilChanged()

    private fun plan(scope: ReportScope, title: String?, liability: Boolean, covered: Covered, currencies: List<String>, chosen: ReportChoices, ctx: Ctx): Plan {
        val resolved = resolve(chosen.range, ctx)
        val range = resolved.range
        val slots = ReportPeriods.trend(range?.to ?: resolved.anchor, chosen.trendLength, ctx.profile, ctx.learned)
        val known = slots.filterIsInstance<PeriodLookup.Known>().map { it.span }
        val window = range?.let { r ->
            (known.minOfOrNull { it.start }?.let { minOf(it, r.from) } ?: r.from) to (known.maxOfOrNull { it.end }?.let { maxOf(it, r.to) } ?: r.to)
        }
        val limit = ctx.today.plus(DatePeriod(days = ReportPeriods.FORWARD_DAYS))
        return Plan(
            scope = scope, title = title, isLiability = liability, currency = covered.currency, currencies = currencies, covered = covered,
            range = range,
            canGoNext = range != null && ReportPeriods.canGoNext(range.to, ctx.today, ctx.profile, ctx.learned),
            slots = slots,
            window = window,
            includeScheduled = chosen.includeScheduled,
            knownUntil = limit.takeIf { chosen.includeScheduled && range != null && range.to > limit },
            choices = chosen,
            today = ctx.today,
        )
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observe(scope: ReportScope): Flow<ReportState> = accountFlow().flatMapLatest { account ->
        if (account == null) {
            flowOf(ReportState.Loading)
        } else {
            choices.dropOtherAccounts(account)
            plansFor(account, scope).flatMapLatest { planned ->
                when (planned) {
                    is Planned.Terminal -> flowOf(planned.state)
                    is Planned.Ready -> data(planned.plan)
                }
            }
        }
    }

    private fun stampOf(scope: ReportScope): Flow<Instant?> = when (scope) {
        ReportScope.AllWallets -> wallets.observeList().map { it.updatedAt }.distinctUntilChanged()
        is ReportScope.Wallet -> transactions.observeFeedReadAt(scope.walletId)
    }

    private fun data(plan: Plan): Flow<ReportState> {
        val ids = plan.covered.walletIds
        val window = plan.window.takeIf { ids.isNotEmpty() }
        val range = plan.range
        val ledgerFlow = if (window == null || range == null) flowOf(LedgerSnapshot.Empty) else ledger.observe(ids, range.from, range.to, window.first, window.second)
        // With the switch on, a report that covers no wallet still answers scheduled zeros, as the server does (live QA, T089).
        val scheduledFlow: Flow<List<ScheduledLine>?> = when {
            !plan.includeScheduled -> flowOf(null)
            window != null -> scheduled.observe(ids, window.first, window.second)
            ids.isEmpty() -> flowOf(emptyList())
            else -> flowOf(null)
        }
        val waitingFlow = if (plan.includeScheduled && range != null && ids.isNotEmpty()) scheduled.observeWaiting(ids, range.from, range.to) else flowOf(false)
        return combine(ledgerFlow, scheduledFlow, waitingFlow, categories.observeReportLabels(ids), stampOf(plan.scope)) { snap, sched, waiting, labels, at ->
            ReportState.Ready(assemble(plan, snap, sched, waiting, labels, at))
        }
    }

    private fun assemble(plan: Plan, snap: LedgerSnapshot, sched: List<ScheduledLine>?, waitingOccurrence: Boolean, labels: ReportLabels, updatedAt: Instant?): Report {
        val range = plan.range
        val includesWaiting = range != null &&
            (snap.waitingTouches.any { it.date <= range.to } || plan.covered.wallets.any { it.waiting } || waitingOccurrence)
        fun report(
            summary: com.fares.saveme.feature.reports.domain.model.SummaryFigures?,
            planned: com.fares.saveme.feature.reports.domain.model.PlannedSummary?,
            breakdown: com.fares.saveme.feature.reports.domain.model.CategoryBreakdown?,
            trend: List<TrendSlot>,
            tooLarge: Boolean,
        ) = Report(
            scope = plan.scope, title = plan.title, currency = plan.currency, currencies = plan.currencies, today = plan.today, range = range,
            canGoNext = plan.canGoNext, covered = plan.covered, summary = summary, planned = planned, breakdown = breakdown,
            trend = trend, includeScheduled = plan.includeScheduled, knownUntil = plan.knownUntil, isLiability = plan.isLiability,
            includesWaiting = includesWaiting, updatedAt = updatedAt, tooLarge = tooLarge, choices = plan.choices,
        )
        return try {
            val ids = plan.covered.walletIds
            val summary = range?.let {
                ReportCalculator.summary(plan.currency, ids, plan.covered.openings, it.from, it.to, snap.lines, snap.before)
            }
            val planned = if (range != null && sched != null) ReportCalculator.planned(plan.currency, ids, range.from, range.to, sched) else null
            val breakdown = range?.let {
                ReportCalculator.breakdown(plan.currency, ids, it.from, it.to, snap.lines, sched, labels::keyOf)
            }
            report(summary, planned, breakdown, trendOf(plan, snap.lines, snap.daily, sched), tooLarge = false)
        } catch (_: TooLarge) {
            report(null, null, null, plan.slots.map { TrendSlot.Unknown }, tooLarge = true)
        }
    }

    private fun trendOf(
        plan: Plan,
        lines: List<com.fares.saveme.feature.transactions.domain.LedgerLine>,
        daily: List<com.fares.saveme.feature.transactions.domain.DaySum>,
        sched: List<ScheduledLine>?,
    ): List<TrendSlot> {
        val spans = plan.slots.filterIsInstance<PeriodLookup.Known>().map { it.span }
        val points = ReportCalculator.trend(plan.currency, plan.covered.walletIds, spans, lines, sched, daily).iterator()
        return plan.slots.map { slot -> if (slot is PeriodLookup.Known) TrendSlot.Known(points.next()) else TrendSlot.Unknown }
    }

    override fun observeChoices(scope: ReportScope): Flow<ReportChoices> =
        accountFlow().flatMapLatest { account -> if (account == null) flowOf(ReportChoices()) else choices.observe(account, scope) }

    override fun observeCurrencies(): Flow<List<String>> = combine(wallets.observeList(), profile) { list, view ->
        val all = (list.active + list.archived).map(::reportWallet)
        CoveredWallets.currencies(all, view?.effective?.defaultCurrency)
    }.distinctUntilChanged()

    override fun observeLabels(scope: ReportScope): Flow<ReportLabels> = when (scope) {
        is ReportScope.Wallet -> categories.observeReportLabels(setOf(scope.walletId))
        ReportScope.AllWallets -> wallets.observeList().map { list -> (list.active + list.archived).map { it.id }.toSet() }
            .distinctUntilChanged()
            .flatMapLatest { ids -> categories.observeReportLabels(ids) }
    }

    // ---- Home ------------------------------------------------------------------------------------------------------

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observeHomePeriod(): Flow<HomePeriod> = accountFlow().flatMapLatest { account ->
        if (account == null) {
            flowOf()
        } else {
            combine(wallets.observeList(), ctxFlow(account)) { list, ctx -> list to ctx }.flatMapLatest { (list, ctx) ->
                homePeriod((list.active + list.archived).map(::reportWallet), ctx)
            }
        }
    }

    private fun homePeriod(all: List<ReportWallet>, ctx: Ctx): Flow<HomePeriod> {
        val currency = ctx.base.defaultCurrency ?: all.firstOrNull { !it.archived }?.currency ?: CoveredWallets.FALLBACK_CURRENCY
        val covered = CoveredWallets.forCurrency(all, currency)
        val range = resolve(RangeSelection.CurrentPeriod, ctx).range
        val empty = HomePeriod(
            noWallets = all.isEmpty(), currency = currency, today = ctx.today, range = range, excluded = covered.excluded, summary = null,
            breakdown = null, dailyExpenses = emptyList(), todayIndex = -1, changePercent = null, includesWaiting = false,
            tooLarge = false, labels = ReportLabels.identity(),
        )
        if (all.isEmpty() || covered.walletIds.isEmpty() || range == null) return flowOf(empty)
        val ids = covered.walletIds
        val previous = ReportPeriods.previous(BudgetPeriodSpan(range.from, range.to), ctx.profile, ctx.learned) as? PeriodLookup.Known
        val extraFrom = previous?.span?.start ?: range.from
        return combine(ledger.observe(ids, range.from, range.to, extraFrom, range.to), categories.observeReportLabels(ids)) { snap, labels ->
            val waiting = snap.waitingTouches.any { it.date <= range.to } || covered.wallets.any { it.waiting }
            try {
                val summary = ReportCalculator.summary(currency, ids, covered.openings, range.from, range.to, snap.lines, snap.before)
                val breakdown = ReportCalculator.breakdown(currency, ids, range.from, range.to, snap.lines, null, labels::keyOf)
                val days = (range.to.toEpochDays() - range.from.toEpochDays() + 1).toInt().coerceAtLeast(0)
                val daily = LongArray(days)
                for (line in snap.lines) {
                    if (line.kind != "expense" || line.walletId !in ids || line.date < range.from || line.date > range.to) continue
                    val index = (line.date.toEpochDays() - range.from.toEpochDays()).toInt()
                    daily[index] = addExact(daily[index], line.amountMinor)
                }
                val todayIndex = (ctx.today.toEpochDays() - range.from.toEpochDays()).toInt().coerceIn(-1, days - 1)
                val change = previous?.let { prev ->
                    val dayCount = todayIndex + 1
                    val from = prev.span.start
                    val last = minOf(from.plus(DatePeriod(days = dayCount - 1)), prev.span.end)
                    var before = 0L
                    for (day in snap.daily) if (day.date >= from && day.date <= last) before = addExact(before, day.expensesMinor)
                    var now = 0L
                    for (index in 0..todayIndex) now = addExact(now, daily[index])
                    if (before <= 0L || dayCount <= 0) null else (((now - before) * 100) / before).toInt()
                }
                empty.copy(
                    summary = summary, breakdown = breakdown, dailyExpenses = daily.toList(), todayIndex = todayIndex,
                    changePercent = change, includesWaiting = waiting, labels = labels,
                )
            } catch (_: TooLarge) {
                empty.copy(tooLarge = true, includesWaiting = waiting, labels = labels)
            }
        }
    }

    // ---- choices ---------------------------------------------------------------------------------------------------

    override suspend fun setRange(scope: ReportScope, range: RangeChoice) {
        val account = accountId ?: return
        val ctx = ctxNow(account)
        val current = choices.get(account, scope).range
        val next: RangeSelection? = when (range) {
            RangeChoice.CurrentPeriod -> RangeSelection.CurrentPeriod
            is RangeChoice.Custom -> RangeSelection.Custom(range.from, range.to).takeIf { RangeRules.validate(range.from, range.to) == null }
            RangeChoice.Previous -> resolve(current, ctx).range?.let { RangeSelection.PeriodContaining(it.from.minus(DatePeriod(days = 1))) }
            RangeChoice.Next -> resolve(current, ctx).range
                ?.takeIf { ReportPeriods.canGoNext(it.to, ctx.today, ctx.profile, ctx.learned) }
                ?.let { RangeSelection.PeriodContaining(it.to.plus(DatePeriod(days = 1))) }
        }
        if (next == null) return
        choices.update(account, scope) { it.copy(range = next) }
        lookUp(scope, account)
    }

    override suspend fun setTrendLength(scope: ReportScope, length: Int) {
        val account = accountId ?: return
        if (length !in ReportChoices.TREND_LENGTHS) return
        choices.update(account, scope) { it.copy(trendLength = length) }
        lookUp(scope, account)
    }

    override fun setCurrency(currency: String) {
        val account = accountId ?: return
        choices.update(account, ReportScope.AllWallets) { it.copy(currency = currency) }
    }

    override fun setIncludeScheduled(scope: ReportScope, on: Boolean) {
        val account = accountId ?: return
        choices.update(account, scope) { it.copy(includeScheduled = on) }
    }

    override fun validateCustom(from: LocalDate, to: LocalDate): RangeProblem? = RangeRules.validate(from, to)

    // ---- refresh, period lookup and the development probe --------------------------------------------------------------

    override suspend fun refresh(scope: ReportScope, force: Boolean): RefreshOutcome {
        val account = accountId ?: return RefreshOutcome.Done
        val feeds = when (scope) {
            ReportScope.AllWallets -> runner.refresh(ReadTarget.Everything, force).toOutcome()
            is ReportScope.Wallet -> combineOutcomes(
                runner.refresh(ReadTarget.Wallet(scope.walletId), force).toOutcome(),
                runner.refresh(ReadTarget.User, force).toOutcome(),
            )
        }
        log.i { "report.refresh scope=${scope.key} outcome=$feeds" }
        if (feeds == RefreshOutcome.Done) {
            attempted.clear()
            lookUp(scope, account)
            runProbe(scope, account)
        }
        return feeds
    }

    /**
     * Learns the Budget Periods the phone cannot derive (FR-019): one `GET /reports/trend` with only `months` and `date`. The
     * unknown periods are the oldest ones, so a lookup ends in the day before the oldest period the phone can name; when
     * nothing can be named it ends in the range's own day.
     */
    private suspend fun lookUp(scope: ReportScope, account: String) {
        val ctx = ctxNow(account)
        val chosen = choices.get(account, scope)
        val resolved = resolve(chosen.range, ctx)
        val toDate = resolved.range?.to ?: resolved.anchor
        val slots = ReportPeriods.trend(toDate, chosen.trendLength, ctx.profile, ctx.learned)
        val unknown = slots.takeWhile { it is PeriodLookup.Unknown }.size
        if (unknown == 0) return
        val (months, date) = if (unknown == slots.size) {
            slots.size to toDate
        } else {
            unknown to (slots[unknown] as PeriodLookup.Known).span.start.minus(DatePeriod(days = 1))
        }
        val key = "$account/$date/$months"
        if (!attempted.add(key)) return
        when (val answer = api.trend(months, date)) {
            is ApiResult.Success -> {
                if (accountId != account) return
                val spans = answer.value.data.periods.mapNotNull { period ->
                    val from = runCatching { LocalDate.parse(period.from) }.getOrNull()
                    val to = runCatching { LocalDate.parse(period.to) }.getOrNull()
                    if (from != null && to != null) BudgetPeriodSpan(from, to) else null
                }
                learned.store(account, ctx.profile, spans)
                log.i { "report.lookup learned ${spans.size} periods" }
            }
            is ApiResult.Failure -> {
                if (answer.problem.code == "INVALID_QUERY_PARAMETER") {
                    log.e { "report.lookup invalid (client defect) request=${answer.problem.requestId}" }
                } else {
                    log.w { "report.lookup refused code=${answer.problem.code.ifEmpty { "status ${answer.problem.status}" }} request=${answer.problem.requestId}" }
                }
            }
            ApiResult.Offline, ApiResult.Timeout -> log.i { "report.lookup no answer" }
        }
    }

    /** After a successful read with nothing waiting in the covered wallets: compares the synced-only figures (FR-028). */
    private suspend fun runProbe(scope: ReportScope, account: String) {
        if (!probe.enabled) return
        val planned = plansFor(account, scope).first() as? Planned.Ready ?: return
        val plan = planned.plan
        val range = plan.range ?: return
        val window = plan.window ?: return
        if (plan.slots.any { it is PeriodLookup.Unknown }) return
        val ids = plan.covered.walletIds
        if (ids.isEmpty() || plan.covered.wallets.any { it.waiting }) return
        val snap = ledger.observe(ids, range.from, range.to, window.first, window.second).first()
        if (snap.waitingTouches.isNotEmpty()) return
        if (scheduled.observeWaiting(ids, window.first, window.second).first()) return
        val sched = if (plan.includeScheduled) scheduled.observe(ids, window.first, window.second).first() else null
        val labels = categories.observeReportLabels(ids).first()
        val computation = try {
            val spans = plan.slots.filterIsInstance<PeriodLookup.Known>().map { it.span }
            ProbeComputation(
                summary = ReportCalculator.summary(plan.currency, ids, plan.covered.openings, range.from, range.to, snap.syncedLines, snap.syncedBefore),
                planned = if (sched != null) ReportCalculator.planned(plan.currency, ids, range.from, range.to, sched) else null,
                breakdown = ReportCalculator.breakdown(plan.currency, ids, range.from, range.to, snap.syncedLines, sched, labels::keyOf),
                trend = ReportCalculator.trend(plan.currency, ids, spans, snap.syncedLines, sched, snap.syncedDaily),
                excluded = plan.covered.excluded.map { it.walletId to it.currency }.toSet(),
            )
        } catch (_: TooLarge) {
            return
        }
        probe.compare(
            ProbeRequest(scope, range.from, range.to, plan.currency, plan.includeScheduled, plan.choices.trendLength, range.to),
            computation,
        )
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
}
