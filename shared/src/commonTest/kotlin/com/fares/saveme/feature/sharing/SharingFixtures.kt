package com.fares.saveme.feature.sharing

import com.fares.saveme.core.network.AppJson
import com.fares.saveme.core.storage.StoredSession
import com.fares.saveme.core.sync.FakeClock
import com.fares.saveme.core.sync.FakePendingChangeDao
import com.fares.saveme.core.sync.FakeSyncRunner
import com.fares.saveme.core.sync.FakeSyncScheduler
import com.fares.saveme.core.sync.SyncEngine
import com.fares.saveme.feature.account.FakeSyncScopeDao
import com.fares.saveme.feature.auth.AuthTestEnv
import com.fares.saveme.feature.auth.CREDENTIAL
import com.fares.saveme.feature.auth.domain.AuthState
import com.fares.saveme.feature.auth.domain.model.AccountSnapshot
import com.fares.saveme.feature.auth.domain.model.AccountState
import com.fares.saveme.feature.auth.domain.model.FeatureValue
import com.fares.saveme.feature.auth.domain.model.RefreshOutcome
import com.fares.saveme.feature.billing.domain.EntitlementSet
import com.fares.saveme.feature.billing.domain.Entitlements
import com.fares.saveme.feature.billing.domain.FlagFeature
import com.fares.saveme.feature.billing.domain.QuotaFeature
import com.fares.saveme.feature.billing.domain.QuotaLimit
import com.fares.saveme.feature.sharing.data.ActivityApi
import com.fares.saveme.feature.sharing.data.InvitationApi
import com.fares.saveme.feature.sharing.data.InvitationRepository
import com.fares.saveme.feature.sharing.data.InvitationSyncHandler
import com.fares.saveme.feature.sharing.data.MemberApi
import com.fares.saveme.feature.sharing.data.dto.InvitationDto
import com.fares.saveme.feature.sharing.data.local.InvitationDao
import com.fares.saveme.feature.sharing.data.local.InvitationEntity
import com.fares.saveme.feature.sharing.domain.ActivityNameSource
import com.fares.saveme.feature.sharing.domain.ActivityNames
import com.fares.saveme.feature.sharing.domain.DefaultSharingService
import com.fares.saveme.feature.sharing.domain.HeldInvitation
import com.fares.saveme.feature.sharing.domain.PlanDemotionNotes
import com.fares.saveme.feature.sharing.domain.ShareLinks
import com.fares.saveme.feature.sharing.domain.ShareText
import com.fares.saveme.feature.wallets.FakeWalletDao
import com.fares.saveme.feature.wallets.FakeWalletMemberDao
import com.fares.saveme.feature.wallets.data.WalletApi
import com.fares.saveme.feature.wallets.data.WalletRepository
import com.fares.saveme.feature.wallets.data.WalletSyncHandler
import com.fares.saveme.feature.wallets.data.dto.WalletDto
import com.fares.saveme.feature.wallets.domain.DefaultWalletMemberships
import com.fares.saveme.feature.wallets.domain.DefaultWalletsService
import com.fares.saveme.feature.wallets.domain.LeftWallets
import com.fares.saveme.feature.wallets.domain.ZeroPendingPostings
import com.fares.saveme.feature.wallets.memberJson
import com.fares.saveme.feature.wallets.walletJson
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlin.time.Instant

/** The 43-character secret shape of sharing.yaml (a fake value; never a real token). */
const val FAKE_TOKEN = "q3W9v_2mZ0bXy8TfL1sKpA4eRgHuJcNdIoVwYzE5x7M"
const val FAKE_LINK = "https://app.example/invite?token=$FAKE_TOKEN"

/** In-memory stand-in that follows [InvitationDao]'s SQL (the SQL itself: InvitationDaoTest). */
class FakeInvitationDao : InvitationDao {
    val rows = MutableStateFlow<Map<String, InvitationEntity>>(emptyMap())

    override fun observePending(accountId: String, walletId: String): Flow<List<InvitationEntity>> = rows.map { all ->
        all.values.filter { it.accountId == accountId && it.walletId == walletId && it.status == "pending" }
            .sortedWith(compareBy({ it.createdAt }, { it.id }))
    }
    override suspend fun get(id: String): InvitationEntity? = rows.value[id]
    override suspend fun getForWallet(walletId: String): List<InvitationEntity> = rows.value.values.filter { it.walletId == walletId }
    override suspend fun getAll(): List<InvitationEntity> = rows.value.values.toList()
    override fun observeLivePendingCount(accountId: String, walletId: String, now: String): Flow<Int> = rows.map { all ->
        all.values.count { it.accountId == accountId && it.walletId == walletId && it.status == "pending" && (it.expiresAt == null || it.expiresAt > now) }
    }
    override suspend fun upsert(entity: InvitationEntity) { rows.value = rows.value + (entity.id to entity) }
    override suspend fun delete(id: String) { rows.value = rows.value - id }
    override suspend fun deleteForWallet(walletId: String) { rows.value = rows.value.filterValues { it.walletId != walletId } }
    override suspend fun deleteAll() { rows.value = emptyMap() }
}

private fun String?.q() = this?.let { "\"$it\"" } ?: "null"

/** sharing.yaml `Invitation`. */
fun invitationJson(
    id: String = "i1",
    walletId: String = "w1",
    role: String = "editor",
    email: String? = "karim@example.com",
    channel: String = if (email == null) "link" else "email",
    status: String = "pending",
    version: Long = 1,
    deletedAt: String? = null,
    expiresAt: String = "2026-10-09T10:00:00Z",
    createdAt: String = "2026-10-02T10:00:00Z",
    invitedBy: String = "u1",
) = """
    {"id":"$id","wallet_id":"$walletId","version":$version,"change_seq":9,"created_at":"$createdAt","updated_at":"$createdAt",
     "deleted_at":${deletedAt.q()},"role":"$role","channel":"$channel","invitee_email":${email.q()},"invited_by":"$invitedBy",
     "status":"$status","expires_at":"$expiresAt","something_new":1}
""".trimIndent()

fun invitationBody(json: String = invitationJson(), meta: String = "{}") = """{"data":$json,"meta":$meta}"""

fun invitationDto(json: String = invitationJson()): InvitationDto = AppJson.decodeFromString(InvitationDto.serializer(), json)

fun previewJson(
    walletName: String = "Home",
    inviter: String? = "Mona",
    role: String = "editor",
    already: Boolean = false,
) = """
    {"invitation_id":"i1","wallet_id":"w1","wallet_name":"$walletName","role":"$role","invited_by_display_name":${inviter.q()},
     "expires_at":"2026-10-09T10:00:00Z","already_member":$already,"something_new":true}
""".trimIndent()

fun dataBody(json: String) = """{"data":$json,"meta":{}}"""

fun activityJson(
    id: String = "a1",
    actorId: String? = "u2",
    actorName: String? = "Karim",
    action: String = "created",
    subjectType: String = "transaction",
    subjectId: String = "t1",
    role: String? = null,
    at: String = "2026-10-02T10:20:00Z",
) = """
    {"id":"$id","wallet_id":"w1","occurred_at":"$at","actor_user_id":${actorId.q()},"actor_display_name":${actorName.q()},
     "action":"$action","subject_type":"$subjectType","subject_id":"$subjectId","role":${role.q()},"something_new":1}
""".trimIndent()

fun activityPageBody(items: List<String>, next: String? = null) =
    """{"data":[${items.joinToString(",")}],"meta":{"next_cursor":${next.q()},"has_more":${next != null},"limit":50}}"""

val ownerAccount = AccountSnapshot("u1", "sara@example.com", true, AccountState.Active, null, "plus", emptyMap())

/** Entitlements scripted by the test: sharing on or off and the members quota (null = unlimited). */
class FakeSharingEntitlements(sharing: Boolean = true, max: Long? = 5) : Entitlements {
    override val state = MutableStateFlow<EntitlementSet?>(setOf(sharing, max))
    var refreshes = 0

    fun set(sharing: Boolean, max: Long?) {
        state.value = setOf(sharing, max)
    }

    override fun can(flag: FlagFeature) = state.value?.can(flag) ?: false
    override fun quota(quota: QuotaFeature): QuotaLimit = state.value?.quota(quota) ?: QuotaLimit.Limited(0)
    override fun allowsAnother(quota: QuotaFeature, currentCount: Int) = state.value?.allowsAnother(quota, currentCount) ?: false
    override suspend fun refreshAfterBillingAction(): RefreshOutcome { refreshes++; return RefreshOutcome.Done }

    private fun setOf(sharing: Boolean, max: Long?) = EntitlementSet(
        mapOf(
            FlagFeature.SharingEnabled.wireKey to FeatureValue.Flag(sharing),
            QuotaFeature.MembersPerWallet.wireKey to FeatureValue.Quota(max),
        ),
    )
}

/** Names scripted by the test. */
class FakeActivityNames(var names: ActivityNames = ActivityNames()) : ActivityNameSource {
    val asked = mutableListOf<String>()
    override suspend fun namesFor(walletId: String, entries: List<com.fares.saveme.feature.sharing.data.dto.ActivityEntryDto>): ActivityNames {
        asked += walletId
        return names
    }
}

/**
 * The sharing stack over a Ktor MockEngine, in-memory DAOs, the real sync engine with the invitation handler, the real wallets
 * repository and memberships, a fixed clock (2026-10-02 10:00 UTC) and one signed-in verified account `u1`, owner of wallet `w1`
 * when [seedOwnerWallet] is called.
 */
class SharingTestEnv(
    val scope: CoroutineScope,
    verified: Boolean = true,
    /** The signed-in person's id: `u1`, or a live account id when captured answers name one. */
    val userId: String = "u1",
    handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData = { throw kotlinx.io.IOException("offline") },
) {
    val auth = AuthTestEnv(handler)
    val invitationApi = InvitationApi(auth.client)
    val memberApi = MemberApi(auth.client)
    val activityApi = ActivityApi(auth.client)
    val invitationDao = FakeInvitationDao()
    val walletDao = FakeWalletDao()
    val memberDao = FakeWalletMemberDao()
    val scopeDao = FakeSyncScopeDao()
    val pendingDao = FakePendingChangeDao()
    val scheduler = FakeSyncScheduler()
    val clock = FakeClock()
    val shareLinks = ShareLinks()
    val held = HeldInvitation()
    val planNotes = PlanDemotionNotes()
    val left = LeftWallets()
    val entitlements = FakeSharingEntitlements()
    val names = FakeActivityNames()
    val removedWallets = mutableListOf<String>()
    val account = ownerAccount.copy(userId = userId, emailVerified = verified)
    val authState = MutableStateFlow<AuthState>(AuthState.SignedIn(account))

    val repository = InvitationRepository(
        invitationDao, accountId = { userId },
        hasQueuedChange = { id -> engine.get(InvitationSyncHandler.RECORD_TYPE, id) != null },
        clock = clock,
    )

    /** The stored version of the wallet the handler asks about; null = unknown. */
    var walletVersion: Long? = 3
    val syncHandler = InvitationSyncHandler(repository, invitationApi, onShareLink = { id, url -> shareLinks.offer(id, url) }, walletVersion = { walletVersion })
    val engine: SyncEngine = SyncEngine(pendingDao, scopeDao, listOf(syncHandler), scheduler, currentAccountId = { userId }, clock = clock)
    val runner = FakeSyncRunner(engine = engine, scope = scope)

    val walletRepository = WalletRepository(
        WalletApi(auth.client), walletDao, memberDao, scopeDao, accountId = { userId },
        hasQueuedChange = { id -> engine.get(WalletSyncHandler.RECORD_TYPE, id) != null },
        onWalletRemoved = { removedWallets += it },
        clock = clock,
    )
    val memberships = DefaultWalletMemberships(walletRepository, accountId = { userId }, left = left)
    val wallets = DefaultWalletsService(
        walletRepository, engine, authState, entitlements = com.fares.saveme.feature.wallets.FakeEntitlements(), postings = ZeroPendingPostings,
        defaultCurrency = flowOf("EGP"), runner = runner,
    )

    private var nextId = 0

    /** Set by a captured-answer test so the invitation the phone creates carries the id the live server answered for. */
    var idSupplier: (() -> String)? = null
    val ticks = MutableStateFlow<Instant>(clock.now)

    val service = DefaultSharingService(
        repository = repository, invitationApi = invitationApi, memberApi = memberApi, activityApi = activityApi,
        engine = { engine }, authState = authState, entitlements = entitlements, memberships = memberships, wallets = wallets,
        runner = runner, shareLinks = shareLinks, heldInvitation = held, planNotes = planNotes, activityNames = names,
        buildShareText = { url -> ShareText.build("Save Me", url, "%1\$s: I'd like to share a wallet with you. Open this link to join: %2\$s") },
        scope = scope, ticks = ticks, clock = clock, deliveryWait = kotlin.time.Duration.parse("PT2S"),
        newId = { idSupplier?.invoke() ?: "i-new-${++nextId}" },
    )

    val requests get() = auth.requests

    suspend fun signedIn() = auth.secureStore.write(StoredSession(CREDENTIAL, "a", Instant.parse("2026-09-26T10:00:00Z")))

    /** Wallet `w1` "Home" owned by `u1`, with the person's own member row. */
    suspend fun seedOwnerWallet(walletId: String = "w1", members: Int = 1, role: String = "owner") {
        walletRepository.storeServerCopy(AppJson.decodeFromString(WalletDto.serializer(), walletJson(walletId, name = "Home", role = role, version = 3)))
        walletRepository.storeMember(
            AppJson.decodeFromString(com.fares.saveme.feature.wallets.data.dto.WalletMemberDto.serializer(), memberJson("m-$userId", userId, "Sara", role, "2026-09-01T00:00:00Z", walletId)),
        )
        for (i in 2..members) {
            walletRepository.storeMember(
                AppJson.decodeFromString(
                    com.fares.saveme.feature.wallets.data.dto.WalletMemberDto.serializer(),
                    memberJson("m-u$i", "u$i", "Member $i", "editor", "2026-09-0${i}T00:00:00Z", walletId),
                ),
            )
        }
    }

    /** A synced invitation, as a feed read would have written it. */
    suspend fun seed(json: String = invitationJson()) {
        repository.storeServerCopy(invitationDto(json))
    }
}
