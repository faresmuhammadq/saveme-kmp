package com.fares.saveme.core.platform

/**
 * SHA-256 of [bytes] through the platform's own crypto (`MessageDigest` on Android, CommonCrypto on iOS): no dependency.
 * Used to derive one-way idempotency keys (spec 018 R-3).
 */
expect fun sha256(bytes: ByteArray): ByteArray
