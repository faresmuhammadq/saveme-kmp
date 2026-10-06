package com.fares.saveme.feature.account.domain.deletion

import com.fares.saveme.core.network.ApiErrorCode
import com.fares.saveme.core.network.ApiResult
import com.fares.saveme.feature.account.data.AccountApi
import com.fares.saveme.feature.account.data.dto.DeletionPlanDto
import com.fares.saveme.feature.account.data.dto.DeletionSuccessorDto
import com.fares.saveme.feature.auth.domain.AuthState
import com.fares.saveme.feature.auth.domain.SessionManager
import com.fares.saveme.feature.auth.domain.model.AccountState
import com.fares.saveme.feature.wallets.domain.WalletMemberships
import com.fares.saveme.feature.wallets.domain.WalletsService
import com.fares.saveme.feature.wallets.domain.model.WalletRole
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlin.time.Instant

/** Reads what would happen to the person's shared wallets and names it from the phone's own records (spec 016 R-07). */
interface DeletionPlanService {
    /** One GET per call. Also refreshes the account snapshot's state and date, for known states only. */
    suspend fun read(): PlanOutcome
}

class DefaultDeletionPlanService(
    private val api: AccountApi,
    private val wallets: WalletsService,
    private val memberships: WalletMemberships,
    private val sessions: SessionManager,
) : DeletionPlanService {

    override suspend fun read(): PlanOutcome = try {
        when (val result = api.deletionPlan()) {
            is ApiResult.Success -> PlanOutcome.Loaded(join(result.value))
            ApiResult.Offline, ApiResult.Timeout -> PlanOutcome.Offline
            is ApiResult.Failure -> PlanOutcome.Failed(result.code, result.problem.requestId)
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        PlanOutcome.Failed(ApiErrorCode.UNKNOWN, null)
    }

    private suspend fun join(dto: DeletionPlanDto): DeletionPlan {
        val me = (sessions.state.value as? AuthState.SignedIn)?.account?.userId
        val list = wallets.observeList().first()
        val names = (list.active + list.archived).associate { it.id to it.shown.name }
        val state = when (dto.state) {
            "active" -> PlanState.Active
            "pending_deletion" -> PlanState.PendingDeletion
            else -> PlanState.Unknown(dto.state)
        }
        val scheduledFor = dto.deletionScheduledFor.toInstantOrNull()
        when (state) {
            PlanState.Active -> sessions.updateDeletion(AccountState.Active, null)
            PlanState.PendingDeletion -> if (scheduledFor != null) sessions.updateDeletion(AccountState.PendingDeletion, scheduledFor)
            is PlanState.Unknown -> Unit
        }
        return DeletionPlan(
            state = state,
            requestedAt = dto.deletionRequestedAt.toInstantOrNull(),
            scheduledFor = scheduledFor,
            wallets = dto.successors.map { owned(it, names[it.walletId], me) },
        )
    }

    private suspend fun owned(dto: DeletionSuccessorDto, walletName: String?, me: String?): OwnedSharedWallet {
        val others = memberships.observe(dto.walletId).first()
            .filter { !it.deleted && it.userId != me }
            .map { Candidate(it.userId, it.displayName?.takeIf { name -> name.isNotBlank() }, WalletRole.fromWire(it.role), it.createdAt.toInstantOrNull()) }
        val suggested = others.firstOrNull { it.userId == dto.successorUserId }
            ?: Candidate(dto.successorUserId, null, null, null, unknown = true)
        val rest = others.filter { it.userId != dto.successorUserId }.sortedBy { it.joinedAt }
        val candidates = listOf(suggested) + rest
        return OwnedSharedWallet(
            walletId = dto.walletId,
            walletName = walletName,
            successorUserId = dto.successorUserId,
            reason = SuccessorReason.fromWire(dto.reason),
            choiceExpected = dto.choiceExpected,
            choiceOffered = dto.choiceExpected && walletName != null && others.isNotEmpty(),
            candidates = candidates,
        )
    }

    private fun String?.toInstantOrNull(): Instant? = this?.let { runCatching { Instant.parse(it) }.getOrNull() }
}
