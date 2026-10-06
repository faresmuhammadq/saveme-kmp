package com.fares.saveme.feature.account.ui.export

import app.cash.turbine.test
import com.fares.saveme.core.network.ApiErrorCode
import com.fares.saveme.core.presentation.runMviTest
import com.fares.saveme.feature.account.domain.export.ExportScope
import com.fares.saveme.feature.account.domain.export.RequestOutcome
import com.fares.saveme.feature.account.ui.export.request.ExportRequestEffect
import com.fares.saveme.feature.account.ui.export.request.ExportRequestIntent
import com.fares.saveme.feature.account.ui.export.request.ExportRequestMessage
import com.fares.saveme.feature.account.ui.export.request.ExportRequestViewModel
import com.fares.saveme.feature.account.ui.export.request.WalletChoice
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceTimeBy
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

class ExportRequestViewModelTest {

    private val wallets = flowOf(listOf(WalletChoice("w1", "Home", false), WalletChoice("w2", "Old", true)))

    @Test
    fun defaultsToEverythingAndToTheWalletWhenPreset() = runMviTest {
        val account = ExportRequestViewModel(request = { _, _ -> RequestOutcome.Offline }, wallets = wallets)
        assertEquals(ExportScope.Account, account.state.value.scope)
        assertTrue(account.state.value.canSubmit)
        val preset = ExportRequestViewModel(request = { _, _ -> RequestOutcome.Offline }, wallets = wallets, presetWalletId = "w1")
        assertEquals(ExportScope.Wallet, preset.state.value.scope)
        assertEquals("w1", preset.state.value.chosenWallet?.id)
    }

    @Test
    fun oneRequestPerTapAndNoneWhileInFlight() = runMviTest {
        val gate = CompletableDeferred<RequestOutcome>()
        var calls = 0
        val vm = ExportRequestViewModel(request = { _, _ -> calls++; gate.await() })
        vm.onIntent(ExportRequestIntent.Submit)
        vm.onIntent(ExportRequestIntent.Submit)
        assertEquals(1, calls)
        assertTrue(vm.state.value.submitting)
        assertFalse(vm.state.value.canSubmit)
        vm.effects.test {
            gate.complete(RequestOutcome.Created(anExport("new")))
            assertEquals(ExportRequestEffect.OpenDetail("new"), awaitItem())
        }
        assertFalse(vm.state.value.submitting)
    }

    @Test
    fun aWalletRequestNeedsAWalletAndSendsIt() = runMviTest {
        val sent = mutableListOf<Pair<ExportScope, String?>>()
        val vm = ExportRequestViewModel(request = { scope, id -> sent += scope to id; RequestOutcome.Offline }, wallets = wallets)
        vm.onIntent(ExportRequestIntent.ScopeChosen(ExportScope.Wallet))
        assertFalse(vm.state.value.canSubmit)
        vm.onIntent(ExportRequestIntent.Submit)
        assertEquals(emptyList(), sent)
        vm.onIntent(ExportRequestIntent.WalletFieldClicked)
        assertTrue(vm.state.value.walletPickerVisible)
        vm.onIntent(ExportRequestIntent.WalletChosen("w2"))
        assertFalse(vm.state.value.walletPickerVisible)
        vm.onIntent(ExportRequestIntent.Submit)
        assertEquals<List<Pair<ExportScope, String?>>>(listOf(ExportScope.Wallet to "w2"), sent)
    }

    @Test
    fun anAccountRequestNeverSendsAWallet() = runMviTest {
        val sent = mutableListOf<String?>()
        val vm = ExportRequestViewModel(request = { _, id -> sent += id; RequestOutcome.Offline }, wallets = wallets, presetWalletId = "w1")
        vm.onIntent(ExportRequestIntent.ScopeChosen(ExportScope.Account))
        vm.onIntent(ExportRequestIntent.Submit)
        assertEquals(listOf<String?>(null), sent)
    }

    @Test
    fun unsentChangesAreSaidFromTheSyncStatus() = runMviTest {
        val unsent = MutableStateFlow(false)
        val vm = ExportRequestViewModel(request = { _, _ -> RequestOutcome.Offline }, unsentChanges = unsent)
        assertFalse(vm.state.value.unsentChanges)
        unsent.value = true
        assertTrue(vm.state.value.unsentChanges)
    }

    @Test
    fun inProgressOffersSeeItOnTheCurrentExportOrTheList() = runMviTest {
        var outcome: RequestOutcome = RequestOutcome.InProgress(anExport("run1"))
        val vm = ExportRequestViewModel(request = { _, _ -> outcome })
        vm.effects.test {
            vm.onIntent(ExportRequestIntent.Submit)
            assertEquals(ExportRequestMessage.InProgress("run1"), vm.state.value.message)
            vm.onIntent(ExportRequestIntent.SeeIt)
            assertEquals(ExportRequestEffect.OpenDetail("run1"), awaitItem())
            outcome = RequestOutcome.InProgress(null)
            vm.onIntent(ExportRequestIntent.Submit)
            vm.onIntent(ExportRequestIntent.SeeIt)
            assertEquals(ExportRequestEffect.OpenExports, awaitItem())
        }
    }

    @Test
    fun rateLimitBlocksUntilTheWaitPassesThenReEnables() = runMviTest {
        var calls = 0
        val vm = ExportRequestViewModel(
            request = { _, _ -> calls++; RequestOutcome.RateLimited(90) },
            now = { Instant.parse("2026-10-06T10:00:00Z") },
        )
        vm.onIntent(ExportRequestIntent.Submit)
        assertEquals(ExportRequestMessage.RateLimited(90), vm.state.value.message)
        assertEquals(Instant.parse("2026-10-06T10:01:30Z"), vm.state.value.blockedUntil)
        vm.onIntent(ExportRequestIntent.Submit)
        assertEquals(1, calls, "blocked")
        testScheduler.advanceTimeBy(89.seconds)
        assertFalse(vm.state.value.canSubmit)
        testScheduler.advanceTimeBy(2.seconds)
        assertNull(vm.state.value.blockedUntil)
        assertTrue(vm.state.value.canSubmit)
    }

    @Test
    fun rateLimitWithoutAWaitDoesNotBlock() = runMviTest {
        val vm = ExportRequestViewModel(request = { _, _ -> RequestOutcome.RateLimited(null) })
        vm.onIntent(ExportRequestIntent.Submit)
        assertEquals(ExportRequestMessage.RateLimited(null), vm.state.value.message)
        assertNull(vm.state.value.blockedUntil)
    }

    @Test
    fun otherRefusalsMapToMessages() = runMviTest {
        suspend fun message(outcome: RequestOutcome): ExportRequestMessage? {
            val vm = ExportRequestViewModel(request = { _, _ -> outcome })
            vm.onIntent(ExportRequestIntent.Submit)
            return vm.state.value.message
        }
        assertEquals(ExportRequestMessage.WalletGone, message(RequestOutcome.WalletGone))
        assertEquals(ExportRequestMessage.Offline, message(RequestOutcome.Offline))
        assertEquals(ExportRequestMessage.Error(ApiErrorCode.INTERNAL_ERROR, "r"), message(RequestOutcome.Failed(ApiErrorCode.INTERNAL_ERROR, "r")))
    }
}
