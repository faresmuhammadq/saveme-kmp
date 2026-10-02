package com.fares.saveme.feature.account.domain

import com.fares.saveme.core.locale.AppLanguage
import com.fares.saveme.core.locale.AppLanguageStore
import com.fares.saveme.core.network.AppJson
import com.fares.saveme.core.sync.ChangeState
import com.fares.saveme.core.sync.PendingChange
import com.fares.saveme.core.sync.ScopeKind
import com.fares.saveme.core.sync.SyncEngine
import com.fares.saveme.core.sync.SyncRunner
import com.fares.saveme.core.sync.SyncScope
import com.fares.saveme.feature.account.data.ProfileRepository
import com.fares.saveme.feature.account.data.ProfileSyncHandler
import com.fares.saveme.feature.account.data.dto.ProfileDto
import com.fares.saveme.feature.account.data.local.ProfileEntity
import com.fares.saveme.feature.account.domain.model.Profile
import com.fares.saveme.feature.account.domain.model.ProfileDraft
import com.fares.saveme.feature.account.domain.model.ProfileField
import com.fares.saveme.feature.account.domain.model.ProfileView
import com.fares.saveme.feature.account.domain.model.SyncOutcome
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlin.time.Instant

/**
 * Profile edits are saved on the device first and sent with the last server version
 * (P-04.2, P-04.4). Since spec 004 the shared sync engine owns the queue, retries, clashes and
 * refusals; this class keeps the profile's own API so its screen is unchanged (FR-019).
 */
class ProfileSyncer(
    private val repository: ProfileRepository,
    private val languages: AppLanguageStore,
    private val engine: SyncEngine,
    private val scope: CoroutineScope,
    private val runner: SyncRunner,
) {
    @OptIn(ExperimentalCoroutinesApi::class)
    val view: Flow<ProfileView?> = repository.observe().flatMapLatest { row ->
        if (row == null) flowOf(null) else engine.observe(RECORD_TYPE, row.id).map { change -> row.toView(change) }
    }

    /**
     * Saves on the device (works offline) and applies the language at once. The send starts here
     * too, in the syncer's own scope so leaving the screen does not stop it; the engine's
     * background work covers a closed app. Returns that send.
     */
    suspend fun saveLocally(draft: ProfileDraft): Job? {
        val row = repository.read() ?: return null
        engine.enqueue(
            recordType = RECORD_TYPE,
            recordId = row.id,
            scope = SyncScope(ScopeKind.User, row.userId),
            baseVersion = row.version,
            payloadJson = AppJson.encodeToString(ProfileDraft.serializer(), draft),
        )
        AppLanguage.fromTag(draft.locale)?.let { languages.set(it) }
        return scope.launch { runner.upload() }
    }

    /** True while an edit of the profile is still only on this phone (waiting, clashing or refused). */
    suspend fun hasPendingEdit(): Boolean {
        val row = repository.read() ?: return false
        return engine.get(RECORD_TYPE, row.id) != null
    }

    /** Sends the profile's waiting edit now and says how it went. */
    suspend fun push(): SyncOutcome {
        val row = repository.read() ?: return SyncOutcome.NothingToSync
        val before = engine.get(RECORD_TYPE, row.id) ?: return SyncOutcome.NothingToSync
        when (before.state) {
            ChangeState.Clash -> return SyncOutcome.Conflict
            ChangeState.Refused -> return SyncOutcome.Rejected
            else -> Unit
        }
        runner.upload()
        val after = engine.get(RECORD_TYPE, row.id) ?: return SyncOutcome.Synced
        return when (after.state) {
            ChangeState.Clash -> SyncOutcome.Conflict
            ChangeState.Refused -> SyncOutcome.Rejected
            ChangeState.Waiting, ChangeState.Sending -> SyncOutcome.Offline
        }
    }

    /** "Keep mine": resend the edit on top of the other device's version. */
    suspend fun keepMine(): SyncOutcome {
        val row = repository.read() ?: return SyncOutcome.NothingToSync
        engine.keepMine(RECORD_TYPE, row.id)
        return push()
    }

    /** "Use the other device's": the server copy wins and the edit is dropped. */
    suspend fun useTheirs() {
        val row = repository.read() ?: return
        engine.useTheirs(RECORD_TYPE, row.id)
    }

    /** Drops a rejected or unwanted edit; the server copy shows again. */
    suspend fun discard() {
        val row = repository.read() ?: return
        engine.discard(RECORD_TYPE, row.id)
    }

    private fun ProfileEntity.toView(change: PendingChange?) = ProfileView(
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
        pending = change?.payloadJson?.let { runCatching { AppJson.decodeFromString(ProfileDraft.serializer(), it) }.getOrNull() },
        conflict = change?.takeIf { it.state == ChangeState.Clash }?.clashJson
            ?.let { runCatching { AppJson.decodeFromString(ProfileDto.serializer(), it).toDomain() }.getOrNull() },
        rejection = change?.takeIf { it.state == ChangeState.Refused }?.refusal?.fields.orEmpty()
            .mapNotNull { (field, code) -> ProfileField.entries.firstOrNull { it.name == field }?.let { it to code } }
            .toMap(),
        refreshedAt = runCatching { Instant.parse(refreshedAt) }.getOrNull(),
    )

    private companion object {
        const val RECORD_TYPE = ProfileSyncHandler.RECORD_TYPE
    }
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
