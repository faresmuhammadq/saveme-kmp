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
 * An occurrence as the server last said it. Only the server creates them (ADR-0018), so the phone never
 * holds a placeholder. [localNotice] is phone-only: `already_confirmed` after an accepted clash (research
 * R-06), cleared when the person opens the occurrence.
 */
@Entity(
    tableName = "occurrence",
    indices = [
        Index(value = ["account_id", "wallet_id", "date"]),
        Index(value = ["recurring_rule_id", "date"]),
        Index(value = ["transaction_id"]),
    ],
)
data class OccurrenceEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "account_id") val accountId: String,
    @ColumnInfo(name = "wallet_id") val walletId: String,
    @ColumnInfo(name = "recurring_rule_id") val recurringRuleId: String,
    val ordinal: Int,
    @ColumnInfo(name = "is_payout") val isPayout: Boolean,
    @ColumnInfo(name = "scheduled_date") val scheduledDate: String,
    val date: String,
    val status: String,
    val kind: String,
    @ColumnInfo(name = "amount_minor") val amountMinor: Long,
    val currency: String,
    @ColumnInfo(name = "category_id") val categoryId: String?,
    @ColumnInfo(name = "counter_wallet_id") val counterWalletId: String?,
    val description: String?,
    val memo: String?,
    val counterparty: String?,
    val edited: Boolean,
    @ColumnInfo(name = "transaction_id") val transactionId: String?,
    @ColumnInfo(name = "auto_confirm_refusal") val autoConfirmRefusal: String?,
    val version: Long,
    @ColumnInfo(name = "change_seq") val changeSeq: Long,
    @ColumnInfo(name = "created_at") val createdAt: String,
    @ColumnInfo(name = "updated_at") val updatedAt: String,
    @ColumnInfo(name = "refreshed_at") val refreshedAt: String,
    @ColumnInfo(name = "local_notice") val localNotice: String? = null,
) {
    /** Amounts, notes and people are the person's own; they are never printed (P-09.2). */
    override fun toString() = "OccurrenceEntity($id, version=$version)"
}

@Dao
interface OccurrenceDao {
    @Query("SELECT * FROM occurrence WHERE account_id = :accountId AND wallet_id = :walletId ORDER BY date, ordinal, id")
    fun observeForWallet(accountId: String, walletId: String): Flow<List<OccurrenceEntity>>

    @Query("SELECT * FROM occurrence WHERE account_id = :accountId ORDER BY date, ordinal, id")
    fun observeAll(accountId: String): Flow<List<OccurrenceEntity>>

    @Query("SELECT * FROM occurrence WHERE recurring_rule_id = :ruleId ORDER BY date, ordinal, id")
    fun observeForRule(ruleId: String): Flow<List<OccurrenceEntity>>

    @Query("SELECT * FROM occurrence WHERE id = :id")
    fun observe(id: String): Flow<OccurrenceEntity?>

    /**
     * What reports need (spec 014 R-05): the occurrences the server still calls `scheduled`, in a wallet of [walletIds] or
     * with one of them as the destination, dated in [from]..[to], plus those a waiting change names ([waitingIds]; a change
     * may move one in or out of the range). The overlay is the caller's.
     */
    @Query(
        """SELECT * FROM occurrence WHERE account_id = :accountId AND (wallet_id IN (:walletIds) OR counter_wallet_id IN (:walletIds))
        AND ((status = 'scheduled' AND date >= :from AND date <= :to) OR id IN (:waitingIds))
        ORDER BY date, ordinal, id""",
    )
    fun observeScheduledFor(accountId: String, walletIds: List<String>, from: String, to: String, waitingIds: List<String>): Flow<List<OccurrenceEntity>>

    @Query("SELECT * FROM occurrence WHERE id = :id")
    suspend fun get(id: String): OccurrenceEntity?

    @Query("SELECT * FROM occurrence WHERE transaction_id = :transactionId")
    suspend fun getByTransactionId(transactionId: String): List<OccurrenceEntity>

    @Query("SELECT * FROM occurrence WHERE recurring_rule_id = :ruleId")
    suspend fun getForRule(ruleId: String): List<OccurrenceEntity>

    @Query("SELECT * FROM occurrence WHERE wallet_id = :walletId")
    suspend fun getForWallet(walletId: String): List<OccurrenceEntity>

    @Upsert
    suspend fun upsert(entity: OccurrenceEntity)

    @Query("UPDATE occurrence SET local_notice = :notice WHERE id = :id")
    suspend fun setNotice(id: String, notice: String?)

    @Query("DELETE FROM occurrence WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM occurrence WHERE recurring_rule_id = :ruleId")
    suspend fun deleteForRule(ruleId: String)

    @Query("DELETE FROM occurrence WHERE wallet_id = :walletId")
    suspend fun deleteForWallet(walletId: String)

    @Query("DELETE FROM occurrence")
    suspend fun deleteAll()
}
