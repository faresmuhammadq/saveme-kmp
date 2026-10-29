package com.fares.saveme.feature.sharing.domain

import co.touchlab.kermit.Logger
import com.fares.saveme.core.network.ApiErrorCode
import com.fares.saveme.core.network.ApiResult
import com.fares.saveme.core.network.AppJson
import com.fares.saveme.core.sync.ChangeState
import com.fares.saveme.core.sync.PendingChange
import com.fares.saveme.core.sync.ReadOutcome
import com.fares.saveme.core.sync.ReadTarget
import com.fares.saveme.core.sync.ScopeKind
import com.fares.saveme.core.sync.SyncEngine
import com.fares.saveme.core.sync.SyncRunner
import com.fares.saveme.core.sync.SyncScope
import com.fares.saveme.feature.auth.domain.AuthState
import com.fares.saveme.feature.billing.domain.Entitlements
import com.fares.saveme.feature.billing.domain.FlagFeature
import com.fares.saveme.feature.billing.domain.QuotaFeature
import com.fares.saveme.feature.sharing.data.ActivityApi
import com.fares.saveme.feature.sharing.data.InvitationApi
import com.fares.saveme.feature.sharing.data.InvitationPayload
import com.fares.saveme.feature.sharing.data.InvitationRepository
import com.fares.saveme.feature.sharing.data.InvitationSyncHandler
import com.fares.saveme.feature.sharing.data.MemberApi
import com.fares.saveme.feature.sharing.data.dto.InvitationPreviewDto
import com.fares.saveme.feature.sharing.data.dto.MemberDto
import com.fares.saveme.feature.sharing.data.local.InvitationEntity
import com.fares.saveme.feature.sharing.domain.model.ActivityPage
import com.fares.saveme.feature.sharing.domain.model.DeclineResult
import com.fares.saveme.feature.sharing.domain.model.Delivery
import com.fares.saveme.feature.sharing.domain.model.InvitationChannel
import com.fares.saveme.feature.sharing.domain.model.InvitationDraft
import com.fares.saveme.feature.sharing.domain.model.InvitationPreview
import com.fares.saveme.feature.sharing.domain.model.InvitationRefusal
import com.fares.saveme.feature.sharing.domain.model.InvitationRefusalKind
import com.fares.saveme.feature.sharing.domain.model.InvitationView
import com.fares.saveme.feature.sharing.domain.model.InviteCheck
import com.fares.saveme.feature.sharing.domain.model.InviteResult
import com.fares.saveme.feature.sharing.domain.model.InvitedRole
import com.fares.saveme.feature.sharing.domain.model.JoinResult
import com.fares.saveme.feature.sharing.domain.model.LeaveCheck
import com.fares.saveme.feature.sharing.domain.model.LeaveResult
import com.fares.saveme.feature.sharing.domain.model.EmailProblem
import com.fares.saveme.feature.sharing.domain.model.MemberActionResult
import com.fares.saveme.feature.sharing.domain.model.MemberView
import com.fares.saveme.feature.sharing.domain.model.PreviewResult
import com.fares.saveme.feature.sharing.domain.model.WithdrawResult
import com.fares.saveme.feature.wallets.domain.MemberRecord
import com.fares.saveme.feature.wallets.domain.WalletMemberships
import com.fares.saveme.feature.wallets.domain.WalletsService
import com.fares.saveme.feature.wallets.domain.model.WalletRole
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlin.uuid.Uuid

/**
 * The sharing domain API (contracts/sharing-domain.md). The screens' stores take its functions as lambdas (P-16.6). Invitations
 * are phone-created syncable records sent through the sync engine, per record (research R-03); role changes, removals,
 * leaving, joining and declining are online-only and never retried automatically (R-11, P-02.6). Nothing here logs an invitation
 * secret, a share link or an address (P-09.2).
 */
interface SharingService {
    // Owner side

    /** Pending invitations of the wallet, oldest first; a withdrawal that waits hides its row. */
    fun observeInvitations(walletId: String): Flow<List<InvitationView>>

    /** Owner first, then joining order. */
    fun observeMembers(walletId: String): Flow<List<MemberView>>

    /** Re-evaluated on entitlement, member and invitation changes (research R-06). */
    fun observeInviteCheck(walletId: String): Flow<InviteCheck>

    /** The plan's number of people when the owner's wallet holds more (or sharing is not included), else null (FR-028). */
    fun observePlanNote(walletId: String): Flow<Int?>

    suspend fun invite(walletId: String, draft: InvitationDraft): InviteResult

    /** After the supersede confirmation. */
    suspend fun inviteConfirmed(walletId: String, draft: InvitationDraft): InviteResult

    /** Waits a short while for a queued invitation to reach the server, so the screen can say "sent" or "saved". */
    suspend fun awaitDelivery(invitationId: String): Delivery

    suspend fun withdraw(invitationId: String): WithdrawResult

    /** Withdraws the invitation and creates a new link invitation with the same role. */
    suspend fun replaceLink(invitationId: String): InviteResult

    /** Whether this app session holds the link of the invitation (it arrived with the server's answer). */
    fun observeLinkHeld(invitationId: String): Flow<Boolean>

    /** The text to share for a link invitation whose link this session holds; null otherwise. Never logged. */
    suspend fun shareTextFor(invitationId: String): String?

    /** A refused create: removes the placeholder and the queued change. */
    suspend fun discard(invitationId: String)

    suspend fun tryAgain(invitationId: String)

    suspend fun changeRole(walletId: String, userId: String, role: WalletRole): MemberActionResult

    suspend fun remove(walletId: String, userId: String): MemberActionResult

    /** Sends what waits, then counts what still waits for the wallet. */
    suspend fun leaveCheck(walletId: String): LeaveCheck

    suspend fun leave(walletId: String): LeaveResult

    // Invitee side

    suspend fun preview(token: String): PreviewResult

    suspend fun join(token: String): JoinResult

    suspend fun decline(token: String): DeclineResult

    // Activity

    suspend fun activityPage(walletId: String, cursor: String?): ActivityPage
}

class DefaultSharingService(
    private val repository: InvitationRepository,
    private val invitationApi: InvitationApi,
    private val memberApi: MemberApi,
    private val activityApi: ActivityApi,
    private val engine: () -> SyncEngine,
    private val authState: StateFlow<AuthState>,
    private val entitlements: Entitlements,
    private val memberships: WalletMemberships,
    private val wallets: WalletsService,
    private val runner: SyncRunner,
    private val shareLinks: ShareLinks,
    private val heldInvitation: HeldInvitation,
    private val planNotes: PlanDemotionNotes,
    private val activityNames: ActivityNameSource,
    /** Builds the text shared with a link: app name, sentence and link only (FR-006). */
    private val buildShareText: suspend (url: String) -> String,
    /** Work that must outlive the screen that started it (the first read of a wallet just joined). */
    private val scope: CoroutineScope,
    private val ticks: Flow<Instant> = MinuteTicker().ticks(),
    private val clock: Clock = Clock.System,
    private val deliveryWait: Duration = 3.seconds,
    private val newId: () -> String = { Uuid.generateV7().toString() },
) : SharingService {

    private val signedIn: AuthState.SignedIn? get() = authState.value as? AuthState.SignedIn

    /** Wallets with a member action in flight: another is refused while it runs (FR-020). */
    private val busy = MutableStateFlow<Set<String>>(emptySet())

    /** Wallets whose latest role change about the person has been decided during the current walk of the activity. */
    private val decidedDemotion = mutableSetOf<String>()

    private fun walletScope(walletId: String) = SyncScope(ScopeKind.Wallet, walletId)

    // --- Owner side: reads ---

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observeInvitations(walletId: String): Flow<List<InvitationView>> =
        authState.map { (it as? AuthState.SignedIn)?.account?.userId }.distinctUntilChanged().flatMapLatest { account ->
            if (account == null) {
                flowOf(emptyList())
            } else {
                combine(
                    repository.observePending(account, walletId),
                    engine().observeAll(InvitationSyncHandler.RECORD_TYPE),
                    shareLinks.observeHeldIds(),
                    ticks,
                ) { rows, changes, held, now ->
                    val byId = changes.associateBy { it.recordId }
                    rows.mapNotNull { row -> row.toView(byId[row.id], row.id in held, now) }
                }
            }
        }

    private fun InvitationEntity.toView(change: PendingChange?, linkHeld: Boolean, now: Instant): InvitationView? {
        val queued = change?.state == ChangeState.Waiting || change?.state == ChangeState.Sending
        if (change?.isDelete == true && queued) return null
        val expiresAt = expiresAt?.let { runCatching { Instant.parse(it) }.getOrNull() }
        val refused = change?.takeIf { it.state == ChangeState.Refused }
        val channelKind = if (channel == InvitationRepository.CHANNEL_LINK) InvitationChannel.Link else InvitationChannel.Email
        return InvitationView(
            id = id, walletId = walletId, roleWire = role, channel = channelKind, email = inviteeEmail, expiresAt = expiresAt,
            waiting = change != null && !change.isDelete && queued,
            refusal = refused?.takeIf { !it.isDelete }?.refusal?.let { InvitationRefusal(it.kind(), it.requestId) }
                ?: refused?.takeIf { !it.isDelete }?.let { InvitationRefusal(InvitationRefusalKind.Other, null) },
            withdrawalRefused = refused?.isDelete == true,
            expired = expiresAt != null && expiresAt <= now,
            linkHeld = channelKind == InvitationChannel.Link && linkHeld,
        )
    }

    private fun com.fares.saveme.core.sync.Refusal.kind(): InvitationRefusalKind = when (code) {
        "EMAIL_NOT_VERIFIED" -> InvitationRefusalKind.NotVerified
        "FEATURE_NOT_ENTITLED" -> when (feature) {
            FEATURE_SHARING -> InvitationRefusalKind.SharingNotIncluded
            FEATURE_MEMBERS -> InvitationRefusalKind.PlanFull
            else -> InvitationRefusalKind.PlanOther
        }
        "FORBIDDEN" -> InvitationRefusalKind.NotOwner
        "NOT_FOUND" -> InvitationRefusalKind.NoLongerAvailable
        "VALIDATION_FAILED" -> InvitationRefusalKind.Invalid
        else -> InvitationRefusalKind.Other
    }

    override fun observeMembers(walletId: String): Flow<List<MemberView>> =
        combine(memberships.observe(walletId), authState) { records, auth ->
            val account = (auth as? AuthState.SignedIn)?.account
            val me = account?.userId
            records.map {
                MemberView(
                    it.userId, it.displayName, it.role, it.version, isYou = it.userId == me,
                    email = account?.email.takeIf { _ -> it.userId == me },
                    joinedAt = runCatching { Instant.parse(it.createdAt) }.getOrNull(),
                )
            }
        }

    override fun observeInviteCheck(walletId: String): Flow<InviteCheck> =
        combine(authState, entitlements.state, observeMembers(walletId), observeInvitations(walletId)) { auth, _, members, invitations ->
            val verified = (auth as? AuthState.SignedIn)?.account?.emailVerified == true
            InviteGate.check(
                verified = verified,
                sharingEnabled = entitlements.can(FlagFeature.SharingEnabled),
                quota = entitlements.quota(QuotaFeature.MembersPerWallet),
                peopleCount = peopleCount(members.size, invitations),
            )
        }

    /** The owner, the active members and the invitations that are neither expired, refused nor waiting to be withdrawn. */
    private fun peopleCount(memberCount: Int, invitations: List<InvitationView>): Int =
        memberCount.coerceAtLeast(1) + invitations.count { !it.expired && it.refusal == null }

    override fun observePlanNote(walletId: String): Flow<Int?> =
        combine(entitlements.state, observeMembers(walletId)) { _, members ->
            if (members.none { it.isYou && it.isOwner }) return@combine null
            val allowed = InviteGate.allowedPeople(entitlements.can(FlagFeature.SharingEnabled), entitlements.quota(QuotaFeature.MembersPerWallet))
            allowed?.takeIf { members.size > it }
        }

    // --- Owner side: invitations ---

    override suspend fun invite(walletId: String, draft: InvitationDraft): InviteResult = createInvitation(walletId, draft, confirmed = false)

    override suspend fun inviteConfirmed(walletId: String, draft: InvitationDraft): InviteResult = createInvitation(walletId, draft, confirmed = true)

    private suspend fun createInvitation(walletId: String, draft: InvitationDraft, confirmed: Boolean): InviteResult {
        val account = signedIn?.account ?: return InviteResult.NotOwner
        if (memberships.ownRole(walletId) != WalletRole.Owner) return InviteResult.NotOwner
        if (wallets.observeWallet(walletId).first()?.archived == true) return InviteResult.WalletArchived

        val email = draft.email?.let(InviteValidation::clean)
        if (email != null) {
            when (InviteValidation.validate(email, account.email)) {
                EmailProblem.Invalid -> return InviteResult.Invalid
                EmailProblem.OwnAddress -> return InviteResult.OwnAddress
                null -> Unit
            }
        }

        val invitations = observeInvitations(walletId).first()
        val earlier = email?.let { address ->
            invitations.firstOrNull { it.email != null && !it.expired && it.refusal == null && InviteValidation.sameAddress(it.email, address) }
        }
        val members = memberships.observe(walletId).first()
        // A confirmed supersede closes the earlier invitation in the same unit of work, so it frees its place.
        val people = peopleCount(members.size, invitations) - if (earlier != null) 1 else 0
        when (
            val gate = InviteGate.check(
                verified = account.emailVerified,
                sharingEnabled = entitlements.can(FlagFeature.SharingEnabled),
                quota = entitlements.quota(QuotaFeature.MembersPerWallet),
                peopleCount = people,
            )
        ) {
            InviteCheck.Allowed -> Unit
            InviteCheck.NotVerified -> return InviteResult.NotVerified
            InviteCheck.SharingNotIncluded -> return InviteResult.SharingNotIncluded
            is InviteCheck.Full -> return InviteResult.Full(gate.max)
        }
        if (earlier != null && !confirmed) return InviteResult.SupersedeNeeded

        val id = newId()
        repository.insertPlaceholder(id, walletId, account.userId, draft.role.wire, email)
        engine().enqueue(
            InvitationSyncHandler.RECORD_TYPE, id, walletScope(walletId), 0,
            AppJson.encodeToString(InvitationPayload.serializer(), InvitationPayload(draft.role.wire, email)),
        )
        runner.requestUpload()
        log.i { "invitation queued (${draft.channel})" }
        return InviteResult.Queued(id)
    }

    override suspend fun awaitDelivery(invitationId: String): Delivery =
        withTimeoutOrNull(deliveryWait) {
            engine().observe(InvitationSyncHandler.RECORD_TYPE, invitationId)
                .map { change ->
                    when {
                        change == null -> Delivery.Sent
                        change.state == ChangeState.Refused -> Delivery.Refused
                        else -> null
                    }
                }
                .filterNotNull()
                .first()
        } ?: Delivery.Waiting

    override suspend fun withdraw(invitationId: String): WithdrawResult {
        val row = repository.get(invitationId) ?: return WithdrawResult.Gone
        if (memberships.ownRole(row.walletId) != WalletRole.Owner) return WithdrawResult.NotOwner
        // A never-sent create is dropped with nothing sent (the engine discards its queued create); a sent one queues a deletion.
        engine().enqueueDelete(InvitationSyncHandler.RECORD_TYPE, invitationId, walletScope(row.walletId), row.version)
        shareLinks.forget(invitationId)
        if (row.version == 0L) repository.removePlaceholder(invitationId) else runner.requestUpload()
        log.i { "invitation withdrawn" }
        return WithdrawResult.Withdrawn
    }

    override suspend fun replaceLink(invitationId: String): InviteResult {
        val row = repository.get(invitationId) ?: return InviteResult.NotOwner
        val role = InvitedRole.fromWire(row.role) ?: InvitedRole.Viewer
        if (withdraw(invitationId) != WithdrawResult.Withdrawn) return InviteResult.NotOwner
        return invite(row.walletId, InvitationDraft(role, null))
    }

    override fun observeLinkHeld(invitationId: String): Flow<Boolean> = shareLinks.observeHeldIds().map { invitationId in it }.distinctUntilChanged()

    override suspend fun shareTextFor(invitationId: String): String? = shareLinks.get(invitationId)?.let { buildShareText(it) }

    override suspend fun discard(invitationId: String) {
        engine().discard(InvitationSyncHandler.RECORD_TYPE, invitationId)
        if (repository.get(invitationId)?.version == 0L) repository.removePlaceholder(invitationId)
    }

    override suspend fun tryAgain(invitationId: String) {
        val change = engine().get(InvitationSyncHandler.RECORD_TYPE, invitationId) ?: return
        if (change.isDelete) {
            engine().enqueueDelete(change.recordType, invitationId, change.scope, change.baseVersion)
        } else {
            engine().enqueue(change.recordType, invitationId, change.scope, change.baseVersion, change.payloadJson)
        }
        runner.requestUpload()
    }

    // --- Members (online only) ---

    override suspend fun changeRole(walletId: String, userId: String, role: WalletRole): MemberActionResult {
        val member = when (val target = target(walletId, userId)) {
            is Target.Refused -> return target.result
            is Target.Ok -> target.member
        }
        if (member.role == role.wire) return MemberActionResult.Done
        val me = signedIn ?: return MemberActionResult.Failed(null)
        if (!me.account.emailVerified) return MemberActionResult.NotVerified
        if (role == WalletRole.Editor) {
            // Raising is plan-checked; lowering never is (FR-017).
            if (!entitlements.can(FlagFeature.SharingEnabled)) return MemberActionResult.NotEntitled(FEATURE_SHARING)
            val people = memberships.observe(walletId).first().size
            val allowed = InviteGate.allowedPeople(true, entitlements.quota(QuotaFeature.MembersPerWallet))
            if (allowed != null && people > allowed) return MemberActionResult.NotEntitled(FEATURE_MEMBERS)
        }
        return withBusy(walletId, MemberActionResult.Busy) {
            when (val answer = memberApi.changeRole(walletId, userId, role.wire, member.version)) {
                is ApiResult.Success -> {
                    memberships.store(answer.value.toRecord())
                    log.i { "member role changed" }
                    MemberActionResult.Done
                }
                else -> memberFailure(walletId, answer)
            }
        }
    }

    override suspend fun remove(walletId: String, userId: String): MemberActionResult {
        val member = when (val target = target(walletId, userId)) {
            is Target.Refused -> return target.result
            is Target.Ok -> target.member
        }
        val me = signedIn ?: return MemberActionResult.Failed(null)
        if (!me.account.emailVerified) return MemberActionResult.NotVerified
        return withBusy(walletId, MemberActionResult.Busy) {
            when (val answer = memberApi.remove(walletId, userId, member.version)) {
                is ApiResult.Success -> {
                    memberships.store(answer.value.toRecord().copy(deleted = true))
                    log.i { "member removed" }
                    MemberActionResult.Done
                }
                else -> memberFailure(walletId, answer)
            }
        }
    }

    private sealed interface Target {
        data class Ok(val member: MemberRecord) : Target
        data class Refused(val result: MemberActionResult) : Target
    }

    /** The caller must own the wallet and the target must be another, non-owner member; otherwise the result says why. */
    private suspend fun target(walletId: String, userId: String): Target {
        if (memberships.ownRole(walletId) != WalletRole.Owner) return Target.Refused(MemberActionResult.OnlyOwner)
        val member = memberships.observe(walletId).first().firstOrNull { it.userId == userId } ?: return Target.Refused(MemberActionResult.Gone)
        if (member.role == WalletRole.Owner.wire || userId == signedIn?.account?.userId) return Target.Refused(MemberActionResult.OwnerRole)
        return Target.Ok(member)
    }

    /** One member action per wallet at a time (FR-020): another one meets [whenBusy]. */
    private suspend fun <T> withBusy(walletId: String, whenBusy: T, block: suspend () -> T): T {
        while (true) {
            val current = busy.value
            if (walletId in current) return whenBusy
            if (busy.compareAndSet(current, current + walletId)) break
        }
        try {
            return block()
        } finally {
            busy.update { it - walletId }
        }
    }

    private suspend fun memberFailure(walletId: String, answer: ApiResult<*>): MemberActionResult = when (answer) {
        ApiResult.Offline, ApiResult.Timeout -> MemberActionResult.Offline
        is ApiResult.Failure -> {
            log.i { "member action refused: ${answer.problem.code.ifEmpty { "none" }} (request ${answer.problem.requestId})" }
            when {
                answer.code == ApiErrorCode.VERSION_CONFLICT -> {
                    storeCurrent(answer)
                    readWallet(walletId)
                    MemberActionResult.Changed
                }
                answer.code == ApiErrorCode.FORBIDDEN -> {
                    readWallet(walletId)
                    MemberActionResult.OnlyOwner
                }
                answer.code == ApiErrorCode.FEATURE_NOT_ENTITLED -> MemberActionResult.NotEntitled(answer.problem.feature)
                answer.code == ApiErrorCode.EMAIL_NOT_VERIFIED -> MemberActionResult.NotVerified
                answer.code == ApiErrorCode.NOT_FOUND -> {
                    readWallet(walletId)
                    MemberActionResult.Gone
                }
                answer.code == ApiErrorCode.VALIDATION_FAILED &&
                    answer.problem.errors.orEmpty().any { it.pointer == "/role" && it.code == "IMMUTABLE_FIELD" } -> MemberActionResult.OwnerRole
                else -> MemberActionResult.Failed(answer.problem.requestId)
            }
        }
        is ApiResult.Success -> MemberActionResult.Failed(null)
    }

    /** The server's present copy of the member (a `409` carries it); a tombstone removes the row. */
    private suspend fun storeCurrent(failure: ApiResult.Failure) {
        val current = failure.problem.current ?: return
        val member = runCatching { AppJson.decodeFromJsonElement(MemberDto.serializer(), current) }.getOrNull() ?: return
        memberships.store(member.toRecord())
    }

    private suspend fun readWallet(walletId: String) {
        runner.refresh(ReadTarget.Wallet(walletId), force = true)
    }

    // --- Leave (online only) ---

    override suspend fun leaveCheck(walletId: String): LeaveCheck {
        if (memberships.ownRole(walletId) == WalletRole.Owner) return LeaveCheck.Owner
        // An upload comes first (a refresh sends what waits, then reads); a failure of the read is the leave's to report.
        if (runner.refresh(ReadTarget.Wallet(walletId), force = true) == ReadOutcome.Offline) return LeaveCheck.Offline
        return LeaveCheck.Ready(engine().pendingIn(walletScope(walletId)).size)
    }

    override suspend fun leave(walletId: String): LeaveResult {
        if (memberships.ownRole(walletId) == WalletRole.Owner) return LeaveResult.OwnerCannotLeave
        val version = memberships.ownMembershipVersion(walletId) ?: return LeaveResult.Failed(null)
        val name = wallets.observeWallet(walletId).first()?.shown?.name.orEmpty()
        return withBusy<LeaveResult>(walletId, LeaveResult.Failed(null)) {
            when (val answer = memberApi.leave(walletId, version)) {
                is ApiResult.Success -> {
                    memberships.store(answer.value.toRecord().copy(deleted = true))
                    memberships.leftWallet(walletId)
                    log.i { "left a wallet" }
                    LeaveResult.Left(name)
                }
                ApiResult.Offline, ApiResult.Timeout -> LeaveResult.Offline
                is ApiResult.Failure -> when (answer.code) {
                    // Already ended: done (FR-019).
                    ApiErrorCode.NOT_FOUND -> {
                        memberships.leftWallet(walletId)
                        LeaveResult.Left(name)
                    }
                    ApiErrorCode.FORBIDDEN -> LeaveResult.OwnerCannotLeave
                    ApiErrorCode.VERSION_CONFLICT -> {
                        storeCurrent(answer)
                        readWallet(walletId)
                        LeaveResult.Changed
                    }
                    else -> LeaveResult.Failed(answer.problem.requestId)
                }
            }
        }
    }

    // --- Invitee side (online only, never retried automatically) ---

    override suspend fun preview(token: String): PreviewResult = when (val answer = invitationApi.preview(token)) {
        is ApiResult.Success -> PreviewResult.Ready(answer.value.toPreview())
        ApiResult.Offline, ApiResult.Timeout -> PreviewResult.Offline
        is ApiResult.Failure -> when (val failure = tokenFailure(answer)) {
            TokenFailure.Unavailable -> PreviewResult.Unavailable
            TokenFailure.Invalid -> PreviewResult.InvalidLink
            is TokenFailure.TooMany -> PreviewResult.TooMany(failure.minutes)
            else -> PreviewResult.Failed(answer.problem.requestId)
        }
    }

    override suspend fun join(token: String): JoinResult = when (val answer = invitationApi.accept(token)) {
        is ApiResult.Success -> {
            val member = answer.value
            // The row makes the wallet followed; its first read runs on, whichever screen is open (R-10).
            memberships.store(member.toRecord())
            heldInvitation.drop()
            scope.launch { runner.refresh(ReadTarget.Wallet(member.walletId), force = true) }
            log.i { "invitation accepted" }
            JoinResult.Joined(member.walletId)
        }
        ApiResult.Offline, ApiResult.Timeout -> JoinResult.Offline
        is ApiResult.Failure -> {
            log.i { "join refused: ${answer.problem.code.ifEmpty { "none" }} (request ${answer.problem.requestId})" }
            when (val failure = tokenFailure(answer)) {
                TokenFailure.Unavailable -> JoinResult.Unavailable
                TokenFailure.Invalid -> JoinResult.InvalidLink
                is TokenFailure.TooMany -> JoinResult.TooMany(failure.minutes)
                TokenFailure.OwnerPlan -> JoinResult.OwnerPlanFull
                TokenFailure.Other -> JoinResult.Failed(answer.problem.requestId)
            }
        }
    }

    override suspend fun decline(token: String): DeclineResult = when (val answer = invitationApi.decline(token)) {
        is ApiResult.Success -> {
            heldInvitation.drop()
            log.i { "invitation declined" }
            DeclineResult.Declined
        }
        ApiResult.Offline, ApiResult.Timeout -> DeclineResult.Offline
        is ApiResult.Failure -> when (val failure = tokenFailure(answer)) {
            TokenFailure.Unavailable -> DeclineResult.Unavailable
            TokenFailure.Invalid -> DeclineResult.InvalidLink
            is TokenFailure.TooMany -> DeclineResult.TooMany(failure.minutes)
            else -> DeclineResult.Failed(answer.problem.requestId)
        }
    }

    private sealed interface TokenFailure {
        data object Unavailable : TokenFailure
        data object Invalid : TokenFailure
        data class TooMany(val minutes: Int) : TokenFailure
        data object OwnerPlan : TokenFailure
        data object Other : TokenFailure
    }

    /** One message for every `404` (P-09.4); the codes decide, never the status or the detail (P-02.4). */
    private fun tokenFailure(answer: ApiResult.Failure): TokenFailure = when (answer.code) {
        ApiErrorCode.NOT_FOUND -> TokenFailure.Unavailable
        ApiErrorCode.VALIDATION_FAILED -> TokenFailure.Invalid
        ApiErrorCode.RATE_LIMITED -> TokenFailure.TooMany((((answer.retryAfterSeconds ?: 60L) + 59L) / 60L).coerceIn(1L, 999L).toInt())
        ApiErrorCode.FEATURE_NOT_ENTITLED -> TokenFailure.OwnerPlan
        else -> TokenFailure.Other
    }

    // --- Activity (online only, not stored) ---

    override suspend fun activityPage(walletId: String, cursor: String?): ActivityPage {
        var restarted = false
        var answer = activityApi.page(walletId, cursor)
        if (cursor != null && answer is ApiResult.Failure && answer.code == ApiErrorCode.INVALID_CURSOR) {
            // The position is no longer valid: start again from the first page, once (FR-026).
            restarted = true
            answer = activityApi.page(walletId, null)
        }
        return when (answer) {
            is ApiResult.Success -> {
                val page = answer.value
                if (cursor == null || restarted) decidedDemotion.remove(walletId)
                recordDemotion(walletId, page.items)
                val names = activityNames.namesFor(walletId, page.items)
                ActivityPage.Loaded(page.items.mapNotNull { ActivityMapping.map(it, names) }, page.nextCursor.takeIf { page.hasMore }, restarted)
            }
            ApiResult.Offline, ApiResult.Timeout -> ActivityPage.Offline
            is ApiResult.Failure -> when (answer.code) {
                ApiErrorCode.NOT_FOUND -> {
                    // The wallet's own feed answers 404 too, and that read removes it from the phone (FR-026).
                    readWallet(walletId)
                    ActivityPage.Gone
                }
                else -> ActivityPage.Failed(answer.problem.requestId)
            }
        }
    }

    /** The newest role change about the person decides whether the note names the owner's plan (FR-027). */
    private fun recordDemotion(walletId: String, entries: List<com.fares.saveme.feature.sharing.data.dto.ActivityEntryDto>) {
        if (walletId in decidedDemotion) return
        val me = signedIn?.account?.userId ?: return
        val limited = ActivityMapping.limitedByPlan(entries, me) ?: return
        decidedDemotion += walletId
        planNotes.record(walletId, limited)
    }

    private fun InvitationPreviewDto.toPreview() = InvitationPreview(
        invitationId = invitationId, walletId = walletId, walletName = walletName, roleWire = role, inviterName = invitedByDisplayName?.takeIf { it.isNotBlank() },
        expiresAt = runCatching { Instant.parse(expiresAt) }.getOrNull(), alreadyMember = alreadyMember,
    )

    private fun MemberDto.toRecord() = MemberRecord(id, walletId, memberUserId, displayName, role, version, createdAt, deleted = deletedAt != null)

    private companion object {
        val log = Logger.withTag("sharing")
        const val FEATURE_SHARING = "sharing.enabled"
        const val FEATURE_MEMBERS = "sharing.max_members_per_wallet"
    }
}
