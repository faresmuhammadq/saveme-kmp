package com.fares.saveme.feature.account.domain.deletion

/** Builds the `successors` of a deletion request (spec 016 FR-027). */
object SuccessorChoices {
    /** The most choices one request may carry. */
    const val MAX = 100

    /**
     * One entry for each wallet whose choice was offered, in plan order, using the pick or else the suggestion; at most [MAX].
     * Null when none: the request then leaves `successors` out. Wallets the phone cannot describe are never included.
     */
    fun build(plan: DeletionPlan, picks: Map<String, String>): List<SuccessorChoice>? =
        plan.wallets.asSequence()
            .filter { it.choiceOffered }
            .map { SuccessorChoice(it.walletId, picks[it.walletId]?.takeIf { pick -> it.candidates.any { c -> c.userId == pick } } ?: it.successorUserId) }
            .take(MAX)
            .toList()
            .ifEmpty { null }
}
