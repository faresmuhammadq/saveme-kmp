package com.fares.saveme.feature.transactions.ui.adjust

import app.cash.turbine.test
import com.fares.saveme.core.presentation.runMviTest
import com.fares.saveme.feature.transactions.domain.AdjustDirection
import com.fares.saveme.feature.transactions.domain.AdjustMode
import com.fares.saveme.feature.transactions.domain.model.AdjustTarget
import com.fares.saveme.feature.transactions.domain.model.CreateResult
import com.fares.saveme.feature.transactions.domain.model.TransactionDraft
import com.fares.saveme.feature.transactions.domain.model.TransactionFieldProblem
import com.fares.saveme.feature.transactions.domain.model.TransactionKind
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The "Adjust balance" screen's store (backend ADR-0028): what it records, and when it refuses to. */
class BalanceAdjustViewModelTest {
    private val today = LocalDate(2026, 11, 3)
    private val recorded = mutableListOf<TransactionDraft>()

    private fun target(balance: Long = 10_000, liability: Boolean = false, canAdjust: Boolean = true) =
        AdjustTarget("w1", "Cash", "EGP", liability, balance, canAdjust)

    private fun store(
        target: Flow<AdjustTarget?> = flowOf(target()),
        result: CreateResult = CreateResult.Created("t1"),
    ) = BalanceAdjustViewModel("w1", { target }, { flowOf(null) }, { today }, { draft -> recorded += draft; result })

    @Test
    fun aTypedNewBalanceIsRecordedAsADatedAdjustmentWithNoCategory() = runMviTest {
        val store = store()
        store.onIntent(BalanceAdjustIntent.AmountChanged("75"))
        store.onIntent(BalanceAdjustIntent.NoteChanged("  cash count  "))
        assertTrue(store.state.value.canSave)
        store.effects.test {
            store.onIntent(BalanceAdjustIntent.SaveClicked)
            assertEquals(BalanceAdjustEffect.Close, awaitItem())
        }
        val draft = recorded.single()
        assertEquals(TransactionKind.AdjustmentOut.wire, draft.kindWire)
        assertEquals(2_500, draft.amountMinor)
        assertEquals(today, draft.date)
        assertEquals("w1", draft.walletId)
        assertEquals("EGP", draft.currency)
        assertNull(draft.categoryId)
        assertNull(draft.counterWalletId)
        assertEquals("cash count", draft.description)
    }

    @Test
    fun addingAnAmountRaisesTheBalanceAndChangingModeClearsTheText() = runMviTest {
        val store = store()
        store.onIntent(BalanceAdjustIntent.ModeChosen(AdjustMode.Change))
        store.onIntent(BalanceAdjustIntent.AmountChanged("12.50"))
        store.onIntent(BalanceAdjustIntent.DirectionChosen(AdjustDirection.Raise))
        store.onIntent(BalanceAdjustIntent.SaveClicked)
        assertEquals(TransactionKind.AdjustmentIn.wire, recorded.single().kindWire)
        assertEquals(1_250, recorded.single().amountMinor)
        store.onIntent(BalanceAdjustIntent.ModeChosen(AdjustMode.NewBalance))
        assertEquals("", store.state.value.amountText)
    }

    @Test
    fun anEmptyOrUnchangedAmountIsRefusedWithoutACall() = runMviTest {
        val store = store()
        store.onIntent(BalanceAdjustIntent.SaveClicked)
        assertEquals(TransactionFieldProblem.AmountEmpty, store.state.value.problem)
        store.onIntent(BalanceAdjustIntent.AmountChanged("100"))
        assertNull(store.state.value.problem)
        store.onIntent(BalanceAdjustIntent.SaveClicked)
        assertEquals(TransactionFieldProblem.AmountNotPositive, store.state.value.problem)
        assertTrue(recorded.isEmpty())
    }

    @Test
    fun aViewerOrAnArchivedWalletCannotBeAdjusted() = runMviTest {
        val store = store(flowOf(target(canAdjust = false)))
        store.onIntent(BalanceAdjustIntent.AmountChanged("75"))
        assertFalse(store.state.value.canSave)
        store.onIntent(BalanceAdjustIntent.SaveClicked)
        assertTrue(recorded.isEmpty())
    }

    @Test
    fun aWalletThatLeavesThePhoneClosesTheScreen() = runMviTest {
        val store = store(flowOf(null))
        store.effects.test {
            assertEquals(BalanceAdjustEffect.ShowMessage(AdjustMessage.NoLongerAvailable), awaitItem())
            assertEquals(BalanceAdjustEffect.Close, awaitItem())
        }
    }

    @Test
    fun anUnverifiedAccountIsSentToVerify() = runMviTest {
        val store = store(result = CreateResult.NotVerified)
        store.onIntent(BalanceAdjustIntent.AmountChanged("75"))
        store.effects.test {
            store.onIntent(BalanceAdjustIntent.SaveClicked)
            assertEquals(BalanceAdjustEffect.RequireVerification, awaitItem())
        }
    }
}
