package com.fares.saveme.feature.transactions.domain

import com.fares.saveme.core.network.AppJson
import com.fares.saveme.core.sync.ChangeState
import com.fares.saveme.core.sync.PendingChange
import com.fares.saveme.feature.transactions.data.BalanceEffect
import com.fares.saveme.feature.transactions.data.dto.TransactionDto
import com.fares.saveme.feature.transactions.data.dto.TransactionPayload
import com.fares.saveme.feature.transactions.data.local.TransactionEntity
import com.fares.saveme.feature.transactions.domain.model.AddedBy
import com.fares.saveme.feature.transactions.domain.model.Counterpart
import com.fares.saveme.feature.transactions.domain.model.Direction
import com.fares.saveme.feature.transactions.domain.model.RefusalKind
import com.fares.saveme.feature.transactions.domain.model.RefusalReason
import com.fares.saveme.feature.transactions.domain.model.Transaction
import com.fares.saveme.feature.transactions.domain.model.TransactionDraft
import com.fares.saveme.feature.transactions.domain.model.TransactionField
import com.fares.saveme.feature.transactions.domain.model.TransactionKind
import com.fares.saveme.feature.transactions.domain.model.TransactionSyncState
import com.fares.saveme.feature.transactions.domain.model.TransactionView
import kotlinx.datetime.LocalDate
import kotlin.time.Instant

private val epoch = Instant.fromEpochSeconds(0)

private fun parseInstant(value: String?): Instant = value?.let { runCatching { Instant.parse(it) }.getOrNull() } ?: epoch

private fun parseDate(value: String): LocalDate = runCatching { LocalDate.parse(value) }.getOrDefault(LocalDate(1970, 1, 1))

internal fun TransactionEntity.toTransaction() = Transaction(
    id = id, walletId = walletId, counterWalletId = counterWalletId, kindWire = kind, date = parseDate(date),
    amountMinor = amountMinor, currency = currency, categoryId = categoryId, description = description, memo = memo,
    counterparty = counterparty, cleared = cleared, createdBy = createdBy, recurringRuleId = recurringRuleId,
    occurrenceId = occurrenceId, version = version, createdAt = parseInstant(createdAt), updatedAt = parseInstant(updatedAt),
)

/** The row a server copy the phone no longer holds would have been (the save of a removed transaction). */
internal fun Transaction.toEntity(accountId: String, refreshedAt: String) = TransactionEntity(
    id = id, accountId = accountId, walletId = walletId, counterWalletId = counterWalletId, kind = kindWire,
    date = date.toString(), amountMinor = amountMinor, currency = currency, categoryId = categoryId,
    description = description, memo = memo, counterparty = counterparty, cleared = cleared, createdBy = createdBy,
    recurringRuleId = recurringRuleId, occurrenceId = occurrenceId, version = version, changeSeq = 0,
    createdAt = createdAt.toString(), updatedAt = updatedAt.toString(), deletedAt = null, refreshedAt = refreshedAt,
)

internal fun TransactionDto.toTransaction() = Transaction(
    id = id, walletId = walletId, counterWalletId = counterWalletId, kindWire = kind, date = parseDate(date),
    amountMinor = amountMinor, currency = currency, categoryId = categoryId, description = description, memo = memo,
    counterparty = counterparty, cleared = cleared, createdBy = createdBy, recurringRuleId = recurringRuleId,
    occurrenceId = occurrenceId, version = version, createdAt = parseInstant(createdAt), updatedAt = parseInstant(updatedAt),
)

internal fun TransactionDraft.toPayload() = TransactionPayload(
    kind = kindWire, date = date.toString(), amountMinor = amountMinor, currency = currency, categoryId = categoryId,
    counterWalletId = counterWalletId, description = description, memo = memo, counterparty = counterparty, cleared = cleared,
)

internal fun TransactionPayload.toDraft(walletId: String) = TransactionDraft(
    walletId = walletId, kindWire = kind, date = parseDate(date), amountMinor = amountMinor, currency = currency,
    categoryId = categoryId, counterWalletId = counterWalletId, description = description, memo = memo,
    counterparty = counterparty, cleared = cleared,
)

internal fun TransactionDraft.toPayloadJson(): String = AppJson.encodeToString(TransactionPayload.serializer(), toPayload())

internal fun decodePayload(json: String): TransactionPayload? =
    runCatching { AppJson.decodeFromString(TransactionPayload.serializer(), json) }.getOrNull()

/** The waiting content of an edit or create, or null when nothing waits or the change is a deletion. */
internal fun PendingChange?.pendingPayload(): TransactionPayload? =
    this?.takeIf { !isDelete }?.let { decodePayload(it.payloadJson) }

/**
 * Maps the queue row (research R-07) to what the screens show. [kindWire] chooses whether a
 * `FORBIDDEN` means "no right in the other wallet" (a transfer) or "no right here".
 */
internal fun PendingChange?.toSyncState(kindWire: String): TransactionSyncState = when (this?.state) {
    null -> TransactionSyncState.None
    ChangeState.Waiting, ChangeState.Sending -> TransactionSyncState.Waiting
    ChangeState.Clash -> {
        val theirs = clashJson?.let { runCatching { AppJson.decodeFromString(TransactionDto.serializer(), it) }.getOrNull() }
        if (theirs == null || theirs.deletedAt != null) {
            TransactionSyncState.Clash(null, deletedElsewhere = true, mineIsDeletion = isDelete)
        } else {
            TransactionSyncState.Clash(theirs.toTransaction(), deletedElsewhere = false, mineIsDeletion = isDelete)
        }
    }
    ChangeState.Refused -> {
        val refusal = refusal
        val fields = refusal?.fields.orEmpty().mapNotNull { (name, code) -> TransactionField.fromName(name)?.let { it to code } }.toMap()
        TransactionSyncState.Refused(RefusalReason(refusalKind(refusal?.code, fields, kindWire), refusal?.requestId), fields)
    }
}

/** Chosen by code and pointer only, never by the server's text (P-02.4, research R-07). */
internal fun refusalKind(code: String?, fields: Map<TransactionField, String>, kindWire: String): RefusalKind = when (code) {
    "EMAIL_NOT_VERIFIED" -> RefusalKind.NotVerified
    "NOT_FOUND" -> RefusalKind.NoLongerAvailable
    "FORBIDDEN" -> if (kindWire == BalanceEffect.TRANSFER) RefusalKind.NoRightInOtherWallet else RefusalKind.NoRightHere
    "VALIDATION_FAILED" -> when {
        fields[TransactionField.Category] == "INVALID_ENUM_VALUE" -> RefusalKind.CategoryUnusable
        fields[TransactionField.Destination] == "CURRENCY_MISMATCH" -> RefusalKind.CurrenciesDiffer
        fields[TransactionField.Destination] in setOf("INVALID_ENUM_VALUE", "REQUIRED") -> RefusalKind.DestinationUnavailable
        fields[TransactionField.Currency] == "CURRENCY_MISMATCH" -> RefusalKind.CurrencyChanged
        else -> RefusalKind.Invalid
    }
    else -> RefusalKind.Other
}

/** Money in, money out, or neither, from [walletId]'s side. */
internal fun directionIn(draft: TransactionDraft, walletId: String): Direction {
    val effect = BalanceEffect.of(draft.kindWire, draft.amountMinor, draft.walletId, draft.counterWalletId, deleted = false, forWallet = walletId)
    return when {
        effect > 0 -> Direction.In
        effect < 0 -> Direction.Out
        else -> Direction.None
    }
}

internal fun addedByOf(createdBy: String, accountId: String?, memberNames: Map<String, String?>): AddedBy = when {
    createdBy.isBlank() || createdBy == accountId -> AddedBy.You
    else -> memberNames[createdBy]?.takeIf { it.isNotBlank() }?.let { AddedBy.Member(it) }
        // A departed member's row is gone from the wallet's members; with none held at all we cannot tell (FR-033).
        ?: if (memberNames.isNotEmpty() && createdBy !in memberNames) AddedBy.FormerMember else AddedBy.AnotherMember
}

internal fun counterpartOf(draft: TransactionDraft, viewedWalletId: String, walletNames: Map<String, String>): Counterpart? {
    if (draft.kind != TransactionKind.Transfer) return null
    val other = if (draft.walletId == viewedWalletId) draft.counterWalletId else draft.walletId
    return other?.let { walletNames[it] }?.let { Counterpart.Named(it) } ?: Counterpart.Unknown
}
