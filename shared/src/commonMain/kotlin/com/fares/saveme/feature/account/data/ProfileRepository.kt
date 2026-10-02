package com.fares.saveme.feature.account.data

import com.fares.saveme.core.database.SyncScopeDao
import com.fares.saveme.core.database.SyncScopeEntity
import com.fares.saveme.core.network.ApiResult
import com.fares.saveme.feature.account.data.dto.MeDto
import com.fares.saveme.feature.account.data.dto.ProfileDto
import com.fares.saveme.feature.account.data.dto.ProfileWriteRequest
import com.fares.saveme.feature.account.data.local.ProfileDao
import com.fares.saveme.feature.account.data.local.ProfileEntity
import kotlinx.coroutines.flow.Flow
import kotlin.time.Clock

/** API calls and the device's copy of the profile. No decisions here (P-10.6). */
class ProfileRepository(
    private val api: AccountApi,
    private val profiles: ProfileDao,
    private val scopes: SyncScopeDao,
    private val clock: Clock = Clock.System,
) {
    fun observe(): Flow<ProfileEntity?> = profiles.observe()

    suspend fun read(): ProfileEntity? = profiles.get()

    suspend fun fetchMe(): ApiResult<MeDto> = api.getMe()

    suspend fun push(request: ProfileWriteRequest): ApiResult<ProfileDto> = api.saveProfile(request)

    /**
     * Writes a copy that arrived without being asked for (a change feed row, `GET /me`): only when its
     * version is the same or newer than the stored one, so an older copy never replaces a newer one
     * (spec 009 FR-004). Returns true when it was written.
     */
    suspend fun storeFeedCopy(dto: ProfileDto): Boolean {
        val stored = profiles.get()
        if (stored != null && stored.userId == dto.userId && stored.version > dto.version) return false
        storeServerCopy(dto)
        return true
    }

    /** Writes the server copy; queued edits live in the sync queue and are untouched (P-04.2). */
    suspend fun storeServerCopy(dto: ProfileDto) {
        val now = clock.now().toString()
        val updated = profiles.updateServerCopy(
            userId = dto.userId,
            id = dto.id,
            version = dto.version,
            displayName = dto.displayName,
            locale = dto.locale,
            timezone = dto.timezone,
            defaultCurrency = dto.defaultCurrency,
            defaultCurrencyLocked = dto.defaultCurrencyLocked,
            budgetPeriodStartDay = dto.budgetPeriodStartDay,
            budgetPeriodStartDayFrom = dto.budgetPeriodStartDayFrom,
            refreshedAt = now,
        )
        if (updated == 0) {
            profiles.upsert(
                ProfileEntity(
                    userId = dto.userId,
                    id = dto.id,
                    version = dto.version,
                    displayName = dto.displayName,
                    locale = dto.locale,
                    timezone = dto.timezone,
                    defaultCurrency = dto.defaultCurrency,
                    defaultCurrencyLocked = dto.defaultCurrencyLocked,
                    budgetPeriodStartDay = dto.budgetPeriodStartDay,
                    budgetPeriodStartDayFrom = dto.budgetPeriodStartDayFrom,
                    refreshedAt = now,
                ),
            )
        }
        scopes.upsert(SyncScopeEntity(SyncScopeEntity.PROFILE, now))
    }
}
