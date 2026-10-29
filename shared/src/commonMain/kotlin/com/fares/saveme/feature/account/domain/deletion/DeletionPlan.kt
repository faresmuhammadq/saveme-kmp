package com.fares.saveme.feature.account.domain.deletion

import com.fares.saveme.core.network.ApiErrorCode
import com.fares.saveme.feature.wallets.domain.model.WalletRole
import com.fares.saveme.feature.wallets.domain.model.WalletType
import kotlin.time.Instant

sealed interface PlanState {
    data object Active : PlanState
    data object PendingDeletion : PlanState
    data class Unknown(val raw: String) : PlanState
}

/** Why the server would pick this member. An unknown reason shows no reason line. */
sealed interface SuccessorReason {
    data object OnlyMember : SuccessorReason
    data object Chosen : SuccessorReason
    data object OldestEditor : SuccessorReason
    data object OldestViewer : SuccessorReason
    data class Unknown(val raw: String) : SuccessorReason

    companion object {
        fun fromWire(raw: String): SuccessorReason = when (raw) {
            "only_member" -> OnlyMember
            "chosen" -> Chosen
            "oldest_editor" -> OldestEditor
            "oldest_viewer" -> OldestViewer
            else -> Unknown(raw)
        }
    }
}

/**
 * Someone who could own a shared wallet, as the phone knows them. [unknown] is the server's suggestion when the phone holds no
 * record of that member ("Another member"): it can be sent as it is, never described.
 */
data class Candidate(
    val userId: String,
    val displayName: String?,
    val role: WalletRole?,
    val joinedAt: Instant?,
    val unknown: Boolean = false,
)

/**
 * A shared wallet the person owns and who would take it over. [walletName] is null when the phone does not hold the wallet.
 * [choiceOffered] = the server expects a choice, the phone holds the wallet and at least one other member (R-07).
 */
data class OwnedSharedWallet(
    val walletId: String,
    val walletName: String?,
    val successorUserId: String,
    val reason: SuccessorReason,
    val choiceExpected: Boolean,
    val choiceOffered: Boolean,
    val candidates: List<Candidate>,
    /** How the phone draws the wallet (its own icon key, accent key and type); null when it does not hold the wallet. */
    val walletIcon: String? = null,
    val walletColor: String? = null,
    val walletType: WalletType? = null,
) {
    /** The member who becomes owner when nothing is picked: the server's own answer. */
    val suggested: Candidate? get() = candidates.firstOrNull { it.userId == successorUserId }
}

data class DeletionPlan(
    val state: PlanState,
    val requestedAt: Instant?,
    val scheduledFor: Instant?,
    val wallets: List<OwnedSharedWallet>,
) {
    /** Whether any wallet asks the person to choose. */
    val offersChoice: Boolean get() = wallets.any { it.choiceOffered }

    /** Whether the server holds a stored choice (the cancel confirmation says it was dropped). */
    val hasStoredChoice: Boolean get() = wallets.any { it.reason == SuccessorReason.Chosen }
}

sealed interface PlanOutcome {
    data class Loaded(val plan: DeletionPlan) : PlanOutcome
    data object Offline : PlanOutcome
    data class Failed(val code: ApiErrorCode, val requestId: String?) : PlanOutcome
}

/** A new owner the person picked for one wallet; mapped to the wire type by the use case (P-10.6). */
data class SuccessorChoice(val walletId: String, val memberUserId: String)
