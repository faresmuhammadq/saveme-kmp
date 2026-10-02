package com.fares.saveme.feature.account

import androidx.room.Room
import androidx.sqlite.driver.AndroidSQLiteDriver
import com.fares.saveme.core.database.AppDatabase
import com.fares.saveme.core.database.DatabaseCleaner
import com.fares.saveme.core.database.SyncScopeEntity
import com.fares.saveme.core.locale.AppLanguage
import com.fares.saveme.core.locale.AppLanguageStore
import com.fares.saveme.core.storage.DeviceIdentityStore
import com.fares.saveme.feature.account.data.local.DeviceSessionEntity
import com.fares.saveme.feature.account.data.local.ProfileEntity
import com.fares.saveme.feature.auth.AuthTestEnv
import com.fares.saveme.feature.auth.domain.SessionManager
import com.fares.saveme.feature.auth.domain.usecase.SignInUseCase
import com.fares.saveme.feature.auth.domain.usecase.SignOutUseCase
import com.fares.saveme.feature.auth.jsonResponse
import com.fares.saveme.feature.auth.signInSuccessBody
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/** Nothing of one person survives sign-out into the next person's session (P-09; quickstart 9). */
@RunWith(RobolectricTestRunner::class)
class SignOutWipeTest {

    private val db = Room.inMemoryDatabaseBuilder<AppDatabase>(RuntimeEnvironment.getApplication())
        .setDriver(AndroidSQLiteDriver())
        .build()

    @After
    fun close() = db.close()

    @Test
    fun signOutEmptiesTheDatabaseAndTheLanguage() = runTest {
        var signedIn = false
        val env = AuthTestEnv {
            if (!signedIn) { signedIn = true; jsonResponse(200, signInSuccessBody()) } else respond("", HttpStatusCode.NoContent)
        }
        val languages = AppLanguageStore(env.keyValues, com.fares.saveme.core.network.FakeClientInfo(language = "en"))
        val sessions = SessionManager(env.repository, env.entitlementsApi, env.events, backgroundScope, cleaners = listOf(DatabaseCleaner(db), languages))
        SignInUseCase(env.clientInfo, DeviceIdentityStore(env.keyValues), env.repository, sessions)(
            "sara@example.com", "pw", "0192f7a0-1c3d-7abc-9f01-2b3c4d5e6f70",
        )
        db.profileDao().upsert(
            ProfileEntity("u1", "p1", 3, "Sara", "ar", "Africa/Cairo", "EGP", false, 1, null, "2026-09-28T10:00:00Z"),
        )
        // A waiting edit (spec 004): it goes with the account, never sent under another (FR-018).
        db.pendingChangeDao().upsert(
            com.fares.saveme.core.sync.PendingChangeEntity(
                "profile", "p1", "u1", "user", "u1", 3, "{}", false, false, 0, 1, "Waiting", 0, null, null, null,
                "2026-09-28T10:00:00Z",
            ),
        )
        // Wallets and their members are wiped too (spec 006 FR-026).
        db.walletDao().upsert(
            com.fares.saveme.feature.wallets.data.local.WalletEntity(
                "w1", "u1", "cash", "Pocket", "EGP", 100, 100, null, null, null, false, "owner", 1, 1,
                "2026-09-28T10:00:00Z", "2026-09-28T10:00:00Z", "2026-09-28T10:00:00Z",
            ),
        )
        db.walletMemberDao().insertAll(
            listOf(com.fares.saveme.feature.wallets.data.local.WalletMemberEntity("m1", "w1", "u1", "Sara", "owner", 1, "2026-09-28T10:00:00Z")),
        )
        db.deviceSessionDao().replaceAll(listOf(DeviceSessionEntity("a", "u1", "Pixel", "android", "1.0.0", "active", "2026-09-28T09:00:00Z", null, true)))
        db.syncScopeDao().upsert(SyncScopeEntity(SyncScopeEntity.PROFILE, "2026-09-28T10:00:00Z"))
        languages.set(AppLanguage.Ar)

        SignOutUseCase(env.repository, sessions, serverTimeout = null)()

        assertNull(db.profileDao().get())
        assertNull(db.pendingChangeDao().get("profile", "p1"))
        assertNull(db.walletDao().get("w1"))
        assertEquals(emptyList<com.fares.saveme.feature.wallets.data.local.WalletMemberEntity>(), db.walletMemberDao().get("w1"))
        assertEquals(emptyList<DeviceSessionEntity>(), db.deviceSessionDao().observeAll().first())
        assertNull(db.syncScopeDao().observe(SyncScopeEntity.PROFILE).first())
        assertNull(env.keyValues.values["app_language"])
        assertEquals(AppLanguage.En, languages.effective())
        assertNull(env.secureStore.read())
    }
}
