package com.fares.saveme.feature.notifications.data

import com.fares.saveme.feature.auth.InMemoryKeyValueStore
import com.fares.saveme.feature.notifications.SESSION_ID
import com.fares.saveme.feature.notifications.TOKEN_A
import com.fares.saveme.feature.notifications.USER_ID
import com.fares.saveme.feature.notifications.domain.fingerprint
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PushRecordsStoreTest {

    @Test
    fun theRegistrationRecordRoundTripsAndClears() = runTest {
        val kv = InMemoryKeyValueStore()
        val store = PushRegistrationStore(kv)
        assertNull(store.read())
        val record = PushRegistrationRecord(USER_ID, SESSION_ID, fingerprint(TOKEN_A), 1_790_000_000)
        store.write(record)
        assertEquals(record, store.read())
        store.write(record.copy(stoppedFor = SESSION_ID))
        assertEquals(SESSION_ID, store.read()?.stoppedFor)
        store.clear()
        assertNull(store.read())
        assertFalse(kv.values.containsKey(PushRegistrationStore.KEY))
    }

    @Test
    fun noStoredValueEqualsOrContainsThePushAddress() = runTest {
        val kv = InMemoryKeyValueStore()
        PushRegistrationStore(kv).write(PushRegistrationRecord(USER_ID, SESSION_ID, fingerprint(TOKEN_A), 1))
        ShownNotificationsStore(kv).markShown(USER_ID, "n-1")
        PromptFlags(kv).markOffered()
        assertTrue(kv.values.values.none { TOKEN_A in it })
    }

    @Test
    fun theRecordPrintsItsStateOnly() {
        val record = PushRegistrationRecord(USER_ID, SESSION_ID, "abcdabcdabcdabcd", 1)
        assertEquals("PushRegistrationRecord(registered)", record.toString())
    }

    @Test
    fun shownIdsKeepTheNewestHundredAndDeduplicate() = runTest {
        val store = ShownNotificationsStore(InMemoryKeyValueStore())
        assertTrue(store.markShown(USER_ID, "id-0"))
        assertFalse(store.markShown(USER_ID, "id-0"))
        (1..120).forEach { assertTrue(store.markShown(USER_ID, "id-$it")) }
        // id-0 and the first twenty have fallen off the end of the list; the newest are still remembered.
        assertTrue(store.markShown(USER_ID, "id-0"))
        assertFalse(store.markShown(USER_ID, "id-120"))
    }

    @Test
    fun anotherPersonStartsAnEmptyListAndClearForgetsAll() = runTest {
        val store = ShownNotificationsStore(InMemoryKeyValueStore())
        store.markShown(USER_ID, "id-1")
        assertTrue(store.markShown("someone-else", "id-1"))
        assertTrue(store.markShown(USER_ID, "id-1"), "the other person's list replaced the first")
        store.clear()
        assertTrue(store.markShown(USER_ID, "id-1"))
    }

    @Test
    fun promptFlagsAreAboutTheInstallationAndSurviveTheOtherRecords() = runTest {
        val kv = InMemoryKeyValueStore()
        val flags = PromptFlags(kv)
        assertFalse(flags.isOffered())
        assertFalse(flags.permissionAsked())
        flags.markOffered()
        flags.markPermissionAsked()
        PushRegistrationStore(kv).clear()
        ShownNotificationsStore(kv).clear()
        assertTrue(flags.isOffered())
        assertTrue(flags.permissionAsked())
    }
}
