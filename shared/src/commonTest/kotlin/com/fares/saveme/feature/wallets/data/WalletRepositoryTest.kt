package com.fares.saveme.feature.wallets.data

import com.fares.saveme.core.network.ApiResult
import com.fares.saveme.core.sync.ScopeKind
import com.fares.saveme.core.sync.SyncScope
import com.fares.saveme.feature.auth.jsonResponse
import com.fares.saveme.feature.auth.problemBody
import com.fares.saveme.feature.wallets.WalletTestEnv
import com.fares.saveme.feature.wallets.domain.model.WalletDraft
import com.fares.saveme.feature.wallets.memberJson
import com.fares.saveme.feature.wallets.pageOf
import com.fares.saveme.feature.wallets.walletBody
import com.fares.saveme.feature.wallets.walletJson
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class WalletRepositoryTest {
    private val draft = WalletDraft("cash", "Pocket", "EGP", 25_000, null, null)

    @Test
    fun aPlaceholderIsOwnerWithBalanceEqualToOpening() = runTest {
        val env = WalletTestEnv(backgroundScope) { jsonResponse(200, walletBody()) }
        env.repository.insertPlaceholder("p1", draft, "u1")
        val row = env.walletDao.rows.value.getValue("p1")
        assertEquals("owner", row.myRole)
        assertEquals(0L, row.version)
        assertEquals(25_000L, row.balanceMinor)
        assertEquals(false, row.currencyLocked)
    }

    @Test
    fun clearEmptiesBothTables() = runTest {
        val env = WalletTestEnv(backgroundScope) { jsonResponse(200, walletBody()) }
        env.seed()
        env.memberDao.insertAll(listOf(com.fares.saveme.feature.wallets.data.local.WalletMemberEntity("m1", "w1", "u1", null, "owner", 1, "2026-09-01T00:00:00Z")))
        env.repository.clear()
        assertEquals(emptyMap(), env.walletDao.rows.value)
        assertEquals(emptyList(), env.memberDao.rows.value)
    }
}
