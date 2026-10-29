package com.fares.saveme.feature.sharing.domain.model

import com.fares.saveme.feature.wallets.domain.model.WalletRole
import kotlin.time.Instant

/** The roles an invitation may offer; never `owner` (sharing.yaml `InvitedRole`). */
enum class InvitedRole(val wire: String) {
    Editor("editor"),
    Viewer("viewer"),
    ;

    companion object {
        fun fromWire(value: String): InvitedRole? = entries.firstOrNull { it.wire == value }
    }
}

enum class InvitationChannel { Email, Link }

/** What the owner enters. A null [email] is a link invitation. The address never prints (P-09.2). */
data class InvitationDraft(val role: InvitedRole, val email: String?) {
    val channel: InvitationChannel get() = if (email == null) InvitationChannel.Link else InvitationChannel.Email

    override fun toString() = "InvitationDraft(role=$role, channel=$channel)"
}

/** Why the server refused an invitation, chosen by code and by `Problem.feature` (spec 013 FR-008). */
enum class InvitationRefusalKind {
    NotVerified,
    SharingNotIncluded,
    PlanFull,
    PlanOther,
    NotOwner,
    NoLongerAvailable,
    Invalid,
    Other,
}

data class InvitationRefusal(val kind: InvitationRefusalKind, val requestId: String?) {
    /** A refusal that sending again unchanged can cure: a plan or a verification reason. */
    val canRetryUnchanged: Boolean
        get() = kind == InvitationRefusalKind.NotVerified || kind == InvitationRefusalKind.SharingNotIncluded ||
            kind == InvitationRefusalKind.PlanFull || kind == InvitationRefusalKind.PlanOther
}

/**
 * One pending invitation as the members screen shows it. [email] is null for a link and never printed in logs.
 * [expiresAt] is null while the invitation has not reached the server.
 */
data class InvitationView(
    val id: String,
    val walletId: String,
    val roleWire: String,
    val channel: InvitationChannel,
    val email: String?,
    val expiresAt: Instant?,
    /** A create waits to be sent ("Waiting to sync"). */
    val waiting: Boolean,
    /** The server refused the create ("Not sent") or the withdrawal. */
    val refusal: InvitationRefusal?,
    val withdrawalRefused: Boolean,
    val expired: Boolean,
    /** This app session holds the link: "Share" and "Copy" are offered. */
    val linkHeld: Boolean,
) {
    val role: InvitedRole? get() = InvitedRole.fromWire(roleWire)

    override fun toString() = "InvitationView($id, waiting=$waiting, expired=$expired)"
}

/** One person in the wallet with the version a role change or removal must carry. */
data class MemberView(
    val userId: String,
    val displayName: String?,
    val roleWire: String,
    val version: Long,
    val isYou: Boolean,
    /** The signed-in person's own address; other members' addresses are not on the phone. */
    val email: String? = null,
    val joinedAt: Instant? = null,
) {
    val role: WalletRole get() = WalletRole.fromWire(roleWire)
    val isOwner: Boolean get() = roleWire == WalletRole.Owner.wire
}

/** Whether the owner may invite another person now (spec 013 R-06). */
sealed interface InviteCheck {
    data object Allowed : InviteCheck
    data object NotVerified : InviteCheck
    data object SharingNotIncluded : InviteCheck
    data class Full(val max: Int) : InviteCheck
}

enum class EmailProblem { Invalid, OwnAddress }

sealed interface InviteResult {
    data class Queued(val invitationId: String) : InviteResult
    data object NotVerified : InviteResult
    data object SharingNotIncluded : InviteResult
    data class Full(val max: Int) : InviteResult
    data object Invalid : InviteResult
    data object OwnAddress : InviteResult

    /** A pending invitation to the same address exists: confirm, then call `inviteConfirmed`. */
    data object SupersedeNeeded : InviteResult
    data object NotOwner : InviteResult
    data object WalletArchived : InviteResult
}

/** What the invite screen leaves behind for Members to say once it has closed. */
enum class InviteNotice { Sent, SavedOffline }

/** Whether an invitation just queued reached the server within a short wait. */
enum class Delivery { Sent, Waiting, Refused }

sealed interface WithdrawResult {
    data object Withdrawn : WithdrawResult
    data object NotOwner : WithdrawResult
    data object Gone : WithdrawResult
}

sealed interface MemberActionResult {
    data object Done : MemberActionResult
    data object Offline : MemberActionResult
    data object Changed : MemberActionResult
    data object OnlyOwner : MemberActionResult
    data class NotEntitled(val feature: String?) : MemberActionResult
    data object NotVerified : MemberActionResult
    data object Gone : MemberActionResult
    data object OwnerRole : MemberActionResult
    data object Busy : MemberActionResult
    data class Failed(val requestId: String?) : MemberActionResult
}

sealed interface LeaveCheck {
    data class Ready(val waitingCount: Int) : LeaveCheck
    data object Owner : LeaveCheck
    data object Offline : LeaveCheck
}

sealed interface LeaveResult {
    data class Left(val walletName: String) : LeaveResult
    data object Offline : LeaveResult
    data object OwnerCannotLeave : LeaveResult
    data object Changed : LeaveResult
    data class Failed(val requestId: String?) : LeaveResult
}

/** What a link offers. Not stored. */
data class InvitationPreview(
    val invitationId: String,
    val walletId: String,
    val walletName: String,
    val roleWire: String,
    val inviterName: String?,
    val expiresAt: Instant?,
    val alreadyMember: Boolean,
) {
    val role: InvitedRole? get() = InvitedRole.fromWire(roleWire)
}

sealed interface PreviewResult {
    data class Ready(val preview: InvitationPreview) : PreviewResult
    data object Unavailable : PreviewResult
    data object InvalidLink : PreviewResult
    data class TooMany(val minutes: Int) : PreviewResult
    data object Offline : PreviewResult
    data class Failed(val requestId: String?) : PreviewResult
}

sealed interface JoinResult {
    data class Joined(val walletId: String) : JoinResult
    data object OwnerPlanFull : JoinResult
    data object Unavailable : JoinResult
    data object InvalidLink : JoinResult
    data class TooMany(val minutes: Int) : JoinResult
    data object Offline : JoinResult
    data class Failed(val requestId: String?) : JoinResult
}

sealed interface DeclineResult {
    data object Declined : DeclineResult
    data object Unavailable : DeclineResult
    data object InvalidLink : DeclineResult
    data class TooMany(val minutes: Int) : DeclineResult
    data object Offline : DeclineResult
    data class Failed(val requestId: String?) : DeclineResult
}

// Activity (spec 013 FR-024, FR-025). No amount exists on any of these types.

enum class ActivityAction(val wire: String) {
    Created("created"),
    Edited("edited"),
    Deleted("deleted"),
    Restored("restored"),
    Archived("archived"),
    OwnershipTransferred("ownership_transferred"),
    Confirmed("confirmed"),
    Skipped("skipped"),
    Joined("joined"),
    Left("left"),
    Removed("removed"),
    RoleChanged("role_changed"),
    RoleLimitedByPlan("role_limited_by_plan"),
    Invited("invited"),
    InvitationRevoked("invitation_revoked"),
    ;

    companion object {
        fun fromWire(value: String): ActivityAction? = entries.firstOrNull { it.wire == value }
    }
}

enum class SubjectKind(val wire: String) {
    Wallet("wallet"),
    Transaction("transaction"),
    RecurringRule("recurring_rule"),
    Occurrence("occurrence"),
    Member("member"),
    Invitation("invitation"),
    ;

    companion object {
        fun fromWire(value: String): SubjectKind? = entries.firstOrNull { it.wire == value }
    }
}

sealed interface Actor {
    data class Named(val name: String) : Actor
    data object FormerMember : Actor

    /** A current member who set no name. */
    data object UnnamedMember : Actor

    /** The server acted (an automatic confirmation, a plan change, an expiry). */
    data object Automatic : Actor
}

/** What the entry was about: [name] is null when the phone has no name for it ("a transaction"); [onPhone] when it holds the record. */
data class ActivitySubject(val kind: SubjectKind, val id: String, val name: String?, val onPhone: Boolean = false)

data class ActivityItem(
    val id: String,
    val at: Instant,
    val actor: Actor,
    val action: ActivityAction,
    val subject: ActivitySubject,
    /** The role the entry carries, as the server sent it; null when it carries none. */
    val roleWire: String?,
) {
    /** A tapped transaction entry opens the transaction when the phone has it. */
    val transactionId: String? get() = subject.id.takeIf { subject.kind == SubjectKind.Transaction && subject.onPhone }
}

sealed interface ActivityPage {
    /** [restarted]: the cursor was refused and the list was read again from the first page. */
    data class Loaded(val items: List<ActivityItem>, val nextCursor: String?, val restarted: Boolean = false) : ActivityPage
    data object Offline : ActivityPage
    data object Gone : ActivityPage
    data class Failed(val requestId: String?) : ActivityPage
}
