package com.fares.saveme.feature.billing

import com.fares.saveme.feature.auth.jsonResponse
import com.fares.saveme.feature.billing.data.dto.PlayAccountReferenceDto
import com.fares.saveme.feature.billing.data.dto.PlayPurchaseRequestDto
import com.fares.saveme.feature.billing.data.dto.PlayRestoreRequestDto
import com.fares.saveme.feature.billing.data.play.AccountReference
import com.fares.saveme.feature.billing.data.play.PlayProduct
import com.fares.saveme.feature.billing.data.play.PlayProductKind
import com.fares.saveme.feature.billing.data.play.PurchaseToken
import com.fares.saveme.feature.billing.domain.play.PassTrigger
import com.fares.saveme.feature.billing.domain.play.PlayKeys
import com.fares.saveme.feature.billing.domain.play.RestoreTrigger
import com.fares.saveme.feature.billing.play.GooglePlayFixtures
import com.fares.saveme.feature.billing.play.PlayHandler
import com.fares.saveme.feature.billing.play.PlayTestEnv
import com.fares.saveme.feature.billing.play.playPurchase
import com.fares.saveme.feature.sharing.domain.LogCapture
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.io.IOException
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Purchase tokens, idempotency keys, account references and prices never reach a log line (FR-050, SC-006, P-09.2). */
class GooglePlayLoggingTest {

    private fun handler(block: PlayHandler): PlayHandler = block

    @Test
    fun noLogLineCarriesATokenAKeyAReferenceOrAPriceForAnyAnswer() = runTest {
        val token = "secret-purchase-token-AAAA1111BBBB2222"
        val answers = listOf(
            handler { jsonResponse(200, GooglePlayFixtures.active()) },
            handler { jsonResponse(202, GooglePlayFixtures.pending()) },
            handler { jsonResponse(409, GooglePlayFixtures.alreadyLinked()) },
            handler { jsonResponse(409, GooglePlayFixtures.keyReused()) },
            handler { jsonResponse(422, GooglePlayFixtures.notVerified()) },
            handler { jsonResponse(422, GooglePlayFixtures.unknownProduct()) },
            handler { jsonResponse(429, GooglePlayFixtures.rateLimited()) },
            handler { jsonResponse(503, GooglePlayFixtures.unavailable()) },
            handler { jsonResponse(500, "boom") },
            handler { throw IOException("no route") },
        )
        val capture = LogCapture()
        val keys = mutableListOf<String>()
        try {
            answers.forEach { answer ->
                val t = PlayTestEnv(backgroundScope, testScheduler)
                t.signIn()
                t.verify = answer
                t.restore = answer
                t.port.own(playPurchase(token))
                t.verifier.pass(PassTrigger.ColdStart)
                runCurrent()
                t.restorer.restore(RestoreTrigger.Tap)
                runCurrent()
                keys += t.verifyRequests.mapNotNull { it.headers["Idempotency-Key"] } + t.restoreRequests.mapNotNull { it.headers["Idempotency-Key"] }
            }
        } finally {
            capture.close()
        }
        assertTrue(capture.lines.any { "play_pass" in it }, "the capture saw the pass logs")
        assertTrue(capture.lines.any { "play_restore" in it }, "the capture saw the restore logs")
        assertTrue(keys.isNotEmpty())
        capture.lines.forEach { line ->
            assertFalse(line.contains(token), line)
            assertFalse(line.contains("secret-purchase"), line)
            assertFalse(line.contains(GooglePlayFixtures.REFERENCE), line)
            keys.forEach { assertFalse(line.contains(it), line) }
            assertFalse(line.contains("gp1:"), line)
            assertFalse(line.contains("149"), line)
        }
    }

    @Test
    fun holdersPrintRedacted() {
        val token = PurchaseToken("secret-purchase-token")
        assertFalse(token.toString().contains("secret"))
        assertFalse(AccountReference(GooglePlayFixtures.REFERENCE).toString().contains(GooglePlayFixtures.REFERENCE))
        assertFalse(PlayAccountReferenceDto(GooglePlayFixtures.REFERENCE).toString().contains(GooglePlayFixtures.REFERENCE))
        val request = PlayPurchaseRequestDto("secret-purchase-token", "plus_monthly")
        assertFalse(request.toString().contains("secret"))
        assertFalse(PlayRestoreRequestDto(listOf(request)).toString().contains("secret"))
        assertFalse(playPurchase("secret-purchase-token").toString().contains("secret"))
        assertFalse(PlayProduct("plus_monthly", PlayProductKind.Subscription, 149_000_000, "EGP").toString().contains("149"))
        assertFalse(PlayKeys.verify("u", token, com.fares.saveme.feature.billing.data.play.PlayPurchaseState.Purchased).contains("secret"))
    }
}
