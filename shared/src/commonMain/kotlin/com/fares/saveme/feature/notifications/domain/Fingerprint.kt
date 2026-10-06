package com.fares.saveme.feature.notifications.domain

/**
 * FNV-1a 64-bit over the UTF-8 bytes, as 16 hex digits. Only detects "the address changed" on this phone; it is not a
 * security control and is never compared across people (R-04, no crypto dependency).
 */
fun fingerprint(value: String): String {
    var hash = 0xcbf29ce484222325uL
    for (byte in value.encodeToByteArray()) {
        hash = (hash xor (byte.toULong() and 0xffuL)) * 0x100000001b3uL
    }
    return hash.toString(16).padStart(16, '0')
}
