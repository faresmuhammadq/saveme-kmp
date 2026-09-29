package com.fares.saveme.feature.account.data.local

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/**
 * The signed-in person's profile: the last server copy, plus a local edit waiting to sync and
 * any conflict or rejection the server answered it with (data-model §1).
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
    @ColumnInfo(name = "pending_json") val pendingJson: String? = null,
    @ColumnInfo(name = "pending_base_version") val pendingBaseVersion: Long? = null,
    @ColumnInfo(name = "conflict_json") val conflictJson: String? = null,
    @ColumnInfo(name = "rejection_json") val rejectionJson: String? = null,
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

    /** Writes the server copy only; a pending edit, conflict or rejection is left alone (P-04.2). */
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

    @Query("UPDATE profile SET pending_json = :json, pending_base_version = :baseVersion, rejection_json = NULL")
    suspend fun setPending(json: String, baseVersion: Long)

    @Query("UPDATE profile SET conflict_json = :json")
    suspend fun setConflict(json: String)

    @Query("UPDATE profile SET rejection_json = :json")
    suspend fun setRejection(json: String)

    @Query("UPDATE profile SET pending_json = NULL, pending_base_version = NULL, conflict_json = NULL, rejection_json = NULL")
    suspend fun clearPending()

    @Query("UPDATE profile SET pending_base_version = :baseVersion, conflict_json = NULL")
    suspend fun rebasePending(baseVersion: Long)
}
