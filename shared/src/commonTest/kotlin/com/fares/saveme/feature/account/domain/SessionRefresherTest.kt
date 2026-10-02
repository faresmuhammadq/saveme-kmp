package com.fares.saveme.feature.account.domain

import com.fares.saveme.core.database.SyncScopeEntity
import com.fares.saveme.core.network.ApiErrorCode
import com.fares.saveme.core.sync.ReadOutcome
import com.fares.saveme.core.sync.ReadTarget
import com.fares.saveme.feature.account.AccountTestEnv
import com.fares.saveme.feature.account.data.local.DeviceSessionEntity
import com.fares.saveme.feature.account.domain.model.ActionOutcome
import com.fares.saveme.feature.account.domain.usecase.EndSessionUseCase
import com.fares.saveme.feature.auth.jsonResponse
import com.fares.saveme.feature.auth.problemBody
import io.ktor.http.HttpMethod
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/** The sessions list is kept current by the person's change feed (spec 009 R-13); the refresher only asks the runner. */
class SessionRefresherTest {

    private fun session(id: String, signedInAt: String = "2026-09-28T09:00:00Z", current: Boolean = false) =
        DeviceSessionEntity(id, "u1", "Device $id", "android", "1.0.0", "active", signedInAt, null, current)

    @Test
    fun aRefreshReadsThePersonsScopeWithoutAFloor() = runTest {
        val env = AccountTestEnv { throw AssertionError("no request of its own: the feed carries the sessions") }
        env.signedIn()
        val refresher = SessionRefresher(env.sessions, env.runner)

        assertEquals(ActionOutcome.Done, refresher.refresh())

        assertEquals(listOf<Pair<ReadTarget, Boolean>>(ReadTarget.User to true), env.runner.refreshes)
        assertEquals(0, env.requests.size)
    }

    @Test
    fun theRunnersOutcomeIsMappedToTheScreensOutcome() = runTest {
        val env = AccountTestEnv { throw AssertionError("unused") }
        val refresher = SessionRefresher(env.sessions, env.runner)
        env.runner.outcome = ReadOutcome.Offline
        assertEquals(ActionOutcome.Offline, refresher.refresh())
        env.runner.outcome = ReadOutcome.Failed
        assertEquals(ActionOutcome.Error(ApiErrorCode.UNKNOWN, null), refresher.refresh())
    }

    @Test
    fun theListShowsTheStoredRowsAndWhenTheScopeWasLastRead() = runTest {
        val env = AccountTestEnv { throw AssertionError("unused") }
        val refresher = SessionRefresher(env.sessions, env.runner)
        env.sessionDao.insertAll(listOf(session("a", current = true), session("b", signedInAt = "2026-08-01T00:00:00Z")))
        assertNull(refresher.sessions.first().refreshedAt)

        env.scopeDao.markFeedRead("user:u1", "2026-10-02T10:00:00.000Z")

        val list = refresher.sessions.first()
        assertEquals(listOf("a", "b"), list.items.map { it.id })
        assertEquals(true, list.items.first().isCurrent)
        assertEquals("2026-10-02T10:00:00Z", list.refreshedAt.toString())
    }

    @Test
    fun anUpgradedPhoneKeepsItsLastWholeListTimeUntilTheFirstFeedReadEnds() = runTest {
        val env = AccountTestEnv { throw AssertionError("unused") }
        val refresher = SessionRefresher(env.sessions, env.runner)
        env.scopeDao.upsert(SyncScopeEntity(SyncScopeEntity.DEVICE_SESSIONS, "2026-09-30T08:00:00Z"))
        assertEquals("2026-09-30T08:00:00Z", refresher.sessions.first().refreshedAt.toString())

        env.scopeDao.markFeedRead("user:u1", "2026-10-02T10:00:00.000Z")
        assertEquals("2026-10-02T10:00:00Z", refresher.sessions.first().refreshedAt.toString())
        assertNotNull(env.scopeDao.rows.value["user:u1"])
    }

    @Test
    fun endingAGoneSessionCountsAsDoneAndRefreshesThroughTheRunner() = runTest {
        val env = AccountTestEnv { request ->
            if (request.method == HttpMethod.Delete) jsonResponse(404, problemBody(404, "NOT_FOUND")) else throw AssertionError("unexpected ${request.method}")
        }
        env.signedIn()
        val refresher = SessionRefresher(env.sessions, env.runner)

        assertEquals(ActionOutcome.Done, EndSessionUseCase(env.sessions, refresher)("gone"))
        assertEquals(listOf<Pair<ReadTarget, Boolean>>(ReadTarget.User to true), env.runner.refreshes)
    }

    @Test
    fun endingOfflineIsReportedNotQueued() = runTest {
        val env = AccountTestEnv { throw io.ktor.utils.io.errors.IOException("offline") }
        env.signedIn()
        assertEquals(ActionOutcome.Offline, EndSessionUseCase(env.sessions, SessionRefresher(env.sessions, env.runner))("b"))
        assertEquals(1, env.requests.size)
        assertEquals(emptyList(), env.runner.refreshes)
    }
}
