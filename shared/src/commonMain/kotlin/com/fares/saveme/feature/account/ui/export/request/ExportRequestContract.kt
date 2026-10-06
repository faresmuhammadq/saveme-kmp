package com.fares.saveme.feature.account.ui.export.request

import com.fares.saveme.core.network.ApiErrorCode
import com.fares.saveme.feature.account.domain.export.ExportScope
import kotlin.time.Instant

/** A wallet the person may export: the server already has it and they are a member (FR-006). */
data class WalletChoice(val id: String, val name: String, val archived: Boolean)

/** The refusal shown; each has its own copy chosen by code (FR-017 to FR-023). */
sealed interface ExportRequestMessage {
    /** [currentId] is the export already being prepared, when the answer named one. */
    data class InProgress(val currentId: String?) : ExportRequestMessage
    data class RateLimited(val seconds: Long?) : ExportRequestMessage
    data object WalletGone : ExportRequestMessage
    data object Offline : ExportRequestMessage
    data class Error(val code: ApiErrorCode, val requestId: String?) : ExportRequestMessage
}

/** Memory only. [blockedUntil] is the end of a rate-limit wait. */
data class ExportRequestState(
    val scope: ExportScope = ExportScope.Account,
    val walletId: String? = null,
    val wallets: List<WalletChoice> = emptyList(),
    val walletPickerVisible: Boolean = false,
    val unsentChanges: Boolean = false,
    val submitting: Boolean = false,
    val blockedUntil: Instant? = null,
    val message: ExportRequestMessage? = null,
) {
    val chosenWallet: WalletChoice? get() = wallets.firstOrNull { it.id == walletId }

    /** A wallet export needs a wallet the phone holds; nothing is sent while one is in flight or a wait is on. */
    val canSubmit: Boolean
        get() = !submitting && blockedUntil == null &&
            (scope == ExportScope.Account || chosenWallet != null)
}

sealed interface ExportRequestIntent {
    data class ScopeChosen(val scope: ExportScope) : ExportRequestIntent
    data object WalletFieldClicked : ExportRequestIntent
    data object WalletPickerDismissed : ExportRequestIntent
    data class WalletChosen(val id: String) : ExportRequestIntent
    data object Submit : ExportRequestIntent
    data object SeeIt : ExportRequestIntent
    data object Back : ExportRequestIntent
}

sealed interface ExportRequestEffect {
    /** Replaces this screen with the export's. */
    data class OpenDetail(val id: String) : ExportRequestEffect
    data object OpenExports : ExportRequestEffect
    data object NavigateBack : ExportRequestEffect
}

sealed interface ExportRequestChange {
    data class Scope(val scope: ExportScope) : ExportRequestChange
    data class WalletPicker(val visible: Boolean) : ExportRequestChange
    data class Wallet(val id: String) : ExportRequestChange
    data class Wallets(val wallets: List<WalletChoice>) : ExportRequestChange
    data class Unsent(val unsent: Boolean) : ExportRequestChange
    data object Submitting : ExportRequestChange
    data class Refused(val message: ExportRequestMessage) : ExportRequestChange
    data class Blocked(val until: Instant) : ExportRequestChange
    data object WaitOver : ExportRequestChange
    data object Sent : ExportRequestChange
}

fun reduce(state: ExportRequestState, change: ExportRequestChange): ExportRequestState = when (change) {
    is ExportRequestChange.Scope -> state.copy(scope = change.scope, message = null)
    is ExportRequestChange.WalletPicker -> state.copy(walletPickerVisible = change.visible)
    is ExportRequestChange.Wallet -> state.copy(walletId = change.id, walletPickerVisible = false, message = null)
    is ExportRequestChange.Wallets -> state.copy(wallets = change.wallets)
    is ExportRequestChange.Unsent -> state.copy(unsentChanges = change.unsent)
    ExportRequestChange.Submitting -> state.copy(submitting = true, message = null)
    is ExportRequestChange.Refused -> state.copy(submitting = false, message = change.message)
    is ExportRequestChange.Blocked -> state.copy(blockedUntil = change.until)
    ExportRequestChange.WaitOver -> state.copy(blockedUntil = null)
    ExportRequestChange.Sent -> state.copy(submitting = false)
}
