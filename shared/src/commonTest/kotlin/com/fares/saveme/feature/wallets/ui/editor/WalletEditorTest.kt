package com.fares.saveme.feature.wallets.ui.editor

import app.cash.turbine.test
import com.fares.saveme.core.presentation.runMviTest
import com.fares.saveme.feature.wallets.domain.model.CreateResult
import com.fares.saveme.feature.wallets.domain.model.SaveResult
import com.fares.saveme.feature.wallets.domain.model.SyncState
import com.fares.saveme.feature.wallets.domain.model.Wallet
import com.fares.saveme.feature.wallets.domain.model.WalletDraft
import com.fares.saveme.feature.wallets.domain.model.WalletField
import com.fares.saveme.feature.wallets.domain.model.WalletFieldProblem
import com.fares.saveme.feature.wallets.domain.model.WalletRole
import com.fares.saveme.feature.wallets.domain.model.WalletType
import com.fares.saveme.feature.wallets.domain.model.WalletView
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Instant

class WalletEditorReducerTest {
    private val base = WalletEditorState(loaded = true, currency = "EGP")

    @Test
    fun typeChoiceSwitchesTheAmountToWhatIsOwed() {
        val state = reduce(base, WalletEditorChange.TypeChosen(WalletType.CreditCard))
        assertTrue(state.isLiability)
        assertEquals("credit_card", state.typeWire)
        assertFalse(reduce(state, WalletEditorChange.TypeChosen(WalletType.Cash)).isLiability)
    }

    @Test
    fun theTypeIsReadOnlyWhileEditing() {
        val editing = base.copy(walletId = "w1", type = WalletType.Savings, typeWire = "savings")
        assertFalse(editing.typeEditable)
        assertEquals(editing, reduce(editing, WalletEditorChange.TypeChosen(WalletType.Cash)))
    }

    @Test
    fun aBlankNameShowsItsErrorAndDisablesSave() {
        val typed = reduce(base, WalletEditorChange.Name("Pocket"))
        assertTrue(typed.canSave)
        val cleared = reduce(typed, WalletEditorChange.Name(" "))
        assertEquals(WalletFieldProblem.NameBlank, cleared.errors[WalletField.Name])
        assertFalse(cleared.canSave)
        assertEquals(WalletFieldProblem.NameTooLong, reduce(typed, WalletEditorChange.Name("x".repeat(101))).errors[WalletField.Name])
        assertEquals(null, reduce(cleared, WalletEditorChange.Name("A")).errors[WalletField.Name])
    }

    @Test
    fun theAmountIsRevalidatedWhenTheCurrencyChanges() {
        val typed = reduce(base, WalletEditorChange.Amount("10.55"))
        assertEquals(null, typed.errors[WalletField.OpeningBalance])
        val jpy = reduce(typed, WalletEditorChange.Currency("JPY"))
        assertEquals(WalletFieldProblem.AmountDigits(0), jpy.errors[WalletField.OpeningBalance])
        assertEquals(null, reduce(jpy, WalletEditorChange.Currency("EGP")).errors[WalletField.OpeningBalance])
    }

    @Test
    fun aMinusIsRefusedOnceTheTypeIsALiability() {
        val negative = reduce(base, WalletEditorChange.Amount("-5"))
        assertEquals(null, negative.errors[WalletField.OpeningBalance])
        assertEquals(WalletFieldProblem.AmountInvalid, reduce(negative, WalletEditorChange.TypeChosen(WalletType.Loan)).errors[WalletField.OpeningBalance])
    }
}

class WalletEditorViewModelTest {
    private val created = mutableListOf<WalletDraft>()
    private val saved = mutableListOf<Triple<String, WalletDraft, Long>>()

    private fun vm(
        id: String? = null,
        view: WalletView? = null,
        create: suspend (WalletDraft) -> CreateResult = { created += it; CreateResult.Created("new") },
        save: suspend (String, WalletDraft, Long) -> SaveResult = { i, d, v -> saved += Triple(i, d, v); SaveResult.Saved },
        currency: String? = "USD",
    ) = WalletEditorViewModel(id, { flowOf(view) as Flow<WalletView?> }, { currency }, create, save)

    private fun walletView(locked: Boolean = false, syncState: SyncState = SyncState.None, draft: WalletDraft? = null) = WalletView(
        Wallet("w1", "savings", "Jar", "EGP", 10_000, 10_000, "savings", "#2C7A5B", null, locked, WalletRole.Owner, 7, Instant.parse("2026-09-01T00:00:00Z")),
        draft, false, syncState, 10_000,
    )

    @Test
    fun createModePreselectsTheProfilesCurrency() = runMviTest {
        val vm = vm()
        assertTrue(vm.state.value.loaded)
        assertEquals("USD", vm.state.value.currency)
        assertTrue(vm.state.value.isCreate)
    }

    @Test
    fun savingBuildsTheDraftAndEmitsSaved() = runMviTest {
        val vm = vm()
        vm.onIntent(WalletEditorIntent.NameChanged("  Cash "))
        vm.onIntent(WalletEditorIntent.AmountChanged("250.5"))
        vm.onIntent(WalletEditorIntent.IconChosen("cash"))
        vm.onIntent(WalletEditorIntent.AccentChosen("#2C7A5B"))
        vm.effects.test {
            vm.onIntent(WalletEditorIntent.SaveClicked)
            assertEquals(WalletEditorEffect.Saved("new"), awaitItem())
        }
        assertEquals(WalletDraft("cash", "Cash", "USD", 25_050, "cash", "#2C7A5B"), created.single())
    }

    @Test
    fun aLiabilityStoresWhatIsOwedAsNegative() = runMviTest {
        val vm = vm()
        vm.onIntent(WalletEditorIntent.TypeChosen(WalletType.CreditCard))
        vm.onIntent(WalletEditorIntent.NameChanged("Visa"))
        vm.onIntent(WalletEditorIntent.AmountChanged("1000"))
        vm.onIntent(WalletEditorIntent.SaveClicked)
        assertEquals(-100_000L, created.single().openingBalanceMinor)
    }

    @Test
    fun aBlankNameCannotBeSavedAndABadAmountShowsItsError() = runMviTest {
        val vm = vm()
        vm.onIntent(WalletEditorIntent.SaveClicked)
        assertTrue(created.isEmpty())
        vm.onIntent(WalletEditorIntent.NameChanged("X"))
        vm.onIntent(WalletEditorIntent.SaveClicked)
        assertEquals(WalletFieldProblem.AmountEmpty, vm.state.value.errors[WalletField.OpeningBalance])
        assertTrue(created.isEmpty())
    }

    @Test
    fun gatesBecomeEffects() = runMviTest {
        val unverified = vm(create = { CreateResult.NotVerified })
        unverified.onIntent(WalletEditorIntent.NameChanged("X"))
        unverified.onIntent(WalletEditorIntent.AmountChanged("1"))
        unverified.effects.test {
            unverified.onIntent(WalletEditorIntent.SaveClicked)
            assertEquals(WalletEditorEffect.RequireVerification, awaitItem())
        }
        val blocked = vm(create = { CreateResult.NotEntitled })
        blocked.onIntent(WalletEditorIntent.NameChanged("X"))
        blocked.onIntent(WalletEditorIntent.AmountChanged("1"))
        blocked.effects.test {
            blocked.onIntent(WalletEditorIntent.SaveClicked)
            assertEquals(WalletEditorEffect.NotEntitled, awaitItem())
        }
    }

    @Test
    fun editModeStartsFromTheShownWalletAndSavesOnItsBaseVersion() = runMviTest {
        val vm = vm(id = "w1", view = walletView())
        val state = vm.state.value
        assertEquals("Jar", state.name)
        assertEquals("100.00", state.amountText)
        assertEquals(7L, state.baseVersion)
        assertFalse(state.typeEditable)
        assertEquals("savings", state.typeWire)
        vm.onIntent(WalletEditorIntent.NameChanged("Jar 2"))
        vm.onIntent(WalletEditorIntent.SaveClicked)
        assertEquals(Triple("w1", WalletDraft("savings", "Jar 2", "EGP", 10_000, "savings", "#2C7A5B"), 7L), saved.single())
    }

    @Test
    fun aWaitingChangeIsWhatTheEditorOpensWith() = runMviTest {
        val vm = vm(id = "w1", view = walletView(draft = WalletDraft("savings", "Edited", "EGP", 10_000, null, null, archive = true)))
        assertEquals("Edited", vm.state.value.name)
        assertTrue(vm.state.value.archive)
        vm.onIntent(WalletEditorIntent.SaveClicked)
        assertTrue(saved.single().second.archive)
    }

    @Test
    fun aLockedCurrencyCannotBeChanged() = runMviTest {
        val vm = vm(id = "w1", view = walletView(locked = true))
        assertFalse(vm.state.value.currencyEditable)
        vm.onIntent(WalletEditorIntent.CurrencyClicked)
        assertFalse(vm.state.value.currencyPickerVisible)
        vm.onIntent(WalletEditorIntent.CurrencyChosen("USD"))
        assertEquals("EGP", vm.state.value.currency)
    }

    @Test
    fun refusalFieldErrorsShowNextToTheField() = runMviTest {
        val refused = SyncState.Refused(
            com.fares.saveme.feature.wallets.domain.model.RefusalReason(com.fares.saveme.feature.wallets.domain.model.RefusalKind.Invalid),
            mapOf(WalletField.Currency to "IMMUTABLE_FIELD"),
        )
        val vm = vm(id = "w1", view = walletView(syncState = refused))
        assertEquals(WalletFieldProblem.Server("IMMUTABLE_FIELD"), vm.state.value.errors[WalletField.Currency])
    }

    @Test
    fun aMissingWalletClosesTheEditor() = runMviTest {
        val vm = vm(id = "gone", view = null)
        vm.effects.test { assertEquals(WalletEditorEffect.NavigateBack, awaitItem()) }
    }

    @Test
    fun invalidFromTheDomainShowsItsFieldErrors() = runMviTest {
        val vm = vm(create = { CreateResult.Invalid(mapOf(WalletField.Name to WalletFieldProblem.NameTooLong)) })
        vm.onIntent(WalletEditorIntent.NameChanged("X"))
        vm.onIntent(WalletEditorIntent.AmountChanged("1"))
        vm.onIntent(WalletEditorIntent.SaveClicked)
        assertEquals(WalletFieldProblem.NameTooLong, vm.state.value.errors[WalletField.Name])
        assertFalse(vm.state.value.saving)
    }

    @Test
    fun theCurrencyIsReadOnlyWhileAnUnsentTransactionExistsEvenIfTheServerHasNotLockedIt() = runMviTest {
        val locked = walletView(locked = false).copy(hasPendingPostings = true)
        val vm = vm(id = "w1", view = locked)
        assertTrue(vm.state.value.currencyLocked)
        assertFalse(vm.state.value.currencyEditable)
        vm.onIntent(WalletEditorIntent.CurrencyClicked)
        assertFalse(vm.state.value.currencyPickerVisible)
        assertFalse(vm(id = "w1", view = walletView()).state.value.currencyLocked)
    }
}
