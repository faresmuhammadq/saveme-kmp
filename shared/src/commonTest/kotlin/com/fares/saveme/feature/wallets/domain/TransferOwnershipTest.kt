package com.fares.saveme.feature.wallets.domain

import com.fares.saveme.feature.auth.jsonResponse
import com.fares.saveme.feature.auth.problemBody
import com.fares.saveme.feature.wallets.WalletTestEnv
import com.fares.saveme.feature.wallets.domain.model.TransferResult
import com.fares.saveme.feature.wallets.memberJson
import com.fares.saveme.feature.wallets.pageOf
import com.fares.saveme.feature.wallets.walletBody
import com.fares.saveme.feature.wallets.walletJson
import io.ktor.http.HttpMethod
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** Research R-15: online only, never retried, and every outcome ends with a fresh look at the wallet. */
class TransferOwnershipTest {
    private val members = pageOf(listOf(memberJson("m1", "u2", "Sara", "owner"), memberJson("m2", "u1", "Me", "editor")))

    @Test
    fun offlineChangesNothing() = runTest {
        val env = WalletTestEnv(backgroundScope) { throw kotlinx.io.IOException("offline") }
        env.signedIn()
        env.seed(walletJson(version = 3))
        assertEquals(TransferResult.Offline, env.service.transferOwnership("w1", "u2"))
        assertEquals("owner", env.walletDao.rows.value.getValue("w1").myRole)
        assertEquals(1, env.requests.size)
    }

    @Test
    fun successStoresTheWalletAsEditorAndRefreshesMembers() = runTest {
        val env = WalletTestEnv(backgroundScope) { request ->
            if (request.method == HttpMethod.Post) jsonResponse(200, walletBody(walletJson(role = "editor", version = 4))) else jsonResponse(200, members)
        }
        env.signedIn()
        env.seed(walletJson(version = 3))
        assertEquals(TransferResult.Done, env.service.transferOwnership("w1", "u2"))
        assertEquals("editor", env.walletDao.rows.value.getValue("w1").myRole)
        assertEquals(listOf("m1", "m2"), env.repository.observeMembers("w1").first().map { it.id })
    }

    @Test
    fun aTargetWhoLeftIsNoLongerAMemberAndTheViewRefreshes() = runTest {
        val env = WalletTestEnv(backgroundScope) { request ->
            when {
                request.method == HttpMethod.Post ->
                    jsonResponse(422, problemBody(422, "VALIDATION_FAILED", ""","errors":[{"pointer":"/user_id","code":"INVALID_ENUM_VALUE"}]"""))
                request.url.encodedPath.endsWith("/members") -> jsonResponse(200, members)
                else -> jsonResponse(200, walletBody(walletJson(version = 5)))
            }
        }
        env.signedIn()
        env.seed(walletJson(version = 3))
        assertEquals(TransferResult.NoLongerMember, env.service.transferOwnership("w1", "u9"))
        assertEquals(5L, env.walletDao.rows.value.getValue("w1").version)
        assertTrue(env.requests.any { it.url.encodedPath.endsWith("/members") })
    }

    @Test
    fun aVersionConflictIsChangedAndRefreshes() = runTest {
        val env = WalletTestEnv(backgroundScope) { request ->
            if (request.method == HttpMethod.Post) jsonResponse(409, problemBody(409, "VERSION_CONFLICT", ""","current":${walletJson(version = 9)}"""))
            else if (request.url.encodedPath.endsWith("/members")) jsonResponse(200, members)
            else jsonResponse(200, walletBody(walletJson(version = 9)))
        }
        env.signedIn()
        env.seed(walletJson(version = 3))
        assertEquals(TransferResult.Changed, env.service.transferOwnership("w1", "u2"))
        assertEquals(9L, env.walletDao.rows.value.getValue("w1").version)
    }

    @Test
    fun forbiddenIsNotOwnerAndNothingIsRetried() = runTest {
        val env = WalletTestEnv(backgroundScope) { jsonResponse(403, problemBody(403, "FORBIDDEN")) }
        env.signedIn()
        env.seed(walletJson(version = 3))
        assertEquals(TransferResult.NotOwner, env.service.transferOwnership("w1", "u2"))
        assertEquals(1, env.requests.size)
    }

    @Test
    fun otherFailuresCarryTheRequestId() = runTest {
        val env = WalletTestEnv(backgroundScope) { jsonResponse(500, problemBody(500, "INTERNAL_ERROR")) }
        env.signedIn()
        env.seed(walletJson(version = 3))
        val failed = assertIs<TransferResult.Failed>(env.service.transferOwnership("w1", "u2"))
        assertEquals("req-42", failed.requestId)
    }

    @Test
    fun anEditorCannotTransfer() = runTest {
        val env = WalletTestEnv(backgroundScope) { jsonResponse(200, members) }
        env.signedIn()
        env.seed(walletJson(version = 3, role = "editor"))
        assertEquals(TransferResult.NotOwner, env.service.transferOwnership("w1", "u2"))
        assertEquals(0, env.requests.size)
    }
}
