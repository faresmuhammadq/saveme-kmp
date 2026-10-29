package com.fares.saveme.feature.wallets.ui.detail

import androidx.lifecycle.viewModelScope
import com.fares.saveme.core.presentation.MviViewModel
import com.fares.saveme.feature.auth.domain.model.RefreshOutcome
import com.fares.saveme.feature.wallets.domain.model.Member
import com.fares.saveme.feature.wallets.domain.model.RecentTransaction
import com.fares.saveme.feature.wallets.domain.model.SaveResult
import com.fares.saveme.feature.wallets.domain.model.TransferResult
import com.fares.saveme.feature.wallets.domain.model.WalletView
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlin.time.Clock

/** One wallet: its balance, members, the owner's actions and the choices a clash or refusal needs (domain lambdas only, P-16.3). */
class WalletDetailViewModel(
    private val walletId: String,
    wallet: Flow<WalletView?>,
    members: Flow<List<Member>>,
    private val refreshMembers: suspend () -> RefreshOutcome,
    private val archive: suspend () -> SaveResult,
    private val delete: suspend () -> SaveResult,
    private val keepMine: suspend () -> Unit,
    private val useTheirs: suspend () -> Unit,
    private val tryAgain: suspend () -> Unit,
    private val discard: suspend () -> Unit,
    private val transfer: suspend (String) -> TransferResult,
    timeZoneId: Flow<String> = emptyFlow(),
    private val clock: Clock = Clock.System,
    pendingInvitations: Flow<Int> = flowOf(0),
    planLimited: Flow<Boolean> = flowOf(false),
    /** True while the person holds an own member row and the wallet record has not been read yet (spec 013 R-10). */
    joining: Flow<Boolean> = flowOf(false),
    /** True once the person left this wallet themself: the wallet vanishing is then not "access lost" (FR-023). */
    leftByPerson: Flow<Boolean> = flowOf(false),
    /** The wallet's latest transactions for the "Recent" card. */
    recent: Flow<List<RecentTransaction>> = flowOf(emptyList()),
) : MviViewModel<WalletDetailState, WalletDetailIntent, WalletDetailEffect>(WalletDetailState(walletId = walletId)) {

    /** True from a confirmed delete on, so the wallet vanishing is not reported as "gone". */
    private var leaving = false
    private var seenWallet = false

    init {
        combine(wallet, joining, leftByPerson) { view, isJoining, left -> Triple(view, isJoining, left) }
            .onEach { (view, isJoining, left) -> onWallet(view, isJoining, left) }
            .launchIn(viewModelScope)
        pendingInvitations.onEach { count -> setState { reduce(it, WalletDetailChange.Pending(count)) } }.launchIn(viewModelScope)
        planLimited.onEach { limited -> setState { reduce(it, WalletDetailChange.PlanLimited(limited)) } }.launchIn(viewModelScope)
        recent.onEach { items -> setState { reduce(it, WalletDetailChange.Recent(items)) } }.launchIn(viewModelScope)
        members.onEach { list -> setState { reduce(it, WalletDetailChange.Members(list)) } }.launchIn(viewModelScope)
        timeZoneId.onEach { id -> setState { reduce(it, WalletDetailChange.TimeZone(id)) } }.launchIn(viewModelScope)
        viewModelScope.launch { readWallet() }
    }

    /** The wallet's read; for a wallet just joined a read that did not complete leaves "Try again" (spec 013 FR-013). */
    private suspend fun readWallet() {
        val outcome = refreshMembers()
        if (outcome == RefreshOutcome.Done) setState { reduce(reduce(it, WalletDetailChange.MembersUpdated(clock.now())), WalletDetailChange.JoiningFailed(false)) }
        else setState { reduce(it, WalletDetailChange.JoiningFailed(true)) }
    }

    private fun onWallet(view: WalletView?, joining: Boolean, leftByPerson: Boolean) {
        setState { reduce(it, WalletDetailChange.Joining(joining && view == null)) }
        if (view == null) {
            // Just joined: the wallet record is on its way, so this is not "gone" (FR-013).
            if (joining) return
            if (!leaving) {
                // After the person's own leave the members screen closes this one with its own message (FR-023).
                if (!leftByPerson) {
                    sendEffect(WalletDetailEffect.ShowMessage(DetailMessage.Gone))
                    sendEffect(WalletDetailEffect.NavigateBack)
                }
            }
            leaving = true
            return
        }
        seenWallet = true
        setState { reduce(it, WalletDetailChange.Stored(view)) }
    }

    override fun onIntent(intent: WalletDetailIntent) {
        when (intent) {
            WalletDetailIntent.TransactionsClicked -> sendEffect(WalletDetailEffect.OpenTransactions(walletId))
            WalletDetailIntent.RecurringClicked -> sendEffect(WalletDetailEffect.OpenRecurring(walletId))
            WalletDetailIntent.ReportClicked -> sendEffect(WalletDetailEffect.OpenReport(walletId))
            WalletDetailIntent.MembersClicked -> sendEffect(WalletDetailEffect.NavigateMembers(walletId))
            WalletDetailIntent.ActivityClicked -> sendEffect(WalletDetailEffect.NavigateActivity(walletId))
            WalletDetailIntent.ExportClicked -> if (currentState.exportable) sendEffect(WalletDetailEffect.OpenExport(walletId))
            WalletDetailIntent.JoiningRetryClicked -> {
                setState { reduce(it, WalletDetailChange.JoiningFailed(false)) }
                viewModelScope.launch { readWallet() }
            }
            WalletDetailIntent.AddTransactionClicked -> if (currentState.canAddTransaction) sendEffect(WalletDetailEffect.OpenAddTransaction(walletId))
            WalletDetailIntent.EditClicked -> if (currentState.canEdit) sendEffect(WalletDetailEffect.OpenEditor(walletId))
            WalletDetailIntent.ArchiveClicked -> if (currentState.canEdit) confirm(DetailConfirm.Archive)
            WalletDetailIntent.DeleteClicked -> if (currentState.canEdit) confirm(DetailConfirm.Delete)
            WalletDetailIntent.TransferClicked -> if (currentState.canTransfer) confirm(DetailConfirm.Transfer(currentState.candidates))
            WalletDetailIntent.DismissConfirm -> if (!currentState.busy) confirm(DetailConfirm.None)
            WalletDetailIntent.ConfirmArchive -> perform { archive() }
            WalletDetailIntent.ConfirmDelete -> perform {
                leaving = true
                val result = delete()
                if (result == SaveResult.Saved) sendEffect(WalletDetailEffect.NavigateBack) else leaving = false
                result
            }
            is WalletDetailIntent.TransferTargetPicked ->
                currentState.members.firstOrNull { it.userId == intent.userId && !it.isYou }?.let { confirm(DetailConfirm.TransferTo(it)) }
            is WalletDetailIntent.ConfirmTransfer -> transferTo(intent.userId)
            WalletDetailIntent.KeepMine -> resolve(keepMine)
            WalletDetailIntent.UseTheirs, WalletDetailIntent.AcceptDeletion -> resolve(useTheirs)
            WalletDetailIntent.TryAgain -> resolve(tryAgain)
            WalletDetailIntent.Discard -> resolve(discard)
            WalletDetailIntent.Back -> sendEffect(WalletDetailEffect.NavigateBack)
        }
    }

    private fun confirm(confirm: DetailConfirm) = setState { reduce(it, WalletDetailChange.Confirm(confirm)) }

    private fun perform(action: suspend () -> SaveResult) {
        if (currentState.busy) return
        setState { reduce(it, WalletDetailChange.Busy(true)) }
        viewModelScope.launch {
            val result = action()
            setState { reduce(it, WalletDetailChange.Busy(false)) }
            confirm(DetailConfirm.None)
            when (result) {
                SaveResult.NotVerified -> sendEffect(WalletDetailEffect.RequireVerification)
                SaveResult.NotOwner -> sendEffect(WalletDetailEffect.ShowMessage(DetailMessage.NotOwner))
                SaveResult.Gone -> Unit
                else -> Unit
            }
        }
    }

    private fun resolve(action: suspend () -> Unit) {
        if (currentState.busy) return
        setState { reduce(it, WalletDetailChange.Busy(true)) }
        viewModelScope.launch {
            action()
            setState { reduce(it, WalletDetailChange.Busy(false)) }
        }
    }

    private fun transferTo(userId: String) {
        if (currentState.busy) return
        setState { reduce(it, WalletDetailChange.Busy(true)) }
        viewModelScope.launch {
            val message = when (transfer(userId)) {
                TransferResult.Done -> null
                TransferResult.Offline -> DetailMessage.TransferOffline
                TransferResult.NoLongerMember -> DetailMessage.NoLongerMember
                TransferResult.Changed -> DetailMessage.Changed
                TransferResult.NotOwner -> DetailMessage.NotOwner
                is TransferResult.Failed -> DetailMessage.TransferFailed
            }
            setState { reduce(it, WalletDetailChange.Busy(false)) }
            confirm(DetailConfirm.None)
            message?.let { sendEffect(WalletDetailEffect.ShowMessage(it)) }
        }
    }
}
