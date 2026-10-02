package com.fares.saveme.feature.transactions.domain

import com.fares.saveme.core.network.ApiResult
import com.fares.saveme.core.storage.KeyValueStore
import com.fares.saveme.core.sync.ScopeKind
import com.fares.saveme.core.sync.SyncEngine
import com.fares.saveme.core.sync.SyncScope
import com.fares.saveme.feature.auth.domain.AuthState
import com.fares.saveme.feature.auth.domain.model.RefreshOutcome
import com.fares.saveme.feature.categories.domain.CategoriesService
import com.fares.saveme.feature.transactions.data.NewTransaction
import com.fares.saveme.feature.transactions.data.TransactionRepository
import com.fares.saveme.feature.transactions.data.TransactionSyncHandler
import com.fares.saveme.feature.transactions.data.local.TransactionEntity
import com.fares.saveme.feature.transactions.domain.model.CreateResult
import com.fares.saveme.feature.transactions.domain.model.SaveResult
import com.fares.saveme.feature.transactions.domain.model.Transaction
import com.fares.saveme.feature.transactions.domain.model.TransactionDraft
import com.fares.saveme.feature.transactions.domain.model.TransactionField
import com.fares.saveme.feature.transactions.domain.model.TransactionFieldProblem
import com.fares.saveme.feature.transactions.domain.model.TransactionKind
import com.fares.saveme.feature.transactions.domain.model.TransactionList
import com.fares.saveme.feature.transactions.domain.model.TransactionQuery
import com.fares.saveme.feature.transactions.domain.model.TransactionView
import com.fares.saveme.feature.transactions.domain.model.WalletChoice
import com.fares.saveme.feature.wallets.domain.AmountInput
import com.fares.saveme.feature.wallets.domain.WalletsService
import com.fares.saveme.feature.wallets.domain.model.WalletList
import com.fares.saveme.feature.wallets.domain.model.WalletView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.LocalDate
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlin.uuid.Uuid

/**
 * The transactions domain API (contracts/transactions-domain.md). Other features use only this
 * package (P-10.4); the screens' stores take its functions as lambdas.
 */
interface TransactionsService {
    /** One wallet's live transactions from its side (transfers in included), grouped by day, newest first, filtered by [query]. */
    fun observeList(walletId: String, query: TransactionQuery = TransactionQuery()): Flow<TransactionList>

    /** Null when gone or hidden by a deletion that waits to sync; [fromWalletId] chooses the side for direction. */
    fun observeTransaction(id: String, fromWalletId: String): Flow<TransactionView?>

    /** Wallets the person can record in: active, visible, role owner or editor (FR-008). */
    fun observeWritableWallets(): Flow<List<WalletChoice>>

    /** Home's preselection: the last wallet recorded in when still writable, else the first writable. */
    suspend fun defaultWalletId(): String?

    /** FR-010: active, same currency, owner or editor, not [sourceWalletId]. */
    fun observeDestinations(sourceWalletId: String): Flow<List<WalletChoice>>

    suspend fun create(draft: TransactionDraft): CreateResult

    /**
     * [baseVersion] is the version shown when the editor opened; kind and wallets are always the stored ones.
     *
     * [lastSeen] is the server copy the editor opened on. When a refresh removed the transaction from the
     * phone while the person was typing (deleted elsewhere), that copy is put back and the change waits on
     * it, so the server answers it with a clash or a refusal instead of the typing being lost (spec Edge Cases).
     */
    suspend fun save(id: String, draft: TransactionDraft, baseVersion: Long, lastSeen: Transaction? = null): SaveResult

    suspend fun delete(id: String): SaveResult

    /** Also "Restore with my change" and "Delete anyway". */
    suspend fun keepMine(id: String)

    /** Also "Accept the deletion" and "Keep the transaction". */
    suspend fun useTheirs(id: String)

    suspend fun tryAgain(id: String)

    suspend fun discard(id: String)

    /** Reads every page of the wallet's list; without [force] a read within the last 60 seconds is skipped. */
    suspend fun refresh(walletId: String, force: Boolean = false): RefreshOutcome
}

class DefaultTransactionsService(
    private val repository: TransactionRepository,
    private val engine: SyncEngine,
    private val wallets: WalletsService,
    private val categories: CategoriesService,
    private val authState: StateFlow<AuthState>,
    private val keyValues: KeyValueStore,
    private val reconciler: BalanceReconciler,
    private val scope: CoroutineScope,
    private val clock: Clock = Clock.System,
    private val newId: () -> String = { Uuid.generateV7().toString() },
    private val foregroundFloor: Duration = 60.seconds,
) : TransactionsService {

    private val refreshLock = Mutex()
    private val lastRead = mutableMapOf<String, Instant>()

    private val accountId: String? get() = (authState.value as? AuthState.SignedIn)?.account?.userId

    private val emailVerified: Boolean get() = (authState.value as? AuthState.SignedIn)?.account?.emailVerified == true

    private fun accountFlow(): Flow<String?> =
        authState.map { (it as? AuthState.SignedIn)?.account?.userId }.distinctUntilChanged()

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observeList(walletId: String, query: TransactionQuery): Flow<TransactionList> {
        val rows = accountFlow().flatMapLatest { id -> if (id == null) flowOf(emptyList()) else repository.observeForWallet(id, walletId) }
        val inputs = combine(
            rows, engine.observeAll(TransactionSyncHandler.RECORD_TYPE), wallets.observeList(),
            wallets.observeWallet(walletId), wallets.observeMembers(walletId),
        ) { stored, queue, list, viewed, members -> ListInputs(stored, queue, list, viewed, members) }
        return combine(inputs, repository.observeRefreshedAt(walletId)) { input, refreshedAt ->
            val updatedAt = refreshedAt?.let { runCatching { Instant.parse(it) }.getOrNull() }
            val viewed = input.viewed
            if (viewed == null) {
                return@combine TransactionList(walletId, "", emptyList(), updatedAt, loaded = true, canAdd = false)
            }
            val context = ViewContext(accountId, input.list.walletsById(), input.members.associate { it.userId to it.displayName })
            val queue = input.queue.associateBy { it.recordId }
            val views = input.stored.map { buildView(it, queue[it.id], context, walletId) }.filterNot { it.hidden }
            TransactionList(
                walletId = walletId,
                currency = viewed.shown.currency,
                days = DayGrouping.group(TransactionFilter.apply(views, query), walletId),
                updatedAt = updatedAt,
                loaded = updatedAt != null || views.isNotEmpty(),
                canAdd = viewed.canWrite() && !viewed.archived,
                totalCount = views.size,
                categoryIds = views.mapNotNull { it.shown.categoryId }.toSet(),
                waitingCount = views.count { it.waiting },
            )
        }
    }

    private class ListInputs(
        val stored: List<TransactionEntity>,
        val queue: List<com.fares.saveme.core.sync.PendingChange>,
        val list: WalletList,
        val viewed: WalletView?,
        val members: List<com.fares.saveme.feature.wallets.domain.model.Member>,
    )

    override fun observeTransaction(id: String, fromWalletId: String): Flow<TransactionView?> = combine(
        repository.observe(id), engine.observe(TransactionSyncHandler.RECORD_TYPE, id), wallets.observeList(),
        wallets.observeMembers(fromWalletId), accountFlow(),
    ) { row, change, list, members, account ->
        if (row == null || row.deletedAt != null) return@combine null
        val context = ViewContext(account, list.walletsById(), members.associate { it.userId to it.displayName })
        buildView(row, change, context, fromWalletId).takeUnless { it.hidden }
    }

    override fun observeWritableWallets(): Flow<List<WalletChoice>> =
        wallets.observeList().map { list -> list.active.filter { it.canWrite() }.map { it.toChoice() } }

    override suspend fun defaultWalletId(): String? {
        val writable = observeWritableWallets().first()
        val last = keyValues.get(LAST_WALLET_KEY)
        return writable.firstOrNull { it.id == last }?.id ?: writable.firstOrNull()?.id
    }

    override fun observeDestinations(sourceWalletId: String): Flow<List<WalletChoice>> =
        wallets.observeList().map { DestinationChoices.of(it, sourceWalletId) }

    override suspend fun create(draft: TransactionDraft): CreateResult {
        val account = accountId ?: return CreateResult.NotVerified
        if (!emailVerified) return CreateResult.NotVerified
        val list = wallets.observeList().first()
        val wallet = (list.active + list.archived).firstOrNull { it.id == draft.walletId } ?: return CreateResult.NoWallet
        if (!wallet.canWrite() || wallet.archived) return CreateResult.NotAllowed
        val clean = clean(draft.copy(currency = wallet.shown.currency))
        validate(clean, creating = true, destinations = DestinationChoices.of(list, wallet.id))?.let { return CreateResult.Invalid(it) }
        val id = newId()
        repository.insertPlaceholder(
            NewTransaction(
                id = id, accountId = account, walletId = clean.walletId, counterWalletId = clean.counterWalletId,
                kind = clean.kindWire, date = clean.date.toString(), amountMinor = clean.amountMinor, currency = clean.currency,
                categoryId = clean.categoryId, description = clean.description, memo = clean.memo,
                counterparty = clean.counterparty, cleared = clean.cleared, createdBy = account,
            ),
        )
        engine.enqueue(TransactionSyncHandler.RECORD_TYPE, id, scopeOf(clean.walletId), 0, clean.toPayloadJson())
        keyValues.set(LAST_WALLET_KEY, clean.walletId)
        drainInBackground()
        return CreateResult.Created(id)
    }

    override suspend fun save(id: String, draft: TransactionDraft, baseVersion: Long, lastSeen: Transaction?): SaveResult {
        val account = accountId
        if (account == null || !emailVerified) return SaveResult.NotVerified
        val stored = repository.get(id)
        val removed = stored == null && lastSeen != null && lastSeen.id == id && lastSeen.version > 0
        val row = stored?.takeIf { it.deletedAt == null }
            ?: lastSeen?.takeIf { removed }?.toEntity(account, clock.now().toString())
            ?: return SaveResult.NoLongerAvailable
        val list = wallets.observeList().first()
        val context = ViewContext(account, list.walletsById(), emptyMap())
        if (!permissions(row.kind, row.walletId, row.counterWalletId, row.walletId, context).first) return SaveResult.NotAllowed
        val currency = if (row.version == 0L) context.wallets[row.walletId]?.shown?.currency ?: draft.currency else row.currency
        // The kind and a transfer's wallets are always the stored ones (FR-012).
        val clean = clean(
            draft.copy(walletId = row.walletId, kindWire = row.kind, counterWalletId = row.counterWalletId, currency = currency),
        )
        validate(clean, creating = false, destinations = emptyList())?.let { return SaveResult.Invalid(it) }
        // The copy the person was editing comes back as the base of their change; a later read keeps it while the change waits.
        if (removed) repository.reinstate(row)
        engine.enqueue(TransactionSyncHandler.RECORD_TYPE, id, scopeOf(row.walletId), baseVersion, clean.toPayloadJson())
        drainInBackground()
        return SaveResult.Saved
    }

    override suspend fun delete(id: String): SaveResult {
        if (accountId == null || !emailVerified) return SaveResult.NotVerified
        val row = repository.get(id)?.takeIf { it.deletedAt == null } ?: return SaveResult.NoLongerAvailable
        val list = wallets.observeList().first()
        val byId = list.walletsById()
        val mayDelete = listOfNotNull(byId[row.walletId], row.counterWalletId?.let { byId[it] }).any { it.canWrite() }
        if (!mayDelete) return SaveResult.NotAllowed
        engine.enqueueDelete(TransactionSyncHandler.RECORD_TYPE, id, scopeOf(row.walletId), row.version)
        // A transaction that never reached the server has nothing to delete there (research R-06).
        if (row.version == 0L) repository.removePlaceholder(id) else drainInBackground()
        return SaveResult.Saved
    }

    override suspend fun keepMine(id: String) {
        engine.keepMine(TransactionSyncHandler.RECORD_TYPE, id)
        drainInBackground()
    }

    override suspend fun useTheirs(id: String) {
        engine.useTheirs(TransactionSyncHandler.RECORD_TYPE, id)
        // The other device's change may or may not be in the wallet's last read; only a read settles it (analysis A5).
        reconciler.requestWalletRead()
    }

    override suspend fun tryAgain(id: String) {
        val change = engine.get(TransactionSyncHandler.RECORD_TYPE, id) ?: return
        if (change.isDelete) {
            engine.enqueueDelete(change.recordType, id, change.scope, change.baseVersion)
        } else {
            engine.enqueue(change.recordType, id, change.scope, change.baseVersion, change.payloadJson)
        }
        drainInBackground()
    }

    override suspend fun discard(id: String) = engine.discard(TransactionSyncHandler.RECORD_TYPE, id)

    override suspend fun refresh(walletId: String, force: Boolean): RefreshOutcome = refreshLock.withLock {
        val last = lastRead[walletId]
        if (!force && last != null && clock.now() - last < foregroundFloor) return RefreshOutcome.Done
        // A pull and an open also send what is waiting, so a person who got back online doesn't wait for the scheduler.
        if (force) engine.syncNow()
        categories.refreshReferences(walletId, force = true)
        when (val result = repository.refreshWallet(walletId)) {
            is ApiResult.Success -> {
                lastRead[walletId] = clock.now()
                // The balance and the list must describe the same server state (research R-08).
                if (result.value.changed) reconciler.requestWalletRead()
                RefreshOutcome.Done
            }
            ApiResult.Offline, ApiResult.Timeout -> RefreshOutcome.Offline
            is ApiResult.Failure -> RefreshOutcome.Failed
        }
    }

    /** Trims text (blank becomes none) and drops what does not apply to the kind (FR-002). */
    private fun clean(draft: TransactionDraft): TransactionDraft {
        val transfer = draft.kind == TransactionKind.Transfer
        return draft.copy(
            categoryId = draft.categoryId?.takeIf { !transfer && it.isNotBlank() },
            counterWalletId = draft.counterWalletId?.takeIf { transfer && it.isNotBlank() },
            description = draft.description.trimmedOrNull(),
            memo = draft.memo.trimmedOrNull(),
            counterparty = draft.counterparty.trimmedOrNull(),
        )
    }

    private fun validate(
        draft: TransactionDraft,
        creating: Boolean,
        destinations: List<WalletChoice>,
    ): Map<TransactionField, TransactionFieldProblem>? {
        val problems = linkedMapOf<TransactionField, TransactionFieldProblem>()
        if (draft.amountMinor <= 0) problems[TransactionField.Amount] = TransactionFieldProblem.AmountNotPositive
        if (draft.amountMinor > AmountInput.MAX_MINOR) problems[TransactionField.Amount] = TransactionFieldProblem.AmountOutOfRange
        if (draft.date < MIN_DATE || draft.date > MAX_DATE) problems[TransactionField.Date] = TransactionFieldProblem.DateOutOfRange
        when (draft.kind) {
            TransactionKind.Income, TransactionKind.Expense ->
                if (draft.categoryId == null) problems[TransactionField.Category] = TransactionFieldProblem.CategoryRequired
            TransactionKind.Transfer -> {
                val destination = draft.counterWalletId
                if (destination == null || (creating && destinations.none { it.id == destination })) {
                    problems[TransactionField.Destination] = TransactionFieldProblem.DestinationRequired
                }
            }
            TransactionKind.Unknown -> if (creating) problems[TransactionField.Kind] = TransactionFieldProblem.KindUnknown
        }
        if (codePointLength(draft.description) > MAX_NOTE) problems[TransactionField.Note] = TransactionFieldProblem.TooLong(MAX_NOTE)
        if (codePointLength(draft.memo) > MAX_DETAILS) problems[TransactionField.Details] = TransactionFieldProblem.TooLong(MAX_DETAILS)
        if (codePointLength(draft.counterparty) > MAX_PERSON) problems[TransactionField.Person] = TransactionFieldProblem.TooLong(MAX_PERSON)
        return problems.takeIf { it.isNotEmpty() }
    }

    private fun scopeOf(walletId: String) = SyncScope(ScopeKind.Wallet, walletId)

    private fun drainInBackground() {
        scope.launch { engine.drain() }
    }

    companion object {
        const val LAST_WALLET_KEY = "transactions.lastWallet"
        const val MAX_NOTE = 100
        const val MAX_DETAILS = 1000
        const val MAX_PERSON = 100
        val MIN_DATE = LocalDate(1900, 1, 1)
        val MAX_DATE = LocalDate(2999, 12, 31)
    }
}

/** The wallets of the phone, the members' names and the signed-in person: what turns a row into a view. */
internal class ViewContext(
    val accountId: String?,
    val wallets: Map<String, WalletView>,
    val memberNames: Map<String, String?>,
)

internal fun WalletList.walletsById(): Map<String, WalletView> = (active + archived).associateBy { it.id }

private fun String?.trimmedOrNull(): String? = this?.trim()?.takeIf { it.isNotEmpty() }

/** UTF-16 units minus the low halves of surrogate pairs: the number of Unicode code points. */
internal fun codePointLength(text: String?): Int = text?.count { !it.isLowSurrogate() } ?: 0

/**
 * Edit and delete rights (FR-013, FR-021): income and expense follow the transaction's own wallet; a
 * transfer needs owner or editor in both wallets to be edited, and in the wallet it is viewed from
 * (with the other writable or not on the phone) to be deleted. An unknown kind can only be deleted.
 */
internal fun permissions(
    kind: String,
    walletId: String,
    counterWalletId: String?,
    viewedWalletId: String,
    context: ViewContext,
): Pair<Boolean, Boolean> {
    val source = context.wallets[walletId]
    return when (TransactionKind.fromWire(kind)) {
        TransactionKind.Income, TransactionKind.Expense -> (source?.canWrite() == true).let { it to it }
        TransactionKind.Unknown -> false to (source?.canWrite() == true)
        TransactionKind.Transfer -> {
            val counter = counterWalletId?.let { context.wallets[it] }
            val edit = source?.canWrite() == true && counter?.canWrite() == true
            val viewed = context.wallets[viewedWalletId]
            val otherId = if (viewedWalletId == walletId) counterWalletId else walletId
            val other = otherId?.let { context.wallets[it] }
            edit to (viewed?.canWrite() == true && (other == null || other.canWrite()))
        }
    }
}

internal fun buildView(row: TransactionEntity, change: com.fares.saveme.core.sync.PendingChange?, context: ViewContext, viewedWalletId: String): TransactionView {
    val server = row.takeIf { it.version > 0 }?.toTransaction()
    val stored = row.toTransaction()
    val payload = change.pendingPayload()
    // The kind and a transfer's wallets never change after creation (FR-012): the row's win over the payload's.
    val shown = payload?.toDraft(row.walletId)?.copy(kindWire = row.kind, counterWalletId = row.counterWalletId ?: payload.counterWalletId)
        ?: stored.toDraft()
    val names = context.wallets.mapValues { it.value.shown.name }
    val (canEdit, canDelete) = permissions(shown.kindWire, shown.walletId, shown.counterWalletId, viewedWalletId, context)
    return TransactionView(
        id = row.id,
        server = server,
        shown = shown,
        createdAt = stored.createdAt,
        pendingDelete = change?.isDelete == true,
        syncState = change.toSyncState(row.kind),
        direction = directionIn(shown, viewedWalletId),
        counterpart = counterpartOf(shown, viewedWalletId, names),
        addedBy = addedByOf(row.createdBy, context.accountId, context.memberNames),
        fromRecurringRule = row.recurringRuleId != null,
        canEdit = canEdit,
        canDelete = canDelete,
        walletCurrency = context.wallets[row.walletId]?.shown?.currency ?: shown.currency,
    )
}
