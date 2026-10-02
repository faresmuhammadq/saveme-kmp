package com.fares.saveme.feature.wallets.domain

import com.fares.saveme.core.network.ApiErrorCode
import com.fares.saveme.core.network.ApiResult
import com.fares.saveme.core.network.AppJson
import com.fares.saveme.core.sync.ChangeState
import com.fares.saveme.core.sync.ReadOutcome
import com.fares.saveme.core.sync.ReadTarget
import com.fares.saveme.core.sync.ScopeKind
import com.fares.saveme.core.sync.SyncEngine
import com.fares.saveme.core.sync.SyncRunner
import com.fares.saveme.core.sync.SyncScope
import com.fares.saveme.feature.auth.domain.AuthState
import com.fares.saveme.feature.auth.domain.model.RefreshOutcome
import com.fares.saveme.feature.billing.domain.Entitlements
import com.fares.saveme.feature.billing.domain.QuotaFeature
import com.fares.saveme.feature.wallets.data.WalletRepository
import com.fares.saveme.feature.wallets.data.WalletSyncHandler
import com.fares.saveme.feature.wallets.data.local.WalletEntity
import com.fares.saveme.feature.wallets.domain.model.CreateResult
import com.fares.saveme.feature.wallets.domain.model.Member
import com.fares.saveme.feature.wallets.domain.model.SaveResult
import com.fares.saveme.feature.wallets.domain.model.TransferResult
import com.fares.saveme.feature.wallets.domain.model.WalletDraft
import com.fares.saveme.feature.wallets.domain.model.WalletField
import com.fares.saveme.feature.wallets.domain.model.WalletFieldProblem
import com.fares.saveme.feature.wallets.domain.model.WalletList
import com.fares.saveme.feature.wallets.domain.model.WalletRole
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
import kotlin.time.Instant
import kotlin.uuid.Uuid

/**
 * The wallets domain API (contracts/wallets-domain.md). Other features use only this package
 * (P-10.4); the screens' stores take its functions as lambdas.
 */
interface WalletsService {
    /** Visible wallets (active, archived), totals and the time of the last complete refresh. */
    fun observeList(): Flow<WalletList>

    /** Null when the wallet is gone or hidden by a deletion that waits to sync. */
    fun observeWallet(id: String): Flow<WalletView?>

    fun observeMembers(id: String): Flow<List<Member>>

    suspend fun canCreateAnother(): Boolean

    suspend fun create(draft: WalletDraft): CreateResult

    /** [baseVersion] is the version shown when editing began (FR-010). */
    suspend fun save(id: String, draft: WalletDraft, baseVersion: Long): SaveResult

    suspend fun archive(id: String): SaveResult

    suspend fun delete(id: String): SaveResult

    suspend fun keepMine(id: String)

    /** Also "Accept the deletion" and "Keep the wallet". */
    suspend fun useTheirs(id: String)

    suspend fun tryAgain(id: String)

    suspend fun discard(id: String)

    /** Reads the list; without [force] a read within the last 60 seconds is skipped. */
    suspend fun refresh(force: Boolean = false): RefreshOutcome

    suspend fun refreshMembers(id: String): RefreshOutcome

    suspend fun transferOwnership(id: String, toUserId: String): TransferResult
}

class DefaultWalletsService(
    private val repository: WalletRepository,
    private val engine: SyncEngine,
    private val authState: StateFlow<AuthState>,
    private val entitlements: Entitlements,
    private val postings: PendingPostings,
    private val defaultCurrency: Flow<String?>,
    private val runner: SyncRunner,
    private val newId: () -> String = { Uuid.generateV7().toString() },
) : WalletsService {

    private val accountId: String? get() = (authState.value as? AuthState.SignedIn)?.account?.userId

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observeList(): Flow<WalletList> {
        val rows = authState
            .map { (it as? AuthState.SignedIn)?.account?.userId }
            .distinctUntilChanged()
            .flatMapLatest { id -> if (id == null) flowOf(emptyList()) else repository.observeAll(id) }
        val views = rows.flatMapLatest { list ->
            if (list.isEmpty()) flowOf(emptyList()) else combine(list.map { viewOf(it) }) { it.toList() }
        }
        return combine(views, defaultCurrency, repository.observeListRefreshedAt()) { all, currency, refreshedAt ->
            val visible = all.filter { !it.hidden }
            WalletList(
                active = visible.filter { !it.archived },
                archived = visible.filter { it.archived },
                totals = totalsOf(visible, currency),
                updatedAt = refreshedAt?.let { runCatching { Instant.parse(it) }.getOrNull() },
            )
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observeWallet(id: String): Flow<WalletView?> = repository.observe(id).flatMapLatest { row ->
        if (row == null) flowOf(null) else viewOf(row).map { view -> view.takeUnless { it.hidden } }
    }

    private fun viewOf(row: WalletEntity): Flow<WalletView> = combine(
        engine.observe(WalletSyncHandler.RECORD_TYPE, row.id),
        postings.deltaFor(row.id),
        postings.hasPending(row.id),
    ) { change, delta, hasPostings -> buildView(row, change, delta, hasPostings) }

    override fun observeMembers(id: String): Flow<List<Member>> =
        repository.observeMembers(id).map { rows -> rows.map { it.toMember(accountId) } }

    private val emailVerified: Boolean get() = (authState.value as? AuthState.SignedIn)?.account?.emailVerified == true

    /** Wallets the person owns that are not about to be deleted; archived ones count (research R-14). */
    private suspend fun ownedLiveCount(): Int {
        val account = accountId ?: return 0
        return repository.observeAll(account).first().count { row ->
            val change = engine.get(WalletSyncHandler.RECORD_TYPE, row.id)
            val deleting = change?.isDelete == true && (change.state == ChangeState.Waiting || change.state == ChangeState.Sending)
            row.myRole == WalletRole.Owner.wire && !deleting
        }
    }

    override suspend fun canCreateAnother(): Boolean = entitlements.allowsAnother(QuotaFeature.Wallets, ownedLiveCount())

    override suspend fun create(draft: WalletDraft): CreateResult {
        val account = accountId ?: return CreateResult.NotVerified
        if (!emailVerified) return CreateResult.NotVerified
        if (!canCreateAnother()) return CreateResult.NotEntitled
        val clean = draft.copy(name = draft.name.trim(), archive = false)
        validate(clean)?.let { return CreateResult.Invalid(it) }
        val id = newId()
        repository.insertPlaceholder(id, clean, account)
        engine.enqueue(
            WalletSyncHandler.RECORD_TYPE, id, SyncScope(ScopeKind.Wallet, id), 0,
            AppJson.encodeToString(WalletDraft.serializer(), clean),
        )
        drainInBackground()
        return CreateResult.Created(id)
    }

    override suspend fun save(id: String, draft: WalletDraft, baseVersion: Long): SaveResult {
        if (!emailVerified) return SaveResult.NotVerified
        val row = repository.get(id) ?: return SaveResult.Gone
        if (WalletRole.fromWire(row.myRole) != WalletRole.Owner) return SaveResult.NotOwner
        val waitingArchive = engine.get(WalletSyncHandler.RECORD_TYPE, id).pendingDraft()?.archive == true
        val clean = draft.copy(name = draft.name.trim(), archive = draft.archive || waitingArchive)
        validate(clean)?.let { return SaveResult.Invalid(it) }
        engine.enqueue(
            WalletSyncHandler.RECORD_TYPE, id, SyncScope(ScopeKind.Wallet, id), baseVersion,
            AppJson.encodeToString(WalletDraft.serializer(), clean),
        )
        drainInBackground()
        return SaveResult.Saved
    }

    override suspend fun archive(id: String): SaveResult {
        val row = repository.get(id) ?: return SaveResult.Gone
        val change = engine.get(WalletSyncHandler.RECORD_TYPE, id)
        val shown = change.pendingDraft() ?: row.toWallet().toDraft()
        return save(id, shown.copy(archive = true), row.version)
    }

    override suspend fun delete(id: String): SaveResult {
        val row = repository.get(id) ?: return SaveResult.Gone
        if (WalletRole.fromWire(row.myRole) != WalletRole.Owner) return SaveResult.NotOwner
        if (!emailVerified) return SaveResult.NotVerified
        engine.enqueueDelete(WalletSyncHandler.RECORD_TYPE, id, SyncScope(ScopeKind.Wallet, id), row.version)
        // A wallet that never reached the server has nothing to delete there (research R-06).
        if (row.version == 0L) repository.removePlaceholder(id) else drainInBackground()
        return SaveResult.Saved
    }

    override suspend fun keepMine(id: String) {
        engine.keepMine(WalletSyncHandler.RECORD_TYPE, id)
        drainInBackground()
    }

    override suspend fun useTheirs(id: String) = engine.useTheirs(WalletSyncHandler.RECORD_TYPE, id)

    override suspend fun tryAgain(id: String) {
        val change = engine.get(WalletSyncHandler.RECORD_TYPE, id) ?: return
        if (change.isDelete) {
            engine.enqueueDelete(change.recordType, id, change.scope, change.baseVersion)
        } else {
            engine.enqueue(change.recordType, id, change.scope, change.baseVersion, change.payloadJson)
        }
        drainInBackground()
    }

    override suspend fun discard(id: String) = engine.discard(WalletSyncHandler.RECORD_TYPE, id)

    override suspend fun refresh(force: Boolean): RefreshOutcome {
        // A pull, an open and a sign-in also send what is waiting, so a person who got back online
        // doesn't have to wait for the scheduler (the foreground trigger does the same).
        if (force) engine.syncNow()
        // The 60 second floor for unforced reads lives in the runner (spec 009 R-10).
        return runner.refresh(ReadTarget.Everything, force).toOutcome()
    }

    override suspend fun refreshMembers(id: String): RefreshOutcome = runner.refresh(ReadTarget.Wallet(id), force = true).toOutcome()

    override suspend fun transferOwnership(id: String, toUserId: String): TransferResult {
        val row = repository.get(id) ?: return TransferResult.Changed
        if (WalletRole.fromWire(row.myRole) != WalletRole.Owner) return TransferResult.NotOwner
        return when (val result = repository.transferOwnership(id, toUserId, row.version)) {
            is ApiResult.Success -> {
                repository.storeServerCopy(result.value)
                runner.refresh(ReadTarget.Wallet(id), force = true)
                TransferResult.Done
            }
            ApiResult.Offline, ApiResult.Timeout -> TransferResult.Offline
            is ApiResult.Failure -> {
                val outcome = when {
                    result.code == ApiErrorCode.FORBIDDEN -> TransferResult.NotOwner
                    result.problem.errors.orEmpty().any { it.pointer == "/user_id" } -> TransferResult.NoLongerMember
                    result.code == ApiErrorCode.VERSION_CONFLICT || result.code == ApiErrorCode.NOT_FOUND -> TransferResult.Changed
                    else -> return TransferResult.Failed(result.problem.requestId)
                }
                if (outcome != TransferResult.NotOwner) runner.refresh(ReadTarget.Wallet(id), force = true)
                outcome
            }
        }
    }

    private fun validate(draft: WalletDraft): Map<WalletField, WalletFieldProblem>? = when {
        draft.name.isBlank() -> mapOf(WalletField.Name to WalletFieldProblem.NameBlank)
        draft.name.length > MAX_NAME -> mapOf(WalletField.Name to WalletFieldProblem.NameTooLong)
        else -> null
    }

    private fun drainInBackground() = runner.requestUpload()

    private fun ReadOutcome.toOutcome(): RefreshOutcome = when (this) {
        ReadOutcome.Done -> RefreshOutcome.Done
        ReadOutcome.Offline -> RefreshOutcome.Offline
        ReadOutcome.Failed -> RefreshOutcome.Failed
    }

    companion object {
        const val MAX_NAME = 100
    }
}
