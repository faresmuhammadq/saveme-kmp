package com.fares.saveme.feature.recurring.domain

import com.fares.saveme.core.sync.PendingChange
import com.fares.saveme.core.sync.SyncEngine
import com.fares.saveme.feature.recurring.data.OccurrenceRepository
import com.fares.saveme.feature.recurring.data.OccurrenceSyncHandler
import com.fares.saveme.feature.recurring.data.local.OccurrenceEntity
import com.fares.saveme.feature.recurring.domain.model.OccurrenceStatus
import com.fares.saveme.feature.recurring.domain.model.decodeOccurrencePayload
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.datetime.LocalDate

/** One occurrence still scheduled, as reports count it (amounts are never printed, P-09.2). */
data class ScheduledLine(
    val occurrenceId: String,
    val kind: String,
    val date: LocalDate,
    val amountMinor: Long,
    val currency: String,
    val walletId: String,
    val counterWalletId: String?,
    val categoryId: String?,
) {
    override fun toString() = "ScheduledLine($occurrenceId)"
}

/**
 * What reports need from recurring without importing it (spec 014 research R-05, P-10.4): the occurrences the phone holds
 * that are still scheduled in some wallets and a range, the waiting changes overlaid.
 */
interface ScheduledMoney {
    fun observe(walletIds: Set<String>, from: LocalDate, to: LocalDate): Flow<List<ScheduledLine>>

    /** Whether an occurrence change touching these wallets waits, with its stored or its shown date in the range (FR-002). */
    fun observeWaiting(walletIds: Set<String>, from: LocalDate, to: LocalDate): Flow<Boolean>
}

object NoScheduledMoney : ScheduledMoney {
    override fun observe(walletIds: Set<String>, from: LocalDate, to: LocalDate): Flow<List<ScheduledLine>> = flowOf(emptyList())
    override fun observeWaiting(walletIds: Set<String>, from: LocalDate, to: LocalDate): Flow<Boolean> = flowOf(false)
}

/**
 * Occurrences the server calls `scheduled`, with the queue laid over them: a waiting confirmation or skip (or a deletion)
 * removes the occurrence, a waiting edit's date, amount and category apply. A confirmation made on this phone is an actual
 * transaction on the transactions side, never counted twice.
 *
 * @param engine resolved on use: the engine is built from every sync handler (spec 006 lesson).
 */
class OccurrenceScheduledMoney(
    private val engine: () -> SyncEngine,
    private val repository: OccurrenceRepository,
    private val accountId: () -> String?,
) : ScheduledMoney {

    private class Built(val lines: List<ScheduledLine>, val waiting: Boolean)

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun built(walletIds: Set<String>, from: LocalDate, to: LocalDate): Flow<Built> {
        val account = accountId()
        if (account == null || walletIds.isEmpty()) return flowOf(Built(emptyList(), false))
        return engine().observeAll(OccurrenceSyncHandler.RECORD_TYPE).flatMapLatest { changes ->
            repository.observeScheduledFor(account, walletIds, from.toString(), to.toString(), changes.map { it.recordId })
                .map { rows -> build(walletIds, from, to, changes, rows) }
        }
    }

    override fun observe(walletIds: Set<String>, from: LocalDate, to: LocalDate): Flow<List<ScheduledLine>> =
        built(walletIds, from, to).map { it.lines }

    override fun observeWaiting(walletIds: Set<String>, from: LocalDate, to: LocalDate): Flow<Boolean> =
        built(walletIds, from, to).map { it.waiting }

    private fun build(walletIds: Set<String>, from: LocalDate, to: LocalDate, changes: List<PendingChange>, rows: List<OccurrenceEntity>): Built {
        val changeById = changes.associateBy { it.recordId }
        val lines = mutableListOf<ScheduledLine>()
        var waiting = false

        fun inRange(date: LocalDate) = date >= from && date <= to

        for (row in rows) {
            if (row.walletId !in walletIds && (row.counterWalletId == null || row.counterWalletId !in walletIds)) continue
            val storedDate = parseDate(row.date)
            val change = changeById[row.id]
            if (change == null) {
                if (row.status == OccurrenceStatus.Scheduled.wire && inRange(storedDate)) lines += line(row, storedDate, row.amountMinor, row.categoryId)
                continue
            }
            val payload = if (change.isDelete) null else decodeOccurrencePayload(change.payloadJson)
            val shownDate = payload?.date?.let { parseDate(it) } ?: storedDate
            if (inRange(storedDate) || inRange(shownDate)) waiting = true
            if (payload != null && OccurrenceStatus.fromWire(payload.status) == OccurrenceStatus.Scheduled && inRange(shownDate)) {
                lines += line(row, shownDate, payload.amountMinor, payload.categoryId)
            }
        }
        return Built(lines, waiting)
    }

    private fun line(row: OccurrenceEntity, date: LocalDate, amountMinor: Long, categoryId: String?) = ScheduledLine(
        row.id, row.kind, date, amountMinor, row.currency, row.walletId, row.counterWalletId, categoryId,
    )

    private fun parseDate(value: String): LocalDate = runCatching { LocalDate.parse(value) }.getOrDefault(LocalDate(1970, 1, 1))
}
