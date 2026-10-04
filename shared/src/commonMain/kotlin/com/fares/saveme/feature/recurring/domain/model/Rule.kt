package com.fares.saveme.feature.recurring.domain.model

import com.fares.saveme.feature.transactions.domain.model.TransactionKind
import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable
import kotlin.time.Instant

/**
 * A recurring rule as the phone last had it from the server (a placeholder when [version] is 0). Wire values are kept
 * so an unknown one is never sent back changed. Money is `Long` minor units (P-03); dates are `LocalDate` (P-05).
 */
data class Rule(
    val id: String,
    val walletId: String,
    val presetWire: String,
    val statusWire: String,
    val kindWire: String,
    val amountMinor: Long,
    val amountBasisWire: String,
    val currency: String,
    val categoryId: String?,
    val counterWalletId: String?,
    val description: String?,
    val memo: String?,
    val counterparty: String?,
    val autoConfirm: Boolean,
    val frequencyWire: String,
    val interval: Int,
    val anchorDay: Int?,
    val startDate: LocalDate,
    val endDate: LocalDate?,
    val occurrenceCount: Int?,
    val payoutRound: Int?,
    val payoutCategoryId: String?,
    /** The rule's IANA zone; null until the server accepted the rule. */
    val timezone: String?,
    val createdBy: String?,
    val version: Long,
    val createdAt: Instant,
    val updatedAt: Instant,
    /** Phone-only: the rule this one continues while it is unsent (research R-09). */
    val followsRuleId: String? = null,
) {
    val preset: Preset get() = Preset.fromWire(presetWire)
    val status: RuleStatus get() = RuleStatus.fromWire(statusWire)
    val kind: TransactionKind get() = TransactionKind.fromWire(kindWire)
    val amountBasis: AmountBasis get() = AmountBasis.fromWire(amountBasisWire)
    val frequency: Frequency get() = Frequency.fromWire(frequencyWire)

    /** Never accepted by the server. */
    val isPlaceholder: Boolean get() = version == 0L

    /** A value this app does not know: the rule is read-only and has no preview (research R-15). */
    val hasUnknownValues: Boolean
        get() = preset == Preset.Unknown || status == RuleStatus.Unknown || frequency == Frequency.Unknown ||
            amountBasis == AmountBasis.Unknown || kind == TransactionKind.Unknown

    /** Amounts, notes and people are the person's own; they are never printed (P-09.2). */
    override fun toString() = "Rule($id, version=$version)"
}

/**
 * What the person enters for a rule, and what waits to sync (the whole writable rule). [endDate] and
 * [occurrenceCount] are mutually exclusive ("never" is both null). Preset, kind, currency and destination of an
 * existing rule are always the stored ones (FR-012).
 */
data class RuleDraft(
    val preset: Preset,
    val kind: TransactionKind,
    val amountMinor: Long,
    val amountBasis: AmountBasis = AmountBasis.PerOccurrence,
    val categoryId: String? = null,
    val counterWalletId: String? = null,
    val description: String? = null,
    val memo: String? = null,
    val counterparty: String? = null,
    val autoConfirm: Boolean = false,
    val frequency: Frequency = Frequency.Monthly,
    val interval: Int = 1,
    val anchorDay: Int? = null,
    val startDate: LocalDate,
    val endDate: LocalDate? = null,
    val occurrenceCount: Int? = null,
    val payoutRound: Int? = null,
    val payoutCategoryId: String? = null,
) {
    /** Amounts, notes and people are the person's own; they are never printed (P-09.2). */
    override fun toString() = "RuleDraft($preset)"
}

/** What the transaction editor hands over for "Make recurring" (FR-003). Redacted in [toString] (P-09.2). */
@Serializable
data class RulePrefill(
    val walletId: String,
    val kind: TransactionKind,
    val amountMinor: Long,
    val categoryId: String?,
    val counterWalletId: String?,
    val description: String?,
    val memo: String?,
    val counterparty: String?,
    val startDate: LocalDate,
) {
    override fun toString() = "RulePrefill($walletId)"
}

/** The fields a validation problem or a refusal can point at (contracts/ui-and-e2e.md tags). */
enum class RuleField {
    Preset, Kind, Amount, Basis, Category, Destination, Frequency, Interval, Day, Start, End, Count, PayoutRound, PayoutCategory,
    Note, Details, Person, From, Status;

    companion object {
        fun fromName(name: String): RuleField? = when (name) {
            "amount" -> Amount
            "amountBasis" -> Basis
            "category" -> Category
            "payoutCategory" -> PayoutCategory
            "destination" -> Destination
            "frequency" -> Frequency
            "interval" -> Interval
            "day" -> Day
            "start" -> Start
            "end" -> End
            "count" -> Count
            "payoutRound" -> PayoutRound
            "status" -> Status
            "preset" -> Preset
            "kind" -> Kind
            "note" -> Note
            "details" -> Details
            "person" -> Person
            else -> null
        }
    }
}

/** A problem with one field the person typed, found before anything is queued. */
sealed interface RuleFieldProblem {
    data object Required : RuleFieldProblem
    data object Invalid : RuleFieldProblem
    data class OutOfRange(val min: Long, val max: Long) : RuleFieldProblem
    data class TooLong(val max: Int) : RuleFieldProblem
    data class AmountDigits(val digits: Int) : RuleFieldProblem

    /** A total that gives some round less than one minor unit. */
    data object TotalTooSmall : RuleFieldProblem

    /** The gam'eya payout passes the largest exact amount. */
    data object PayoutTooLarge : RuleFieldProblem

    /** Frozen once a round is resolved (FR-013). */
    data object Frozen : RuleFieldProblem

    /** An end that would cut off a resolved round. */
    data object CutsResolvedRound : RuleFieldProblem

    /** The split date is not after the last resolved round or before the rule's start (FR-014). */
    data object FromOutOfRange : RuleFieldProblem
}

/** Which members of a rule can no longer change because rounds are resolved (FR-013). */
data class FrozenMembers(
    /** Frequency, interval, day, start date, amount basis and payout round. */
    val schedule: Boolean = false,
    /** Amount and count: for a gam'eya or a total-basis rule. */
    val amountAndCount: Boolean = false,
    val lastResolvedDate: LocalDate? = null,
)
