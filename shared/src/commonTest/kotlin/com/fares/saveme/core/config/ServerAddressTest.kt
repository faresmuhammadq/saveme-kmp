package com.fares.saveme.core.config

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ServerAddressTest {
    @Test
    fun hostAndPort() = assertEquals(ServerAddress("192.168.1.14", 8091), ServerAddress.parse("192.168.1.14:8091"))

    @Test
    fun hostWithoutPort() = assertEquals(ServerAddress("saveme.local", null), ServerAddress.parse("saveme.local"))

    @Test
    fun pastedSchemeAndSlashAreTolerated() {
        assertEquals("192.168.1.20:8091", ServerAddress.parse(" http://192.168.1.20:8091/ ")?.hostPort)
    }

    @Test
    fun rejectsBadPort() {
        assertNull(ServerAddress.parse("host:0"))
        assertNull(ServerAddress.parse("host:70000"))
        assertNull(ServerAddress.parse("host:abc"))
        assertNull(ServerAddress.parse("host:"))
    }

    @Test
    fun rejectsPathsEmptyAndSpaces() {
        assertNull(ServerAddress.parse(""))
        assertNull(ServerAddress.parse("host/api"))
        assertNull(ServerAddress.parse("my host:80"))
        assertNull(ServerAddress.parse(":8080"))
    }
}
