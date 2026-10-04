package com.fares.saveme.feature.budgets.domain

import com.fares.saveme.core.network.AppJson
import com.fares.saveme.core.sync.ChangeState
import com.fares.saveme.core.sync.PendingChange
import com.fares.saveme.feature.budgets.data.BudgetPayload
import com.fares.saveme.feature.budgets.data.dto.BudgetDto
import com.fares.saveme.feature.budgets.data.local.BudgetEntity
import com.fares.saveme.feature.budgets.data.local.BudgetProgressEntity
import com.fares.saveme.feature.budgets.domain.model.Budget
import com.fares.saveme.feature.budgets.domain.model.BudgetDraft
import com.fares.saveme.feature.budgets.domain.model.BudgetField
import com.fares.saveme.feature.budgets.domain.model.RefusalKind
import com.fares.saveme.feature.budgets.domain.model.RefusalReason
import com.fares.saveme.feature.budgets.domain.model.StoredPeriod
import com.fares.saveme.feature.budgets.domain.model.StoredProgress
import com.fares.saveme.feature.budgets.domain.model.SyncState
import kotlinx.datetime.LocalDate
import kotlin.time.Instant

private val epoch = Instant.fromEpochSeconds(0)

private fun parse(value: String?): Instant? = value?.let { runCatching { Instant.parse(it) }.getOrNull() }

private fun parseDate(value: String): LocalDate? = runCatching { LocalDate.parse(value) }.getOrNull()

internal fun BudgetEntity.toBudget() = Budget(
    id = id, categoryId = categoryId, periodTypeWire = periodType, limitMinor = amountMinor, currency = currency,
    archivedAt = parse(archivedAt), version = version, createdAt = parse(createdAt) ?: epoch,
)

internal fun BudgetDto.toBudget() = Budget(
    id = id, categoryId = categoryId, periodTypeWire = periodType, limitMinor = amountMinor, currency = currency,
    archivedAt = parse(archivedAt), version = version, createdAt = parse(createdAt) ?: epoch,
)

internal fun BudgetDraft.toPayload() = BudgetPayload(categoryId, currency, limitMinor, archive)

internal fun BudgetPayload.toDraft() = BudgetDraft(categoryId, currency, amountMinor, archive)

internal fun BudgetDraft.toPayloadJson(): String = AppJson.encodeToString(BudgetPayload.serializer(), toPayload())

/** The waiting content of an edit or create, or null when nothing waits or the change is a deletion. */
internal fun PendingChange?.pendingDraft(): BudgetDraft? =
    this?.takeIf { !isDelete }?.let { decodeDraft(it.payloadJson) }

internal fun decodeDraft(json: String): BudgetDraft? =
    runCatching { AppJson.decodeFromString(BudgetPayload.serializer(), json).toDraft() }.getOrNull()

/** The stored progress row as the pure selection rule wants it; null when its dates cannot be read. */
internal fun BudgetProgressEntity.toStoredProgress(): StoredProgress? {
    val start = parseDate(periodStart) ?: return null
    val end = parseDate(periodEnd) ?: return null
    return StoredProgress(
        period = StoredPeriod(start, end, daysInPeriod),
        currency = currency, limitMinor = limitMinor, spentMinor = spentMinor,
        otherCurrencies = otherCurrencies.split(',').filter { it.isNotEmpty() },
        localSpentMinor = localSpentMinor,
        localOtherCurrencies = localOtherCurrencies.split(',').filter { it.isNotEmpty() },
    )
}

/** Maps the queue row (data-model §7) to what the screens show. */
internal fun PendingChange?.toSyncState(): SyncState = when (this?.state) {
    null -> SyncState.None
    ChangeState.Waiting, ChangeState.Sending -> SyncState.Waiting
    ChangeState.Clash -> {
        val theirs = clashJson?.let { runCatching { AppJson.decodeFromString(BudgetDto.serializer(), it) }.getOrNull() }
        if (theirs == null || theirs.deletedAt != null) SyncState.Clash(null, deletedElsewhere = true, mineIsDeletion = isDelete)
        else SyncState.Clash(theirs.toBudget(), deletedElsewhere = false, mineIsDeletion = isDelete)
    }
    ChangeState.Refused -> {
        val refusal = refusal
        val fields = refusal?.fields.orEmpty().mapNotNull { (name, code) -> BudgetField.fromName(name)?.let { it to code } }.toMap()
        SyncState.Refused(RefusalReason(refusalKind(refusal?.code, fields), refusal?.requestId), fields)
    }
}

/** Chosen by code and pointer only, never by the server's text (P-02.4, FR-007). */
internal fun refusalKind(code: String?, fields: Map<BudgetField, String>): RefusalKind = when (code) {
    "EMAIL_NOT_VERIFIED" -> RefusalKind.NotVerified
    "NOT_FOUND" -> RefusalKind.NoLongerAvailable
    "VALIDATION_FAILED" -> when {
        fields.values.any { it == "IMMUTABLE_FIELD" } -> RefusalKind.Immutable
        fields[BudgetField.Category] == "INVALID_ENUM_VALUE" -> RefusalKind.CategoryUnusable
        fields[BudgetField.Limit] in setOf("OUT_OF_RANGE", "AMOUNT_NOT_INTEGER") -> RefusalKind.LimitInvalid
        fields[BudgetField.Currency] == "INVALID_CURRENCY" -> RefusalKind.CurrencyInvalid
        else -> RefusalKind.Invalid
    }
    "INVALID_ID" -> RefusalKind.Invalid
    else -> RefusalKind.Other
}
