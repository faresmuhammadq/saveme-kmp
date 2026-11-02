package com.fares.saveme.feature.sharedbudgets.data.local

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/**
 * A Shared Budget as the server last said it (backend ADR-0027). One made on this phone has a placeholder row with `version = 0`;
 * changes waiting to sync live in the sync queue. The histories are the server's JSON, kept as text and decoded by the domain.
 * Limits are the person's own figures and are never printed (P-09.2).
 */
@Entity(tableName = "shared_budget")
data class SharedBudgetEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "owner_id") val ownerId: String,
    val name: String,
    val currency: String,
    @ColumnInfo(name = "start_day") val startDay: Int,
    val timezone: String,
    @ColumnInfo(name = "limit_minor") val limitMinor: Long?,
    @ColumnInfo(name = "archived_at") val archivedAt: String?,
    @ColumnInfo(name = "limit_history") val limitHistory: String,
    @ColumnInfo(name = "start_day_history") val startDayHistory: String,
    val version: Long,
    @ColumnInfo(name = "change_seq") val changeSeq: Long,
    @ColumnInfo(name = "created_at") val createdAt: String,
    @ColumnInfo(name = "updated_at") val updatedAt: String,
    @ColumnInfo(name = "refreshed_at") val refreshedAt: String,
) {
    override fun toString() = "SharedBudgetEntity($id, version=$version)"
}

/** A live membership. The person's own is also what makes the budget followed; an ended one is removed, not kept. */
@Entity(tableName = "shared_budget_member", indices = [Index(value = ["shared_budget_id"]), Index(value = ["user_id"])])
data class SharedBudgetMemberEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "shared_budget_id") val sharedBudgetId: String,
    @ColumnInfo(name = "user_id") val userId: String,
    @ColumnInfo(name = "display_name") val displayName: String?,
    val role: String,
    @ColumnInfo(name = "read_only") val readOnly: Boolean,
    val version: Long,
    @ColumnInfo(name = "change_seq") val changeSeq: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: String,
) {
    override fun toString() = "SharedBudgetMemberEntity($id, version=$version)"
}

@Entity(tableName = "shared_budget_line", indices = [Index(value = ["shared_budget_id"])])
data class SharedBudgetLineEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "shared_budget_id") val sharedBudgetId: String,
    @ColumnInfo(name = "category_id") val categoryId: String,
    @ColumnInfo(name = "limit_minor") val limitMinor: Long?,
    val name: String?,
    val icon: String?,
    val color: String?,
    @ColumnInfo(name = "limit_history") val limitHistory: String,
    val version: Long,
    @ColumnInfo(name = "change_seq") val changeSeq: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: String,
) {
    override fun toString() = "SharedBudgetLineEntity($id, version=$version)"
}

/** One transaction's appearance in a budget: no wallet, memo or counterparty. The note and amount are never printed (P-09.2). */
@Entity(tableName = "shared_budget_entry", indices = [Index(value = ["shared_budget_id", "date"])])
data class SharedBudgetEntryEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "shared_budget_id") val sharedBudgetId: String,
    @ColumnInfo(name = "created_by") val createdBy: String,
    @ColumnInfo(name = "category_id") val categoryId: String,
    val date: String,
    @ColumnInfo(name = "amount_minor") val amountMinor: Long,
    val description: String?,
    @ColumnInfo(name = "detached_at") val detachedAt: String?,
    val version: Long,
    @ColumnInfo(name = "change_seq") val changeSeq: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: String,
) {
    override fun toString() = "SharedBudgetEntryEntity($id, version=$version)"
}

@Dao
interface SharedBudgetDao {
    @Query("SELECT * FROM shared_budget WHERE id = :id")
    suspend fun get(id: String): SharedBudgetEntity?

    @Query("SELECT * FROM shared_budget WHERE id = :id")
    fun observe(id: String): Flow<SharedBudgetEntity?>

    @Query("SELECT * FROM shared_budget")
    suspend fun getAll(): List<SharedBudgetEntity>

    /** The budgets the person belongs to, by creation. */
    @Query(
        """SELECT b.* FROM shared_budget b WHERE b.id IN (SELECT shared_budget_id FROM shared_budget_member WHERE user_id = :userId)
        ORDER BY b.created_at, b.id""",
    )
    fun observeMine(userId: String): Flow<List<SharedBudgetEntity>>

    @Upsert
    suspend fun upsert(entity: SharedBudgetEntity)

    @Query("DELETE FROM shared_budget WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM shared_budget")
    suspend fun deleteAll()

    // Members
    @Query("SELECT * FROM shared_budget_member WHERE shared_budget_id = :budgetId ORDER BY role = 'owner' DESC, updated_at, id")
    fun observeMembers(budgetId: String): Flow<List<SharedBudgetMemberEntity>>

    /** The person's own membership rows, one per budget they belong to. */
    @Query("SELECT * FROM shared_budget_member WHERE user_id = :userId")
    fun observeOwnMembers(userId: String): Flow<List<SharedBudgetMemberEntity>>

    /** Entries of every budget dated on or after [from] (ISO dates compare in time order): enough for each budget's current period. */
    @Query("SELECT * FROM shared_budget_entry WHERE date >= :from")
    fun observeEntriesSince(from: String): Flow<List<SharedBudgetEntryEntity>>

    @Query("SELECT * FROM shared_budget_line ORDER BY shared_budget_id, id")
    fun observeAllLines(): Flow<List<SharedBudgetLineEntity>>

    @Query("SELECT * FROM shared_budget_member WHERE shared_budget_id = :budgetId")
    suspend fun members(budgetId: String): List<SharedBudgetMemberEntity>

    @Query("SELECT * FROM shared_budget_member WHERE id = :id")
    suspend fun member(id: String): SharedBudgetMemberEntity?

    @Query("SELECT shared_budget_id FROM shared_budget_member WHERE user_id = :userId")
    suspend fun ownBudgetIds(userId: String): List<String>

    @Query("SELECT * FROM shared_budget_member WHERE shared_budget_id = :budgetId AND user_id = :userId")
    suspend fun ownMember(budgetId: String, userId: String): SharedBudgetMemberEntity?

    @Upsert
    suspend fun upsertMember(entity: SharedBudgetMemberEntity)

    @Query("DELETE FROM shared_budget_member WHERE id = :id")
    suspend fun deleteMember(id: String)

    @Query("DELETE FROM shared_budget_member")
    suspend fun deleteAllMembers()

    // Lines
    @Query("SELECT * FROM shared_budget_line WHERE shared_budget_id = :budgetId ORDER BY id")
    fun observeLines(budgetId: String): Flow<List<SharedBudgetLineEntity>>

    @Query("SELECT * FROM shared_budget_line WHERE shared_budget_id = :budgetId")
    suspend fun lines(budgetId: String): List<SharedBudgetLineEntity>

    @Query("SELECT * FROM shared_budget_line WHERE id = :id")
    suspend fun line(id: String): SharedBudgetLineEntity?

    @Upsert
    suspend fun upsertLine(entity: SharedBudgetLineEntity)

    @Query("DELETE FROM shared_budget_line WHERE id = :id")
    suspend fun deleteLine(id: String)

    @Query("DELETE FROM shared_budget_line")
    suspend fun deleteAllLines()

    // Entries
    @Query("SELECT * FROM shared_budget_entry WHERE shared_budget_id = :budgetId AND date BETWEEN :from AND :to ORDER BY date DESC, id")
    fun observeEntries(budgetId: String, from: String, to: String): Flow<List<SharedBudgetEntryEntity>>

    @Query("SELECT * FROM shared_budget_entry WHERE shared_budget_id = :budgetId")
    suspend fun entries(budgetId: String): List<SharedBudgetEntryEntity>

    @Query("SELECT * FROM shared_budget_entry WHERE id = :id")
    suspend fun entry(id: String): SharedBudgetEntryEntity?

    @Upsert
    suspend fun upsertEntry(entity: SharedBudgetEntryEntity)

    @Query("DELETE FROM shared_budget_entry WHERE id = :id")
    suspend fun deleteEntry(id: String)

    @Query("DELETE FROM shared_budget_entry")
    suspend fun deleteAllEntries()

    @Query("DELETE FROM shared_budget_member WHERE shared_budget_id = :budgetId")
    suspend fun deleteMembersOf(budgetId: String)

    @Query("DELETE FROM shared_budget_line WHERE shared_budget_id = :budgetId")
    suspend fun deleteLinesOf(budgetId: String)

    @Query("DELETE FROM shared_budget_entry WHERE shared_budget_id = :budgetId")
    suspend fun deleteEntriesOf(budgetId: String)

    /** A budget the person no longer belongs to goes with everything under it, in one transaction. */
    @Transaction
    suspend fun deleteBudgetWithChildren(budgetId: String) {
        deleteEntriesOf(budgetId)
        deleteLinesOf(budgetId)
        deleteMembersOf(budgetId)
        delete(budgetId)
    }

    @Transaction
    suspend fun deleteEverything() {
        deleteAllEntries()
        deleteAllLines()
        deleteAllMembers()
        deleteAll()
    }
}
