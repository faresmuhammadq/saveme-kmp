package com.fares.saveme.feature.transactions.domain.model

import kotlinx.datetime.LocalDate
import kotlin.time.Instant

/** The kinds of transactions.yaml; a value this app doesn't know is [Unknown] and stays as the server sent it. */
enum class TransactionKind(val wire: String) {
    Income("income"),
    Expense("expense"),
    Transfer("transfer"),
    AdjustmentIn("adjustment_in"),
    AdjustmentOut("adjustment_out"),
    Unknown(""),
    ;

    /** A balance adjustment: raises or lowers a wallet's balance, never spending or income (ADR-0028). */
    val isAdjustment: Boolean get() = this == AdjustmentIn || this == AdjustmentOut

    companion object {
        /** The kinds a person can choose when recording. */
        val selectable: List<TransactionKind> = listOf(Expense, Income, Transfer)

        fun fromWire(value: String): TransactionKind = entries.firstOrNull { it != Unknown && it.wire == value } ?: Unknown
    }
}

/** The server's copy of a transaction, as last read. Money is `Long` minor units (P-03). */
data class Transaction(
    val id: String,
    val walletId: String,
    val counterWalletId: String?,
    val kindWire: String,
    val date: LocalDate,
    val amountMinor: Long,
    val currency: String,
    val categoryId: String?,
    val description: String?,
    val memo: String?,
    val counterparty: String?,
    val cleared: Boolean,
    val createdBy: String,
    val recurringRuleId: String?,
    val occurrenceId: String?,
    val version: Long,
    val createdAt: Instant,
    val updatedAt: Instant,
    val sharedBudgetId: String? = null,
) {
    val kind: TransactionKind get() = TransactionKind.fromWire(kindWire)

    fun toDraft() = TransactionDraft(
        walletId = walletId, kindWire = kindWire, date = date, amountMinor = amountMinor, currency = currency,
        categoryId = categoryId, counterWalletId = counterWalletId, description = description, memo = memo,
        counterparty = counterparty, cleared = cleared, sharedBudgetId = sharedBudgetId,
    )
}

/** What the person entered (and what waits to sync). [kindWire] keeps an unknown kind unchanged. */
data class TransactionDraft(
    val walletId: String,
    val kindWire: String,
    val date: LocalDate,
    val amountMinor: Long,
    val currency: String,
    val categoryId: String?,
    val counterWalletId: String?,
    val description: String?,
    val memo: String?,
    val counterparty: String?,
    val cleared: Boolean,
    /** The Shared Budget it counts toward, or null (backend ADR-0027). */
    val sharedBudgetId: String? = null,
) {
    val kind: TransactionKind get() = TransactionKind.fromWire(kindWire)
}

/** The fields a validation problem or a refusal can point at (data-model §3). */
enum class TransactionField { Wallet, Kind, Amount, Date, Category, Destination, Currency, Note, Details, Person, Cleared, Budget;

    companion object {
        fun fromName(name: String): TransactionField? = when (name) {
            "amount" -> Amount
            "date" -> Date
            "category" -> Category
            "destination" -> Destination
            "currency" -> Currency
            "kind" -> Kind
            "note" -> Note
            "details" -> Details
            "person" -> Person
            "cleared" -> Cleared
            "budget" -> Budget
            else -> null
        }
    }
}

/** A problem with one field the person typed, or one the server reported. */
sealed interface TransactionFieldProblem {
    data object AmountEmpty : TransactionFieldProblem
    data object AmountNotPositive : TransactionFieldProblem
    data object AmountInvalid : TransactionFieldProblem
    data object AmountOutOfRange : TransactionFieldProblem
    data class AmountDigits(val digits: Int) : TransactionFieldProblem
    data object DateOutOfRange : TransactionFieldProblem
    data object CategoryRequired : TransactionFieldProblem
    data object DestinationRequired : TransactionFieldProblem
    data object KindUnknown : TransactionFieldProblem
    data class TooLong(val max: Int) : TransactionFieldProblem

    /** The server's own error code for the field (REQUIRED, OUT_OF_RANGE, IMMUTABLE_FIELD, …). */
    data class Server(val code: String) : TransactionFieldProblem
}

/** A wallet the person can record in or transfer to. */
data class WalletChoice(val id: String, val name: String, val currency: String)

/**
 * The wallet a balance adjustment is made in, as the phone shows it: [balanceMinor] counts unsent changes, and for a
 * card or loan is stored negative (P-04.4). [canAdjust] is false for a viewer or an archived wallet.
 */
data class AdjustTarget(
    val walletId: String,
    val name: String,
    val currency: String,
    val isLiability: Boolean,
    val balanceMinor: Long,
    val canAdjust: Boolean,
)
