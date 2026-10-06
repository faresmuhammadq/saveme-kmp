package com.fares.saveme.feature.notifications.domain

import com.fares.saveme.feature.auth.InMemoryKeyValueStore
import com.fares.saveme.feature.notifications.FakeProvider
import com.fares.saveme.feature.notifications.data.PromptFlags
import com.fares.saveme.feature.notifications.data.PushRegistrationStore
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** FR-011: once per installation, only with a provider and a permission that can still be asked. */
class NotificationPromptGateTest {

    @Test
    fun offeredOnlyWithAProviderAndAnAskablePermissionAndNotBefore() = runTest {
        val kv = InMemoryKeyValueStore()
        val gate = NotificationPromptGate(FakeProvider(), PromptFlags(kv))
        assertTrue(gate.shouldOffer(permissionAskable = true))
        assertFalse(gate.shouldOffer(permissionAskable = false))
        assertFalse(NotificationPromptGate(FakeProvider(isAvailable = false), PromptFlags(kv)).shouldOffer(true))
        gate.markOffered()
        assertFalse(gate.shouldOffer(true))
    }

    @Test
    fun offeringIsPermanentAcrossSignOut() = runTest {
        val kv = InMemoryKeyValueStore()
        val gate = NotificationPromptGate(FakeProvider(), PromptFlags(kv))
        assertTrue(gate.offerIfDue(permissionAskable = true, otherSheetShowing = false))
        PushRegistrationStore(kv).clear()
        assertFalse(gate.offerIfDue(permissionAskable = true, otherSheetShowing = false), "Not now is final")
    }

    @Test
    fun anotherSheetSuppressesWithoutCountingAsOffered() = runTest {
        val gate = NotificationPromptGate(FakeProvider(), PromptFlags(InMemoryKeyValueStore()))
        assertFalse(gate.offerIfDue(permissionAskable = true, otherSheetShowing = true))
        assertTrue(gate.offerIfDue(permissionAskable = true, otherSheetShowing = false), "the next moment may offer it")
    }

    @Test
    fun theAskedFlagIsPerInstallation() = runTest {
        val gate = NotificationPromptGate(FakeProvider(), PromptFlags(InMemoryKeyValueStore()))
        assertFalse(gate.permissionAsked())
        gate.markPermissionAsked()
        assertTrue(gate.permissionAsked())
    }
}
