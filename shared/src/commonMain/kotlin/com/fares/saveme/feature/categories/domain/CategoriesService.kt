package com.fares.saveme.feature.categories.domain

import com.fares.saveme.core.locale.AppLanguage
import com.fares.saveme.core.network.ApiResult
import com.fares.saveme.core.sync.ReadOutcome
import com.fares.saveme.core.sync.ReadTarget
import com.fares.saveme.core.sync.ScopeKind
import com.fares.saveme.core.sync.SyncEngine
import com.fares.saveme.core.sync.SyncRunner
import com.fares.saveme.core.sync.SyncScope
import com.fares.saveme.feature.auth.domain.AuthState
import com.fares.saveme.feature.auth.domain.model.RefreshOutcome
import com.fares.saveme.feature.categories.data.CategoryReferenceRepository
import com.fares.saveme.feature.categories.data.CategoryRepository
import com.fares.saveme.feature.categories.data.CategorySyncHandler
import com.fares.saveme.feature.categories.data.local.CategoryEntity
import com.fares.saveme.feature.categories.domain.model.CategoriesOverview
import com.fares.saveme.feature.categories.domain.model.CategoryChoices
import com.fares.saveme.feature.categories.domain.model.CategoryDisplays
import com.fares.saveme.feature.categories.domain.model.CategoryDraft
import com.fares.saveme.feature.categories.domain.model.CategoryField
import com.fares.saveme.feature.categories.domain.model.CategoryFieldProblem
import com.fares.saveme.feature.categories.domain.model.CategoryKind
import com.fares.saveme.feature.categories.domain.model.CategoryView
import com.fares.saveme.feature.categories.domain.model.ReportLabels
import com.fares.saveme.feature.categories.domain.model.CreateResult
import com.fares.saveme.feature.categories.domain.model.SaveResult
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
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlin.uuid.Uuid

/**
 * The categories domain API (contracts/categories-domain.md). Other features use only this
 * package (P-10.4); the screens' stores take its functions as lambdas.
 */
interface CategoriesService {
    fun observeOverview(kind: CategoryKind): Flow<CategoriesOverview>

    /** Null when the category is gone or hidden by a deletion that waits to sync. */
    fun observeCategory(id: String): Flow<CategoryView?>

    /** Ids an unsent record on this phone uses; those categories can't be deleted (research R-06). */
    fun observeUsedLocally(): Flow<Set<String>>

    /** FR-018: own active (by name), then active defaults (platform order); the current one is kept whatever its state. */
    fun observeChoices(kind: CategoryKind, currentCategoryId: String? = null): Flow<CategoryChoices>

    /** FR-019: one snapshot per screen; resolve(id) is own → default → [walletId]'s references → unavailable. */
    fun observeDisplays(walletId: String? = null): Flow<CategoryDisplays>

    /**
     * Spec 014 R-08: the grouping key and the names for a report over [walletIds] (the union of those wallets' category
     * references). The default body keys every id as itself and resolves with [observeDisplays] for no wallet.
     */
    fun observeReportLabels(walletIds: Set<String>): Flow<ReportLabels> = observeDisplays(null).map { ReportLabels.identity(it) }

    suspend fun create(draft: CategoryDraft): CreateResult

    /** [baseVersion] is the version shown when editing began (FR-007); the kind is always the stored kind. */
    suspend fun save(id: String, draft: CategoryDraft, baseVersion: Long): SaveResult

    suspend fun archive(id: String): SaveResult

    suspend fun delete(id: String): SaveResult

    /** After a CATEGORY_IN_USE refusal: the waiting deletion becomes a waiting archive (FR-017). */
    suspend fun archiveInstead(id: String)

    /** After a CATEGORY_IN_USE refusal: drops the deletion. */
    suspend fun keepCategory(id: String)

    suspend fun keepMine(id: String)

    /** Also "Accept the deletion" and "Keep the category" on a delete clash. */
    suspend fun useTheirs(id: String)

    suspend fun tryAgain(id: String)

    suspend fun discard(id: String)

    /** Reads own categories and the default set; without [force] a read within the last 60 seconds is skipped. */
    suspend fun refresh(force: Boolean = false): RefreshOutcome

    /** Reads one wallet (its feed carries its partner categories); without [force] a read within the last 60 seconds is skipped. */
    suspend fun refreshReferences(walletId: String, force: Boolean = false): RefreshOutcome
}

class DefaultCategoriesService(
    private val repository: CategoryRepository,
    private val references: CategoryReferenceRepository,
    private val engine: SyncEngine,
    private val authState: StateFlow<AuthState>,
    private val pendingUses: PendingCategoryUses,
    private val language: Flow<AppLanguage>,
    private val scope: CoroutineScope,
    private val runner: SyncRunner,
    private val clock: Clock = Clock.System,
    private val newId: () -> String = { Uuid.generateV7().toString() },
    private val foregroundFloor: Duration = 60.seconds,
) : CategoriesService {

    private val refreshLock = Mutex()
    private var lastDefaultsRead: Instant? = null
    private val referenceLock = Mutex()
    private val lastReferenceRead = mutableMapOf<String, Instant>()

    private val accountId: String? get() = (authState.value as? AuthState.SignedIn)?.account?.userId

    private val emailVerified: Boolean get() = (authState.value as? AuthState.SignedIn)?.account?.emailVerified == true

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun ownViews(): Flow<List<CategoryView>> =
        authState
            .map { (it as? AuthState.SignedIn)?.account?.userId }
            .distinctUntilChanged()
            .flatMapLatest { id -> if (id == null) flowOf(emptyList()) else repository.observeOwn(id) }
            .flatMapLatest { rows ->
                if (rows.isEmpty()) flowOf(emptyList()) else combine(rows.map { viewOf(it) }) { it.toList() }
            }

    private fun viewOf(row: CategoryEntity): Flow<CategoryView> =
        engine.observe(CategorySyncHandler.RECORD_TYPE, row.id).map { change -> buildView(row, change) }

    private fun defaults() = repository.observeDefaults().map { rows -> rows.map { it.toDefault() } }

    private fun stamp(flow: Flow<String?>) = flow.map { value -> value?.let { runCatching { Instant.parse(it) }.getOrNull() } }

    override fun observeOverview(kind: CategoryKind): Flow<CategoriesOverview> = combine(
        ownViews(), defaults(), language, stamp(repository.observeOwnRefreshedAt()), stamp(repository.observeDefaultsRefreshedAt()),
    ) { views, defaults, lang, ownAt, defaultsAt -> buildOverview(kind, views, defaults, lang, ownAt, defaultsAt) }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observeCategory(id: String): Flow<CategoryView?> = repository.observe(id).flatMapLatest { row ->
        if (row == null) flowOf(null) else viewOf(row).map { view -> view.takeUnless { it.hidden } }
    }

    override fun observeUsedLocally(): Flow<Set<String>> = pendingUses.usedLocally()

    override fun observeChoices(kind: CategoryKind, currentCategoryId: String?): Flow<CategoryChoices> = combine(
        ownViews(), defaults(), references.observeAll().map { rows -> rows.map { it.toReference() } }, language,
    ) { own, defaults, partners, lang -> buildChoices(kind, currentCategoryId, own, defaults, partners, lang) }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observeDisplays(walletId: String?): Flow<CategoryDisplays> {
        val walletReferences = if (walletId == null) flowOf(emptyList()) else references.observe(walletId).map { rows -> rows.map { it.toReference() } }
        return combine(ownViews(), defaults(), walletReferences, language) { own, defaults, partners, lang ->
            val resolver = CategoryResolver(own, defaults, partners, lang)
            CategoryDisplays(
                lookup = resolver::resolve,
                onMiss = { if (walletId != null) scope.launch { refreshReferences(walletId, force = false) } },
            )
        }
    }

    override fun observeReportLabels(walletIds: Set<String>): Flow<ReportLabels> {
        val covered = references.observeAll().map { rows -> rows.filter { it.walletId in walletIds }.map { it.toReference() } }
        return combine(ownViews(), defaults(), covered, language) { own, defaults, partners, lang ->
            val resolver = CategoryResolver(own, defaults, partners, lang)
            val keys = defaults.associate { it.id to it.systemKey }
            ReportLabels(
                key = { id -> keys[id] ?: id },
                displays = CategoryDisplays(
                    lookup = resolver::resolve,
                    // A miss asks for the references of every covered wallet, each under its own floor.
                    onMiss = { walletIds.forEach { id -> scope.launch { refreshReferences(id, force = false) } } },
                ),
            )
        }
    }

    override suspend fun create(draft: CategoryDraft): CreateResult {
        val account = accountId ?: return CreateResult.NotVerified
        if (!emailVerified) return CreateResult.NotVerified
        val clean = draft.copy(name = draft.name.trim(), archive = false)
        validate(clean, creating = true)?.let { return CreateResult.Invalid(it) }
        val id = newId()
        repository.insertPlaceholder(id, account, clean.kind, clean.name, clean.icon, clean.color)
        engine.enqueue(CategorySyncHandler.RECORD_TYPE, id, scopeOf(account), 0, clean.toPayloadJson())
        drainInBackground()
        return CreateResult.Created(id)
    }

    override suspend fun save(id: String, draft: CategoryDraft, baseVersion: Long): SaveResult {
        val account = accountId ?: return SaveResult.NotVerified
        if (!emailVerified) return SaveResult.NotVerified
        // A default's id is never an own category's, so no write can ever be queued for one (FR-011).
        val row = repository.get(id) ?: return SaveResult.NoLongerAvailable
        val waitingArchive = engine.get(CategorySyncHandler.RECORD_TYPE, id).pendingDraft()?.archive == true
        val clean = draft.copy(kind = row.kind, name = draft.name.trim(), archive = draft.archive || waitingArchive)
        validate(clean, creating = false)?.let { return SaveResult.Invalid(it) }
        engine.enqueue(CategorySyncHandler.RECORD_TYPE, id, scopeOf(account), baseVersion, clean.toPayloadJson())
        drainInBackground()
        return SaveResult.Saved
    }

    override suspend fun archive(id: String): SaveResult {
        val row = repository.get(id) ?: return SaveResult.NoLongerAvailable
        val shown = engine.get(CategorySyncHandler.RECORD_TYPE, id).pendingDraft() ?: row.toCategory().toDraft()
        return save(id, shown.copy(archive = true), row.version)
    }

    override suspend fun delete(id: String): SaveResult {
        val account = accountId ?: return SaveResult.NotVerified
        if (!emailVerified) return SaveResult.NotVerified
        val row = repository.get(id) ?: return SaveResult.NoLongerAvailable
        val change = engine.get(CategorySyncHandler.RECORD_TYPE, id)
        val view = buildView(row, change)
        if (!view.canDelete(pendingUses.usedLocally().first())) return SaveResult.InUse
        engine.enqueueDelete(CategorySyncHandler.RECORD_TYPE, id, scopeOf(account), row.version)
        // A category that never reached the server has nothing to delete there (research R-06).
        if (row.version == 0L) repository.removePlaceholder(id) else drainInBackground()
        return SaveResult.Saved
    }

    override suspend fun archiveInstead(id: String) {
        val account = accountId ?: return
        val row = repository.get(id) ?: return
        val change = engine.get(CategorySyncHandler.RECORD_TYPE, id)
        repository.markInUse(id)
        val shown = decodeDraft(change?.payloadJson.orEmpty()) ?: row.toCategory().toDraft()
        engine.enqueue(CategorySyncHandler.RECORD_TYPE, id, scopeOf(account), row.version, shown.copy(archive = true).toPayloadJson())
        drainInBackground()
    }

    override suspend fun keepCategory(id: String) {
        repository.markInUse(id)
        engine.discard(CategorySyncHandler.RECORD_TYPE, id)
    }

    override suspend fun keepMine(id: String) {
        engine.keepMine(CategorySyncHandler.RECORD_TYPE, id)
        drainInBackground()
    }

    override suspend fun useTheirs(id: String) = engine.useTheirs(CategorySyncHandler.RECORD_TYPE, id)

    override suspend fun tryAgain(id: String) {
        val change = engine.get(CategorySyncHandler.RECORD_TYPE, id) ?: return
        if (change.isDelete) {
            engine.enqueueDelete(change.recordType, id, change.scope, change.baseVersion)
        } else {
            engine.enqueue(change.recordType, id, change.scope, change.baseVersion, change.payloadJson)
        }
        drainInBackground()
    }

    override suspend fun discard(id: String) = engine.discard(CategorySyncHandler.RECORD_TYPE, id)

    override suspend fun refresh(force: Boolean): RefreshOutcome = refreshLock.withLock {
        // A pull, an open and a sign-in also send what is waiting, so a person who got back online
        // doesn't have to wait for the scheduler (the foreground trigger does the same).
        if (force) engine.syncNow()
        // The person's categories come from their change feed (floor in the runner); the default set is
        // the one list no feed carries, so it is still read here, with the same 60 second floor (FR-016).
        val own = runner.refresh(ReadTarget.User, force).toOutcome()
        val last = lastDefaultsRead
        val defaults = if (!force && last != null && clock.now() - last < foregroundFloor) {
            RefreshOutcome.Done
        } else {
            repository.refreshDefaults().toOutcome().also { if (it == RefreshOutcome.Done) lastDefaultsRead = clock.now() }
        }
        combineOutcomes(own, defaults)
    }

    override suspend fun refreshReferences(walletId: String, force: Boolean): RefreshOutcome {
        referenceLock.withLock {
            val last = lastReferenceRead[walletId]
            if (!force && last != null && clock.now() - last < foregroundFloor) return RefreshOutcome.Done
            // The attempt counts even when it fails, so a screen full of misses asks once a minute, not once a row.
            lastReferenceRead[walletId] = clock.now()
        }
        // The wallet's references arrive with its feed (spec 009 FR-001); the runner keeps its own floor.
        return runner.refresh(ReadTarget.Wallet(walletId), force).toOutcome()
    }

    /** Name length is counted in code points, as the server counts it (FR-006). */
    private fun validate(draft: CategoryDraft, creating: Boolean): Map<CategoryField, CategoryFieldProblem>? = when {
        creating && CategoryKind.fromWire(draft.kind) == null ->
            mapOf(CategoryField.Kind to CategoryFieldProblem.Server("INVALID_ENUM_VALUE"))
        draft.name.isBlank() -> mapOf(CategoryField.Name to CategoryFieldProblem.NameBlank)
        codePointLength(draft.name) > MAX_NAME -> mapOf(CategoryField.Name to CategoryFieldProblem.NameTooLong)
        else -> null
    }

    private fun scopeOf(account: String) = SyncScope(ScopeKind.User, account)

    private fun drainInBackground() = runner.requestUpload()

    private fun combineOutcomes(a: RefreshOutcome, b: RefreshOutcome) = when {
        a == RefreshOutcome.Offline || b == RefreshOutcome.Offline -> RefreshOutcome.Offline
        a == RefreshOutcome.Failed || b == RefreshOutcome.Failed -> RefreshOutcome.Failed
        else -> RefreshOutcome.Done
    }

    private fun ApiResult<Unit>.toOutcome(): RefreshOutcome = when (this) {
        is ApiResult.Success -> RefreshOutcome.Done
        ApiResult.Offline, ApiResult.Timeout -> RefreshOutcome.Offline
        is ApiResult.Failure -> RefreshOutcome.Failed
    }

    private fun ReadOutcome.toOutcome(): RefreshOutcome = when (this) {
        ReadOutcome.Done -> RefreshOutcome.Done
        ReadOutcome.Offline -> RefreshOutcome.Offline
        ReadOutcome.Failed -> RefreshOutcome.Failed
    }

    companion object {
        const val MAX_NAME = 50
    }
}

/** UTF-16 units minus the low halves of surrogate pairs: the number of Unicode code points. */
internal fun codePointLength(text: String): Int = text.count { !it.isLowSurrogate() }
