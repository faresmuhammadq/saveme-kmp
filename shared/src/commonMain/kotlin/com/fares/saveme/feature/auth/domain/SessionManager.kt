package com.fares.saveme.feature.auth.domain

import com.fares.saveme.core.network.ApiEvent
import com.fares.saveme.core.network.ApiEvents
import com.fares.saveme.core.storage.StoredSession
import com.fares.saveme.feature.auth.data.AuthRepository
import com.fares.saveme.feature.auth.data.dto.AccountSnapshotDto
import com.fares.saveme.feature.auth.data.dto.SignInResultDto
import com.fares.saveme.feature.auth.domain.model.AccountSnapshot
import com.fares.saveme.feature.auth.domain.model.AccountState
import com.fares.saveme.feature.auth.domain.model.FeatureValue
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
        mutableState.value = AuthState.SignedOut(reason)
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
    plan = entitlements.plan,
    features = entitlements.features.mapValues { (_, v) ->
        v.booleanOrNull?.let { FeatureValue.Flag(it) } ?: FeatureValue.Quota(v.longOrNull ?: 0)
    },
)
