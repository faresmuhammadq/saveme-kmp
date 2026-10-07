package com.fares.saveme.feature.billing.domain.play

import com.fares.saveme.core.platform.sha256
import com.fares.saveme.feature.billing.data.play.PlayPurchaseState
import com.fares.saveme.feature.billing.data.play.PurchaseToken
import com.fares.saveme.feature.billing.domain.AttemptKey
import kotlin.uuid.Uuid

/**
 * Idempotency keys for Google Play sends (spec 018 R-3). A verify key is derived one-way, never stored: the same account +
 * purchase + Google Play state always yields the same key, across retries, foreground passes and process death.
 */
object PlayKeys {
    private const val PREFIX = "gp1:"

    /**
     * `gp1:` + hex(SHA-256(`gp1|user|state|recheckHour|token`)): 68 characters inside the convention's `[A-Za-z0-9._:-]{16,255}`.
     * [recheckHour] (UTC hours since the epoch) is set only when rechecking, an hour or more later, a purchase Google Play reports
     * as purchased but the server answered `202` for; the server stores only success answers, so a new key is the only way to get
     * a fresh answer.
     */
    fun verify(userId: String, token: PurchaseToken, state: PlayPurchaseState, recheckHour: Long? = null): String {
        val stateName = when (state) {
            PlayPurchaseState.Pending -> "pending"
            PlayPurchaseState.Purchased -> "purchased"
        }
        val material = "gp1|$userId|$stateName|${recheckHour ?: "-"}|${token.value}"
        return PREFIX + hex(sha256(material.encodeToByteArray()))
    }

    /** One restore attempt: random, reused on its automatic or tapped retries (spec 015's attempt key, redacted). */
    fun attempt(): AttemptKey = AttemptKey(Uuid.random().toString())

    /** A short one-way digest of a token, for in-memory sets that must not hold the token itself. */
    fun digest(token: PurchaseToken): String = hex(sha256("gp1-digest|${token.value}".encodeToByteArray())).take(32)

    private fun hex(bytes: ByteArray): String = buildString(bytes.size * 2) {
        for (b in bytes) {
            val v = b.toInt() and 0xff
            append(HEX[v shr 4]).append(HEX[v and 0x0f])
        }
    }

    private const val HEX = "0123456789abcdef"
}
