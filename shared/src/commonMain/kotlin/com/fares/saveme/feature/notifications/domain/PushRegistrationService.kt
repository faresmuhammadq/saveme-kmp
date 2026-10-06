package com.fares.saveme.feature.notifications.domain

import com.fares.saveme.core.network.ApiErrorCode
import com.fares.saveme.core.network.ApiResult
import com.fares.saveme.core.storage.SecureStore
import com.fares.saveme.feature.auth.domain.AuthState
import com.fares.saveme.feature.auth.domain.SessionManager
import com.fares.saveme.feature.notifications.data.NotificationsApi
import com.fares.saveme.feature.notifications.data.PushRegistrationRecord
import com.fares.saveme.feature.notifications.data.PushRegistrationStore
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours

/** What the background job calls; a seam so the worker is testable without the whole service. */
fun interface PushReconciler {
    suspend fun reconcile(trigger: ReconcileTrigger): ReconcileOutcome
}

/**
 * One idempotent operation that makes the server's idea of this phone match the phone (research R-04): it reads
 * everything at run time, so it registers for the session stored when it runs (FR-003). Nothing here is gated on email
 * verification or plan (FR-009). The push address lives in a local variable and in the request only (FR-004, FR-008).
 */
class PushRegistrationService(
    private val api: NotificationsApi,
    private val store: PushRegistrationStore,
    private val secureStore: SecureStore,
    private val sessions: SessionManager,
    private val provider: PushTokenProvider,
    private val permission: NotificationPermissionState,
    private val clock: Clock = Clock.System,
) : PushReconciler {
    override suspend fun reconcile(trigger: ReconcileTrigger): ReconcileOutcome {
        if (sessions.state.value == AuthState.Unknown) sessions.restore()
        val userId = (sessions.state.value as? AuthState.SignedIn)?.account?.userId ?: return ReconcileOutcome.Nothing
        val stored = secureStore.read() ?: return ReconcileOutcome.Nothing
        if (!provider.isAvailable) return ReconcileOutcome.Nothing

        val record = store.read()?.takeIf { it.userId == userId }
        if (!permission.notificationsAllowed()) return unregister(userId, stored.sessionId, record)

        val token = provider.currentToken() ?: return ReconcileOutcome.Nothing
        val fingerprint = fingerprint(token)
        if (record != null && record.sessionId == stored.sessionId && record.fingerprint == fingerprint) {
            if (record.stoppedFor == stored.sessionId) return ReconcileOutcome.Nothing
            if (trigger == ReconcileTrigger.Foreground && clock.now() - record.registeredAt < REFRESH_AFTER) return ReconcileOutcome.Nothing
        }
        return register(userId, stored.sessionId, token, fingerprint, retried = false)
    }

    private suspend fun register(userId: String, sessionId: String, token: String, fingerprint: String, retried: Boolean): ReconcileOutcome =
        when (val result = api.registerDevice(sessionId, token)) {
            is ApiResult.Success -> {
                store.write(PushRegistrationRecord(userId, sessionId, fingerprint, clock.now().epochSeconds))
                pushLog.i { "registered" }
                ReconcileOutcome.Registered
            }
            ApiResult.Offline, ApiResult.Timeout -> ReconcileOutcome.RetryLater
            is ApiResult.Failure -> when {
                result.code == ApiErrorCode.NOT_FOUND -> {
                    // FR-005: the session may have changed under us; try the stored one once, else stop until a new session or address.
                    val latest = secureStore.read()?.sessionId
                    if (!retried && latest != null && latest != sessionId) {
                        register(userId, latest, token, fingerprint, retried = true)
                    } else {
                        store.write(PushRegistrationRecord(userId, sessionId, fingerprint, clock.now().epochSeconds, stoppedFor = sessionId))
                        pushLog.w { "stopped:not_found" }
                        ReconcileOutcome.Stopped
                    }
                }
                result.code == ApiErrorCode.RATE_LIMITED -> ReconcileOutcome.RetryAfter(result.retryAfterSeconds ?: DEFAULT_WAIT_SECONDS)
                result.code == ApiErrorCode.UNAUTHENTICATED -> ReconcileOutcome.Nothing
                result.problem.status >= 500 || result.code.retryClass == com.fares.saveme.core.network.RetryClass.Automatic -> ReconcileOutcome.RetryLater
                else -> {
                    pushLog.w { "stopped:${result.code.name.lowercase()}" }
                    ReconcileOutcome.Stopped
                }
            }
        }

    /** Notifications are off on this phone: remove this session's registration once and forget it (FR-006). */
    private suspend fun unregister(userId: String, sessionId: String, record: PushRegistrationRecord?): ReconcileOutcome {
        if (record == null) return ReconcileOutcome.Nothing
        if (record.sessionId != sessionId || record.stoppedFor != null) {
            // A leftover of an earlier session: the server dropped it when that session ended.
            store.clear()
            return ReconcileOutcome.Nothing
        }
        return when (val result = api.unregisterDevice(record.sessionId)) {
            is ApiResult.Success -> done()
            ApiResult.Offline, ApiResult.Timeout -> ReconcileOutcome.RetryLater
            is ApiResult.Failure -> when {
                result.code == ApiErrorCode.NOT_FOUND -> done()
                result.code == ApiErrorCode.RATE_LIMITED -> ReconcileOutcome.RetryAfter(result.retryAfterSeconds ?: DEFAULT_WAIT_SECONDS)
                result.code == ApiErrorCode.UNAUTHENTICATED -> ReconcileOutcome.Nothing
                result.problem.status >= 500 -> ReconcileOutcome.RetryLater
                else -> {
                    pushLog.w { "unregister stopped:${result.code.name.lowercase()}" }
                    store.clear()
                    ReconcileOutcome.Stopped
                }
            }
        }
    }

    private suspend fun done(): ReconcileOutcome {
        store.clear()
        pushLog.i { "unregistered" }
        return ReconcileOutcome.Unregistered
    }

    private companion object {
        val REFRESH_AFTER = 24.hours
        const val DEFAULT_WAIT_SECONDS = 60L
    }
}
