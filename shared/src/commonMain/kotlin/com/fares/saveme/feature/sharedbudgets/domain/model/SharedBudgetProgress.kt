package com.fares.saveme.feature.sharedbudgets.domain.model

import com.fares.saveme.feature.budgets.domain.model.BudgetPeriod

/** One category of a Shared Budget with what has been spent in it this period. Amounts are never printed in logs (P-09.2). */
data class LineProgress(
    val categoryId: String,
    /** Empty when the line carries no name: the screen shows its own placeholder. */
    val name: String,
    val iconKey: String?,
    val colorHex: String?,
    /** Null: the line has no limit of its own. */
    val limitMinor: Long?,
    val spentMinor: Long,
) {
    override fun toString() = "LineProgress"
}

/** What one member spent in the budget this period; [name] is null for someone who has left ("Former member"). */
data class MemberTotal(val userId: String, val name: String?, val isMe: Boolean, val spentMinor: Long) {
    override fun toString() = "MemberTotal"
}

/**
 * One Shared Budget's figures for the current period, computed on the phone from the entries it holds (backend ADR-0027). The
 * limits are the ones that apply on the day: a change applies from the current period and earlier periods keep theirs.
 */
data class SharedBudgetProgress(
    val id: String,
    val name: String,
    val currency: String,
    val period: BudgetPeriod,
    /** Null: no overall limit. */
    val limitMinor: Long?,
    val spentMinor: Long,
    val projectedMinor: Long?,
    val lines: List<LineProgress>,
    /** Spent in categories the list does not name (the platform's Other, a removed line). */
    val unlistedMinor: Long,
    val members: List<MemberTotal>,
    val isOwner: Boolean,
    val archived: Boolean,
    /** When the phone last heard from the server about this budget; null until it has. */
    val refreshedAt: String?,
) {
    override fun toString() = "SharedBudgetProgress($id)"
}
