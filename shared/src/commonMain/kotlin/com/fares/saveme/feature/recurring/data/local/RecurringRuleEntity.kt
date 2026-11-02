package com.fares.saveme.feature.recurring.data.local

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/**
 * A recurring rule as the server last said it (research R-03). A rule made on this phone has a placeholder
 * row with `version = 0`; changes waiting to sync live in the sync queue. [followsRuleId] is phone-only: the
 * rule this one continues after "Change from a date" while it is unsent (R-09).
 */
@Entity(
    tableName = "recurring_rule",
    indices = [
        Index(value = ["account_id", "wallet_id", "status"]),
        Index(value = ["follows_rule_id"]),
    ],
)
data class RecurringRuleEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "account_id") val accountId: String,
    @ColumnInfo(name = "wallet_id") val walletId: String,
    val preset: String,
    val status: String,
    val kind: String,
    @ColumnInfo(name = "amount_minor") val amountMinor: Long,
    @ColumnInfo(name = "amount_basis") val amountBasis: String,
    val currency: String,
    @ColumnInfo(name = "category_id") val categoryId: String?,
    @ColumnInfo(name = "counter_wallet_id") val counterWalletId: String?,
    val description: String?,
    val memo: String?,
    val counterparty: String?,
    @ColumnInfo(name = "auto_confirm") val autoConfirm: Boolean,
    val frequency: String,
    val interval: Int,
    @ColumnInfo(name = "anchor_day") val anchorDay: Int?,
    @ColumnInfo(name = "start_date") val startDate: String,
    @ColumnInfo(name = "end_date") val endDate: String?,
    @ColumnInfo(name = "occurrence_count") val occurrenceCount: Int?,
    @ColumnInfo(name = "payout_round") val payoutRound: Int?,
    @ColumnInfo(name = "payout_category_id") val payoutCategoryId: String?,
    val timezone: String?,
    @ColumnInfo(name = "created_by") val createdBy: String?,
    val version: Long,
    @ColumnInfo(name = "change_seq") val changeSeq: Long,
    @ColumnInfo(name = "created_at") val createdAt: String,
    @ColumnInfo(name = "updated_at") val updatedAt: String,
    @ColumnInfo(name = "refreshed_at") val refreshedAt: String,
    @ColumnInfo(name = "follows_rule_id") val followsRuleId: String? = null,
    /** The Shared Budget every expense of this rule counts toward (backend ADR-0027); null for none. */
    @ColumnInfo(name = "shared_budget_id") val sharedBudgetId: String? = null,
) {
    /** Amounts, notes and people are the person's own; they are never printed (P-09.2). */
    override fun toString() = "RecurringRuleEntity($id, version=$version)"
}

@Dao
interface RecurringRuleDao {
    @Query("SELECT * FROM recurring_rule WHERE account_id = :accountId AND wallet_id = :walletId ORDER BY created_at, id")
    fun observeForWallet(accountId: String, walletId: String): Flow<List<RecurringRuleEntity>>

    @Query("SELECT * FROM recurring_rule WHERE account_id = :accountId ORDER BY created_at, id")
    fun observeAll(accountId: String): Flow<List<RecurringRuleEntity>>

    @Query("SELECT * FROM recurring_rule WHERE id = :id")
    fun observe(id: String): Flow<RecurringRuleEntity?>

    @Query("SELECT * FROM recurring_rule WHERE id = :id")
    suspend fun get(id: String): RecurringRuleEntity?

    @Query("SELECT * FROM recurring_rule WHERE wallet_id = :walletId")
    suspend fun getForWallet(walletId: String): List<RecurringRuleEntity>

    /** The unsent rules that continue [ruleId] (research R-09). */
    @Query("SELECT * FROM recurring_rule WHERE follows_rule_id = :ruleId")
    suspend fun getFollowing(ruleId: String): List<RecurringRuleEntity>

    @Upsert
    suspend fun upsert(entity: RecurringRuleEntity)

    @Query("DELETE FROM recurring_rule WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM recurring_rule WHERE wallet_id = :walletId")
    suspend fun deleteForWallet(walletId: String)

    @Query("DELETE FROM recurring_rule")
    suspend fun deleteAll()
}
