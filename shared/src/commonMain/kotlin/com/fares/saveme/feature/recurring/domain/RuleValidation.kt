package com.fares.saveme.feature.recurring.domain

import com.fares.saveme.feature.recurring.domain.model.AmountBasis
import com.fares.saveme.feature.recurring.domain.model.ConfirmDraft
import com.fares.saveme.feature.recurring.domain.model.ConfirmField
import com.fares.saveme.feature.recurring.domain.model.Frequency
import com.fares.saveme.feature.recurring.domain.model.Preset
import com.fares.saveme.feature.recurring.domain.model.RuleDraft
import com.fares.saveme.feature.recurring.domain.model.RuleField
import com.fares.saveme.feature.recurring.domain.model.RuleFieldProblem
import com.fares.saveme.feature.recurring.domain.schedule.Allocation
import com.fares.saveme.feature.transactions.domain.DefaultTransactionsService
import com.fares.saveme.feature.transactions.domain.codePointLength
import com.fares.saveme.feature.transactions.domain.model.TransactionKind
import kotlinx.datetime.LocalDate

/**
 * The server's rules for a rule and for one occurrence's values, applied before anything is queued (FR-005, FR-010,
 * contracts/recurring-domain.md). Pure: no I/O, no clock.
 */
internal object RuleValidation {
    val MIN_DATE = LocalDate(1900, 1, 1)
    val MAX_DATE = LocalDate(2999, 12, 31)
    const val MAX_INTERVAL = 365
    const val MAX_COUNT = 1000
    const val PRESET_MIN_COUNT = 2
    const val PRESET_MAX_COUNT = 120

    private fun String?.trimmedOrNull(): String? = this?.trim()?.takeIf { it.isNotEmpty() }

    /**
     * Trims texts, drops what does not apply to the kind and the preset, fixes what a preset fixes (FR-010) and gives a
     * monthly or yearly rule its anchor day (the start date's day when none was chosen); daily and weekly rules have none.
     */
    fun clean(draft: RuleDraft): RuleDraft {
        var cleaned = draft.copy(description = draft.description.trimmedOrNull(), memo = draft.memo.trimmedOrNull(), counterparty = draft.counterparty.trimmedOrNull())
        val gameya = cleaned.preset == Preset.Gameya
        if (gameya) cleaned = cleaned.copy(kind = TransactionKind.Expense)
        val transfer = cleaned.kind == TransactionKind.Transfer
        cleaned = cleaned.copy(
            categoryId = cleaned.categoryId?.takeIf { !transfer && it.isNotBlank() },
            counterWalletId = cleaned.counterWalletId?.takeIf { transfer && it.isNotBlank() },
            payoutRound = cleaned.payoutRound.takeIf { gameya },
            payoutCategoryId = cleaned.payoutCategoryId?.takeIf { gameya && it.isNotBlank() },
        )
        if (cleaned.preset == Preset.Gameya || cleaned.preset == Preset.Installment) {
            cleaned = cleaned.copy(frequency = Frequency.Monthly, interval = 1, endDate = null)
        }
        return cleaned.copy(
            anchorDay = when (cleaned.frequency) {
                Frequency.Monthly, Frequency.Yearly -> cleaned.anchorDay ?: cleaned.startDate.day
                Frequency.Daily, Frequency.Weekly -> null
                Frequency.Unknown -> cleaned.anchorDay
            },
        )
    }

    /**
     * @param destinations the wallet ids a transfer may go to; null when the destination is the stored one and is not
     *   checked again (an existing rule).
     */
    fun validate(draft: RuleDraft, creating: Boolean, destinations: Set<String>?): Map<RuleField, RuleFieldProblem> {
        val problems = linkedMapOf<RuleField, RuleFieldProblem>()
        val preset = draft.preset
        val presetShaped = preset == Preset.Gameya || preset == Preset.Installment
        if (creating && preset == Preset.Unknown) problems[RuleField.Preset] = RuleFieldProblem.Invalid
        if (draft.kind == TransactionKind.Unknown) problems[RuleField.Kind] = RuleFieldProblem.Invalid
        if (preset == Preset.Installment && draft.kind == TransactionKind.Income) problems[RuleField.Kind] = RuleFieldProblem.Invalid
        if (draft.frequency == Frequency.Unknown) problems[RuleField.Frequency] = RuleFieldProblem.Invalid
        if (draft.amountBasis == AmountBasis.Unknown) problems[RuleField.Basis] = RuleFieldProblem.Invalid

        if (draft.amountMinor !in 1..Allocation.MAX_MINOR) problems[RuleField.Amount] = RuleFieldProblem.OutOfRange(1, Allocation.MAX_MINOR)
        if (draft.interval !in 1..MAX_INTERVAL) problems[RuleField.Interval] = RuleFieldProblem.OutOfRange(1, MAX_INTERVAL.toLong())
        val anchor = draft.anchorDay
        if ((draft.frequency == Frequency.Monthly || draft.frequency == Frequency.Yearly) && anchor == null) {
            problems[RuleField.Day] = RuleFieldProblem.Required
        } else if (anchor != null && anchor !in 1..31) {
            problems[RuleField.Day] = RuleFieldProblem.OutOfRange(1, 31)
        }

        if (draft.startDate < MIN_DATE || draft.startDate > MAX_DATE) {
            problems[RuleField.Start] = RuleFieldProblem.OutOfRange(MIN_DATE.year.toLong(), MAX_DATE.year.toLong())
        }
        val end = draft.endDate
        val count = draft.occurrenceCount
        if (end != null && count != null) {
            problems[RuleField.End] = RuleFieldProblem.Invalid
        } else if (end != null) {
            if (end < MIN_DATE || end > MAX_DATE) {
                problems[RuleField.End] = RuleFieldProblem.OutOfRange(MIN_DATE.year.toLong(), MAX_DATE.year.toLong())
            } else if (end < draft.startDate) {
                problems[RuleField.End] = RuleFieldProblem.Invalid
            }
        }
        if (presetShaped) {
            if (count == null) problems[RuleField.Count] = RuleFieldProblem.Required
        }
        if (count != null) {
            val min = if (presetShaped) PRESET_MIN_COUNT else 1
            val max = if (presetShaped) PRESET_MAX_COUNT else MAX_COUNT
            if (count !in min..max) problems[RuleField.Count] = RuleFieldProblem.OutOfRange(min.toLong(), max.toLong())
        }
        if (draft.amountBasis == AmountBasis.Total) {
            if (count == null) {
                problems[RuleField.Basis] = RuleFieldProblem.Invalid
            } else if (RuleField.Amount !in problems && count in 1..MAX_COUNT && Allocation.split(draft.amountMinor, count) == null) {
                problems[RuleField.Amount] = RuleFieldProblem.TotalTooSmall
            }
        }

        if (preset == Preset.Gameya) {
            val round = draft.payoutRound
            if (round == null) {
                problems[RuleField.PayoutRound] = RuleFieldProblem.Required
            } else if (count != null && round !in 1..count) {
                problems[RuleField.PayoutRound] = RuleFieldProblem.OutOfRange(1, count.toLong())
            }
            if (draft.payoutCategoryId == null) problems[RuleField.PayoutCategory] = RuleFieldProblem.Required
            if (RuleField.Amount !in problems && Allocation.payout(draft.amountBasis, draft.amountMinor, count) == null) {
                problems[RuleField.Amount] = RuleFieldProblem.PayoutTooLarge
            }
        }

        when (draft.kind) {
            TransactionKind.Income, TransactionKind.Expense ->
                if (draft.categoryId == null) problems[RuleField.Category] = RuleFieldProblem.Required
            TransactionKind.Transfer -> {
                val destination = draft.counterWalletId
                if (destination == null) {
                    problems[RuleField.Destination] = RuleFieldProblem.Required
                } else if (destinations != null && destination !in destinations) {
                    problems[RuleField.Destination] = RuleFieldProblem.Invalid
                }
            }
            TransactionKind.AdjustmentIn, TransactionKind.AdjustmentOut, TransactionKind.Unknown -> Unit
        }
        textProblems(draft.description, draft.memo, draft.counterparty).forEach { (field, problem) ->
            problems[RuleField.valueOf(field)] = problem
        }
        return problems
    }

    private fun textProblems(note: String?, details: String?, person: String?): Map<String, RuleFieldProblem> {
        val problems = linkedMapOf<String, RuleFieldProblem>()
        if (codePointLength(note) > DefaultTransactionsService.MAX_NOTE) problems["Note"] = RuleFieldProblem.TooLong(DefaultTransactionsService.MAX_NOTE)
        if (codePointLength(details) > DefaultTransactionsService.MAX_DETAILS) problems["Details"] = RuleFieldProblem.TooLong(DefaultTransactionsService.MAX_DETAILS)
        if (codePointLength(person) > DefaultTransactionsService.MAX_PERSON) problems["Person"] = RuleFieldProblem.TooLong(DefaultTransactionsService.MAX_PERSON)
        return problems
    }

    /** Trims the form's texts and drops a category a transfer cannot have. */
    fun cleanConfirm(kind: TransactionKind, draft: ConfirmDraft): ConfirmDraft = draft.copy(
        categoryId = draft.categoryId?.takeIf { kind != TransactionKind.Transfer && it.isNotBlank() },
        description = draft.description.trimmedOrNull(),
        memo = draft.memo.trimmedOrNull(),
        counterparty = draft.counterparty.trimmedOrNull(),
    )

    fun validateConfirm(kind: TransactionKind, draft: ConfirmDraft): Map<ConfirmField, RuleFieldProblem> {
        val problems = linkedMapOf<ConfirmField, RuleFieldProblem>()
        if (draft.amountMinor !in 1..Allocation.MAX_MINOR) problems[ConfirmField.Amount] = RuleFieldProblem.OutOfRange(1, Allocation.MAX_MINOR)
        if (draft.date < MIN_DATE || draft.date > MAX_DATE) {
            problems[ConfirmField.Date] = RuleFieldProblem.OutOfRange(MIN_DATE.year.toLong(), MAX_DATE.year.toLong())
        }
        if ((kind == TransactionKind.Income || kind == TransactionKind.Expense) && draft.categoryId == null) {
            problems[ConfirmField.Category] = RuleFieldProblem.Required
        }
        textProblems(draft.description, draft.memo, draft.counterparty).forEach { (field, problem) ->
            problems[ConfirmField.valueOf(field)] = problem
        }
        return problems
    }
}
