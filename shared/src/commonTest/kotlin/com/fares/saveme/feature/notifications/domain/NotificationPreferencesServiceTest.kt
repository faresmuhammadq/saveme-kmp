package com.fares.saveme.feature.notifications.domain

import com.fares.saveme.feature.auth.jsonResponse
import com.fares.saveme.feature.notifications.NotificationsTestEnv
import com.fares.saveme.feature.notifications.bodyText
import com.fares.saveme.feature.notifications.preferencesBody
import com.fares.saveme.feature.notifications.problem
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NotificationPreferencesServiceTest {

    private suspend fun NotificationsTestEnv.service(): NotificationPreferencesService {
        signIn()
        return NotificationPreferencesService(api)
    }

    private val edited = NotificationPreferences(
        version = 1,
        categories = NotificationCategory.entries.associateWith { it != NotificationCategory.BudgetExceeded },
        quietHours = QuietHours(true, QuarterHour(23 * 60), QuarterHour(7 * 60)),
    )

    @Test
    fun loadMapsTheDefaultsToTheDomain() = runTest {
        val service = NotificationsTestEnv(backgroundScope).service()
        val loaded = assertIs<PreferencesLoad.Loaded>(service.load()).preferences
        assertEquals(1, loaded.version)
        assertTrue(NotificationCategory.entries.all { loaded.isOn(it) })
        assertEquals(QuietHours(true, QuarterHour(22 * 60), QuarterHour(8 * 60)), loaded.quietHours)
        assertNull(loaded.updatedAt)
    }

    @Test
    fun loadOutcomes() = runTest {
        val env = NotificationsTestEnv(backgroundScope)
        val service = env.service()
        env.preferences = { throw io.ktor.utils.io.errors.IOException("no route") }
        assertEquals(PreferencesLoad.Offline, service.load())
        env.preferences = { jsonResponse(500, problem(500, "INTERNAL_ERROR")) }
        assertEquals(PreferencesLoad.Failed("req-77"), service.load())
        // An answer the app cannot read as quarter hours is a failure, not a crash.
        env.preferences = { jsonResponse(200, preferencesBody(start = "22:07")) }
        assertIs<PreferencesLoad.Failed>(service.load())
    }

    @Test
    fun saveSendsOneRequestWithEveryMemberAndTheLastReadVersion() = runTest {
        val env = NotificationsTestEnv(backgroundScope)
        val service = env.service()
        env.preferences = { jsonResponse(200, preferencesBody(version = 2, budgets = false, start = "23:00", end = "07:00", updatedAt = "2026-10-06T19:05:00.000000Z")) }
        val saved = assertIs<PreferencesSave.Saved>(service.save(edited, 1)).preferences
        assertEquals(2, saved.version)
        assertFalse(saved.isOn(NotificationCategory.BudgetExceeded))
        assertEquals(QuarterHour(23 * 60), saved.quietHours.start)
        assertEquals(1, env.env.requests.count { it.url.encodedPath.endsWith("notification-preferences") })
        assertEquals(
            """{"version":1,"categories":{"recurring_due":true,"budget_exceeded":false,"sharing":true,"billing":true,"export_ready":true,"deletion_reminder":true},"quiet_hours":{"enabled":true,"start":"23:00","end":"07:00"}}""",
            env.env.requests.last().bodyText(),
        )
    }

    @Test
    fun aConflictCarriesTheStoredValuesAndAnUnreadableOneIsAFailure() = runTest {
        val env = NotificationsTestEnv(backgroundScope)
        val service = env.service()
        val current = preferencesBody(version = 3, budgets = false).removePrefix("""{"data":""").substringBeforeLast(""","meta":{}}""")
        env.preferences = { jsonResponse(409, problem(409, "VERSION_CONFLICT", ""","current":$current""")) }
        assertEquals(3, assertIs<PreferencesSave.Conflict>(service.save(edited, 1)).current.version)
        env.preferences = { jsonResponse(409, problem(409, "VERSION_CONFLICT")) }
        assertEquals(PreferencesSave.Failed("req-77"), service.save(edited, 1))
    }

    @Test
    fun rateLimitedKeepsTheWaitWhenThereIsOne() = runTest {
        val env = NotificationsTestEnv(backgroundScope)
        val service = env.service()
        env.preferences = { jsonResponse(429, problem(429, "RATE_LIMITED"), "Retry-After" to "125") }
        assertEquals(PreferencesSave.RateLimited(125), service.save(edited, 1))
        env.preferences = { jsonResponse(429, problem(429, "RATE_LIMITED")) }
        assertEquals(PreferencesSave.RateLimited(null), service.save(edited, 1))
    }

    @Test
    fun validationOfflineAndOtherRefusalsAreNeverRetried() = runTest {
        val env = NotificationsTestEnv(backgroundScope)
        val service = env.service()
        env.preferences = { jsonResponse(422, problem(422, "VALIDATION_FAILED")) }
        assertEquals(PreferencesSave.Invalid("req-77"), service.save(edited, 1))
        env.preferences = { throw io.ktor.utils.io.errors.IOException("no route") }
        assertEquals(PreferencesSave.Offline, service.save(edited, 1))
        env.preferences = { jsonResponse(503, problem(503, "SERVICE_UNAVAILABLE")) }
        assertEquals(PreferencesSave.Failed("req-77"), service.save(edited, 1))
        assertEquals(3, env.env.requests.count { it.url.encodedPath.endsWith("notification-preferences") }, "one request per call")
    }

    @Test
    fun quarterHoursParseAndPrintAndRefuseTheRest() {
        assertEquals("09:15", QuarterHour.parse("09:15")?.format())
        assertEquals("23:45", QuarterHour.parse("23:45")?.format())
        assertEquals(0, QuarterHour.parse("00:00")?.minutes)
        assertNull(QuarterHour.parse("09:10"))
        assertNull(QuarterHour.parse("24:00"))
        assertNull(QuarterHour.parse("9:15"))
        assertEquals(QuarterHour(1425), QuarterHour.snapped(5000))
        assertEquals(QuarterHour(60), QuarterHour.snapped(74))
    }
}
