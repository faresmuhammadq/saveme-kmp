package com.fares.saveme.feature.account.domain

import com.fares.saveme.core.database.SyncScopeEntity
import com.fares.saveme.feature.account.AccountTestEnv
import com.fares.saveme.feature.account.domain.model.ActionOutcome
import com.fares.saveme.feature.account.domain.usecase.EndSessionUseCase
import com.fares.saveme.feature.account.pageBody
import com.fares.saveme.feature.account.sessionJson
import com.fares.saveme.feature.auth.jsonResponse
import com.fares.saveme.feature.auth.problemBody
import io.ktor.http.HttpMethod
import io.ktor.utils.io.errors.IOException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class SessionRefresherTest {

    @Test
    fun fetchesEveryPageThenStoresOnce() = runTest {
        val env = AccountTestEnv { request ->
            when (request.url.parameters["cursor"]) {
                null -> jsonResponse(200, pageBody(listOf(sessionJson("a", current = true, signedInAt = "2026-09-28T09:00:00Z")), "p2"))
                "p2" -> jsonResponse(200, pageBody(listOf(sessionJson("b")), "p3"))
                else -> jsonResponse(200, pageBody(listOf(sessionJson("c", signedInAt = "2026-08-01T00:00:00Z"))))
            }
        }
        env.signedIn()
        val refresher = SessionRefresher(env.sessions)

        assertEquals(ActionOutcome.Done, refresher.refresh())

        assertEquals(3, env.requests.size)
        val list = refresher.sessions.first()
        assertEquals(listOf("a", "b", "c"), list.items.map { it.id })
        assertEquals(true, list.items.first().isCurrent)
        assertNotNull(list.refreshedAt)
        assertNotNull(env.scopeDao.rows.value[SyncScopeEntity.DEVICE_SESSIONS])
    }

    @Test
    fun stopsAfterTwentyPages() = runTest {
        var n = 0
        val env = AccountTestEnv { jsonResponse(200, pageBody(listOf(sessionJson("s${n++}")), "next")) }
        env.signedIn()
        SessionRefresher(env.sessions).refresh()
        assertEquals(20, env.requests.size)
        assertEquals(20, env.sessionDao.rows.value.size)
    }

    @Test
    fun offlineKeepsTheStoredListAndIsNotRetried() = runTest {
        var online = true
        val env = AccountTestEnv {
            if (online) jsonResponse(200, pageBody(listOf(sessionJson("a", current = true)))) else throw IOException("offline")
        }
        env.signedIn()
        val refresher = SessionRefresher(env.sessions)
        refresher.refresh()
        online = false

        assertEquals(ActionOutcome.Offline, refresher.refresh())
        assertEquals(listOf("a"), refresher.sessions.first().items.map { it.id })
        assertEquals(2, env.requests.size)
    }

    @Test
    fun endingAGoneSessionCountsAsDoneAndRefreshes() = runTest {
        val env = AccountTestEnv { request ->
            if (request.method == HttpMethod.Delete) jsonResponse(404, problemBody(404, "NOT_FOUND"))
            else jsonResponse(200, pageBody(listOf(sessionJson("a", current = true))))
        }
        env.signedIn()
        val refresher = SessionRefresher(env.sessions)

        assertEquals(ActionOutcome.Done, EndSessionUseCase(env.sessions, refresher)("gone"))
        assertEquals(HttpMethod.Get, env.requests.last().method)
    }

    @Test
    fun endingOfflineIsReportedNotQueued() = runTest {
        val env = AccountTestEnv { throw IOException("offline") }
        env.signedIn()
        assertEquals(ActionOutcome.Offline, EndSessionUseCase(env.sessions, SessionRefresher(env.sessions))("b"))
        assertEquals(1, env.requests.size)
    }
}
