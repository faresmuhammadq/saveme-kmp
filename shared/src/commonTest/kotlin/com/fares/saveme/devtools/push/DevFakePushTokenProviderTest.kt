package com.fares.saveme.devtools.push

import com.fares.saveme.feature.auth.InMemoryKeyValueStore
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DevFakePushTokenProviderTest {

    @Test
    fun aStableTokenInTheBackendsFormat() = runTest {
        val kv = InMemoryKeyValueStore()
        val provider = DevFakePushTokenProvider(kv) {}
        val token = provider.currentToken()
        assertTrue(provider.isAvailable)
        assertTrue(token.startsWith(DevFakePushTokenProvider.PREFIX))
        assertTrue(token.length <= 4096 && token.all { it in '!'..'~' }, "printable ASCII")
        assertEquals(token, provider.currentToken())
        assertEquals(token, DevFakePushTokenProvider(kv) {}.currentToken(), "the same installation, the same address")
    }

    @Test
    fun discardForgetsIt() = runTest {
        val kv = InMemoryKeyValueStore()
        val provider = DevFakePushTokenProvider(kv) {}
        val token = provider.currentToken()
        provider.discard()
        assertNull(kv.values[DevFakePushTokenProvider.KEY])
        assertNotEquals(token, provider.currentToken())
    }

    @Test
    fun rotateMakesANewOneAndAnnouncesIt() = runTest {
        var announced = 0
        val provider = DevFakePushTokenProvider(InMemoryKeyValueStore()) { announced++ }
        val first = provider.currentToken()
        provider.rotate()
        assertEquals(1, announced)
        assertNotEquals(first, provider.currentToken())
    }
}
