package com.fares.saveme.feature.account

import androidx.room.Room
import androidx.sqlite.driver.AndroidSQLiteDriver
import com.fares.saveme.core.database.AppDatabase
import com.fares.saveme.core.database.DatabaseCleaner
import com.fares.saveme.core.storage.DeviceIdentityStore
import com.fares.saveme.feature.account.data.AccountApi
import com.fares.saveme.feature.account.data.local.ProfileEntity
import com.fares.saveme.feature.auth.AuthTestEnv
import com.fares.saveme.feature.auth.domain.AuthState
import com.fares.saveme.feature.auth.domain.SessionManager
import com.fares.saveme.feature.auth.domain.SignOutReason
import com.fares.saveme.feature.auth.domain.model.AccountState
import com.fares.saveme.feature.auth.domain.usecase.SignInUseCase
import com.fares.saveme.feature.auth.jsonResponse
import com.fares.saveme.feature.auth.problemBody
import com.fares.saveme.feature.auth.signInSuccessBody
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import kotlin.time.Instant

/**
 * The purge ends every session, so the next request of a pending-deletion account answers `UNAUTHENTICATED` and the app signs out
 * and wipes every table through the existing path, with no deletion-specific message (spec 016 FR-032, research R-11).
 */
@RunWith(RobolectricTestRunner::class)
class DeletionPurgeSignOutTest {

    private val db = Room.inMemoryDatabaseBuilder<AppDatabase>(RuntimeEnvironment.getApplication())
        .setDriver(AndroidSQLiteDriver())
        .build()

    @After
    fun close() = db.close()

    @Test
    fun aPendingDeletionAccountWhoseSessionWasEndedIsSignedOutAndWiped() = runTest {
        var signedIn = false
        val env = AuthTestEnv {
            if (!signedIn) {
                signedIn = true
                jsonResponse(200, signInSuccessBody())
            } else {
                jsonResponse(401, problemBody(401, "UNAUTHENTICATED"))
            }
        }
        val sessions = SessionManager(env.repository, env.entitlementsApi, env.events, backgroundScope, cleaners = listOf(DatabaseCleaner(db)))
        SignInUseCase(env.clientInfo, DeviceIdentityStore(env.keyValues), env.repository, sessions)(
            "sara@example.com", "pw", "0192f7a0-1c3d-7abc-9f01-2b3c4d5e6f70",
        )
        sessions.updateDeletion(AccountState.PendingDeletion, Instant.parse("2026-11-05T10:00:00Z"))
        db.profileDao().upsert(ProfileEntity("u1", "p1", 3, "Sara", "ar", "Africa/Cairo", "EGP", false, 1, null, "2026-09-28T10:00:00Z"))
        db.walletDao().upsert(
            com.fares.saveme.feature.wallets.data.local.WalletEntity(
                "w1", "u1", "cash", "Pocket", "EGP", 100, 100, null, null, null, false, "owner", 1, 1,
                "2026-09-28T10:00:00Z", "2026-09-28T10:00:00Z", "2026-09-28T10:00:00Z",
            ),
        )

        // Any request after the purge: the server answers 401 UNAUTHENTICATED for the ended session.
        env.secureStore.read()
        AccountApi(env.client).deletionPlan()
        repeat(100) {
            advanceUntilIdle()
            if (sessions.state.value is AuthState.SignedOut) return@repeat
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) { kotlinx.coroutines.delay(20) }
        }
        advanceUntilIdle()

        assertEquals(AuthState.SignedOut(SignOutReason.SessionEnded), sessions.state.value)
        assertNull(db.profileDao().get())
        assertNull(db.walletDao().get("w1"))
        assertNull(env.secureStore.read())
    }
}
