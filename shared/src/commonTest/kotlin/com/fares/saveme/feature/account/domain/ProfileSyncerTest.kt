package com.fares.saveme.feature.account.domain

import com.fares.saveme.core.locale.AppLanguage
import com.fares.saveme.feature.account.AccountTestEnv
import com.fares.saveme.feature.account.domain.model.ProfileDraft
import com.fares.saveme.feature.account.domain.model.ProfileField
import com.fares.saveme.feature.account.domain.model.SyncOutcome
import com.fares.saveme.feature.account.jsonBody
import com.fares.saveme.feature.account.meBody
import com.fares.saveme.feature.account.profileJson
import com.fares.saveme.feature.auth.domain.SessionManager
import com.fares.saveme.feature.auth.jsonResponse
import com.fares.saveme.feature.auth.problemBody
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.http.HttpMethod
import io.ktor.utils.io.errors.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.time.Duration

/** Offline-first profile writes (P-04.2, P-04.4; research R-02, R-03). */
class ProfileSyncerTest {

    private class Harness(scope: TestScope, var put: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData) {
        val env = AccountTestEnv { request ->
            if (request.method == HttpMethod.Get) jsonResponse(200, meBody()) else put(request)
        }
        val syncer = ProfileSyncer(env.profiles, env.languages, env.engine, scope.backgroundScope as CoroutineScope)
        val sessions = SessionManager(env.auth.repository, env.auth.entitlementsApi, env.events, scope.backgroundScope)
        val refresher = AccountRefresher(env.profiles, syncer, sessions, env.languages)
        val puts get() = env.requests.filter { it.method == HttpMethod.Put }
    }

    private val edit = ProfileDraft("Sara M", "en", "Africa/Cairo", "EGP", 1)

    @Test
    fun aLocalSaveIsStoredAtOnceAndPushedWithTheServerVersion() = runTest {
        // The PUT is held until the local copy has been checked: the send runs on Ktor's own
        // thread, and without the gate it could finish (and clear the edit) before the check.
        val sent = CompletableDeferred<Unit>()
        val h = Harness(this) {
            sent.await()
            jsonResponse(200, """{"data":${profileJson(version = 4, displayName = "Sara M")},"meta":{}}""")
        }
        h.env.signedIn()
        h.refresher.refresh()

        val push = h.syncer.saveLocally(edit)
        assertEquals(edit, h.syncer.view.first()!!.pending, "saved on the device before any request")
        sent.complete(Unit)
        push!!.join()

        assertEquals("3", h.puts.single().jsonBody()["version"].toString())
        val view = h.syncer.view.first()!!
        assertNull(view.pending)
        assertEquals(4L, view.server.version)
        assertEquals("Sara M", view.server.displayName)
    }

    /**
     * Spec 004 moved retries into the sync engine: offline no longer schedules an in-process timer
     * here, it asks the engine's scheduler for a drain once a connection exists (no delay). Backoff
     * itself is covered by SyncEngineRetryTest. The person-visible behaviour is unchanged: the edit
     * stays waiting and is sent once online.
     */
    @Test
    fun offlineKeepsTheEditAndSendsItOnceOnline() = runTest {
        var online = false
        val h = Harness(this) {
            if (online) jsonResponse(200, """{"data":${profileJson(version = 4, displayName = "Sara M")},"meta":{}}""") else throw IOException("offline")
        }
        h.env.signedIn()
        h.refresher.refresh()

        h.syncer.saveLocally(edit)!!.join()
        assertEquals(1, h.puts.size)
        assertEquals(edit, h.syncer.view.first()!!.pending)
        assertEquals(Duration.ZERO, h.env.scheduler.enqueued.last(), "waits for a connection, no backoff")

        online = true
        h.env.engine.drain() // what the scheduler runs once a connection exists
        assertEquals(2, h.puts.size)
        assertNull(h.syncer.view.first()!!.pending)
    }

    @Test
    fun aPullWhileAnEditWaitsUpdatesOnlyTheServerCopy() = runTest {
        val h = Harness(this) { throw IOException("offline") }
        h.env.signedIn()
        h.refresher.refresh()
        h.syncer.saveLocally(edit.copy(locale = "ar"))!!.join()

        h.refresher.refresh()

        val view = h.syncer.view.first()!!
        assertEquals(edit.copy(locale = "ar"), view.pending)
        assertEquals(AppLanguage.Ar, h.env.languages.effective(), "the pull must not undo the waiting language")
    }

    @Test
    fun conflictWaitsForTheChoiceThenKeepMineRebases() = runTest {
        var conflict = true
        val h = Harness(this) {
            if (conflict) {
                conflict = false
                jsonResponse(409, problemBody(409, "VERSION_CONFLICT", ""","current":${profileJson(version = 6, displayName = "Web")}"""))
            } else {
                jsonResponse(200, """{"data":${profileJson(version = 7, displayName = "Sara M")},"meta":{}}""")
            }
        }
        h.env.signedIn()
        h.refresher.refresh()
        h.syncer.saveLocally(edit)!!.join()

        val view = h.syncer.view.first()!!
        assertEquals("Web", assertNotNull(view.conflict).displayName)
        assertEquals(edit, view.pending)
        assertEquals(SyncOutcome.Conflict, h.syncer.push(), "no push until the person chooses")
        assertEquals(1, h.puts.size)

        assertEquals(SyncOutcome.Synced, h.syncer.keepMine())
        assertEquals("6", h.puts.last().jsonBody()["version"].toString())
        assertEquals("Sara M", h.syncer.view.first()!!.server.displayName)
    }

    @Test
    fun useTheirsDropsTheEditAndTakesTheirLanguage() = runTest {
        val h = Harness(this) {
            jsonResponse(409, problemBody(409, "VERSION_CONFLICT", ""","current":${profileJson(version = 6, displayName = "Web", locale = "ar")}"""))
        }
        h.env.signedIn()
        h.refresher.refresh()
        h.syncer.saveLocally(edit)!!.join()

        h.syncer.useTheirs()

        val view = h.syncer.view.first()!!
        assertNull(view.pending)
        assertNull(view.conflict)
        assertEquals("Web", view.server.displayName)
        assertEquals(AppLanguage.Ar, h.env.languages.effective())
    }

    @Test
    fun rejectionIsKeptAsFieldErrors() = runTest {
        val h = Harness(this) {
            jsonResponse(422, problemBody(422, "VALIDATION_FAILED", ""","errors":[{"pointer":"/default_currency","code":"IMMUTABLE_FIELD"}]"""))
        }
        h.env.signedIn()
        h.refresher.refresh()
        h.syncer.saveLocally(edit.copy(defaultCurrency = "USD"))!!.join()

        val view = h.syncer.view.first()!!
        assertEquals(mapOf(ProfileField.DefaultCurrency to "IMMUTABLE_FIELD"), view.rejection)
        assertNotNull(view.pending)
        h.syncer.discard()
        assertNull(h.syncer.view.first()!!.pending)
    }

    @Test
    fun aPullSetsTheAppLanguageFromTheProfile() = runTest {
        val h = Harness(this) { throw IOException("unused") }
        h.env.signedIn()
        h.env.auth.keyValues.values.clear()
        val env = AccountTestEnv { jsonResponse(200, meBody(profile = profileJson(locale = "ar"))) }
        env.signedIn()
        val refresher = AccountRefresher(env.profiles, ProfileSyncer(env.profiles, env.languages, env.engine, backgroundScope), SessionManager(env.auth.repository, env.auth.entitlementsApi, env.events, backgroundScope), env.languages)
        refresher.refresh()
        assertEquals(AppLanguage.Ar, env.languages.effective())
    }
}
