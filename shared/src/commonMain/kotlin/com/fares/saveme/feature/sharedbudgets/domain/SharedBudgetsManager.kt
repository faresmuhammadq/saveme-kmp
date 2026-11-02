package com.fares.saveme.feature.sharedbudgets.domain

import co.touchlab.kermit.Logger
import com.fares.saveme.core.network.ApiErrorCode
import com.fares.saveme.core.network.ApiResult
import com.fares.saveme.core.network.AppJson
import com.fares.saveme.core.sync.ReadTarget
import com.fares.saveme.core.sync.ScopeKind
import com.fares.saveme.core.sync.SyncEngine
import com.fares.saveme.core.sync.SyncRunner
import com.fares.saveme.core.sync.SyncScope
import com.fares.saveme.feature.auth.domain.AuthState
import com.fares.saveme.feature.budgets.domain.BudgetMath
import com.fares.saveme.feature.sharedbudgets.data.SharedBudgetApi
import com.fares.saveme.feature.sharedbudgets.data.SharedBudgetPayload
import com.fares.saveme.feature.sharedbudgets.data.SharedBudgetRepository
import com.fares.saveme.feature.sharedbudgets.data.SharedBudgetSyncHandler
import com.fares.saveme.feature.sharedbudgets.data.dto.SharedBudgetInvitationPreviewDto
import com.fares.saveme.feature.sharedbudgets.data.dto.SharedBudgetLineWriteRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/** What the person typed for a budget. [limitMinor] null means no overall limit. */
data class SharedBudgetDraft(val name: String, val currency: String, val startDay: Int, val limitMinor: Long?) {
    override fun toString() = "SharedBudgetDraft"
}

enum class SharedBudgetField { Name, Currency, StartDay, Limit }

sealed interface BudgetWriteResult {
    data class Saved(val id: String) : BudgetWriteResult
    data object NotVerified : BudgetWriteResult
    data object NoLongerAvailable : BudgetWriteResult
    data class Invalid(val fields: Set<SharedBudgetField>) : BudgetWriteResult
}

/** The answer to a call that needs the server right now. */
sealed interface ActionResult {
    data object Done : ActionResult
    data object Offline : ActionResult
    data object NotVerified : ActionResult

    /** The person may not do this (not the owner, read-only), or the budget is gone. */
    data object NotAllowed : ActionResult

    /** The owner's plan does not allow more people. */
    data object PlanFull : ActionResult

    /** The budget changed since the screen last showed it; the screen refreshes and the person tries again. */
    data object Stale : ActionResult
    data class Failed(val requestId: String?) : ActionResult
}

sealed interface InviteResult {
    /** [link] is the one-time link; null when the server replayed a create. Never printed. */
    class Created(val link: String?) : InviteResult {
        override fun toString() = "InviteResult.Created(link=<redacted>)"
    }

    data object Offline : InviteResult
    data object NotVerified : InviteResult
    data object NotAllowed : InviteResult
    data object PlanFull : InviteResult
    data class TooMany(val minutes: Int) : InviteResult
    data class Failed(val requestId: String?) : InviteResult
}

sealed interface PreviewResult {
    data class Ready(val preview: SharedBudgetInvitationPreviewDto) : PreviewResult
    data object Invalid : PreviewResult
    data object Unavailable : PreviewResult
    data object Offline : PreviewResult
    data class TooMany(val minutes: Int) : PreviewResult
    data class Failed(val requestId: String?) : PreviewResult
}

sealed interface JoinResult {
    data class Joined(val budgetId: String) : JoinResult
    data object Invalid : JoinResult
    data object Unavailable : JoinResult
    data object PlanFull : JoinResult
    data object Offline : JoinResult
    data class TooMany(val minutes: Int) : JoinResult
    data class Failed(val requestId: String?) : JoinResult
}

/**
 * What the screens do to Shared Budgets. The budget itself (create, save, delete) is queued and works offline like every other
 * record; lines, members, invitations, ownership and archiving need a connection and answer [ActionResult.Offline] without one.
 */
interface SharedBudgetsManager {
    suspend fun create(draft: SharedBudgetDraft): BudgetWriteResult
    suspend fun save(id: String, draft: SharedBudgetDraft, baseVersion: Long): BudgetWriteResult
    suspend fun delete(id: String): BudgetWriteResult
    suspend fun archive(id: String): ActionResult
    suspend fun addLine(budgetId: String, categoryId: String, limitMinor: Long?): ActionResult
    suspend fun setLineLimit(budgetId: String, lineId: String, limitMinor: Long?): ActionResult
    suspend fun removeLine(budgetId: String, lineId: String): ActionResult
    suspend fun invite(budgetId: String): InviteResult
    suspend fun leave(budgetId: String): ActionResult
    suspend fun removeMember(budgetId: String, userId: String): ActionResult
    suspend fun transferOwnership(budgetId: String, userId: String): ActionResult
    suspend fun preview(token: String): PreviewResult
    suspend fun join(token: String): JoinResult
    suspend fun decline(token: String): ActionResult

    /** Reads the budget's own feed from the server (a screen opening or pulling); without [force] within the last minute is skipped. */
    suspend fun refresh(budgetId: String, force: Boolean)
}

@OptIn(ExperimentalUuidApi::class)
class DefaultSharedBudgetsManager(
    private val repository: SharedBudgetRepository,
    private val api: SharedBudgetApi,
    private val engine: () -> SyncEngine,
    private val runner: SyncRunner,
    private val authState: StateFlow<AuthState>,
    private val deviceZoneId: () -> String,
    private val scope: CoroutineScope,
    private val newId: () -> String = { Uuid.generateV7().toString() },
) : SharedBudgetsManager {
    private val log = Logger.withTag("shared-budgets")

    private val accountId: String? get() = (authState.value as? AuthState.SignedIn)?.account?.userId
    private val emailVerified: Boolean get() = (authState.value as? AuthState.SignedIn)?.account?.emailVerified == true

    private fun scopeOf(id: String) = SyncScope(ScopeKind.SharedBudget, id)

    private fun problems(draft: SharedBudgetDraft): Set<SharedBudgetField> = buildSet {
        val name = draft.name.trim()
        if (name.isEmpty() || name.count { !it.isLowSurrogate() } > MAX_NAME) add(SharedBudgetField.Name)
        if (!CURRENCY.matches(draft.currency)) add(SharedBudgetField.Currency)
        if (draft.startDay !in 1..28) add(SharedBudgetField.StartDay)
        draft.limitMinor?.let { if (it < 1 || it > BudgetMath.MAX_MINOR) add(SharedBudgetField.Limit) }
    }

    private fun SharedBudgetDraft.payloadJson(): String =
        AppJson.encodeToString(SharedBudgetPayload.serializer(), SharedBudgetPayload(name.trim(), currency, startDay, limitMinor))

    override suspend fun create(draft: SharedBudgetDraft): BudgetWriteResult {
        val account = accountId ?: return BudgetWriteResult.NotVerified
        if (!emailVerified) return BudgetWriteResult.NotVerified
        problems(draft).takeIf { it.isNotEmpty() }?.let { return BudgetWriteResult.Invalid(it) }
        val id = newId()
        repository.insertPlaceholder(id, account, draft.name.trim(), draft.currency, draft.startDay, draft.limitMinor, deviceZoneId())
        engine().enqueue(SharedBudgetSyncHandler.RECORD_TYPE, id, scopeOf(id), 0, draft.payloadJson())
        runner.requestUpload()
        return BudgetWriteResult.Saved(id)
    }

    override suspend fun save(id: String, draft: SharedBudgetDraft, baseVersion: Long): BudgetWriteResult {
        accountId ?: return BudgetWriteResult.NotVerified
        if (!emailVerified) return BudgetWriteResult.NotVerified
        val row = repository.get(id) ?: return BudgetWriteResult.NoLongerAvailable
        // The currency of a budget is fixed once it exists.
        val clean = draft.copy(currency = row.currency)
        problems(clean).takeIf { it.isNotEmpty() }?.let { return BudgetWriteResult.Invalid(it) }
        engine().enqueue(SharedBudgetSyncHandler.RECORD_TYPE, id, scopeOf(id), baseVersion, clean.payloadJson())
        runner.requestUpload()
        return BudgetWriteResult.Saved(id)
    }

    override suspend fun delete(id: String): BudgetWriteResult {
        accountId ?: return BudgetWriteResult.NotVerified
        if (!emailVerified) return BudgetWriteResult.NotVerified
        val row = repository.get(id) ?: return BudgetWriteResult.NoLongerAvailable
        engine().enqueueDelete(SharedBudgetSyncHandler.RECORD_TYPE, id, scopeOf(id), row.version)
        // A budget that never reached the server has nothing to delete there.
        if (row.version == 0L) repository.removePlaceholder(id) else runner.requestUpload()
        return BudgetWriteResult.Saved(id)
    }

    override suspend fun archive(id: String): ActionResult = guarded {
        val row = repository.get(id)
        if (row == null || row.version == 0L) return@guarded ActionResult.NotAllowed
        api.archive(id, row.version).toAction { repository.storeServerCopy(it) }
    }

    override suspend fun addLine(budgetId: String, categoryId: String, limitMinor: Long?): ActionResult = guarded {
        if (repository.lines(budgetId).any { it.categoryId == categoryId }) return@guarded ActionResult.Done
        api.putLine(budgetId, newId(), SharedBudgetLineWriteRequest(categoryId, limitMinor, 0)).toAction { repository.storeLine(it) }
    }

    override suspend fun setLineLimit(budgetId: String, lineId: String, limitMinor: Long?): ActionResult = guarded {
        val line = repository.getLine(lineId) ?: return@guarded ActionResult.NotAllowed
        api.putLine(budgetId, lineId, SharedBudgetLineWriteRequest(line.categoryId, limitMinor, line.version)).toAction { repository.storeLine(it) }
    }

    override suspend fun removeLine(budgetId: String, lineId: String): ActionResult = guarded {
        val line = repository.getLine(lineId) ?: return@guarded ActionResult.Done
        api.deleteLine(budgetId, lineId, line.version).toAction { repository.storeLine(it) }
    }

    override suspend fun invite(budgetId: String): InviteResult {
        if (accountId == null || !emailVerified) return InviteResult.NotVerified
        return when (val answer = api.putInvitation(budgetId, newId())) {
            is ApiResult.Success -> InviteResult.Created(answer.value.shareUrl)
            ApiResult.Offline, ApiResult.Timeout -> InviteResult.Offline
            is ApiResult.Failure -> when (answer.code) {
                ApiErrorCode.FORBIDDEN, ApiErrorCode.NOT_FOUND -> InviteResult.NotAllowed
                ApiErrorCode.FEATURE_NOT_ENTITLED -> InviteResult.PlanFull
                ApiErrorCode.RATE_LIMITED -> InviteResult.TooMany(minutes(answer.retryAfterSeconds))
                else -> InviteResult.Failed(answer.problem.requestId)
            }
        }
    }

    override suspend fun leave(budgetId: String): ActionResult = guarded {
        api.leave(budgetId).toAction { repository.accessLost(budgetId) }
    }

    override suspend fun removeMember(budgetId: String, userId: String): ActionResult = guarded {
        api.removeMember(budgetId, userId).toAction { refresh(budgetId, force = true) }
    }

    override suspend fun transferOwnership(budgetId: String, userId: String): ActionResult = guarded {
        val row = repository.get(budgetId)
        if (row == null || row.version == 0L) return@guarded ActionResult.NotAllowed
        api.transferOwnership(budgetId, userId, row.version).toAction { repository.storeServerCopy(it) }
    }

    override suspend fun preview(token: String): PreviewResult = when (val answer = api.preview(token)) {
        is ApiResult.Success -> PreviewResult.Ready(answer.value)
        ApiResult.Offline, ApiResult.Timeout -> PreviewResult.Offline
        is ApiResult.Failure -> when (answer.code) {
            ApiErrorCode.NOT_FOUND -> PreviewResult.Unavailable
            ApiErrorCode.VALIDATION_FAILED -> PreviewResult.Invalid
            ApiErrorCode.RATE_LIMITED -> PreviewResult.TooMany(minutes(answer.retryAfterSeconds))
            else -> PreviewResult.Failed(answer.problem.requestId)
        }
    }

    override suspend fun join(token: String): JoinResult = when (val answer = api.accept(token)) {
        is ApiResult.Success -> {
            val member = answer.value
            // The row makes the budget followed; its first read runs on whichever screen is open.
            repository.storeOwnMembership(member)
            scope.launch { runner.refresh(ReadTarget.SharedBudget(member.sharedBudgetId), force = true) }
            log.i { "shared budget invitation accepted" }
            JoinResult.Joined(member.sharedBudgetId)
        }
        ApiResult.Offline, ApiResult.Timeout -> JoinResult.Offline
        is ApiResult.Failure -> when (answer.code) {
            ApiErrorCode.NOT_FOUND -> JoinResult.Unavailable
            ApiErrorCode.VALIDATION_FAILED -> JoinResult.Invalid
            ApiErrorCode.FEATURE_NOT_ENTITLED -> JoinResult.PlanFull
            ApiErrorCode.RATE_LIMITED -> JoinResult.TooMany(minutes(answer.retryAfterSeconds))
            else -> JoinResult.Failed(answer.problem.requestId)
        }
    }

    override suspend fun decline(token: String): ActionResult = guarded { api.decline(token).toAction { } }

    override suspend fun refresh(budgetId: String, force: Boolean) {
        runner.refresh(ReadTarget.SharedBudget(budgetId), force)
    }

    private suspend fun guarded(block: suspend () -> ActionResult): ActionResult {
        if (accountId == null || !emailVerified) return ActionResult.NotVerified
        return block()
    }

    private suspend fun <T> ApiResult<T>.toAction(onSuccess: suspend (T) -> Unit): ActionResult = when (this) {
        is ApiResult.Success -> {
            onSuccess(value)
            ActionResult.Done
        }
        ApiResult.Offline, ApiResult.Timeout -> ActionResult.Offline
        is ApiResult.Failure -> when (code) {
            ApiErrorCode.FORBIDDEN, ApiErrorCode.NOT_FOUND -> ActionResult.NotAllowed
            ApiErrorCode.FEATURE_NOT_ENTITLED -> ActionResult.PlanFull
            ApiErrorCode.VERSION_CONFLICT -> ActionResult.Stale
            else -> ActionResult.Failed(problem.requestId)
        }
    }

    private fun minutes(retryAfterSeconds: Long?): Int = (((retryAfterSeconds ?: 60L) + 59L) / 60L).coerceIn(1L, 999L).toInt()

    private companion object {
        const val MAX_NAME = 100
        val CURRENCY = Regex("^[A-Z]{3}$")
    }
}
