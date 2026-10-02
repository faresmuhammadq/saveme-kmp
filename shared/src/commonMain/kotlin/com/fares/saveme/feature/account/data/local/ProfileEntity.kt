package com.fares.saveme.feature.account.data.local

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/**
 * The signed-in person's profile: the last server copy. A local edit waiting to sync, and any
 * clash or refusal, live in the sync queue (spec 004, `pending_change`).
 */
@Entity(tableName = "profile")
data class ProfileEntity(
    @PrimaryKey @ColumnInfo(name = "user_id") val userId: String,
    val id: String,
    val version: Long,
    @ColumnInfo(name = "display_name") val displayName: String?,
    val locale: String,
    val timezone: String,
    @ColumnInfo(name = "default_currency") val defaultCurrency: String,
    @ColumnInfo(name = "default_currency_locked") val defaultCurrencyLocked: Boolean,
    @ColumnInfo(name = "budget_period_start_day") val budgetPeriodStartDay: Int,
    @ColumnInfo(name = "budget_period_start_day_from") val budgetPeriodStartDayFrom: String?,
    @ColumnInfo(name = "refreshed_at") val refreshedAt: String,
)

@Dao
interface ProfileDao {
    @Query("SELECT * FROM profile LIMIT 1")
    fun observe(): Flow<ProfileEntity?>

    @Query("SELECT * FROM profile LIMIT 1")
    suspend fun get(): ProfileEntity?

    @Upsert
    suspend fun upsert(entity: ProfileEntity)

    @Query("DELETE FROM profile")
    suspend fun deleteAll()

    /** Writes the server copy only; queued edits live elsewhere and are never touched (P-04.2). */
    @Query(
        """UPDATE profile SET id = :id, version = :version, display_name = :displayName, locale = :locale,
        timezone = :timezone, default_currency = :defaultCurrency, default_currency_locked = :defaultCurrencyLocked,
        budget_period_start_day = :budgetPeriodStartDay, budget_period_start_day_from = :budgetPeriodStartDayFrom,
        refreshed_at = :refreshedAt WHERE user_id = :userId""",
    )
    suspend fun updateServerCopy(
        userId: String,
        id: String,
        version: Long,
        displayName: String?,
        locale: String,
        timezone: String,
        defaultCurrency: String,
        defaultCurrencyLocked: Boolean,
        budgetPeriodStartDay: Int,
        budgetPeriodStartDayFrom: String?,
        refreshedAt: String,
    ): Int
}
