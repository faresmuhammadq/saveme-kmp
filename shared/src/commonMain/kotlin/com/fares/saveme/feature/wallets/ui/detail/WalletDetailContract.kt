package com.fares.saveme.feature.wallets.ui.detail

import com.fares.saveme.feature.wallets.domain.model.Member
import com.fares.saveme.feature.wallets.domain.model.WalletRole
import com.fares.saveme.feature.wallets.domain.model.WalletView
import kotlin.time.Instant

/** Which confirmation, if any, is open. */
sealed interface DetailConfirm {
    data object None : DetailConfirm
    data object Archive : DetailConfirm
    data object Delete : DetailConfirm

    /** The sheet listing the members who could become the owner. */
    data class Transfer(val candidates: List<Member>) : DetailConfirm

    /** The confirmation for one chosen member. */
    data class TransferTo(val member: Member) : DetailConfirm
}

enum class DetailMessage { Gone, NotOwner, TransferOffline, NoLongerMember, Changed, TransferFailed }

data class WalletDetailState(
    val walletId: String = "",
    val wallet: WalletView? = null,
    val members: List<Member> = emptyList(),
    val membersUpdatedAt: Instant? = null,
    val confirm: DetailConfirm = DetailConfirm.None,
    val busy: Boolean = false,
    val timeZoneId: String = "UTC",
    /** Pending, unexpired invitations of the wallet, for "See members (n pending)" (spec 013 FR-016). */
    val pendingInvitations: Int = 0,
    /** The latest role change about the person, read from the activity this session, was the owner's plan (FR-027). */
    val planLimited: Boolean = false,
    /** The person joined and the wallet's first read has not landed yet: "Getting the wallet…", never "gone" (FR-013). */
    val joining: Boolean = false,
    /** That first read failed or was offline: "Try again" is offered. */
    val joiningFailed: Boolean = false,
) {
    val loaded: Boolean get() = wallet != null

    val isViewer: Boolean get() = wallet?.server?.myRole == WalletRole.Viewer

    /** Only the owner edits, archives, deletes or transfers a wallet; the server decides, this hides the controls (FR-022). */
    val canEdit: Boolean get() = wallet?.server?.myRole == WalletRole.Owner

    val candidates: List<Member> get() = members.filter { !it.isYou }

    /** Every role may export a wallet the server already has (a wallet made on this phone has version 0, spec 016 FR-002). */
    val exportable: Boolean get() = wallet != null && wallet.server.version > 0

    /** Offered to an owner or editor of an active wallet (spec 008 FR-008). */
    val canAddTransaction: Boolean
        get() = wallet != null && !wallet.archived &&
            (wallet.server.myRole == WalletRole.Owner || wallet.server.myRole == WalletRole.Editor)

    /** Offered to the owner when another member exists and no change waits (FR-023, research R-15). */
    val canTransfer: Boolean
        get() = canEdit && candidates.isNotEmpty() && wallet?.pending == null && wallet?.pendingDelete != true &&
            wallet?.syncState == com.fares.saveme.feature.wallets.domain.model.SyncState.None
}

sealed interface WalletDetailIntent {
    data object TransactionsClicked : WalletDetailIntent
    data object RecurringClicked : WalletDetailIntent
    data object MembersClicked : WalletDetailIntent
    data object ActivityClicked : WalletDetailIntent
    data object ExportClicked : WalletDetailIntent
    data object JoiningRetryClicked : WalletDetailIntent
    data object AddTransactionClicked : WalletDetailIntent
    data object EditClicked : WalletDetailIntent
    data object ArchiveClicked : WalletDetailIntent
    data object DeleteClicked : WalletDetailIntent
    data object TransferClicked : WalletDetailIntent
    data object ConfirmArchive : WalletDetailIntent
    data object ConfirmDelete : WalletDetailIntent
    data class TransferTargetPicked(val userId: String) : WalletDetailIntent
    data class ConfirmTransfer(val userId: String) : WalletDetailIntent
    data object DismissConfirm : WalletDetailIntent
    data object KeepMine : WalletDetailIntent
    data object UseTheirs : WalletDetailIntent
    data object AcceptDeletion : WalletDetailIntent
    data object TryAgain : WalletDetailIntent
    data object Discard : WalletDetailIntent
    data object Back : WalletDetailIntent
}

sealed interface WalletDetailEffect {
    data class OpenTransactions(val walletId: String) : WalletDetailEffect
    data class OpenRecurring(val walletId: String) : WalletDetailEffect
    data class NavigateMembers(val walletId: String) : WalletDetailEffect
    data class NavigateActivity(val walletId: String) : WalletDetailEffect
    data class OpenExport(val walletId: String) : WalletDetailEffect
    data class OpenAddTransaction(val walletId: String) : WalletDetailEffect
    data class OpenEditor(val id: String) : WalletDetailEffect
    data object NavigateBack : WalletDetailEffect
    data class ShowMessage(val message: DetailMessage) : WalletDetailEffect
    data object RequireVerification : WalletDetailEffect
}

sealed interface WalletDetailChange {
    data class Stored(val wallet: WalletView?) : WalletDetailChange
    data class Members(val members: List<Member>) : WalletDetailChange
    data class MembersUpdated(val at: Instant) : WalletDetailChange
    data class TimeZone(val id: String) : WalletDetailChange
    data class Confirm(val confirm: DetailConfirm) : WalletDetailChange
    data class Busy(val busy: Boolean) : WalletDetailChange
    data class Pending(val count: Int) : WalletDetailChange
    data class PlanLimited(val limited: Boolean) : WalletDetailChange
    data class Joining(val joining: Boolean) : WalletDetailChange
    data class JoiningFailed(val failed: Boolean) : WalletDetailChange
}

fun reduce(state: WalletDetailState, change: WalletDetailChange): WalletDetailState = when (change) {
    is WalletDetailChange.Stored -> state.copy(wallet = change.wallet)
    is WalletDetailChange.Members -> state.copy(members = change.members)
    is WalletDetailChange.MembersUpdated -> state.copy(membersUpdatedAt = change.at)
    is WalletDetailChange.TimeZone -> state.copy(timeZoneId = change.id)
    is WalletDetailChange.Confirm -> state.copy(confirm = change.confirm)
    is WalletDetailChange.Busy -> state.copy(busy = change.busy)
    is WalletDetailChange.Pending -> state.copy(pendingInvitations = change.count)
    is WalletDetailChange.PlanLimited -> state.copy(planLimited = change.limited)
    is WalletDetailChange.Joining -> state.copy(joining = change.joining, joiningFailed = if (change.joining) state.joiningFailed else false)
    is WalletDetailChange.JoiningFailed -> state.copy(joiningFailed = change.failed)
}

enum class ClashField { Name, OpeningBalance, Currency, Icon, Color, Archived }

sealed interface ClashValue {
    data class Text(val value: String?) : ClashValue
    data class Money(val minor: Long, val currency: String) : ClashValue
    data class Icon(val key: String?) : ClashValue
    data class Accent(val key: String?) : ClashValue
    data class Archived(val archived: Boolean) : ClashValue
}

data class ClashLine(val field: ClashField, val mine: ClashValue, val theirs: ClashValue)

/** Only the fields where this phone's change and the other device's copy differ (research R-07). */
fun clashLines(mine: com.fares.saveme.feature.wallets.domain.model.WalletDraft, theirs: com.fares.saveme.feature.wallets.domain.model.Wallet): List<ClashLine> {
    val other = theirs.toDraft()
    return listOfNotNull(
        ClashLine(ClashField.Name, ClashValue.Text(mine.name), ClashValue.Text(other.name)).takeIf { mine.name != other.name },
        ClashLine(
            ClashField.OpeningBalance,
            ClashValue.Money(mine.openingBalanceMinor, mine.currency),
            ClashValue.Money(other.openingBalanceMinor, other.currency),
        ).takeIf { mine.openingBalanceMinor != other.openingBalanceMinor },
        ClashLine(ClashField.Currency, ClashValue.Text(mine.currency), ClashValue.Text(other.currency)).takeIf { mine.currency != other.currency },
        ClashLine(ClashField.Icon, ClashValue.Icon(mine.icon), ClashValue.Icon(other.icon)).takeIf { mine.icon != other.icon },
        ClashLine(ClashField.Color, ClashValue.Accent(mine.color), ClashValue.Accent(other.color)).takeIf { mine.color != other.color },
        ClashLine(ClashField.Archived, ClashValue.Archived(mine.archive), ClashValue.Archived(other.archive)).takeIf { mine.archive != other.archive },
    )
}
