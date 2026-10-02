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
) {
    val loaded: Boolean get() = wallet != null

    /** Only the owner edits, archives, deletes or transfers a wallet; the server decides, this hides the controls (FR-022). */
    val canEdit: Boolean get() = wallet?.server?.myRole == WalletRole.Owner

    val candidates: List<Member> get() = members.filter { !it.isYou }

    /** Offered to the owner when another member exists and no change waits (FR-023, research R-15). */
    val canTransfer: Boolean
        get() = canEdit && candidates.isNotEmpty() && wallet?.pending == null && wallet?.pendingDelete != true &&
            wallet?.syncState == com.fares.saveme.feature.wallets.domain.model.SyncState.None
}

sealed interface WalletDetailIntent {
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
}

fun reduce(state: WalletDetailState, change: WalletDetailChange): WalletDetailState = when (change) {
    is WalletDetailChange.Stored -> state.copy(wallet = change.wallet)
    is WalletDetailChange.Members -> state.copy(members = change.members)
    is WalletDetailChange.MembersUpdated -> state.copy(membersUpdatedAt = change.at)
    is WalletDetailChange.TimeZone -> state.copy(timeZoneId = change.id)
    is WalletDetailChange.Confirm -> state.copy(confirm = change.confirm)
    is WalletDetailChange.Busy -> state.copy(busy = change.busy)
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
