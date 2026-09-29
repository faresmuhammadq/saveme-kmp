package com.fares.saveme.feature.auth.domain

import com.fares.saveme.core.network.ApiEvent
import com.fares.saveme.core.network.ApiEvents
import com.fares.saveme.core.session.UserDataCleaner
import com.fares.saveme.core.storage.StoredSession
import com.fares.saveme.feature.auth.data.AuthRepository
import co.touchlab.kermit.Logger
import com.fares.saveme.feature.auth.data.dto.AccountDto
import com.fares.saveme.feature.auth.data.dto.AccountSnapshotDto
import com.fares.saveme.feature.auth.data.dto.SignInResultDto
import com.fares.saveme.feature.auth.domain.model.AccountSnapshot
import com.fares.saveme.feature.auth.domain.model.AccountState
import com.fares.saveme.feature.auth.domain.model.FeatureValue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.longOrNull
import kotlin.time.Clock
import kotlin.time.Instant

/** Owns the signed-in state. Any 401 on a credentialed call ends the local session (P-09.5). */
class SessionManager(
    private val repository: AuthRepository,
    apiEvents: ApiEvents,
    scope: CoroutineScope,
    private val clock: Clock = Clock.System,
    private val cleaners: List<UserDataCleaner> = emptyList(),
) {
    private val mutableState = MutableStateFlow<AuthState>(AuthState.Unknown)
    val state: StateFlow<AuthState> = mutableState.asStateFlow()

    init {
        apiEvents.events
            .filterIsInstance<ApiEvent.SessionEnded>()
            .onEach { signOutLocally(SignOutReason.SessionEnded) }
            .launchIn(scope)
    }

    suspend fun restore() {
        val session = repository.readSession()
        val snapshot = repository.readSnapshot()
        mutableState.value = if (session != null && snapshot != null) {
            AuthState.SignedIn(snapshot.toDomain())
        } else {
            if (session != null || snapshot != null) repository.clearLocal()
            AuthState.SignedOut(SignOutReason.None)
        }
    }

    suspend fun establish(result: SignInResultDto) {
        val snapshot = AccountSnapshotDto(result.account, result.entitlements)
        repository.saveSession(StoredSession(result.credential, result.session.id, clock.now()), snapshot)
        mutableState.value = AuthState.SignedIn(snapshot.toDomain(), deletionCancelled = result.deletionCancelled)
    }

    suspend fun signOutLocally(reason: SignOutReason) {
        repository.clearLocal()
        // One failing cleaner must not keep the rest of this person's data, or the sign-out (P-09).
        cleaners.forEach { cleaner ->
            try {
                cleaner.clear()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Logger.w(e, tag = "session") { "local data cleaner failed" }
            }
        }
        mutableState.value = AuthState.SignedOut(reason)
    }

    /** Replaces the cached account with the server's (from `/me`), keeping the entitlements. */
    suspend fun updateAccount(account: AccountDto) {
        val snapshot = repository.readSnapshot() ?: return
        val updated = snapshot.copy(account = account)
        repository.saveSnapshot(updated)
        val current = mutableState.value as? AuthState.SignedIn ?: return
        mutableState.value = current.copy(account = updated.toDomain())
    }

    /** After a deletion request or cancel: the account state and its scheduled date. */
    suspend fun updateDeletion(state: AccountState, scheduledFor: Instant?) {
        val snapshot = repository.readSnapshot() ?: return
        updateAccount(
            snapshot.account.copy(
                state = if (state == AccountState.PendingDeletion) "pending_deletion" else "active",
                deletionScheduledFor = scheduledFor?.toString(),
            ),
        )
    }

    suspend fun markEmailVerified() {
        val current = mutableState.value as? AuthState.SignedIn ?: return
        repository.markEmailVerified()
        mutableState.value = current.copy(account = current.account.copy(emailVerified = true))
    }
}

internal fun AccountSnapshotDto.toDomain() = AccountSnapshot(
    userId = account.id,
    email = account.email,
    emailVerified = account.emailVerified,
    state = if (account.state == "pending_deletion") AccountState.PendingDeletion else AccountState.Active,
    deletionScheduledFor = account.deletionScheduledFor?.let { runCatching { Instant.parse(it) }.getOrNull() },
    plan = entitlements.plan.name,
    features = entitlements.features.mapValues { (_, v) ->
        v.booleanOrNull?.let { FeatureValue.Flag(it) } ?: FeatureValue.Quota(v.longOrNull ?: 0)
    },
)
