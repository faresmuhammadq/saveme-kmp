package com.fares.saveme.feature.transactions.domain

import com.fares.saveme.feature.transactions.domain.model.TransactionKind
import com.fares.saveme.feature.wallets.domain.model.WalletFieldProblem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/** What the person typed becomes one adjustment transaction (backend ADR-0028); integer minor units only. */
class BalanceAdjustmentTest {
    private fun resolve(
        text: String,
        mode: AdjustMode = AdjustMode.NewBalance,
        direction: AdjustDirection = AdjustDirection.Raise,
        liability: Boolean = false,
        shown: Long = 10_000,
    ) = BalanceAdjustment.resolve(mode, direction, text, "EGP", liability, shown)

    private fun ready(outcome: AdjustOutcome): AdjustOutcome.Ready = assertIs<AdjustOutcome.Ready>(outcome)

    @Test
    fun aNewBalanceAboveTheShownOneIsAnAdjustmentIn() {
        val outcome = ready(resolve("125.50", shown = 10_000))
        assertEquals(TransactionKind.AdjustmentIn, outcome.adjustment.kind)
        assertEquals(2_550, outcome.adjustment.amountMinor)
        assertEquals(12_550, outcome.balanceAfterMinor)
    }

    @Test
    fun aNewBalanceBelowTheShownOneIsAnAdjustmentOut() {
        val outcome = ready(resolve("75", shown = 10_000))
        assertEquals(TransactionKind.AdjustmentOut, outcome.adjustment.kind)
        assertEquals(2_500, outcome.adjustment.amountMinor)
        assertEquals(7_500, outcome.balanceAfterMinor)
    }

    @Test
    fun anAssetMayBeCorrectedToANegativeBalance() {
        val outcome = ready(resolve("-20", shown = 10_000))
        assertEquals(TransactionKind.AdjustmentOut, outcome.adjustment.kind)
        assertEquals(12_000, outcome.adjustment.amountMinor)
        assertEquals(-2_000, outcome.balanceAfterMinor)
    }

    @Test
    fun theSameBalanceIsNoChange() {
        assertEquals(AdjustOutcome.NoChange, resolve("100", shown = 10_000))
    }

    @Test
    fun aLiabilityIsTypedAsTheAmountOwedAndStoredNegative() {
        // Owes 500.00 (stored -50_000); now owes 300.00: the balance rises by 200.00.
        val outcome = ready(resolve("300", liability = true, shown = -50_000))
        assertEquals(TransactionKind.AdjustmentIn, outcome.adjustment.kind)
        assertEquals(20_000, outcome.adjustment.amountMinor)
        assertEquals(-30_000, outcome.balanceAfterMinor)
        // Owing more lowers it.
        val more = ready(resolve("700", liability = true, shown = -50_000))
        assertEquals(TransactionKind.AdjustmentOut, more.adjustment.kind)
        assertEquals(20_000, more.adjustment.amountMinor)
    }

    @Test
    fun addAndTakeAwayWorkOnTheShownBalance() {
        val add = ready(resolve("25", AdjustMode.Change, AdjustDirection.Raise))
        assertEquals(TransactionKind.AdjustmentIn, add.adjustment.kind)
        assertEquals(2_500, add.adjustment.amountMinor)
        assertEquals(12_500, add.balanceAfterMinor)
        val take = ready(resolve("40", AdjustMode.Change, AdjustDirection.Lower))
        assertEquals(TransactionKind.AdjustmentOut, take.adjustment.kind)
        assertEquals(6_000, take.balanceAfterMinor)
    }

    @Test
    fun forALiabilityOweMoreLowersTheBalanceAndOweLessRaisesIt() {
        val more = ready(resolve("100", AdjustMode.Change, AdjustDirection.Raise, liability = true, shown = -50_000))
        assertEquals(TransactionKind.AdjustmentOut, more.adjustment.kind)
        assertEquals(-60_000, more.balanceAfterMinor)
        val less = ready(resolve("100", AdjustMode.Change, AdjustDirection.Lower, liability = true, shown = -50_000))
        assertEquals(TransactionKind.AdjustmentIn, less.adjustment.kind)
        assertEquals(-40_000, less.balanceAfterMinor)
    }

    @Test
    fun aChangeIsNeverZeroNegativeOrGarbage() {
        assertEquals(AdjustOutcome.NoChange, resolve("0", AdjustMode.Change))
        assertEquals(AdjustOutcome.Invalid(WalletFieldProblem.AmountInvalid), resolve("-5", AdjustMode.Change))
        assertEquals(AdjustOutcome.Invalid(WalletFieldProblem.AmountInvalid), resolve("abc"))
        assertEquals(AdjustOutcome.Invalid(WalletFieldProblem.AmountEmpty), resolve(""))
        assertEquals(AdjustOutcome.Invalid(WalletFieldProblem.AmountDigits(2)), resolve("1.234"))
    }

    @Test
    fun theLargestAmountIsHeldExactlyAndOneMoreIsRefused() {
        val max = 9_007_199_254_740_991L
        val atLimit = ready(resolve("90071992547409.91", AdjustMode.Change, shown = 0))
        assertEquals(max, atLimit.adjustment.amountMinor)
        assertEquals(AdjustOutcome.Invalid(WalletFieldProblem.AmountOutOfRange), resolve("90071992547409.91", AdjustMode.Change, shown = 1))
        assertEquals(AdjustOutcome.Invalid(WalletFieldProblem.AmountOutOfRange), resolve("90071992547409.92", AdjustMode.Change, shown = 0))
    }

    @Test
    fun arabicDigitsAreRead() {
        assertEquals(2_500, ready(resolve("٢٥", AdjustMode.Change)).adjustment.amountMinor)
    }
}
