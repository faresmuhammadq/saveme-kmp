package com.fares.saveme.feature.account

import com.fares.saveme.core.network.AppJson
import com.fares.saveme.core.sync.FeedPuller
import com.fares.saveme.core.sync.HttpSyncApi
import com.fares.saveme.core.sync.ScopeKind
import com.fares.saveme.core.sync.ScopeRead
import com.fares.saveme.core.sync.SyncLock
import com.fares.saveme.core.sync.SyncScope
import com.fares.saveme.feature.account.CapturedFormerMemberResponses as F
import com.fares.saveme.feature.auth.jsonResponse
import com.fares.saveme.feature.billing.Captured
import com.fares.saveme.feature.sharing.SharingTestEnv
import com.fares.saveme.feature.sharing.data.InvitationFeedApplier
import com.fares.saveme.feature.transactions.domain.addedByOf
import com.fares.saveme.feature.transactions.domain.model.AddedBy
import com.fares.saveme.feature.wallets.domain.MemberRecord
import com.fares.saveme.feature.wallets.domain.model.WalletRole
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * After the owner's purge (spec 022, live run in qa-report.md section 3.6) the partners' wallet feeds, captured from the live
 * backend, replayed through the real feed puller and appliers, the members service and the "added by" mapping (SC-007, US6).
 */
class FormerMemberLiveReplayTest {

    private fun parse(c: Captured): JsonObject = AppJson.parseToJsonElement(c.body).jsonObject

    private fun feedEnv(scope: CoroutineScope, userId: String, feed: Captured) =
        SharingTestEnv(scope, userId = userId) { jsonResponse(feed.status, feed.body) }

    private fun SharingTestEnv.puller(): FeedPuller = FeedPuller(
        HttpSyncApi(auth.client, auth.events, batchTimeoutMillis = null), scopeDao,
        {
            listOf(
                InvitationFeedApplier(repository),
                com.fares.saveme.feature.wallets.data.WalletMemberFeedApplier(walletRepository),
                com.fares.saveme.feature.wallets.data.WalletFeedApplier(walletRepository),
                com.fares.saveme.feature.wallets.data.OwnMembershipFeedApplier(walletRepository),
            )
        },
        { com.fares.saveme.feature.wallets.domain.DefaultWalletScopes(walletRepository, { userId }) }, SyncLock(), { userId }, clock,
    )

    /** The phone as it was before the purge: the departed owner, the person (as a viewer) and the others, all at version 1. */
    private suspend fun SharingTestEnv.before(wallet: String, ownRowId: String, departedRowId: String, others: List<Triple<String, String, String>>) {
        seedOwnerWallet(wallet, role = "viewer")
        memberships.store(MemberRecord("m-$userId", wallet, userId, "Sara", "viewer", 1, "2026-09-01T00:00:00Z", deleted = true))
        memberships.store(MemberRecord(ownRowId, wallet, userId, "ME", "viewer", 1, "2026-10-06T00:23:03Z"))
        memberships.store(MemberRecord(departedRowId, wallet, F.DEPARTED, "OWNQA", "owner", 1, "2026-10-06T00:19:35Z"))
        others.forEach { (id, user, name) -> memberships.store(MemberRecord(id, wallet, user, name, "editor", 1, "2026-10-06T00:23:02Z")) }
    }

    private fun transactionAuthors(feed: Captured): List<String> =
        parse(feed).getValue("data").jsonArray.map { it.jsonObject }.filter { it.getValue("type").jsonPrimitive.content == "transaction" }
            .map { it.getValue("record").jsonObject.getValue("created_by").jsonPrimitive.content }

    private suspend fun SharingTestEnv.names(wallet: String): Map<String, String?> =
        service.observeMembers(wallet).first().associate { it.userId to it.displayName }

    @Test
    fun theLiveSoleViewerBecomesOwnerAndTheDepartedOwnersEntriesReadAsAFormerMember() = runTest {
        val env = feedEnv(backgroundScope, F.PARTNER, F.partnerFeedAlphaAfter)
        env.signedIn()
        env.before(F.WALLET_ALPHA, "01a10e95-e09f-7098-b9e8-4617d4d432ef", "01a10e94-801c-7183-ad6c-36ecfafe6c4b", emptyList())
        assertIs<ScopeRead.Done>(env.puller().read(SyncScope(ScopeKind.Wallet, F.WALLET_ALPHA)))
        assertEquals("owner", env.walletDao.rows.value.getValue(F.WALLET_ALPHA).myRole)
        assertEquals(WalletRole.Owner, env.memberships.ownRole(F.WALLET_ALPHA))
        val members = env.service.observeMembers(F.WALLET_ALPHA).first()
        assertEquals(listOf(F.PARTNER), members.map { it.userId }, "the departed member's row is a tombstone: the list no longer holds them")
        val authors = transactionAuthors(F.partnerFeedAlphaAfter)
        assertEquals(listOf(F.DEPARTED), authors, "the transaction keeps its author's id")
        assertEquals(AddedBy.FormerMember, addedByOf(authors.single(), F.PARTNER, env.names(F.WALLET_ALPHA)))
    }

    @Test
    fun theLiveChosenViewerOwnsBetaTheEditorIsDemotedByThePlanAndTheDepartedOwnersEntriesAreFormerMemberOnes() = runTest {
        val env = feedEnv(backgroundScope, F.CHOSEN_VIEWER, F.newOwnerFeedBetaAfter)
        env.signedIn()
        env.before(
            F.WALLET_BETA, "01a10e97-aabe-73e3-8ed8-89063baa2c6f", "01a10e97-a7e0-7153-84af-a3faa50df7d3",
            listOf(Triple("01a10e97-a98e-73d9-b24d-1c8f4744865b", F.EDITOR, "EDIQA")),
        )
        assertIs<ScopeRead.Done>(env.puller().read(SyncScope(ScopeKind.Wallet, F.WALLET_BETA)))
        assertEquals("owner", env.walletDao.rows.value.getValue(F.WALLET_BETA).myRole)
        val members = env.service.observeMembers(F.WALLET_BETA).first()
        assertEquals(setOf(F.CHOSEN_VIEWER, F.EDITOR), members.map { it.userId }.toSet())
        assertEquals("viewer", members.single { it.userId == F.EDITOR }.roleWire, "the new owner's plan limits the others: nobody is removed")
        val names = env.names(F.WALLET_BETA)
        val (departed, edited) = transactionAuthors(F.newOwnerFeedBetaAfter).let { it.first() to it.last() }
        assertEquals(F.DEPARTED, departed)
        assertEquals(AddedBy.FormerMember, addedByOf(departed, F.CHOSEN_VIEWER, names))
        assertEquals(AddedBy.Member("EDIQA"), addedByOf(edited, F.CHOSEN_VIEWER, names), "a member still present keeps the name")
        assertFalse(members.any { it.userId == F.DEPARTED })
    }

    @Test
    fun theLiveFeedOfAMemberWhoseRoleChangedStillCarriesTheTombstoneAndTheRowsStayInSync() = runTest {
        val env = feedEnv(backgroundScope, F.EDITOR, F.otherMemberFeedBetaAfter)
        env.signedIn()
        env.before(
            F.WALLET_BETA, "01a10e97-a98e-73d9-b24d-1c8f4744865b", "01a10e97-a7e0-7153-84af-a3faa50df7d3",
            listOf(Triple("01a10e97-aabe-73e3-8ed8-89063baa2c6f", F.CHOSEN_VIEWER, "VIEQA")),
        )
        assertIs<ScopeRead.Done>(env.puller().read(SyncScope(ScopeKind.Wallet, F.WALLET_BETA)))
        assertEquals("viewer", env.walletDao.rows.value.getValue(F.WALLET_BETA).myRole)
        assertTrue(env.service.observeMembers(F.WALLET_BETA).first().none { it.userId == F.DEPARTED })
    }

    @Test
    fun theLiveMembersEndpointListsNoDepartedMemberToEitherPartner() {
        for (members in listOf(F.partnerMembersAlpha, F.newOwnerMembersBeta)) {
            val ids = parse(members).getValue("data").jsonArray.map { it.jsonObject.getValue("member_user_id").jsonPrimitive.content }
            assertFalse(F.DEPARTED in ids, "the server's own member list no longer contains the departed person")
            assertTrue(ids.isNotEmpty())
        }
    }
}
