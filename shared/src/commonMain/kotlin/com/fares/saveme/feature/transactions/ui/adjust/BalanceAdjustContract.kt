package com.fares.saveme.feature.transactions.ui.adjust

import com.fares.saveme.feature.transactions.domain.AdjustDirection
import com.fares.saveme.feature.transactions.domain.AdjustMode
import com.fares.saveme.feature.transactions.domain.AdjustOutcome
import com.fares.saveme.feature.transactions.domain.BalanceAdjustment
import com.fares.saveme.feature.transactions.domain.model.AdjustTarget
import com.fares.saveme.feature.transactions.domain.model.TransactionFieldProblem
import kotlinx.datetime.LocalDate
import kotlin.time.Instant

/** Why a save did not go through, shown as a message. */
enum class AdjustMessage { NotAllowed, NoLongerAvailable }

data class BalanceAdjustState(
    val walletId: String = "",
    val loaded: Boolean = false,
    val target: AdjustTarget? = null,
    val mode: AdjustMode = AdjustMode.NewBalance,
    val direction: AdjustDirection = AdjustDirection.Raise,
    val amountText: String = "",
    val date: LocalDate? = null,
    val datePickerOpen: Boolean = false,
    val note: String = "",
    val saving: Boolean = false,
    /** A problem the person sees only after pressing Save, so an empty field is not scolded while typing. */
    val problem: TransactionFieldProblem? = null,
    /** When this wallet's partners' entries were last read, for "Updated" under the balance; null before the first read. */
    val feedReadAt: Instant? = null,
) {
    val canEdit: Boolean get() = target?.canAdjust == true

    /** What the typed text means for the wallet right now; null while nothing is typed. */
    val outcome: AdjustOutcome?
        get() = target?.takeIf { amountText.isNotBlank() }?.let {
            BalanceAdjustment.resolve(mode, direction, amountText, it.currency, it.isLiability, it.balanceMinor)
        }

    val canSave: Boolean get() = loaded && canEdit && !saving && date != null && outcome is AdjustOutcome.Ready
}

sealed interface BalanceAdjustIntent {
    data class ModeChosen(val mode: AdjustMode) : BalanceAdjustIntent
    data class DirectionChosen(val direction: AdjustDirection) : BalanceAdjustIntent
    data class AmountChanged(val text: String) : BalanceAdjustIntent
    data object DateClicked : BalanceAdjustIntent
    data class DateChosen(val date: LocalDate) : BalanceAdjustIntent
    data object DatePickerDismissed : BalanceAdjustIntent
    data class NoteChanged(val text: String) : BalanceAdjustIntent
    data object SaveClicked : BalanceAdjustIntent
    data object Back : BalanceAdjustIntent
}

sealed interface BalanceAdjustEffect {
    data object Close : BalanceAdjustEffect
    data object RequireVerification : BalanceAdjustEffect
    data class ShowMessage(val message: AdjustMessage) : BalanceAdjustEffect
}
