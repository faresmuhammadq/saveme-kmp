package com.fares.saveme.feature.account.data

import com.fares.saveme.core.locale.AppLanguage
import com.fares.saveme.core.network.AppJson
import com.fares.saveme.core.sync.ScopeKind
import com.fares.saveme.core.sync.SyncScope
import com.fares.saveme.feature.account.AccountTestEnv
import com.fares.saveme.feature.account.data.local.DeviceSessionEntity
import com.fares.saveme.feature.account.data.local.ProfileEntity
import com.fares.saveme.feature.account.domain.model.ProfileDraft
import com.fares.saveme.feature.account.profileJson
import com.fares.saveme.feature.account.sessionJson
import com.fares.saveme.feature.auth.jsonResponse
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonElement
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** The profile and the device sessions from the person's feed (spec 009 FR-004, FR-005, research R-13). */
class AccountFeedAppliersTest {

    private val user = SyncScope(ScopeKind.User, "u1")

    private fun json(text: String): JsonElement = AppJson.parseToJsonElement(text)

    private fun env() = AccountTestEnv { jsonResponse(200, "{}") }

    private fun profileApplier(env: AccountTestEnv) =
        ProfileFeedApplier(env.profiles, env.languages) { id -> env.engine.get(ProfileSyncHandler.RECORD_TYPE, id) != null }

    private suspend fun AccountTestEnv.seedProfile(version: Long, locale: String = "en") = profileDao.upsert(
        ProfileEntity("u1", "p1", version, "Sara", locale, "Africa/Cairo", "EGP", false, 1, null, "2026-09-28T10:00:00Z"),
    )

    @Test
    fun aProfileIsAppliedWhenItsVersionIsTheSameOrNewerAndIgnoredWhenLower() = runTest {
        val env = env()
        env.seedProfile(version = 3)
        val applier = profileApplier(env)

        applier.apply(user, listOf(json(profileJson(version = 2, displayName = "Stale"))))
        assertEquals("Sara", env.profileDao.row.value!!.displayName)

        applier.apply(user, listOf(json(profileJson(version = 3, displayName = "Same"))))
        assertEquals("Same", env.profileDao.row.value!!.displayName, "the same version is applied")

        applier.apply(user, listOf(json(profileJson(version = 4, displayName = "Newer"))))
        assertEquals("Newer", env.profileDao.row.value!!.displayName)
        assertEquals(4L, env.profileDao.row.value!!.version)
    }

    @Test
    fun aProfileThePhoneNeverHadIsStored() = runTest {
        val env = env()
        profileApplier(env).apply(user, listOf(json(profileJson(version = 1))))
        assertEquals(1L, env.profileDao.row.value!!.version)
    }

    @Test
    fun theAppLanguageFollowsTheProfileOnlyWhenNoProfileEditWaits() = runTest {
        val env = env()
        env.seedProfile(version = 3)
        val applier = profileApplier(env)

        applier.apply(user, listOf(json(profileJson(version = 4, locale = "ar"))))
        assertEquals(AppLanguage.Ar, env.languages.effective())

        // A profile edit waits: its language already applies and is not taken from the server copy.
        env.languages.set(AppLanguage.En)
        env.engine.enqueue("profile", "p1", user, 4, AppJson.encodeToString(ProfileDraft.serializer(), ProfileDraft("Sara", "en", "Africa/Cairo", "EGP", 1)))
        applier.apply(user, listOf(json(profileJson(version = 5, locale = "ar"))))
        assertEquals(AppLanguage.En, env.languages.effective())
    }

    @Test
    fun aProfileFeedRowNeverTouchesTheWaitingEdit() = runTest {
        val env = env()
        env.seedProfile(version = 3)
        env.engine.enqueue("profile", "p1", user, 3, AppJson.encodeToString(ProfileDraft.serializer(), ProfileDraft("Mine", "en", "Africa/Cairo", "EGP", 1)))
        profileApplier(env).apply(user, listOf(json(profileJson(version = 4, displayName = "Theirs"))))
        assertEquals("Theirs", env.profileDao.row.value!!.displayName)
        assertEquals(3L, env.engine.get("profile", "p1")!!.baseVersion)
        assertEquals("waiting", env.engine.get("profile", "p1")!!.state.name.lowercase())
    }

    private fun session(id: String, state: String = "active", deletedAt: String? = null) =
        sessionJson(id, current = id == "a").replace("\"state\":\"active\"", "\"state\":\"$state\"")
            .replace("\"deleted_at\":null", "\"deleted_at\":${deletedAt?.let { "\"$it\"" } ?: "null"}")

    @Test
    fun activeSessionsAreUpsertedAndOtherStatesOrDeletionsRemoveTheRow() = runTest {
        val env = env()
        val applier = DeviceSessionFeedApplier(env.sessions)
        applier.apply(user, listOf(json(session("a")), json(session("b")), json(session("c"))))
        assertEquals(setOf("a", "b", "c"), env.sessionDao.rows.value.map { it.id }.toSet())
        assertEquals(true, env.sessionDao.rows.value.single { it.id == "a" }.current)

        applier.apply(user, listOf(json(session("b", state = "revoked")), json(session("c", deletedAt = "2026-10-02T00:00:00Z"))))
        assertEquals(listOf("a"), env.sessionDao.rows.value.map { it.id })
    }

    @Test
    fun aSessionFeedRowIsKeptWithItsFields() = runTest {
        val env = env()
        DeviceSessionFeedApplier(env.sessions).apply(user, listOf(json(session("a"))))
        val row: DeviceSessionEntity = env.sessionDao.rows.value.single()
        assertEquals("Device a", row.deviceName)
        assertEquals("android", row.platform)
        assertNull(row.lastUsedAt)
    }

    @Test
    fun aSweepRemovesTheSessionsTheReadDidNotReturn() = runTest {
        val env = env()
        val applier = DeviceSessionFeedApplier(env.sessions)
        applier.apply(user, listOf(json(session("a")), json(session("b")), json(session("c"))))
        applier.sweep(user, setOf("a", "c"))
        assertEquals(setOf("a", "c"), env.sessionDao.rows.value.map { it.id }.toSet())
    }
}
