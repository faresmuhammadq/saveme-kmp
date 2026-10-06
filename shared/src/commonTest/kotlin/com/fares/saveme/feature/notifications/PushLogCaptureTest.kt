package com.fares.saveme.feature.notifications

import co.touchlab.kermit.LogWriter
import co.touchlab.kermit.Logger
import co.touchlab.kermit.Severity
import com.fares.saveme.core.sync.FakeSyncRunner
import com.fares.saveme.feature.auth.jsonResponse
import com.fares.saveme.feature.notifications.data.ShownNotificationsStore
import com.fares.saveme.feature.notifications.domain.ChannelLabels
import com.fares.saveme.feature.notifications.domain.NotificationPreferences
import com.fares.saveme.feature.notifications.domain.NotificationPreferencesService
import com.fares.saveme.feature.notifications.domain.PushCoordinator
import com.fares.saveme.feature.notifications.domain.PushDataCleaner
import com.fares.saveme.feature.notifications.domain.PushPayloadParser
import com.fares.saveme.feature.notifications.domain.PushReceiver
import com.fares.saveme.feature.notifications.domain.PushRouter
import com.fares.saveme.feature.notifications.domain.PushSignOutParticipant
import com.fares.saveme.feature.notifications.domain.ReconcileTrigger
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** P-09.2, FR-008, FR-038, SC-005: the push tag carries outcomes and types only; no log line holds an address, a payload value, a title or a text. */
class PushLogCaptureTest {

    private val lines = mutableListOf<String>()

    init {
        Logger.setLogWriters(object : LogWriter() {
            override fun log(severity: Severity, message: String, tag: String, throwable: Throwable?) {
                lines += "$tag $message ${throwable?.message.orEmpty()}"
            }
        })
        Logger.setMinSeverity(Severity.Verbose)
    }

    @AfterTest
    fun reset() = Logger.setLogWriters(emptyList())

    private val title = "SECRET-TITLE-9"
    private val body = "SECRET-BODY-9"

    @Test
    fun registerRotateReceiveTapSaveAndSignOutLogNothingSensitive() = runTest {
        val env = NotificationsTestEnv(backgroundScope)
        env.signIn()
        val display = FakeDisplay()
        val runner = FakeSyncRunner()
        val scheduler = FakeScheduler()
        val receiver = PushReceiver(
            env.sessions, ShownNotificationsStore(env.records), display, { ChannelLabels(emptyMap(), "New notification") },
            { PushCoordinator(env.sessions, scheduler, backgroundScope) }, { runner }, backgroundScope,
        )

        env.service.reconcile(ReconcileTrigger.SignedIn)
        env.provider.token = TOKEN_B
        env.service.reconcile(ReconcileTrigger.TokenChanged)
        receiver.onNewToken()

        listOf(
            "occurrence_due" to arrayOf("wallet_id" to WALLET, "occurrence_id" to OCCURRENCE),
            "occurrence_auto_confirm_refused" to arrayOf("wallet_id" to WALLET, "occurrence_id" to OCCURRENCE),
            "occurrences_due_summary" to emptyArray(),
            "budget_exceeded" to arrayOf("budget_id" to BUDGET),
            "invitation_received" to arrayOf("invitation_id" to INVITATION),
            "member_role_changed" to arrayOf("wallet_id" to WALLET, "role" to "editor"),
            "member_removed" to arrayOf("wallet_id" to WALLET),
            "wallet_ownership_received" to arrayOf("wallet_id" to WALLET),
            "subscription_ending" to emptyArray(),
            "subscription_lapsed" to emptyArray(),
            "export_ready" to arrayOf("export_id" to EXPORT),
            "deletion_reminder" to emptyArray(),
            "future_type" to emptyArray(),
        ).forEachIndexed { i, (type, ids) ->
            val data = mapOf("schema" to "1", "type" to type, "category" to "sharing", "notification_id" to "0192f7a0-0000-7000-8000-00000000${i.toString().padStart(4, '0')}") + ids
            receiver.onMessage(data, title, body)
            // A tap goes through the same parser and router.
            PushRouter.route(PushPayloadParser.parse(data))
        }
        testScheduler.runCurrent()

        env.preferences = { jsonResponse(200, preferencesBody(version = 2)) }
        val prefs = NotificationPreferencesService(env.api)
        (prefs.load() as com.fares.saveme.feature.notifications.domain.PreferencesLoad.Loaded).preferences.let { loaded: NotificationPreferences ->
            prefs.save(loaded, loaded.version)
        }

        env.devices = { respond("", HttpStatusCode.NoContent) }
        PushSignOutParticipant(env.api, env.store, env.env.secureStore).beforeSignOut()
        PushDataCleaner(display, env.store, ShownNotificationsStore(env.records), scheduler, env.provider, backgroundScope).clear()
        testScheduler.runCurrent()

        assertTrue(lines.any { it.startsWith("push ") }, "the push tag does log")
        val secrets = listOf(
            TOKEN_A, TOKEN_B, "dev-fake-push", title, body, WALLET, OCCURRENCE, BUDGET, EXPORT, INVITATION, "0192f7a0-0000-7000-8000-00000000",
            fingerprintOf(TOKEN_A), fingerprintOf(TOKEN_B),
        )
        secrets.forEach { secret -> assertFalse(lines.any { secret in it }, "a log line holds \"$secret\":\n" + lines.filter { secret in it }.joinToString("\n")) }
        // The request id and the session id never ride on the push tag.
        assertFalse(lines.filter { it.startsWith("push ") }.any { SESSION_ID in it })
    }

    private fun fingerprintOf(token: String) = com.fares.saveme.feature.notifications.domain.fingerprint(token)
}
