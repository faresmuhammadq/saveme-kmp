package com.fares.saveme.feature.budgets.domain.model

import kotlin.time.Instant

/** The server's copy of a budget, as last read. Money is `Long` minor units (P-03). */
data class Budget(
    val id: String,
    /** Null = all expenses. Immutable. */
    val categoryId: String?,
    /** The wire value, kept so an unknown type is never sent back changed (research R-15). */
    val periodTypeWire: String,
    val limitMinor: Long,
    val currency: String,
    val archivedAt: Instant?,
    /** 0 = a budget made on this phone that never reached the server. */
    val version: Long,
    val createdAt: Instant,
) {
    val isMonthly: Boolean get() = periodTypeWire == MONTHLY

    fun toDraft() = BudgetDraft(categoryId, currency, limitMinor, archive = archivedAt != null)

    /** The limit is a person's own figure; it is never printed (P-09.2). */
    override fun toString() = "Budget($id, version=$version)"

    companion object {
        const val MONTHLY = "monthly"
    }
}

/**
 * The whole writable budget, as the person edits it and as it waits to sync. [archive] archives after saving;
 * archiving is one-way, so a draft never sets it back to false once true.
 */
data class BudgetDraft(
    val categoryId: String?,
    val currency: String,
    val limitMinor: Long,
    val archive: Boolean = false,
) {
    override fun toString() = "BudgetDraft"
}

/** Members the editor and the server's validation errors can point at. */
enum class BudgetField(val fieldName: String) {
    Category("category"),
    Limit("limit"),
    Currency("currency"),
    Period("period"),
    ;

    companion object {
        /** The plain field name the data layer puts in a refusal. */
        fun fromName(name: String?): BudgetField? = entries.firstOrNull { it.fieldName == name }
    }
}

/** A problem with one field the person typed, or one the server reported. */
sealed interface BudgetFieldProblem {
    data object LimitEmpty : BudgetFieldProblem
    data object LimitInvalid : BudgetFieldProblem
    data object LimitNotPositive : BudgetFieldProblem
    data object LimitOutOfRange : BudgetFieldProblem
    data class LimitDigits(val digits: Int) : BudgetFieldProblem

    /** The category (or "All expenses") already has an active budget in this currency (FR-004). */
    data object AlreadyBudgeted : BudgetFieldProblem

    /** The server's own error code for the field (IMMUTABLE_FIELD, OUT_OF_RANGE, …). */
    data class Server(val code: String) : BudgetFieldProblem
}

sealed interface CreateResult {
    data class Created(val id: String) : CreateResult
    data object NotVerified : CreateResult
    data class Invalid(val errors: Map<BudgetField, BudgetFieldProblem>) : CreateResult
    data object AlreadyBudgeted : CreateResult
}

sealed interface SaveResult {
    data object Saved : SaveResult
    data object NotVerified : SaveResult
    data object NoLongerAvailable : SaveResult

    /** A budget of a period type this version does not know is read-only (research R-15). */
    data object ReadOnly : SaveResult
    data class Invalid(val errors: Map<BudgetField, BudgetFieldProblem>) : SaveResult
}
