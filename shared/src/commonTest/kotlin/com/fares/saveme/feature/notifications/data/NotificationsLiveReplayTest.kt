package com.fares.saveme.feature.notifications.data

import com.fares.saveme.core.network.ApiErrorCode
import com.fares.saveme.core.network.ApiResult
import com.fares.saveme.core.sync.FakeSyncRunner
import com.fares.saveme.core.sync.ReadTarget
import com.fares.saveme.feature.auth.jsonResponse
import com.fares.saveme.feature.notifications.FakeDisplay
import com.fares.saveme.feature.notifications.FakeScheduler
import com.fares.saveme.feature.notifications.NotificationsTestEnv
import com.fares.saveme.feature.notifications.SESSION_ID
import com.fares.saveme.feature.notifications.TOKEN_A
import com.fares.saveme.feature.notifications.TOKEN_B
import com.fares.saveme.feature.notifications.bodyText
import com.fares.saveme.feature.notifications.data.CapturedPushResponses as Captured
import com.fares.saveme.feature.notifications.data.dto.PushRegistrationDto
import com.fares.saveme.feature.notifications.domain.ChannelLabels
import com.fares.saveme.feature.notifications.domain.NotificationCategory
import com.fares.saveme.feature.notifications.domain.NotificationPreferences
import com.fares.saveme.feature.notifications.domain.NotificationPreferencesService
import com.fares.saveme.feature.notifications.domain.PreferencesLoad
import com.fares.saveme.feature.notifications.domain.PreferencesSave
import com.fares.saveme.feature.notifications.domain.PushCoordinator
import com.fares.saveme.feature.notifications.domain.PushMessage
import com.fares.saveme.feature.notifications.domain.PushPayloadParser
import com.fares.saveme.feature.notifications.domain.PushRead
import com.fares.saveme.feature.notifications.domain.PushReceiver
import com.fares.saveme.feature.notifications.domain.PushRouter
import com.fares.saveme.feature.notifications.domain.PushTarget
import com.fares.saveme.feature.notifications.domain.QuarterHour
import com.fares.saveme.feature.notifications.domain.QuietHours
import com.fares.saveme.feature.notifications.domain.ReconcileOutcome
import com.fares.saveme.feature.notifications.domain.ReconcileTrigger
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The real wire clients, registration service, preferences service and receiving path driven by what the local backend
 * (spec 023, `notifications.yaml` v1.14) really answered and recorded on 2026-10-06 (spec 017 T081/T082, qa-report.md).
 */
class NotificationsLiveReplayTest {

    // --- registration -----------------------------------------------------------------------------------------------

    @Test
    fun theRealRegisteredAnswerDecodesAndTheSecondOneOnlyMovesRefreshedAt() = runTest {
        val env = NotificationsTestEnv(backgroundScope, devices = { jsonResponse(200, Captured.REGISTER_200) })
        env.signIn()
        val first = assertIs<ApiResult.Success<PushRegistrationDto>>(env.api.registerDevice(SESSION_ID, TOKEN_A)).value
        env.devices = { jsonResponse(200, Captured.REGISTER_200_AGAIN) }
        val again = assertIs<ApiResult.Success<PushRegistrationDto>>(env.api.registerDevice(SESSION_ID, TOKEN_A)).value
        env.devices = { jsonResponse(200, Captured.REGISTER_200_ROTATED) }
        val rotated = assertIs<ApiResult.Success<PushRegistrationDto>>(env.api.registerDevice(SESSION_ID, TOKEN_B)).value
        assertEquals(first.registeredAt, again.registeredAt)
        assertEquals(first.registeredAt, rotated.registeredAt)
        assertTrue(first.refreshedAt < again.refreshedAt && again.refreshedAt < rotated.refreshedAt)
        assertEquals("""{"push_token":"$TOKEN_B"}""", env.deviceRequests.last().bodyText())
        assertTrue("push_token" !in Captured.REGISTER_200, "the answer never returns the push address")
    }

    @Test
    fun registeringAfterSignInStoresOnlyAFingerprintAndAnewAddressRegistersAgain() = runTest {
        val env = NotificationsTestEnv(backgroundScope, devices = { jsonResponse(200, Captured.REGISTER_200) })
        env.signIn()
        assertEquals(ReconcileOutcome.Registered, env.service.reconcile(ReconcileTrigger.SignedIn))
        assertTrue(env.records.values.values.none { TOKEN_A in it })
        env.provider.token = TOKEN_B
        env.devices = { jsonResponse(200, Captured.REGISTER_200_ROTATED) }
        assertEquals(ReconcileOutcome.Registered, env.service.reconcile(ReconcileTrigger.TokenChanged))
        assertEquals(2, env.deviceRequests.size)
    }

    @Test
    fun theRealNotFoundForAnotherSessionStopsRegisteringWithoutSigningOut() = runTest {
        for (body in listOf(Captured.REGISTER_404_OTHER_SESSION, Captured.REGISTER_404_UNKNOWN_ID)) {
            val env = NotificationsTestEnv(backgroundScope, devices = { jsonResponse(404, body) })
            env.signIn()
            assertEquals(ApiErrorCode.NOT_FOUND, assertIs<ApiResult.Failure>(env.api.registerDevice(SESSION_ID, TOKEN_A)).code)
            assertEquals(ReconcileOutcome.Stopped, env.service.reconcile(ReconcileTrigger.SignedIn))
            assertIs<com.fares.saveme.feature.auth.domain.AuthState.SignedIn>(env.sessions.state.value)
        }
    }

    @Test
    fun theRealUnknownFieldAndEmptyTokenRefusalsAreContractBugsThatStop() = runTest {
        val env = NotificationsTestEnv(backgroundScope, devices = { jsonResponse(422, Captured.REGISTER_422_UNKNOWN_FIELD) })
        env.signIn()
        val unknown = assertIs<ApiResult.Failure>(env.api.registerDevice(SESSION_ID, TOKEN_A))
        assertEquals(ApiErrorCode.VALIDATION_FAILED, unknown.code)
        assertEquals("/platform", unknown.problem.errors?.single()?.pointer)
        assertEquals(ReconcileOutcome.Stopped, env.service.reconcile(ReconcileTrigger.SignedIn))
        env.devices = { jsonResponse(422, Captured.REGISTER_422_EMPTY_TOKEN) }
        assertEquals("/push_token", assertIs<ApiResult.Failure>(env.api.registerDevice(SESSION_ID, TOKEN_A)).problem.errors?.single()?.pointer)
        // The app never sends a member other than push_token (FR-039), so the first refusal cannot come from it.
        assertEquals("""{"push_token":"$TOKEN_A"}""", env.deviceRequests.first().bodyText())
    }

    @Test
    fun theRealTooManyRegistrationsWaitTheServersHeaderNotTheDefault() = runTest {
        val env = NotificationsTestEnv(
            backgroundScope,
            devices = { jsonResponse(429, Captured.REGISTER_429, "Retry-After" to Captured.REGISTER_429_RETRY_AFTER) },
        )
        env.signIn()
        val failure = assertIs<ApiResult.Failure>(env.api.registerDevice(SESSION_ID, TOKEN_A))
        assertEquals(ApiErrorCode.RATE_LIMITED, failure.code)
        assertEquals(3564L, failure.retryAfterSeconds)
        assertEquals(ReconcileOutcome.RetryAfter(3564), env.service.reconcile(ReconcileTrigger.SignedIn))
        assertNull(env.store.read())
    }

    @Test
    fun theRealUnauthenticatedAnswerIsTheAppWideSessionEnd() = runTest {
        val env = NotificationsTestEnv(backgroundScope, devices = { jsonResponse(401, Captured.REGISTER_401) })
        env.signIn()
        assertEquals(ApiErrorCode.UNAUTHENTICATED, assertIs<ApiResult.Failure>(env.api.registerDevice(SESSION_ID, TOKEN_A)).code)
    }

    // --- removal ----------------------------------------------------------------------------------------------------

    @Test
    fun theRealNoContentAnswersAreOneUnregisterAndNothingLeftToRemove() = runTest {
        val env = NotificationsTestEnv(backgroundScope, devices = { jsonResponse(200, Captured.REGISTER_200) })
        env.signIn()
        env.service.reconcile(ReconcileTrigger.SignedIn)
        env.permission.allowed = false
        env.devices = { respond("", HttpStatusCode.NoContent) }
        assertEquals(ReconcileOutcome.Unregistered, env.service.reconcile(ReconcileTrigger.Foreground))
        assertEquals(HttpMethod.Delete, env.deviceRequests.last().method)
        // The real server answers 204 again for a registration that is already gone: the wire client reads it as success.
        assertIs<ApiResult.Success<Unit>>(env.api.unregisterDevice(SESSION_ID))
        assertNull(env.store.read())
    }

    @Test
    fun theRealNotFoundAndRateLimitOnDeleteAreHandledByTheJob() = runTest {
        val env = NotificationsTestEnv(backgroundScope, devices = { jsonResponse(200, Captured.REGISTER_200) })
        env.signIn()
        env.service.reconcile(ReconcileTrigger.SignedIn)
        env.permission.allowed = false
        env.devices = { jsonResponse(429, Captured.UNREGISTER_429, "Retry-After" to Captured.UNREGISTER_429_RETRY_AFTER) }
        assertEquals(ReconcileOutcome.RetryAfter(3558), env.service.reconcile(ReconcileTrigger.Foreground))
        env.devices = { jsonResponse(404, Captured.UNREGISTER_404) }
        assertEquals(ReconcileOutcome.Unregistered, env.service.reconcile(ReconcileTrigger.Foreground))
        assertNull(env.store.read())
    }

    // --- preferences ------------------------------------------------------------------------------------------------

    private suspend fun NotificationsTestEnv.preferencesService(): NotificationPreferencesService {
        signIn()
        return NotificationPreferencesService(api)
    }

    private val savedByTheQaScript = NotificationPreferences(
        version = 1,
        categories = NotificationCategory.entries.associateWith { it != NotificationCategory.BudgetExceeded },
        quietHours = QuietHours(true, QuarterHour(23 * 60), QuarterHour(7 * 60)),
    )

    @Test
    fun theRealDefaultsAreAllOnQuietTwentyTwoToEightAtVersionOne() = runTest {
        val env = NotificationsTestEnv(backgroundScope, preferences = { jsonResponse(200, Captured.PREFERENCES_200_DEFAULTS) })
        val loaded = assertIs<PreferencesLoad.Loaded>(env.preferencesService().load()).preferences
        assertEquals(1, loaded.version)
        assertTrue(NotificationCategory.entries.all { loaded.isOn(it) })
        assertEquals(QuietHours(true, QuarterHour(22 * 60), QuarterHour(8 * 60)), loaded.quietHours)
        assertNull(loaded.updatedAt)
    }

    @Test
    fun theWholeObjectTheAppSendsIsByteForByteWhatTheServerAcceptedAndTheAnswerReplacesIt() = runTest {
        val env = NotificationsTestEnv(backgroundScope, preferences = { jsonResponse(200, Captured.PREFERENCES_PUT_200_SAVED) })
        val saved = assertIs<PreferencesSave.Saved>(env.preferencesService().save(savedByTheQaScript, 1)).preferences
        assertEquals(2, saved.version)
        assertFalse(saved.isOn(NotificationCategory.BudgetExceeded))
        assertEquals(QuietHours(true, QuarterHour(23 * 60), QuarterHour(7 * 60)), saved.quietHours)
        assertTrue(saved.updatedAt != null)
        // The request the QA script sent is the one the app builds.
        val sent = env.env.requests.last().bodyText()
        assertEquals(
            """{"version":1,"categories":{"recurring_due":true,"budget_exceeded":false,"sharing":true,"billing":true,"export_ready":true,"deletion_reminder":true},"quiet_hours":{"enabled":true,"start":"23:00","end":"07:00"}}""",
            sent,
        )
    }

    @Test
    fun theRealStaleVersionAnswerCarriesCurrentWhichReplacesTheEdits() = runTest {
        val env = NotificationsTestEnv(backgroundScope, preferences = { jsonResponse(409, Captured.PREFERENCES_PUT_409_CONFLICT) })
        val conflict = assertIs<PreferencesSave.Conflict>(env.preferencesService().save(savedByTheQaScript, 1))
        assertEquals(2, conflict.current.version)
        assertEquals(QuietHours(true, QuarterHour(23 * 60), QuarterHour(7 * 60)), conflict.current.quietHours)
        assertFalse(conflict.current.isOn(NotificationCategory.BudgetExceeded))
    }

    @Test
    fun theRealValidationRefusalsAreInvalidAndTheClientRefusesTheSameInputBeforeSending() = runTest {
        for (body in listOf(
            Captured.PREFERENCES_PUT_422_START_EQUALS_END,
            Captured.PREFERENCES_PUT_422_NOT_A_QUARTER_HOUR,
            Captured.PREFERENCES_PUT_422_UNKNOWN_FIELD,
            Captured.PREFERENCES_PUT_422_MISSING_VERSION,
        )) {
            val env = NotificationsTestEnv(backgroundScope, preferences = { jsonResponse(422, body) })
            assertIs<PreferencesSave.Invalid>(env.preferencesService().save(savedByTheQaScript, 1))
        }
        // The server refuses start == end even while quiet hours are off, so the app's validity ignores `enabled` too.
        assertFalse(QuietHours(enabled = false, QuarterHour(9 * 60), QuarterHour(9 * 60)).isValid)
        assertFalse(QuietHours(enabled = true, QuarterHour(9 * 60), QuarterHour(9 * 60)).isValid)
        assertTrue(QuietHours(enabled = true, QuarterHour(22 * 60), QuarterHour(8 * 60)).isValid)
        assertTrue(QuietHours(enabled = true, QuarterHour(13 * 60), QuarterHour(15 * 60)).isValid)
    }

    @Test
    fun theRealRateLimitCarriesTheWaitTheScreenShows() = runTest {
        val env = NotificationsTestEnv(
            backgroundScope,
            preferences = { jsonResponse(429, Captured.PREFERENCES_PUT_429, "Retry-After" to Captured.PREFERENCES_429_RETRY_AFTER) },
        )
        assertEquals(PreferencesSave.RateLimited(3579), env.preferencesService().save(savedByTheQaScript, 1))
    }

    @Test
    fun theRealReadAfterTheSaveShowsTheStoredValues() = runTest {
        val env = NotificationsTestEnv(backgroundScope, preferences = { jsonResponse(200, Captured.PREFERENCES_200_AFTER_SAVE) })
        val loaded = assertIs<PreferencesLoad.Loaded>(env.preferencesService().load()).preferences
        assertEquals(2, loaded.version)
        assertFalse(loaded.isOn(NotificationCategory.BudgetExceeded))
    }

    // --- recorded pushes --------------------------------------------------------------------------------------------

    @Test
    fun everyRecordedPushParsesToItsTypeAndTheCollapseKeyIsTheServers() {
        val expected = mapOf(
            "export_ready" to PushMessage.ExportReady::class,
            "invitation_received" to PushMessage.InvitationReceived::class,
            "member_role_changed" to PushMessage.MemberRoleChanged::class,
            "member_removed" to PushMessage.MemberRemoved::class,
            "occurrence_due" to PushMessage.OccurrenceDue::class,
            "budget_exceeded" to PushMessage.BudgetExceeded::class,
            "deletion_reminder" to PushMessage.DeletionReminder::class,
        )
        assertEquals(expected.keys, Captured.ALL_PUSHES.map { it.data.getValue("type") }.toSet())
        Captured.ALL_PUSHES.forEach { push ->
            val type = push.data.getValue("type")
            val message = PushPayloadParser.parse(push.data)
            assertEquals(expected.getValue(type), message::class, type)
            assertEquals(push.data.getValue("notification_id"), message.notificationId, type)
            assertEquals(NotificationCategory.fromWire(push.data.getValue("category")), message.category, type)
            assertEquals(push.serverCollapseKey, message.collapseKey, "the app's tag equals the server's collapse key for $type")
        }
        val role = assertIs<PushMessage.MemberRoleChanged>(PushPayloadParser.parse(Captured.MEMBER_ROLE_CHANGED.data))
        assertEquals("viewer", role.role)
    }

    @Test
    fun everyRecordedPushRoutesToTheTableRowOfItsType() {
        fun route(push: Captured.CapturedPush) = PushRouter.route(PushPayloadParser.parse(push.data))
        assertEquals(PushTarget.Export(Captured.EXPORT_ID), route(Captured.EXPORT_READY).target)
        assertEquals(PushRead.None, route(Captured.EXPORT_READY).read)
        assertEquals(PushTarget.InvitationNotice, route(Captured.INVITATION_RECEIVED).target)
        assertEquals(PushRead.None, route(Captured.INVITATION_RECEIVED).read)
        assertEquals(PushTarget.Wallet(Captured.WALLET_ID), route(Captured.MEMBER_ROLE_CHANGED).target)
        assertEquals(PushRead.Wallet(Captured.WALLET_ID), route(Captured.MEMBER_ROLE_CHANGED).read)
        assertEquals(PushTarget.Wallets, route(Captured.MEMBER_REMOVED).target)
        assertEquals(PushRead.Everything, route(Captured.MEMBER_REMOVED).read)
        assertEquals(PushTarget.Occurrence(Captured.OCCURRENCE_ID), route(Captured.OCCURRENCE_DUE).target)
        assertEquals(PushRead.Wallet(Captured.WALLET_ID), route(Captured.OCCURRENCE_DUE).read)
        assertEquals(PushTarget.Budget(Captured.BUDGET_ID), route(Captured.BUDGET_EXCEEDED).target)
        assertEquals(PushRead.User, route(Captured.BUDGET_EXCEEDED).read)
        assertEquals(PushTarget.AccountDeletion, route(Captured.DELETION_REMINDER).target)
        assertEquals(PushRead.Account, route(Captured.DELETION_REMINDER).read)
    }

    private class Rig(scope: TestScope) {
        val env = NotificationsTestEnv(scope.backgroundScope)
        val display = FakeDisplay()
        val runner = FakeSyncRunner()
        val coordinator = PushCoordinator(env.sessions, FakeScheduler(), scope.backgroundScope)
        val receiver = PushReceiver(
            sessions = env.sessions,
            shown = ShownNotificationsStore(env.records),
            display = display,
            labels = { ChannelLabels(emptyMap(), "New notification") },
            coordinator = { coordinator },
            runner = { runner },
            scope = scope.backgroundScope,
        )
    }

    @Test
    fun everyRecordedPushIsShownOnItsChannelUnderTheServersCollapseKeyAndOnlyWithTheContractKeys() = runTest {
        val rig = Rig(this)
        rig.env.signIn()
        Captured.ALL_PUSHES.forEach { push ->
            rig.receiver.onMessage(push.data + ("google.message_id" to "m1"), "Title", "Body")
        }
        testScheduler.runCurrent()
        assertEquals(Captured.ALL_PUSHES.size, rig.display.posted.size)
        rig.display.posted.zip(Captured.ALL_PUSHES).forEach { (posted, push) ->
            assertEquals(push.data.getValue("category"), posted.channel)
            assertEquals(push.serverCollapseKey, posted.tag)
            assertTrue("google.message_id" !in posted.tap)
            assertEquals(push.data, posted.tap)
        }
        // Foreground reads: occurrence and role change read the wallet, removal everything, budget the person's own scope.
        assertTrue(rig.runner.refreshes.any { it.first == ReadTarget.Wallet(Captured.WALLET_ID) })
        assertTrue(rig.runner.refreshes.any { it.first == ReadTarget.Everything })
        assertTrue(rig.runner.refreshes.any { it.first == ReadTarget.User })
        // The same push delivered twice is shown once.
        rig.receiver.onMessage(Captured.EXPORT_READY.data, "Title", "Body")
        assertEquals(Captured.ALL_PUSHES.size, rig.display.posted.size)
    }
}
