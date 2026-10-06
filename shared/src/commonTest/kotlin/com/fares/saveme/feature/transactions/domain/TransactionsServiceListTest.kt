package com.fares.saveme.feature.transactions.domain

import com.fares.saveme.core.network.AppJson
import com.fares.saveme.core.sync.ScopeKind
import com.fares.saveme.core.sync.SyncScope
import com.fares.saveme.feature.transactions.TransactionTestEnv
import com.fares.saveme.feature.transactions.data.dto.TransactionPayload
import com.fares.saveme.feature.transactions.domain.model.AddedBy
import com.fares.saveme.feature.transactions.domain.model.Counterpart
import com.fares.saveme.feature.transactions.domain.model.Direction
import com.fares.saveme.feature.transactions.domain.model.TransactionQuery
import com.fares.saveme.feature.transactions.domain.model.TransactionSyncState
import com.fares.saveme.feature.transactions.transactionJson
import com.fares.saveme.feature.wallets.walletJson
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** FR-015 to FR-020, FR-031: a wallet's list as the phone builds it. */
class TransactionsServiceListTest {
    private val w1 = SyncScope(ScopeKind.Wallet, "w1")

    private fun payload(amount: Long, date: String = "2026-10-01", category: String? = "c1", note: String? = null) =
        AppJson.encodeToString(TransactionPayload.serializer(), TransactionPayload("expense", date, amount, "EGP", category, description = note))

    @Test
    fun rowsAreGroupedByDayNewestFirstWithNetTotals() = runTest {
        val env = TransactionTestEnv(backgroundScope)
        env.seedWallet(walletJson(id = "w1"))
        env.seed(transactionJson(id = "a", date = "2026-10-01", amount = 1_000))
        env.seed(transactionJson(id = "b", date = "2026-10-02", amount = 5_000, kind = "income", categoryId = "ci"))
        env.seed(transactionJson(id = "c", date = "2026-10-02", amount = 700, createdAt = "2026-10-02T10:00:00Z"))
        val list = env.service.observeList("w1").first()
        assertEquals(listOf(LocalDate(2026, 10, 2), LocalDate(2026, 10, 1)), list.days.map { it.date })
        assertEquals(listOf("c", "b"), list.days[0].items.map { it.id })
        assertEquals(4_300, list.days[0].netMinor)
        assertEquals(-1_000, list.days[1].netMinor)
        assertEquals("EGP", list.currency)
        assertEquals(3, list.totalCount)
    }

    @Test
    fun aTransferShowsInBothWalletsWithItsDirectionAndTheOtherWalletsName() = runTest {
        val env = TransactionTestEnv(backgroundScope)
        env.seedWallet(walletJson(id = "w1", name = "Cash"))
        env.seedWallet(walletJson(id = "w2", name = "Bank"))
        env.seed(transactionJson(id = "tr", walletId = "w1", kind = "transfer", categoryId = null, counterWalletId = "w2", amount = 2_000))
        val out = env.service.observeList("w1").first().days.single().items.single()
        val inn = env.service.observeList("w2").first().days.single().items.single()
        assertEquals(Direction.Out, out.direction)
        assertEquals(Counterpart.Named("Bank"), out.counterpart)
        assertEquals(Direction.In, inn.direction)
        assertEquals(Counterpart.Named("Cash"), inn.counterpart)
        assertEquals(-2_000, env.service.observeList("w1").first().days.single().netMinor)
        assertEquals(2_000, env.service.observeList("w2").first().days.single().netMinor)
    }

    @Test
    fun aTransferWhoseOtherWalletIsNotOnThePhoneHasAnUnknownCounterpart() = runTest {
        val env = TransactionTestEnv(backgroundScope)
        env.seedWallet(walletJson(id = "w1"))
        env.seed(transactionJson(id = "tr", walletId = "w9", kind = "transfer", categoryId = null, counterWalletId = "w1"))
        assertEquals(Counterpart.Unknown, env.service.observeList("w1").first().days.single().items.single().counterpart)
    }

    @Test
    fun aWaitingEditShowsItsNewValuesAndAWaitingDeletionHidesTheRowAndAPlaceholderIsListed() = runTest {
        val env = TransactionTestEnv(backgroundScope)
        env.seedWallet(walletJson(id = "w1"))
        env.seed(transactionJson(id = "edited", amount = 1_000, version = 2))
        env.seed(transactionJson(id = "deleted", amount = 2_000, version = 2))
        env.engine.enqueue("transaction", "edited", w1, 2, payload(1_500, note = "New"))
        env.engine.enqueueDelete("transaction", "deleted", w1, 2)
        env.service.create(
            com.fares.saveme.feature.transactions.domain.model.TransactionDraft(
                "w1", "expense", LocalDate(2026, 10, 3), 300, "EGP", "c1", null, null, null, null, false,
            ),
        )
        val items = env.service.observeList("w1").first().days.flatMap { it.items }
        assertEquals(setOf("edited", "t-new-1"), items.map { it.id }.toSet())
        val edited = items.first { it.id == "edited" }
        assertEquals(1_500, edited.shown.amountMinor)
        assertEquals("New", edited.shown.description)
        assertEquals(TransactionSyncState.Waiting, edited.syncState)
        assertNull(items.first { it.id == "t-new-1" }.server)
    }

    @Test
    fun aWalletHiddenByItsOwnWaitingDeletionHasNoTransactions() = runTest {
        val env = TransactionTestEnv(backgroundScope)
        env.seedWallet(walletJson(id = "w1"))
        env.seed(transactionJson(id = "a"))
        env.engine.enqueueDelete("wallet", "w1", w1, 1)
        val list = env.service.observeList("w1").first()
        assertTrue(list.days.isEmpty())
        assertFalse(list.canAdd)
    }

    @Test
    fun addedByNamesTheMemberOrSaysAFormerMemberAndTheRecurringLinkShows() = runTest {
        val env = TransactionTestEnv(backgroundScope)
        env.seedWallet(walletJson(id = "w1"))
        env.seedMember("u2", "Omar")
        env.seed(transactionJson(id = "mine", createdBy = "u1"))
        env.seed(transactionJson(id = "omar", createdBy = "u2"))
        env.seed(transactionJson(id = "other", createdBy = "u3", recurringRuleId = "r1"))
        val byId = env.service.observeList("w1").first().days.flatMap { it.items }.associateBy { it.id }
        assertEquals(AddedBy.You, byId.getValue("mine").addedBy)
        assertEquals(AddedBy.Member("Omar"), byId.getValue("omar").addedBy)
        // The wallet's members the phone holds do not include u3, so they left (spec 016 FR-033).
        assertEquals(AddedBy.FormerMember, byId.getValue("other").addedBy)
        assertTrue(byId.getValue("other").fromRecurringRule)
        assertFalse(byId.getValue("mine").fromRecurringRule)
    }

    @Test
    fun withNoMemberRecordsAtAllTheAuthorStaysAnotherMember() = runTest {
        val env = TransactionTestEnv(backgroundScope)
        env.seedWallet(walletJson(id = "w1"))
        env.seed(transactionJson(id = "other", createdBy = "u3"))
        val byId = env.service.observeList("w1").first().days.flatMap { it.items }.associateBy { it.id }
        assertEquals(AddedBy.AnotherMember, byId.getValue("other").addedBy)
    }

    @Test
    fun canAddFollowsTheRoleAndTheArchivedState() = runTest {
        val env = TransactionTestEnv(backgroundScope)
        env.seedWallet(walletJson(id = "own", role = "owner"))
        env.seedWallet(walletJson(id = "ed", role = "editor"))
        env.seedWallet(walletJson(id = "vw", role = "viewer"))
        env.seedWallet(walletJson(id = "ar", role = "owner", archivedAt = "2026-09-30T00:00:00Z"))
        assertEquals(listOf(true, true, false, false), listOf("own", "ed", "vw", "ar").map { env.service.observeList(it).first().canAdd })
    }

    @Test
    fun editAndDeleteRightsFollowTheRoleAndAViewerSeesItReadOnly() = runTest {
        val env = TransactionTestEnv(backgroundScope)
        env.seedWallet(walletJson(id = "w1", role = "editor"))
        env.seedWallet(walletJson(id = "w2", role = "viewer"))
        env.seed(transactionJson(id = "a", walletId = "w1"))
        env.seed(transactionJson(id = "v", walletId = "w2"))
        val a = env.service.observeTransaction("a", "w1").first()!!
        val v = env.service.observeTransaction("v", "w2").first()!!
        assertTrue(a.canEdit && a.canDelete)
        assertFalse(v.canEdit || v.canDelete)
    }

    @Test
    fun theLoadedFlagIsFalseBeforeAnyReadAndRowsOrAReadMakeItTrue() = runTest {
        val env = TransactionTestEnv(backgroundScope)
        env.seedWallet(walletJson(id = "w1"))
        assertFalse(env.service.observeList("w1").first().loaded)
        env.seed(transactionJson(id = "a"))
        assertTrue(env.service.observeList("w1").first().loaded)
    }

    @Test
    fun searchRunsOverTheShownValuesIncludingAWaitingEdit() = runTest {
        val env = TransactionTestEnv(backgroundScope)
        env.seedWallet(walletJson(id = "w1"))
        env.seed(transactionJson(id = "a", description = "Plumber", version = 2))
        env.seed(transactionJson(id = "b", description = "Taxi", version = 2))
        env.engine.enqueue("transaction", "b", w1, 2, payload(100, note = "Plumber again"))
        val hits = env.service.observeList("w1", TransactionQuery(search = "plumber")).first().days.flatMap { it.items }.map { it.id }
        assertEquals(setOf("a", "b"), hits.toSet())
        val list = env.service.observeList("w1", TransactionQuery(search = "zzz")).first()
        assertTrue(list.days.isEmpty())
        assertEquals(2, list.totalCount)
        assertNotNull(list.categoryIds)
        assertIs<Set<String>>(list.categoryIds)
    }
}
