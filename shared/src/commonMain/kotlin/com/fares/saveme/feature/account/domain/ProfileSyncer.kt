package com.fares.saveme.feature.account.domain

import co.touchlab.kermit.Logger
import com.fares.saveme.core.locale.AppLanguage
import com.fares.saveme.core.locale.AppLanguageStore
import com.fares.saveme.core.network.ApiErrorCode
import com.fares.saveme.core.network.ApiResult
import com.fares.saveme.core.network.AppJson
import com.fares.saveme.feature.account.data.ProfileRepository
import com.fares.saveme.feature.account.data.dto.ProfileDto
import com.fares.saveme.feature.account.data.dto.ProfileWriteRequest
import com.fares.saveme.feature.account.data.local.ProfileEntity
import com.fares.saveme.feature.account.domain.model.Profile
import com.fares.saveme.feature.account.domain.model.ProfileDraft
import com.fares.saveme.feature.account.domain.model.ProfileField
import com.fares.saveme.feature.account.domain.model.ProfileView
import com.fares.saveme.feature.account.domain.model.SyncOutcome
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.LocalDate
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/**
 * Profile edits are saved on the device first and pushed with the last server version
 * (P-04.2, P-04.4). A conflict waits for the person's choice; the server never merges and
 * neither does this class (research R-02, R-03).
 */
class ProfileSyncer(
    private val repository: ProfileRepository,
    private val languages: AppLanguageStore,
    private val scope: CoroutineScope,
    private val retryDelays: List<Duration> = listOf(5.seconds, 15.seconds, 60.seconds, 5.minutes),
) {
    private val mutex = Mutex()
    /** The scheduled retry after an offline push, if any. */
    internal var retryJob: Job? = null
        private set
    private var attempt = 0

    val view: Flow<ProfileView?> = repository.observe().map { it?.toView() }

    /**
     * Saves on the device (works offline), applies the language at once, then pushes in the
     * syncer's own scope so leaving the screen does not stop it. Returns that push.
     */
    suspend fun saveLocally(draft: ProfileDraft): Job? {
        mutex.withLock {
            val row = repository.read() ?: return null
            // An edit on top of an unsynced edit keeps the version the person last saw from the server.
            val base = row.pendingBaseVersion ?: row.version
            repository.savePending(AppJson.encodeToString(ProfileDraft.serializer(), draft), base)
        }
        AppLanguage.fromTag(draft.locale)?.let { languages.set(it) }
        return scope.launch { push() }
    }

    suspend fun push(): SyncOutcome = mutex.withLock {
        val row = repository.read() ?: return@withLock SyncOutcome.NothingToSync
        val draft = row.pendingDraft() ?: return@withLock SyncOutcome.NothingToSync
        if (row.conflictJson != null) return@withLock SyncOutcome.Conflict
        if (row.rejectionJson != null) return@withLock SyncOutcome.Rejected
        val request = ProfileWriteRequest(
            displayName = draft.displayName,
            locale = draft.locale,
            timezone = draft.timezone,
            defaultCurrency = draft.defaultCurrency,
            budgetPeriodStartDay = draft.budgetStartDay,
            version = row.pendingBaseVersion ?: row.version,
        )
        when (val result = repository.push(request)) {
            is ApiResult.Success -> {
                repository.storeServerCopy(result.value)
                repository.clearPending()
                attempt = 0
                SyncOutcome.Synced
            }
            is ApiResult.Failure -> when (result.code) {
                ApiErrorCode.VERSION_CONFLICT -> {
                    val current = result.problem.current
                    if (current == null) {
                        SyncOutcome.Error(result.problem.requestId)
                    } else {
                        repository.storeConflict(current.toString())
                        SyncOutcome.Conflict
                    }
                }
                ApiErrorCode.VALIDATION_FAILED -> {
                    val fields = result.problem.errors.orEmpty()
                        .mapNotNull { e -> ProfileField.fromPointer(e.pointer)?.let { it.name to e.code } }
                        .toMap()
                    repository.storeRejection(AppJson.encodeToString(rejectionSerializer, fields))
                    SyncOutcome.Rejected
                }
                else -> SyncOutcome.Error(result.problem.requestId)
            }
            ApiResult.Offline, ApiResult.Timeout -> {
                scheduleRetry()
                SyncOutcome.Offline
            }
        }
    }

    /** "Keep mine": resend the edit on top of the other device's version. */
    suspend fun keepMine(): SyncOutcome {
        mutex.withLock {
            val conflict = repository.read()?.conflictProfile() ?: return SyncOutcome.NothingToSync
            repository.rebasePending(conflict.version)
        }
        return push()
    }

    /** "Use the other device's": the server copy wins and the edit is dropped. */
    suspend fun useTheirs() {
        mutex.withLock {
            val row = repository.read() ?: return
            val conflict = row.conflictJson?.let { AppJson.decodeFromString(ProfileDto.serializer(), it) } ?: return
            repository.storeServerCopy(conflict)
            repository.clearPending()
            AppLanguage.fromTag(conflict.locale)?.let { languages.set(it) }
        }
    }

    /** Drops a rejected or unwanted edit; the server copy shows again. */
    suspend fun discard() {
        mutex.withLock {
            val row = repository.read() ?: return
            repository.clearPending()
            AppLanguage.fromTag(row.locale)?.let { languages.set(it) }
        }
    }

    private fun scheduleRetry() {
        if (retryJob?.isActive == true) return
        val wait = retryDelays[attempt.coerceAtMost(retryDelays.lastIndex)]
        attempt++
        retryJob = scope.launch {
            delay(wait)
            Logger.d(tag = "sync") { "retrying profile push (attempt $attempt)" }
            // This retry is over once it runs; a failure here must be able to schedule the next one.
            retryJob = null
            push()
        }
    }

    private companion object {
        val rejectionSerializer = MapSerializer(String.serializer(), String.serializer())
    }

    private fun ProfileEntity.pendingDraft(): ProfileDraft? =
        pendingJson?.let { runCatching { AppJson.decodeFromString(ProfileDraft.serializer(), it) }.getOrNull() }

    private fun ProfileEntity.conflictProfile(): Profile? =
        conflictJson?.let { runCatching { AppJson.decodeFromString(ProfileDto.serializer(), it).toDomain() }.getOrNull() }

    private fun ProfileEntity.toView() = ProfileView(
        server = Profile(
            displayName = displayName,
            locale = locale,
            timezoneId = timezone,
            defaultCurrency = defaultCurrency,
            currencyLocked = defaultCurrencyLocked,
            budgetStartDay = budgetPeriodStartDay,
            budgetStartDayFrom = budgetPeriodStartDayFrom?.let { runCatching { LocalDate.parse(it) }.getOrNull() },
            version = version,
        ),
        pending = pendingDraft(),
        conflict = conflictProfile(),
        rejection = rejectionJson
            ?.let { runCatching { AppJson.decodeFromString(rejectionSerializer, it) }.getOrNull() }
            .orEmpty()
            .mapNotNull { (field, code) -> ProfileField.entries.firstOrNull { it.name == field }?.let { it to code } }
            .toMap(),
        refreshedAt = runCatching { Instant.parse(refreshedAt) }.getOrNull(),
    )
}

internal fun ProfileDto.toDomain() = Profile(
    displayName = displayName,
    locale = locale,
    timezoneId = timezone,
    defaultCurrency = defaultCurrency,
    currencyLocked = defaultCurrencyLocked,
    budgetStartDay = budgetPeriodStartDay,
    budgetStartDayFrom = budgetPeriodStartDayFrom?.let { runCatching { LocalDate.parse(it) }.getOrNull() },
    version = version,
)
