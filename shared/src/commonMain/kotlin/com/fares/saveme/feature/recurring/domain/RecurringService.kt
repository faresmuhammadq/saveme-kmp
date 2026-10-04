package com.fares.saveme.feature.recurring.domain

import co.touchlab.kermit.Logger
import com.fares.saveme.core.network.ApiErrorCode
import com.fares.saveme.core.network.ApiResult
import com.fares.saveme.core.network.AppJson
import com.fares.saveme.core.sync.ReadOutcome
import com.fares.saveme.core.sync.ReadTarget
import com.fares.saveme.core.sync.ScopeKind
import com.fares.saveme.core.sync.SyncScope
import com.fares.saveme.feature.recurring.data.dto.OccurrencePayload
import com.fares.saveme.feature.recurring.data.dto.RulePayload
import com.fares.saveme.feature.recurring.domain.model.Rule
import com.fares.saveme.feature.recurring.domain.model.RuleField
import com.fares.saveme.feature.recurring.domain.model.RuleFieldProblem
import com.fares.saveme.feature.recurring.domain.model.toPayload
import com.fares.saveme.feature.transactions.domain.DestinationChoices
import com.fares.saveme.feature.transactions.domain.PendingConfirmation
import com.fares.saveme.feature.transactions.domain.model.TransactionKind
import kotlinx.coroutines.flow.first
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.minus
import com.fares.saveme.core.sync.PendingChange
import com.fares.saveme.core.sync.SyncEngine
import com.fares.saveme.core.sync.SyncRunner
import com.fares.saveme.feature.auth.domain.AuthState
import com.fares.saveme.feature.auth.domain.model.RefreshOutcome
import com.fares.saveme.feature.recurring.data.OccurrenceRepository
import com.fares.saveme.feature.recurring.data.OccurrenceSyncHandler
import com.fares.saveme.feature.recurring.data.NewRule
import com.fares.saveme.feature.recurring.data.RecurringApi
import com.fares.saveme.feature.recurring.data.RecurringRepository
import com.fares.saveme.feature.recurring.data.RecurringRuleSyncHandler
import com.fares.saveme.feature.recurring.data.local.OccurrenceEntity
import com.fares.saveme.feature.recurring.data.local.RecurringRuleEntity
import com.fares.saveme.feature.recurring.domain.model.AmountBasis
import com.fares.saveme.feature.recurring.domain.model.CatchUpOutcome
import com.fares.saveme.feature.recurring.domain.model.ConfirmDraft
import com.fares.saveme.feature.recurring.domain.model.FrozenMembers
import com.fares.saveme.feature.recurring.domain.model.OccurrenceLists
import com.fares.saveme.feature.recurring.domain.model.OccurrencePending
import com.fares.saveme.feature.recurring.domain.model.OccurrenceResult
import com.fares.saveme.feature.recurring.domain.model.OccurrenceState
import com.fares.saveme.feature.recurring.domain.model.OccurrenceStatus
import com.fares.saveme.feature.recurring.domain.model.OccurrenceView
import com.fares.saveme.feature.recurring.domain.model.Preset
import com.fares.saveme.feature.recurring.domain.model.PreviewRound
import com.fares.saveme.feature.recurring.domain.model.RecurringRecord
import com.fares.saveme.feature.recurring.domain.model.RecurringSyncState
import com.fares.saveme.feature.recurring.domain.model.RuleDraft
import com.fares.saveme.feature.recurring.domain.model.RuleResult
import com.fares.saveme.feature.recurring.domain.model.RuleStatus
import com.fares.saveme.feature.recurring.domain.model.RuleView
import com.fares.saveme.feature.recurring.domain.model.SchedulePreview
import com.fares.saveme.feature.recurring.domain.model.decodeOccurrencePayload
import com.fares.saveme.feature.recurring.domain.model.decodeRulePayload
import com.fares.saveme.feature.recurring.domain.model.parseDate
import com.fares.saveme.feature.recurring.domain.model.toDraft
import com.fares.saveme.feature.recurring.domain.model.toOccurrence
import com.fares.saveme.feature.recurring.domain.model.toPending
import com.fares.saveme.feature.recurring.domain.model.toRecurringSyncState
import com.fares.saveme.feature.recurring.domain.model.toRule
import com.fares.saveme.feature.recurring.domain.schedule.Allocation
import com.fares.saveme.feature.recurring.domain.schedule.OccurrenceStates
import com.fares.saveme.feature.recurring.domain.schedule.RuleDates
import com.fares.saveme.feature.recurring.domain.schedule.RuleToday
import com.fares.saveme.feature.recurring.domain.schedule.StateSlot
import com.fares.saveme.feature.recurring.domain.schedule.TodayTicker
import com.fares.saveme.feature.transactions.domain.OccurrenceTransactions
import com.fares.saveme.feature.wallets.domain.WalletsService
import com.fares.saveme.feature.wallets.domain.model.WalletList
import com.fares.saveme.feature.wallets.domain.model.WalletRole
import com.fares.saveme.feature.wallets.domain.model.WalletView
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.datetime.LocalDate
import kotlin.time.Clock
import kotlin.time.Instant
import kotlin.uuid.Uuid

/**
 * The recurring domain API (contracts/recurring-domain.md). Screens read only from the phone's copy (P-04.1) and take
 * these functions as lambdas (P-16.6). Other features reach it through their own hook interfaces (research R-10).
 */
interface RecurringService {
    /** Rules of one wallet (or all the person's wallets when null), with sync marks, next date and frozen members. */
    fun observeRules(walletId: String?): Flow<List<RuleView>>
    fun observeRule(ruleId: String): Flow<RuleView?>

    /** Occurrences held on the phone, overlays applied, states derived with the rule's today (ticks each minute). */
    fun observeOccurrences(walletId: String?, ruleId: String? = null): Flow<OccurrenceLists>
    fun observeOccurrence(occurrenceId: String): Flow<OccurrenceView?>

    /** Phone-side preview (FR-006); pure, for the editor and unsynced rules. */
    fun preview(draft: RuleDraft, limit: Int = 5): SchedulePreview

    suspend fun create(walletId: String, draft: RuleDraft): RuleResult
    suspend fun save(ruleId: String, draft: RuleDraft, baseVersion: Long): RuleResult
    suspend fun changeFrom(ruleId: String, from: LocalDate, draft: RuleDraft, baseVersion: Long): RuleResult
    suspend fun setStatus(ruleId: String, status: RuleStatus, baseVersion: Long): RuleResult
    suspend fun delete(ruleId: String): RuleResult

    suspend fun confirm(occurrenceId: String, draft: ConfirmDraft): OccurrenceResult
    suspend fun skip(occurrenceId: String): OccurrenceResult
    suspend fun unskip(occurrenceId: String): OccurrenceResult
    suspend fun edit(occurrenceId: String, draft: ConfirmDraft): OccurrenceResult
    suspend fun cancelConfirmation(occurrenceId: String)
    suspend fun dismissNotice(occurrenceId: String)

    suspend fun catchUp(ruleId: String): CatchUpOutcome

    /** Reads the wallet's feed (or every active wallet's when null); a forced read also sends what waits (pull to refresh). */
    suspend fun refresh(walletId: String?, force: Boolean = false): RefreshOutcome

    suspend fun keepMine(record: RecurringRecord, id: String)
    suspend fun useTheirs(record: RecurringRecord, id: String)
    suspend fun tryAgain(record: RecurringRecord, id: String)
    suspend fun discard(record: RecurringRecord, id: String)
}

/**
 * The phone's recurring service: the read side, the preview and every write. A write is checked here (verified
 * address, role, frozen members, the server's validation rules), saved locally first and queued on the sync engine
 * (P-04); nothing here is sent directly except [catchUp].
 *
 * @param profileZone the person's profile time zone id, null until known (used for a rule not yet accepted).
 * @param deviceZone the device's zone id, the last fallback for "today".
 */
class DefaultRecurringService(
    internal val rules: RecurringRepository,
    internal val occurrences: OccurrenceRepository,
    internal val engine: SyncEngine,
    internal val api: RecurringApi,
    internal val wallets: WalletsService,
    internal val occurrenceTransactions: OccurrenceTransactions,
    internal val authState: StateFlow<AuthState>,
    internal val runner: SyncRunner,
    internal val profileZone: Flow<String?>,
    internal val deviceZone: () -> String,
    internal val ticker: TodayTicker = TodayTicker(),
    internal val clock: Clock = Clock.System,
    internal val newId: () -> String = { Uuid.generateV7().toString() },
) : RecurringService {

    internal val accountId: String? get() = (authState.value as? AuthState.SignedIn)?.account?.userId

    internal val emailVerified: Boolean get() = (authState.value as? AuthState.SignedIn)?.account?.emailVerified == true

    private fun accountFlow(): Flow<String?> = authState.map { (it as? AuthState.SignedIn)?.account?.userId }.distinctUntilChanged()

    /** Everything a derivation needs that is not a row of the rule or occurrence itself. */
    private class Context(
        val queueRules: Map<String, PendingChange>,
        val queueOccurrences: Map<String, PendingChange>,
        val wallets: Map<String, WalletView>,
        val now: Instant,
        val profileZone: String?,
    )

    private fun contextFlow(): Flow<Context> = combine(
        engine.observeAll(RecurringRuleSyncHandler.RECORD_TYPE), engine.observeAll(OccurrenceSyncHandler.RECORD_TYPE),
        wallets.observeList(), ticker.ticks(), profileZone,
    ) { qr, qo, list, now, zone ->
        Context(qr.associateBy { it.recordId }, qo.associateBy { it.recordId }, list.byId(), now, zone)
    }

    private fun WalletList.byId(): Map<String, WalletView> = (active + archived).associateBy { it.id }

    private fun roleOf(context: Context, walletId: String): WalletRole? = context.wallets[walletId]?.server?.myRole

    private fun writableWallet(context: Context, walletId: String): Boolean {
        val wallet = context.wallets[walletId] ?: return false
        return (wallet.server.myRole == WalletRole.Owner || wallet.server.myRole == WalletRole.Editor) && !wallet.archived
    }

    private fun todayOf(context: Context, rule: RecurringRuleEntity?): LocalDate =
        RuleToday.of(rule?.timezone, context.profileZone, deviceZone(), context.now)

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observeRules(walletId: String?): Flow<List<RuleView>> =
        accountFlow().flatMapLatest { account ->
            if (account == null) return@flatMapLatest flowOf(emptyList())
            val ruleRows = if (walletId == null) rules.observeAll(account) else rules.observeForWallet(account, walletId)
            val occurrenceRows = if (walletId == null) occurrences.observeAll(account) else occurrences.observeForWallet(account, walletId)
            combine(ruleRows, occurrenceRows, contextFlow()) { ruleList, occurrenceList, context ->
                val byRule = occurrenceList.groupBy { it.recurringRuleId }
                ruleList.map { buildRuleView(it, byRule[it.id].orEmpty(), context) }.filterNot { it.hidden }
            }
        }

    override fun observeRule(ruleId: String): Flow<RuleView?> =
        combine(rules.observe(ruleId), occurrences.observeForRule(ruleId), contextFlow()) { row, occurrenceList, context ->
            row?.let { buildRuleView(it, occurrenceList, context) }?.takeUnless { it.hidden }
        }

    private fun buildRuleView(row: RecurringRuleEntity, occurrenceRows: List<OccurrenceEntity>, context: Context): RuleView {
        val rule = row.toRule()
        val change = context.queueRules[row.id]
        val payload = change?.takeIf { !it.isDelete }?.let { decodeRulePayload(it.payloadJson) }
        val shown = payload?.toDraft() ?: rule.toDraft()
        val heldOn = payload?.followsRuleId ?: row.followsRuleId
        val held = heldOn?.takeIf { it in context.queueRules && it != row.id }
        val today = todayOf(context, row)
        val slots = occurrenceRows.map { effectiveSlot(it, context.queueOccurrences[it.id]) }
        val resolved = slots.filter { it.status == OccurrenceStatus.Confirmed || it.status == OccurrenceStatus.Skipped }
        val frozenSchedule = resolved.isNotEmpty()
        val role = roleOf(context, row.walletId)
        val canWrite = writableWallet(context, row.walletId)
        val nextDate = slots.filter { it.status == OccurrenceStatus.Scheduled && it.date >= today }.minOfOrNull { it.date }
            ?: if (slots.isEmpty() && !rule.hasUnknownValues) {
                RuleDates.dates(shown.frequency, shown.interval, shown.anchorDay, shown.startDate, shown.endDate, shown.occurrenceCount, 1, from = today)
                    .firstOrNull()?.date
            } else {
                null
            }
        return RuleView(
            rule = rule,
            shown = shown,
            hasPendingChange = payload != null,
            pendingDelete = change?.isDelete == true,
            syncState = change.toRecurringSyncState(heldOn = held),
            nextDate = nextDate,
            today = today,
            readOnly = rule.hasUnknownValues || rule.status == RuleStatus.Ended || !canWrite,
            frozen = FrozenMembers(
                schedule = frozenSchedule,
                amountAndCount = frozenSchedule && (rule.preset == Preset.Gameya || rule.amountBasis == AmountBasis.Total),
                lastResolvedDate = resolved.maxOfOrNull { it.date },
            ),
            myRole = role,
            walletName = context.wallets[row.walletId]?.shown?.name,
            hasOccurrences = occurrenceRows.isNotEmpty(),
        )
    }

    /** The slot with the waiting change applied first (a pending skip or confirm), for the derivation (research R-08). */
    private fun effectiveSlot(row: OccurrenceEntity, change: PendingChange?): StateSlot {
        val pending = change?.takeIf { !it.isDelete }?.let { decodeOccurrencePayload(it.payloadJson) }?.toPending()
        return StateSlot(row.id, pending?.date ?: parseDate(row.date), pending?.status ?: OccurrenceStatus.fromWire(row.status), row.isPayout)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observeOccurrences(walletId: String?, ruleId: String?): Flow<OccurrenceLists> =
        accountFlow().flatMapLatest { account ->
            if (account == null) return@flatMapLatest flowOf(OccurrenceLists())
            val occurrenceRows = when {
                ruleId != null -> occurrences.observeForRule(ruleId)
                walletId != null -> occurrences.observeForWallet(account, walletId)
                else -> occurrences.observeAll(account)
            }
            val ruleRows = if (walletId != null) rules.observeForWallet(account, walletId) else rules.observeAll(account)
            combine(occurrenceRows, ruleRows, contextFlow()) { occurrenceList, ruleList, context ->
                lists(buildOccurrenceViews(occurrenceList, ruleList.associateBy { it.id }, context))
            }
        }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observeOccurrence(occurrenceId: String): Flow<OccurrenceView?> =
        occurrences.observe(occurrenceId).flatMapLatest { row ->
            if (row == null) return@flatMapLatest flowOf(null)
            combine(occurrences.observeForRule(row.recurringRuleId), rules.observe(row.recurringRuleId), contextFlow()) { siblings, rule, context ->
                buildOccurrenceViews(siblings, listOfNotNull(rule).associateBy { it.id }, context).firstOrNull { it.id == occurrenceId }
            }
        }

    private fun buildOccurrenceViews(rows: List<OccurrenceEntity>, ruleById: Map<String, RecurringRuleEntity>, context: Context): List<OccurrenceView> {
        val hiddenRules = ruleById.filterValues { rule ->
            context.queueRules[rule.id]?.let { it.isDelete && it.state == com.fares.saveme.core.sync.ChangeState.Waiting } == true
        }.keys
        return rows.filterNot { it.recurringRuleId in hiddenRules }.groupBy { it.recurringRuleId }.flatMap { (ruleId, group) ->
            val rule = ruleById[ruleId]
            val today = todayOf(context, rule)
            val states = OccurrenceStates.deriveAll(group.map { effectiveSlot(it, context.queueOccurrences[it.id]) }, today)
            group.map { row ->
                val change = context.queueOccurrences[row.id]
                val occurrence = row.toOccurrence()
                val pending = change?.takeIf { !it.isDelete }?.let { decodeOccurrencePayload(it.payloadJson) }?.toPending()
                OccurrenceView(
                    occurrence = occurrence,
                    pending = pending,
                    state = states.getValue(row.id),
                    ruleDescription = rule?.description,
                    syncState = change.toRecurringSyncState(),
                    shownDate = pending?.date ?: occurrence.date,
                    shownAmountMinor = pending?.amountMinor ?: occurrence.amountMinor,
                    shownCategoryId = if (pending != null) pending.categoryId else occurrence.categoryId,
                    shownDescription = if (pending != null) pending.description else occurrence.description,
                    shownMemo = if (pending != null) pending.memo else occurrence.memo,
                    shownCounterparty = if (pending != null) pending.counterparty else occurrence.counterparty,
                    currency = occurrence.currency,
                    canWrite = writableWallet(context, row.walletId),
                    notice = occurrence.localNotice,
                )
            }
        }
    }

    /** Due with missed first then oldest; upcoming nearest first; history (confirmed, skipped, unknown) newest first. */
    private fun lists(views: List<OccurrenceView>): OccurrenceLists = OccurrenceLists(
        due = views.filter { it.state == OccurrenceState.Missed || it.state == OccurrenceState.Due }
            .sortedWith(compareBy<OccurrenceView> { if (it.state == OccurrenceState.Missed) 0 else 1 }.thenBy { it.shownDate }.thenBy { it.occurrence.ordinal }.thenBy { it.id }),
        upcoming = views.filter { it.state == OccurrenceState.Upcoming }.sortedWith(compareBy<OccurrenceView> { it.shownDate }.thenBy { it.occurrence.ordinal }.thenBy { it.id }),
        history = views.filter { it.state == OccurrenceState.Confirmed || it.state == OccurrenceState.Skipped || it.state == OccurrenceState.Unknown }
            .sortedWith(compareByDescending<OccurrenceView> { it.shownDate }.thenByDescending { it.occurrence.ordinal }.thenBy { it.id }),
    )

    override fun preview(draft: RuleDraft, limit: Int): SchedulePreview {
        val fixedRounds = draft.preset != Preset.Custom && draft.occurrenceCount != null
        val rounds = RuleDates.dates(
            draft.frequency, draft.interval, draft.anchorDay, draft.startDate, draft.endDate, draft.occurrenceCount,
            if (fixedRounds) draft.occurrenceCount ?: limit else limit,
        )
        val shown = rounds.map { PreviewRound(it.ordinal, it.date, Allocation.forRound(draft.amountBasis, draft.amountMinor, draft.occurrenceCount, it.ordinal), isPayout = false) }
        if (draft.preset != Preset.Gameya) return SchedulePreview(shown)
        val payout = Allocation.payout(draft.amountBasis, draft.amountMinor, draft.occurrenceCount)
        val payoutRound = draft.payoutRound
        val withPayout = if (payout != null && payoutRound != null) {
            shown.flatMap { if (it.ordinal == payoutRound) listOf(it, PreviewRound(it.ordinal, it.date, payout, isPayout = true)) else listOf(it) }
        } else {
            shown
        }
        return SchedulePreview(withPayout, payout)
    }

    // ---- Writes -------------------------------------------------------------------------------------------------

    private fun scopeOf(walletId: String) = SyncScope(ScopeKind.Wallet, walletId)

    private fun WalletView.mayWrite(): Boolean =
        (server.myRole == WalletRole.Owner || server.myRole == WalletRole.Editor) && !archived

    private fun verifiedAccount(): String? = accountId?.takeIf { emailVerified }

    private suspend fun walletMap(): Map<String, WalletView> = wallets.observeList().first().let { (it.active + it.archived).associateBy { w -> w.id } }

    private fun encode(payload: RulePayload) = AppJson.encodeToString(RulePayload.serializer(), payload)

    private fun encode(payload: OccurrencePayload) = AppJson.encodeToString(OccurrencePayload.serializer(), payload)

    private fun RuleDraft.toNewRule(id: String, account: String, walletId: String, status: String, currency: String, followsRuleId: String?) = NewRule(
        id = id, accountId = account, walletId = walletId, preset = preset.wire, status = status, kind = kind.wire,
        amountMinor = amountMinor, amountBasis = amountBasis.wire, currency = currency, categoryId = categoryId,
        counterWalletId = counterWalletId, description = description, memo = memo, counterparty = counterparty,
        autoConfirm = autoConfirm, frequency = frequency.wire, interval = interval, anchorDay = anchorDay,
        startDate = startDate.toString(), endDate = endDate?.toString(), occurrenceCount = occurrenceCount,
        payoutRound = payoutRound, payoutCategoryId = payoutCategoryId, followsRuleId = followsRuleId,
    )

    override suspend fun create(walletId: String, draft: RuleDraft): RuleResult {
        val account = verifiedAccount() ?: return RuleResult.NotVerified
        val list = wallets.observeList().first()
        val wallet = (list.active + list.archived).firstOrNull { it.id == walletId } ?: return RuleResult.NotAllowed
        if (!wallet.mayWrite()) return RuleResult.NotAllowed
        val clean = RuleValidation.clean(draft)
        val problems = RuleValidation.validate(clean, creating = true, destinations = DestinationChoices.of(list, walletId).map { it.id }.toSet())
        if (problems.isNotEmpty()) return RuleResult.Invalid(problems)
        val id = newId()
        val currency = wallet.shown.currency
        rules.putPlaceholder(clean.toNewRule(id, account, walletId, RuleStatus.Active.wire, currency, followsRuleId = null))
        engine.enqueue(RecurringRuleSyncHandler.RECORD_TYPE, id, scopeOf(walletId), 0, encode(clean.toPayload(RuleStatus.Active.wire, currency)))
        log.d { "queued recurring_rule/$id (create)" }
        runner.requestUpload()
        return RuleResult.Created(id)
    }

    /** What a rule write starts from: the stored row, its waiting change and the person's right to write. */
    private class LoadedRule(
        val account: String,
        val row: RecurringRuleEntity,
        val rule: Rule,
        val change: PendingChange?,
        val pending: RulePayload?,
    ) {
        /** The content the rule shows now: the waiting change, else the server's. */
        val shown: RuleDraft get() = pending?.toDraft() ?: rule.toDraft()
        val shownStatus: String get() = pending?.status ?: rule.statusWire
        val followsRuleId: String? get() = pending?.followsRuleId ?: row.followsRuleId
    }

    private sealed interface RuleLoad {
        class Ready(val rule: LoadedRule) : RuleLoad
        class Refused(val result: RuleResult) : RuleLoad
    }

    /** Verified address, the rule on the phone, the person's right to write its wallet (FR-002). */
    private suspend fun loadRule(ruleId: String): RuleLoad {
        val account = verifiedAccount() ?: return RuleLoad.Refused(RuleResult.NotVerified)
        val row = rules.get(ruleId) ?: return RuleLoad.Refused(RuleResult.NoLongerAvailable)
        val change = engine.get(RecurringRuleSyncHandler.RECORD_TYPE, ruleId)
        if (change?.isDelete == true) return RuleLoad.Refused(RuleResult.NoLongerAvailable)
        if (walletMap()[row.walletId]?.mayWrite() != true) return RuleLoad.Refused(RuleResult.NotAllowed)
        val pending = change?.let { decodeRulePayload(it.payloadJson) }
        return RuleLoad.Ready(LoadedRule(account, row, row.toRule(), change, pending))
    }

    /** Rounds confirmed or skipped on the phone, a waiting skip or confirmation counted (FR-013). */
    private class Resolved(val ordinal: Int, val date: LocalDate)

    private suspend fun resolvedRounds(ruleId: String): List<Resolved> {
        val queue = engine.observeAll(OccurrenceSyncHandler.RECORD_TYPE).first().associateBy { it.recordId }
        return occurrences.forRule(ruleId).mapNotNull { row ->
            val slot = effectiveSlot(row, queue[row.id])
            if (slot.status == OccurrenceStatus.Confirmed || slot.status == OccurrenceStatus.Skipped) Resolved(row.ordinal, slot.date) else null
        }
    }

    private fun frozenIssues(rule: Rule, clean: RuleDraft, resolved: List<Resolved>): Map<RuleField, RuleFieldProblem> {
        if (resolved.isEmpty()) return emptyMap()
        val problems = linkedMapOf<RuleField, RuleFieldProblem>()
        val frozen = RuleFieldProblem.Frozen
        if (clean.frequency != rule.frequency) problems[RuleField.Frequency] = frozen
        if (clean.interval != rule.interval) problems[RuleField.Interval] = frozen
        if (clean.anchorDay != rule.anchorDay) problems[RuleField.Day] = frozen
        if (clean.startDate != rule.startDate) problems[RuleField.Start] = frozen
        if (clean.amountBasis != rule.amountBasis) problems[RuleField.Basis] = frozen
        if (clean.payoutRound != rule.payoutRound) problems[RuleField.PayoutRound] = frozen
        if (rule.preset == Preset.Gameya || rule.amountBasis == AmountBasis.Total) {
            if (clean.amountMinor != rule.amountMinor) problems[RuleField.Amount] = frozen
            if (clean.occurrenceCount != rule.occurrenceCount) problems[RuleField.Count] = frozen
        }
        val lastDate = resolved.maxOf { it.date }
        val lastOrdinal = resolved.maxOf { it.ordinal }
        val end = clean.endDate
        val count = clean.occurrenceCount
        if (RuleField.End !in problems && end != null && end < lastDate) problems[RuleField.End] = RuleFieldProblem.CutsResolvedRound
        if (RuleField.Count !in problems && count != null && count < lastOrdinal) problems[RuleField.Count] = RuleFieldProblem.CutsResolvedRound
        return problems
    }

    /** Shared by save and the split's old rule: the whole rule, with the editor's base version. */
    private suspend fun queueRule(loaded: LoadedRule, draft: RuleDraft, status: String, baseVersion: Long) {
        val rule = loaded.rule
        val payload = draft.toPayload(status, rule.currency, loaded.followsRuleId, rule)
        if (rule.isPlaceholder) {
            rules.putPlaceholder(draft.toNewRule(rule.id, loaded.account, rule.walletId, status, rule.currency, loaded.followsRuleId).copy(preset = rule.presetWire, kind = rule.kindWire))
        }
        engine.enqueue(RecurringRuleSyncHandler.RECORD_TYPE, rule.id, scopeOf(rule.walletId), if (rule.isPlaceholder) 0 else baseVersion, encode(payload))
        log.d { "queued recurring_rule/${rule.id}" }
        runner.requestUpload()
    }

    override suspend fun save(ruleId: String, draft: RuleDraft, baseVersion: Long): RuleResult {
        val loaded = when (val load = loadRule(ruleId)) {
            is RuleLoad.Refused -> return load.result
            is RuleLoad.Ready -> load.rule
        }
        val rule = loaded.rule
        if (rule.hasUnknownValues || rule.status == RuleStatus.Ended) return RuleResult.ReadOnly
        // Preset, kind, currency and destination are always the stored ones (FR-012).
        val clean = RuleValidation.clean(draft.copy(preset = rule.preset, kind = rule.kind, counterWalletId = rule.counterWalletId))
        val problems = linkedMapOf<RuleField, RuleFieldProblem>()
        problems += RuleValidation.validate(clean, creating = false, destinations = null)
        problems += frozenIssues(rule, clean, resolvedRounds(ruleId))
        if (problems.isNotEmpty()) return RuleResult.Invalid(problems)
        queueRule(loaded, clean, loaded.shownStatus, baseVersion)
        return RuleResult.Saved
    }

    override suspend fun changeFrom(ruleId: String, from: LocalDate, draft: RuleDraft, baseVersion: Long): RuleResult {
        val loaded = when (val load = loadRule(ruleId)) {
            is RuleLoad.Refused -> return load.result
            is RuleLoad.Ready -> load.rule
        }
        val rule = loaded.rule
        if (rule.hasUnknownValues || rule.status == RuleStatus.Ended) return RuleResult.ReadOnly
        // A rule the server has never seen has nothing resolved yet: the split is a plain edit.
        if (rule.isPlaceholder) return save(ruleId, draft, baseVersion)
        // A preset's rounds are fixed by their count and a total's shares by its count: neither can end a day earlier.
        if (rule.preset != Preset.Custom || rule.amountBasis == AmountBasis.Total) return RuleResult.ReadOnly
        val resolved = resolvedRounds(ruleId)
        val lastResolved = resolved.maxOfOrNull { it.date }
        if (from < rule.startDate || (lastResolved != null && from <= lastResolved) || from < RuleValidation.MIN_DATE || from > RuleValidation.MAX_DATE) {
            return RuleResult.Invalid(mapOf(RuleField.From to RuleFieldProblem.FromOutOfRange))
        }
        val newDraft = RuleValidation.clean(
            draft.copy(preset = Preset.Custom, kind = rule.kind, counterWalletId = rule.counterWalletId, startDate = from, anchorDay = draft.anchorDay),
        )
        val problems = RuleValidation.validate(newDraft, creating = false, destinations = null)
        if (problems.isNotEmpty()) return RuleResult.Invalid(problems)
        val oldDraft = loaded.shown.copy(endDate = from.minus(1, DateTimeUnit.DAY), occurrenceCount = null)
        queueRule(loaded, oldDraft, loaded.shownStatus, baseVersion)
        val newId = newId()
        rules.putPlaceholder(newDraft.toNewRule(newId, loaded.account, rule.walletId, loaded.shownStatus, rule.currency, followsRuleId = rule.id))
        engine.enqueue(
            RecurringRuleSyncHandler.RECORD_TYPE, newId, scopeOf(rule.walletId), 0,
            encode(newDraft.toPayload(loaded.shownStatus, rule.currency, followsRuleId = rule.id)),
        )
        log.d { "queued recurring_rule/$newId (continues ${rule.id})" }
        runner.requestUpload()
        return RuleResult.Created(newId)
    }

    override suspend fun setStatus(ruleId: String, status: RuleStatus, baseVersion: Long): RuleResult {
        val loaded = when (val load = loadRule(ruleId)) {
            is RuleLoad.Refused -> return load.result
            is RuleLoad.Ready -> load.rule
        }
        val rule = loaded.rule
        if (rule.hasUnknownValues || rule.status == RuleStatus.Ended || status == RuleStatus.Unknown) return RuleResult.ReadOnly
        queueRule(loaded, loaded.shown, status.wire, baseVersion)
        return RuleResult.Saved
    }

    override suspend fun delete(ruleId: String): RuleResult {
        if (verifiedAccount() == null) return RuleResult.NotVerified
        val row = rules.get(ruleId) ?: return RuleResult.NoLongerAvailable
        if (walletMap()[row.walletId]?.mayWrite() != true) return RuleResult.NotAllowed
        val scope = scopeOf(row.walletId)
        if (row.version == 0L) {
            // Never sent: nothing to delete on the server; rules that continue it go with it (FR-016, R-09).
            val followers = rules.following(ruleId)
            engine.enqueueDelete(RecurringRuleSyncHandler.RECORD_TYPE, ruleId, scope, 0)
            rules.removePlaceholder(ruleId)
            followers.forEach { engine.discard(RecurringRuleSyncHandler.RECORD_TYPE, it.id) }
        } else {
            engine.enqueueDelete(RecurringRuleSyncHandler.RECORD_TYPE, ruleId, scope, row.version)
            runner.requestUpload()
        }
        log.d { "queued recurring_rule/$ruleId (delete)" }
        return RuleResult.Saved
    }

    // ---- Occurrences --------------------------------------------------------------------------------------------

    private class LoadedOccurrence(
        val row: OccurrenceEntity,
        val change: PendingChange?,
        val pending: OccurrencePayload?,
    ) {
        val confirmationWaiting: Boolean get() = pending?.status == OccurrenceStatus.Confirmed.wire
        val status: OccurrenceStatus get() = OccurrenceStatus.fromWire(pending?.status ?: row.status)
        val date: LocalDate get() = parseDate(pending?.date ?: row.date)
        val amountMinor: Long get() = pending?.amountMinor ?: row.amountMinor
        val categoryId: String? get() = if (pending != null) pending.categoryId else row.categoryId
        val description: String? get() = if (pending != null) pending.description else row.description
        val memo: String? get() = if (pending != null) pending.memo else row.memo
        val counterparty: String? get() = if (pending != null) pending.counterparty else row.counterparty

        fun payload(status: OccurrenceStatus, date: LocalDate, amountMinor: Long, categoryId: String?, description: String?, memo: String?, counterparty: String?, transactionId: String?) =
            OccurrencePayload(status.wire, date.toString(), amountMinor, categoryId, description, memo, counterparty, transactionId)

        /** The same values the server already has: queuing it would change nothing. */
        fun isWhatServerHas(p: OccurrencePayload): Boolean =
            p.status == row.status && p.date == row.date && p.amountMinor == row.amountMinor && p.categoryId == row.categoryId &&
                p.description == row.description && p.memo == row.memo && p.counterparty == row.counterparty
    }

    private sealed interface OccurrenceLoad {
        class Ready(val occurrence: LoadedOccurrence) : OccurrenceLoad
        class Refused(val result: OccurrenceResult) : OccurrenceLoad
    }

    /**
     * Verified address, the occurrence on the phone, the person's right (a transfer needs both wallets when the other
     * is on the phone; the server's answer is authoritative), and nothing the app does not understand or that is final.
     */
    private suspend fun loadOccurrence(occurrenceId: String): OccurrenceLoad {
        if (verifiedAccount() == null) return OccurrenceLoad.Refused(OccurrenceResult.NotVerified)
        val row = occurrences.get(occurrenceId) ?: return OccurrenceLoad.Refused(OccurrenceResult.NoLongerAvailable)
        val ruleRow = rules.get(row.recurringRuleId)
        val ruleChange = ruleRow?.let { engine.get(RecurringRuleSyncHandler.RECORD_TYPE, it.id) }
        if (ruleChange?.isDelete == true) return OccurrenceLoad.Refused(OccurrenceResult.NoLongerAvailable)
        val byId = walletMap()
        if (byId[row.walletId]?.mayWrite() != true) return OccurrenceLoad.Refused(OccurrenceResult.NotAllowed)
        val counter = row.counterWalletId?.let { byId[it] }
        if (TransactionKind.fromWire(row.kind) == TransactionKind.Transfer && counter != null && !counter.mayWrite()) {
            return OccurrenceLoad.Refused(OccurrenceResult.NotAllowed)
        }
        if (ruleRow?.toRule()?.hasUnknownValues == true || OccurrenceStatus.fromWire(row.status) == OccurrenceStatus.Unknown) {
            return OccurrenceLoad.Refused(OccurrenceResult.ReadOnly)
        }
        if (OccurrenceStatus.fromWire(row.status) == OccurrenceStatus.Confirmed) return OccurrenceLoad.Refused(OccurrenceResult.ReadOnly)
        val change = engine.get(OccurrenceSyncHandler.RECORD_TYPE, occurrenceId)
        return OccurrenceLoad.Ready(LoadedOccurrence(row, change, change?.let { decodeOccurrencePayload(it.payloadJson) }))
    }

    private suspend fun queueOccurrence(loaded: LoadedOccurrence, payload: OccurrencePayload) {
        val row = loaded.row
        if (loaded.isWhatServerHas(payload)) {
            // Back to what the server has (undo of a skip, a skip taken back): nothing is left to send.
            if (loaded.change != null) engine.discard(OccurrenceSyncHandler.RECORD_TYPE, row.id)
            return
        }
        engine.enqueue(OccurrenceSyncHandler.RECORD_TYPE, row.id, scopeOf(row.walletId), row.version, encode(payload))
        log.d { "queued occurrence/${row.id} (${payload.status})" }
        runner.requestUpload()
    }

    override suspend fun confirm(occurrenceId: String, draft: ConfirmDraft): OccurrenceResult {
        val loaded = when (val load = loadOccurrence(occurrenceId)) {
            is OccurrenceLoad.Refused -> return load.result
            is OccurrenceLoad.Ready -> load.occurrence
        }
        val row = loaded.row
        val kind = TransactionKind.fromWire(row.kind)
        val clean = RuleValidation.cleanConfirm(kind, draft)
        val problems = RuleValidation.validateConfirm(kind, clean)
        if (problems.isNotEmpty()) return OccurrenceResult.Invalid(problems)
        // One confirmation, one transaction id: generated once, kept by every later change of the waiting write (FR-024).
        val transactionId = loaded.pending?.takeIf { loaded.confirmationWaiting }?.transactionId ?: newId()
        val payload = loaded.payload(OccurrenceStatus.Confirmed, clean.date, clean.amountMinor, clean.categoryId, clean.description, clean.memo, clean.counterparty, transactionId)
        engine.enqueue(OccurrenceSyncHandler.RECORD_TYPE, row.id, scopeOf(row.walletId), row.version, encode(payload))
        val confirmation = PendingConfirmation(
            transactionId = transactionId, walletId = row.walletId, counterWalletId = row.counterWalletId, kind = row.kind,
            amountMinor = clean.amountMinor, currency = row.currency, date = clean.date, categoryId = clean.categoryId,
            description = clean.description, memo = clean.memo, counterparty = clean.counterparty, ruleId = row.recurringRuleId,
            occurrenceId = row.id,
        )
        if (loaded.confirmationWaiting) occurrenceTransactions.updateConfirmation(confirmation) else occurrenceTransactions.placeConfirmation(confirmation)
        log.d { "queued occurrence/${row.id} (confirm)" }
        runner.requestUpload()
        return OccurrenceResult.Done
    }

    override suspend fun skip(occurrenceId: String): OccurrenceResult = setSkipped(occurrenceId, skipped = true)

    override suspend fun unskip(occurrenceId: String): OccurrenceResult = setSkipped(occurrenceId, skipped = false)

    private suspend fun setSkipped(occurrenceId: String, skipped: Boolean): OccurrenceResult {
        val loaded = when (val load = loadOccurrence(occurrenceId)) {
            is OccurrenceLoad.Refused -> return load.result
            is OccurrenceLoad.Ready -> load.occurrence
        }
        // A waiting confirmation is cancelled first; it is not turned into a skip behind the person's back.
        if (loaded.confirmationWaiting) return OccurrenceResult.ReadOnly
        val target = if (skipped) OccurrenceStatus.Skipped else OccurrenceStatus.Scheduled
        if (loaded.status == target) return OccurrenceResult.Done
        val payload = loaded.payload(target, loaded.date, loaded.amountMinor, loaded.categoryId, loaded.description, loaded.memo, loaded.counterparty, null)
        queueOccurrence(loaded, payload)
        return OccurrenceResult.Done
    }

    override suspend fun edit(occurrenceId: String, draft: ConfirmDraft): OccurrenceResult {
        val loaded = when (val load = loadOccurrence(occurrenceId)) {
            is OccurrenceLoad.Refused -> return load.result
            is OccurrenceLoad.Ready -> load.occurrence
        }
        if (loaded.confirmationWaiting) return confirm(occurrenceId, draft)
        val kind = TransactionKind.fromWire(loaded.row.kind)
        val clean = RuleValidation.cleanConfirm(kind, draft)
        val problems = RuleValidation.validateConfirm(kind, clean)
        if (problems.isNotEmpty()) return OccurrenceResult.Invalid(problems)
        val payload = loaded.payload(loaded.status, clean.date, clean.amountMinor, clean.categoryId, clean.description, clean.memo, clean.counterparty, null)
        queueOccurrence(loaded, payload)
        return OccurrenceResult.Done
    }

    override suspend fun cancelConfirmation(occurrenceId: String) {
        val change = engine.get(OccurrenceSyncHandler.RECORD_TYPE, occurrenceId) ?: return
        if (decodeOccurrencePayload(change.payloadJson)?.status != OccurrenceStatus.Confirmed.wire) return
        // The engine's discard calls the handler, which drops the phone-only transaction (R-07).
        engine.discard(OccurrenceSyncHandler.RECORD_TYPE, occurrenceId)
        occurrenceTransactions.dropConfirmationOf(occurrenceId)
    }

    override suspend fun dismissNotice(occurrenceId: String) = occurrences.clearNotice(occurrenceId)

    // ---- Catch up -----------------------------------------------------------------------------------------------

    override suspend fun catchUp(ruleId: String): CatchUpOutcome {
        if (verifiedAccount() == null) return CatchUpOutcome.NotVerified
        val row = rules.get(ruleId) ?: return CatchUpOutcome.NotAllowed
        val rule = row.toRule()
        if (walletMap()[row.walletId]?.mayWrite() != true || rule.status == RuleStatus.Ended || rule.isPlaceholder || rule.hasUnknownValues) {
            return CatchUpOutcome.NotAllowed
        }
        return when (val result = api.materialise(row.walletId, ruleId)) {
            is ApiResult.Success -> {
                log.d { "materialise recurring_rule/$ruleId: done" }
                readWallet(row.walletId)
            }
            ApiResult.Offline, ApiResult.Timeout -> CatchUpOutcome.NeedsConnection
            is ApiResult.Failure -> {
                log.d { "materialise recurring_rule/$ruleId: ${result.problem.code} (request ${result.problem.requestId})" }
                when (result.code) {
                    // The rule is gone or changed on the server: the read brings the truth.
                    ApiErrorCode.NOT_FOUND, ApiErrorCode.VERSION_CONFLICT -> readWallet(row.walletId)
                    ApiErrorCode.EMAIL_NOT_VERIFIED -> CatchUpOutcome.NotVerified
                    ApiErrorCode.FORBIDDEN -> CatchUpOutcome.NotAllowed
                    else -> CatchUpOutcome.Failed(result.problem.requestId)
                }
            }
        }
    }

    override suspend fun refresh(walletId: String?, force: Boolean): RefreshOutcome {
        if (force) engine.syncNow()
        val ids = walletId?.let { listOf(it) } ?: wallets.observeList().first().active.map { it.id }
        var outcome = RefreshOutcome.Done
        for (id in ids) {
            when (runner.refresh(ReadTarget.Wallet(id), force)) {
                ReadOutcome.Done -> Unit
                ReadOutcome.Offline -> return RefreshOutcome.Offline
                ReadOutcome.Failed -> outcome = RefreshOutcome.Failed
            }
        }
        return outcome
    }

    private suspend fun readWallet(walletId: String): CatchUpOutcome = when (runner.refresh(ReadTarget.Wallet(walletId), force = true)) {
        ReadOutcome.Done -> CatchUpOutcome.Done
        ReadOutcome.Offline -> CatchUpOutcome.NeedsConnection
        ReadOutcome.Failed -> CatchUpOutcome.Failed(null)
    }

    // ---- Clash and refusal choices ------------------------------------------------------------------------------

    override suspend fun keepMine(record: RecurringRecord, id: String) {
        engine.keepMine(record.recordType, id)
        runner.requestUpload()
    }

    /** Also "Accept the deletion": the server copy wins and the change goes, with its phone-only transaction or its follower. */
    override suspend fun useTheirs(record: RecurringRecord, id: String) {
        engine.useTheirs(record.recordType, id)
    }

    override suspend fun tryAgain(record: RecurringRecord, id: String) {
        val change = engine.get(record.recordType, id) ?: return
        if (change.isDelete) {
            engine.enqueueDelete(change.recordType, id, change.scope, change.baseVersion)
        } else {
            engine.enqueue(change.recordType, id, change.scope, change.baseVersion, change.payloadJson)
        }
        runner.requestUpload()
    }

    override suspend fun discard(record: RecurringRecord, id: String) = engine.discard(record.recordType, id)

    private companion object {
        val log = Logger.withTag("recurring")
    }
}
