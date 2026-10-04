package com.fares.saveme.feature.recurring.domain.model

sealed interface RuleResult {
    data class Created(val id: String) : RuleResult
    data object Saved : RuleResult
    data object NotVerified : RuleResult
    data object NotAllowed : RuleResult
    data object NoLongerAvailable : RuleResult

    /** An ended rule, a rule with values this app does not know, or a viewer: nothing may change. */
    data object ReadOnly : RuleResult
    data class Invalid(val errors: Map<RuleField, RuleFieldProblem>) : RuleResult
}

sealed interface OccurrenceResult {
    data object Done : OccurrenceResult
    data object NotVerified : OccurrenceResult
    data object NotAllowed : OccurrenceResult
    data object NoLongerAvailable : OccurrenceResult

    /** Confirmed (final), an unknown status, or a rule without received occurrences. */
    data object ReadOnly : OccurrenceResult
    data class Invalid(val errors: Map<ConfirmField, RuleFieldProblem>) : OccurrenceResult
}

/** The confirm form's fields. */
enum class ConfirmField { Date, Amount, Category, Note, Details, Person }

sealed interface CatchUpOutcome {
    data object Done : CatchUpOutcome
    data object NeedsConnection : CatchUpOutcome
    data object NotAllowed : CatchUpOutcome
    data object NotVerified : CatchUpOutcome
    data class Failed(val requestId: String?) : CatchUpOutcome
}

/** The two record types the clash and refusal choices apply to. */
enum class RecurringRecord(val recordType: String) {
    Rule("recurring_rule"),
    Occurrence("occurrence"),
}
