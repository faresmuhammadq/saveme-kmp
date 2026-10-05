package com.fares.saveme.feature.wallets.ui.list

import com.fares.saveme.feature.wallets.domain.model.CurrencyTotal
import com.fares.saveme.feature.wallets.domain.model.SyncState
import com.fares.saveme.feature.wallets.domain.model.WalletList
import com.fares.saveme.feature.wallets.domain.model.WalletRole
import com.fares.saveme.feature.wallets.domain.model.WalletType
import com.fares.saveme.feature.wallets.domain.model.WalletView
import kotlin.time.Instant

enum class WalletStatusUi { Waiting, Changed, NotSaved }

/** What one card shows; mapped from the domain's view so the screen holds no business rules. */
data class WalletCardUi(
    val id: String,
    val name: String,
    val type: WalletType,
    val amountMinor: Long,
    val currency: String,
    val iconKey: String?,
    val accentKey: String?,
    val status: WalletStatusUi?,
    val archived: Boolean,
    /** Shown only when the person is not the owner. */
    val role: WalletRole?,
)

data class WalletsState(
    val active: List<WalletCardUi> = emptyList(),
    val archived: List<WalletCardUi> = emptyList(),
    val totals: List<CurrencyTotal> = emptyList(),
    val refreshing: Boolean = false,
    val updatedAt: Instant? = null,
    val offline: Boolean = false,
    val refreshFailed: Boolean = false,
    val notEntitledNotice: Boolean = false,
    /** False until the first refresh has finished or a wallet is on the phone: shows a skeleton, not "no wallets". */
    val loaded: Boolean = false,
    val waitingCount: Int = 0,
    val timeZoneId: String = "UTC",
    /** The name of the wallet the person just left, until they dismiss the line (spec 013 FR-019). */
    val leftNotice: String? = null,
) {
    val isEmpty: Boolean get() = active.isEmpty() && archived.isEmpty()
}

sealed interface WalletsIntent {
    data object Opened : WalletsIntent
    data object Refresh : WalletsIntent
    data object CreateClicked : WalletsIntent
    data class WalletClicked(val id: String) : WalletsIntent
    data object DismissNotice : WalletsIntent
    data object DismissLeftNotice : WalletsIntent
    data object Back : WalletsIntent
}

sealed interface WalletsEffect {
    data object OpenEditor : WalletsEffect
    data class OpenDetail(val id: String) : WalletsEffect
    data object RequireVerification : WalletsEffect
    data object NavigateBack : WalletsEffect
}

sealed interface WalletsChange {
    data class Stored(val list: WalletList) : WalletsChange
    data class TimeZone(val id: String) : WalletsChange
    data class Refreshing(val busy: Boolean) : WalletsChange
    data class RefreshDone(val offline: Boolean, val failed: Boolean) : WalletsChange
    data class NotEntitled(val visible: Boolean) : WalletsChange
    data class LeftNotice(val walletName: String?) : WalletsChange
}

fun reduce(state: WalletsState, change: WalletsChange): WalletsState = when (change) {
    is WalletsChange.Stored -> {
        val cards = change.list.active.map { it.toCard() } to change.list.archived.map { it.toCard() }
        state.copy(
            active = cards.first,
            archived = cards.second,
            totals = change.list.totals,
            updatedAt = change.list.updatedAt,
            loaded = state.loaded || cards.first.isNotEmpty() || cards.second.isNotEmpty(),
            waitingCount = (change.list.active + change.list.archived).count { it.syncState is SyncState.Waiting },
        )
    }
    is WalletsChange.TimeZone -> state.copy(timeZoneId = change.id)
    is WalletsChange.Refreshing -> state.copy(refreshing = change.busy)
    is WalletsChange.RefreshDone -> state.copy(refreshing = false, loaded = true, offline = change.offline, refreshFailed = change.failed)
    is WalletsChange.NotEntitled -> state.copy(notEntitledNotice = change.visible)
    is WalletsChange.LeftNotice -> state.copy(leftNotice = change.walletName)
}

internal fun WalletView.toCard() = WalletCardUi(
    id = id,
    name = shown.name,
    type = shown.walletType,
    amountMinor = displayedBalanceMinor,
    currency = shown.currency,
    iconKey = shown.icon,
    accentKey = shown.color,
    status = when (syncState) {
        SyncState.None -> if (hasPendingPostings) WalletStatusUi.Waiting else null
        SyncState.Waiting -> WalletStatusUi.Waiting
        is SyncState.Clash -> WalletStatusUi.Changed
        is SyncState.Refused -> WalletStatusUi.NotSaved
    },
    archived = archived,
    role = server.myRole.takeIf { it != WalletRole.Owner },
)
