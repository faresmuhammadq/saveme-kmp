package com.fares.saveme.feature.transactions.domain

import com.fares.saveme.feature.transactions.domain.model.TransactionKind
import com.fares.saveme.feature.wallets.domain.AmountInput
import com.fares.saveme.feature.wallets.domain.model.WalletFieldProblem
import kotlin.math.abs

/** How the person describes the correction: the balance they now have, or an amount to add or take away. */
enum class AdjustMode { NewBalance, Change }

/** Raises or lowers the figure the person sees: the balance, or for a card or loan the amount owed. */
enum class AdjustDirection { Raise, Lower }

/** The wallet's kind of adjustment and its positive amount; the person never types a sign (backend ADR-0028). */
data class Adjustment(val kind: TransactionKind, val amountMinor: Long)

sealed interface AdjustOutcome {
    data class Ready(val adjustment: Adjustment, val balanceAfterMinor: Long) : AdjustOutcome

    /** The typed new balance equals the shown one. */
    data object NoChange : AdjustOutcome

    data class Invalid(val problem: WalletFieldProblem) : AdjustOutcome
}

/**
 * Turns what the person typed into the adjustment transaction to record (backend ADR-0028). Pure, integer minor
 * units only (P-03). A liability is stored negative and typed as the amount owed, so owing more lowers the balance.
 */
object BalanceAdjustment {

    /**
     * @param shownBalanceMinor the wallet's balance as the phone shows it, unsent changes included
     * @param typed the text of the amount field: the new balance (a leading minus is allowed for an asset) or the change
     */
    fun resolve(
        mode: AdjustMode,
        direction: AdjustDirection,
        typed: String,
        currency: String,
        isLiability: Boolean,
        shownBalanceMinor: Long,
    ): AdjustOutcome = when (mode) {
        AdjustMode.NewBalance -> newBalance(typed, currency, isLiability, shownBalanceMinor)
        AdjustMode.Change -> change(direction, typed, currency, isLiability, shownBalanceMinor)
    }

    private fun newBalance(typed: String, currency: String, isLiability: Boolean, shown: Long): AdjustOutcome =
        when (val parsed = AmountInput.parse(typed, currency, isLiability)) {
            is AmountInput.Result.Invalid -> AdjustOutcome.Invalid(parsed.problem)
            is AmountInput.Result.Valid -> {
                val target = parsed.minor
                val delta = target - shown
                when {
                    delta == 0L -> AdjustOutcome.NoChange
                    abs(delta) > AmountInput.MAX_MINOR -> AdjustOutcome.Invalid(WalletFieldProblem.AmountOutOfRange)
                    else -> AdjustOutcome.Ready(Adjustment(kindOf(delta > 0), abs(delta)), target)
                }
            }
        }

    private fun change(direction: AdjustDirection, typed: String, currency: String, isLiability: Boolean, shown: Long): AdjustOutcome =
        when (val parsed = AmountInput.parse(typed, currency, liability = false)) {
            is AmountInput.Result.Invalid -> AdjustOutcome.Invalid(parsed.problem)
            is AmountInput.Result.Valid -> when {
                parsed.minor == 0L -> AdjustOutcome.NoChange
                parsed.minor < 0L -> AdjustOutcome.Invalid(WalletFieldProblem.AmountInvalid)
                else -> {
                    val raisesBalance = (direction == AdjustDirection.Raise) != isLiability
                    val signed = if (raisesBalance) parsed.minor else -parsed.minor
                    val after = shown + signed
                    if (after > AmountInput.MAX_MINOR || after < -AmountInput.MAX_MINOR) {
                        AdjustOutcome.Invalid(WalletFieldProblem.AmountOutOfRange)
                    } else {
                        AdjustOutcome.Ready(Adjustment(kindOf(raisesBalance), parsed.minor), after)
                    }
                }
            }
        }

    private fun kindOf(raisesBalance: Boolean) = if (raisesBalance) TransactionKind.AdjustmentIn else TransactionKind.AdjustmentOut
}
