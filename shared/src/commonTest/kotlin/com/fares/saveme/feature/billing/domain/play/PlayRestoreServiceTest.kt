package com.fares.saveme.feature.billing.domain.play

import com.fares.saveme.feature.auth.domain.AuthState
import com.fares.saveme.feature.auth.jsonResponse
import com.fares.saveme.feature.auth.problemBody
import com.fares.saveme.feature.billing.bodyText
import com.fares.saveme.feature.billing.data.play.PlayConnection
import com.fares.saveme.feature.billing.data.play.PlayError
import com.fares.saveme.feature.billing.data.play.PlayResult
import com.fares.saveme.feature.billing.data.play.PlayUnavailable
import com.fares.saveme.feature.billing.play.GooglePlayFixtures
import com.fares.saveme.feature.billing.play.PlayHandler
import com.fares.saveme.feature.billing.play.PlayTestEnv
import com.fares.saveme.feature.billing.play.playPurchase
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.io.IOException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/** Spec 018 US2 and US3: restore after sign-in and on tap, chunking, refusals, outcomes and the another-account note. */
class PlayRestoreServiceTest {

    private fun handler(block: PlayHandler): PlayHandler = block

    private fun TestScope.env() = PlayTestEnv(backgroundScope, testScheduler)

    private fun PlayTestEnv.tokensOf(index: Int): List<String> =
        Json.parseToJsonElement(restoreRequests[index].bodyText()).jsonObject["purchases"]!!.jsonArray.map { it.jsonObject["purchase_token"]!!.jsonPrimitive.content }

    @Test
    fun aSignInWithNoPurchasesSendsNothing() = runTest {
        val t = env()
        t.signIn()
        val result = t.restorer.restore(RestoreTrigger.SignIn)
        assertIs<RestoreResult.Done>(result)
        assertEquals(0, t.restoreRequests.size, "saves the 10-an-hour budget")
    }

    @Test
    fun aSignInWithPurchasesSendsOneRestoreWithAllOfThemAndStaysSilent() = runTest {
        val t = env()
        t.signIn()
        t.restore = { jsonResponse(200, GooglePlayFixtures.restoreOf("linked", "already_yours")) }
        t.port.own(playPurchase("a", minutesAgo = 5), playPurchase("b", acknowledged = true, minutesAgo = 1))

        val result = assertIs<RestoreResult.Done>(t.restorer.restore(RestoreTrigger.SignIn))
        runCurrent()

        assertEquals(1, t.restoreRequests.size)
        assertEquals(listOf("b", "a"), t.tokensOf(0), "most recent first; acknowledged purchases go too")
        assertEquals("Plus · Monthly", (t.sessions.state.value as AuthState.SignedIn).account.plan)
        assertFalse(t.notes.notes.value.anotherAccount)
        assertEquals(2, result.summary.sent)
    }

    @Test
    fun aTapSendsEverythingIncludingPurchasesRefusedEarlierInTheRun() = runTest {
        val t = env()
        t.signIn()
        t.verify = { jsonResponse(409, GooglePlayFixtures.alreadyLinked()) }
        t.port.own(playPurchase("a"))
        t.verifier.pass(PassTrigger.ColdStart)
        assertTrue(t.notes.notes.value.refused.isNotEmpty())

        t.restore = { jsonResponse(200, GooglePlayFixtures.restoreOf("linked")) }
        t.restorer.restore(RestoreTrigger.Tap)

        assertEquals(listOf("a"), t.tokensOf(0))
        assertTrue(t.notes.notes.value.refused.isEmpty(), "linked now: no longer refused")
    }

    @Test
    fun aTapWithNothingOwnedSendsAnEmptyListAndSaysSo() = runTest {
        val t = env()
        t.signIn()
        t.restore = { jsonResponse(200, GooglePlayFixtures.restoreOf()) }
        val result = assertIs<RestoreResult.Done>(t.restorer.restore(RestoreTrigger.Tap))
        assertEquals(0, t.tokensOf(0).size)
        assertEquals(listOf(RestoreMessagePart.Nothing), result.summary.messageParts())
    }

    @Test
    fun twentySixPurchasesGoAs25PlusOneWithDifferentKeysMostRecentFirst() = runTest {
        val t = env()
        t.signIn()
        t.restore = { jsonResponse(200, GooglePlayFixtures.restoreOf()) }
        t.port.purchasesResult = PlayResult.Ok(List(26) { playPurchase("t-$it", minutesAgo = it) })

        t.restorer.restore(RestoreTrigger.Tap)

        assertEquals(2, t.restoreRequests.size)
        assertEquals(25, t.tokensOf(0).size)
        assertEquals(1, t.tokensOf(1).size)
        assertEquals("t-0", t.tokensOf(0).first())
        assertEquals("t-25", t.tokensOf(1).single())
        assertEquals(26, (t.tokensOf(0) + t.tokensOf(1)).toSet().size, "each token once")
        assertNotEquals(t.restoreRequests[0].headers["Idempotency-Key"], t.restoreRequests[1].headers["Idempotency-Key"])
    }

    @Test
    fun aRepeatedTokenIsSentOnce() = runTest {
        val t = env()
        t.signIn()
        t.restore = { jsonResponse(200, GooglePlayFixtures.restoreOf()) }
        t.port.own(playPurchase("same"), playPurchase("same"))
        t.restorer.restore(RestoreTrigger.Tap)
        assertEquals(listOf("same"), t.tokensOf(0))
    }

    @Test
    fun aRefusalStopsTheRemainingChunksAndTryAgainResendsTheFailedChunkWithItsKey() = runTest {
        val t = env()
        t.signIn()
        var calls = 0
        t.restore = {
            when (calls++) {
                0 -> jsonResponse(200, GooglePlayFixtures.restoreOf("linked"))
                1 -> jsonResponse(503, problemBody(503, "SERVICE_UNAVAILABLE"))
                else -> jsonResponse(200, GooglePlayFixtures.restoreOf("linked"))
            }
        }
        t.port.purchasesResult = PlayResult.Ok(List(60) { playPurchase("t-$it", minutesAgo = it) })

        val refused = assertIs<RestoreResult.ServiceUnavailable>(t.restorer.restore(RestoreTrigger.Tap))
        assertEquals(2, t.restoreRequests.size, "the third chunk was not tried")
        val failedKey = t.restoreRequests[1].headers["Idempotency-Key"]

        val done = assertIs<RestoreResult.Done>(t.restorer.restore(RestoreTrigger.Tap, refused.retry))
        assertEquals(4, t.restoreRequests.size, "the failed chunk again, then the rest")
        assertEquals(failedKey, t.restoreRequests[2].headers["Idempotency-Key"], "Try again reuses the failed chunk's key")
        assertEquals(t.tokensOf(1), t.tokensOf(2))
        assertEquals(60, done.summary.sent, "the summary counts the chunk done before the refusal too")
    }

    @Test
    fun eachRefusalHasItsOwnResult() = runTest {
        val cases = listOf<Pair<PlayHandler, (RestoreResult) -> Boolean>>(
            handler { throw IOException("no route") } to { r -> r is RestoreResult.Offline },
            handler { jsonResponse(429, GooglePlayFixtures.rateLimited()) } to { r -> r is RestoreResult.RateLimited && r.retryAfterSeconds == 120L },
            handler { jsonResponse(503, GooglePlayFixtures.unavailable()) } to { r -> r is RestoreResult.ServiceUnavailable },
            handler { jsonResponse(409, GooglePlayFixtures.inProgress()) } to { r -> r is RestoreResult.InProgress },
            handler { jsonResponse(422, problemBody(422, "VALIDATION_FAILED")) } to { r -> r is RestoreResult.Failed && r.requestId == "req-42" },
            handler { jsonResponse(500, "boom") } to { r -> r is RestoreResult.Failed },
        )
        cases.forEach { (handler, matches) ->
            val t = env()
            t.signIn()
            t.restore = handler
            t.port.own(playPurchase("a"))
            val result = t.restorer.restore(RestoreTrigger.Tap)
            assertTrue(matches(result), "unexpected $result")
            assertEquals(1, t.restoreRequests.size, "a tapped restore is never retried without a tap")
        }
    }

    @Test
    fun googlePlayUnavailableSendsNothing() = runTest {
        val t = env()
        t.signIn()
        t.port.connection = PlayConnection.Unavailable(PlayUnavailable.NotWired)
        assertEquals(RestoreResult.GooglePlayUnavailable, t.restorer.restore(RestoreTrigger.Tap))
        assertEquals(RestoreResult.GooglePlayUnavailable, t.restorer.restore(RestoreTrigger.SignIn))
        t.port.connection = PlayConnection.Ready
        t.port.purchasesResult = PlayResult.Failed(PlayError.ServiceUnavailable)
        assertEquals(RestoreResult.GooglePlayUnavailable, t.restorer.restore(RestoreTrigger.Tap))
        assertEquals(0, t.restoreRequests.size)
    }

    @Test
    fun restoreNeedsNoVerifiedEmailAndNoPlan() = runTest {
        val t = env()
        t.signIn()
        t.restore = { jsonResponse(200, GooglePlayFixtures.restoreOf("linked")) }
        t.port.own(playPurchase("a"))
        assertIs<RestoreResult.Done>(t.restorer.restore(RestoreTrigger.Tap))
        assertEquals(1, t.restoreRequests.size)
    }

    @Test
    fun linkedElsewhereSetsTheAnotherAccountNoteAndTheVerifierDoesNotResendIt() = runTest {
        val t = env()
        t.signIn()
        t.restore = { jsonResponse(200, GooglePlayFixtures.restored()) }
        t.port.own(playPurchase("a", minutesAgo = 2), playPurchase("b", productId = "plus_yearly", minutesAgo = 1))

        val result = assertIs<RestoreResult.Done>(t.restorer.restore(RestoreTrigger.SignIn))
        runCurrent()

        assertTrue(t.notes.notes.value.anotherAccount)
        assertEquals(1, result.summary.count(RestoreOutcome.LinkedElsewhere))
        t.verifier.pass(PassTrigger.Foreground)
        assertEquals(1, t.verifyRequests.size, "only the purchase that was not refused is sent (index 0 is the more recent one, b)")
    }

    @Test
    fun theSummaryAssemblesOneCombinedMessage() {
        fun summary(sent: Int, vararg counts: Pair<RestoreOutcome, Int>) = RestoreSummary(sent, counts.toMap())
        assertEquals(listOf(RestoreMessagePart.Restored), summary(1, RestoreOutcome.Linked to 1).messageParts())
        assertEquals(listOf(RestoreMessagePart.Restored), summary(1, RestoreOutcome.AlreadyYours to 1).messageParts())
        assertEquals(listOf(RestoreMessagePart.Nothing), summary(0).messageParts())
        assertEquals(listOf(RestoreMessagePart.Nothing), summary(2, RestoreOutcome.NotVerified to 2).messageParts())
        assertEquals(listOf(RestoreMessagePart.AnotherAccount), summary(1, RestoreOutcome.LinkedElsewhere to 1).messageParts())
        assertEquals(listOf(RestoreMessagePart.Pending), summary(1, RestoreOutcome.Pending to 1).messageParts())
        assertEquals(listOf(RestoreMessagePart.Unavailable), summary(1, RestoreOutcome.Unavailable to 1).messageParts())
        assertEquals(
            listOf(RestoreMessagePart.Restored, RestoreMessagePart.AnotherAccount, RestoreMessagePart.Pending, RestoreMessagePart.Unavailable),
            summary(4, RestoreOutcome.Linked to 1, RestoreOutcome.LinkedElsewhere to 1, RestoreOutcome.Pending to 1, RestoreOutcome.Unavailable to 1).messageParts(),
        )
        assertEquals(emptyList(), summary(1, RestoreOutcome.Unknown to 1).messageParts(), "unknown outcomes add nothing")
        assertEquals(listOf(RestoreMessagePart.Restored), summary(2, RestoreOutcome.Linked to 1, RestoreOutcome.Unknown to 1).messageParts())
    }

    @Test
    fun anUnknownOutcomeDecodesAsUnknown() {
        assertEquals(RestoreOutcome.Unknown, RestoreOutcome.from("something_new"))
        assertEquals(RestoreOutcome.LinkedElsewhere, RestoreOutcome.from("linked_elsewhere"))
    }

    @Test
    fun aSignedOutPersonRestoresNothing() = runTest {
        val t = env()
        assertEquals(RestoreResult.SignedOut, t.restorer.restore(RestoreTrigger.Tap))
        assertEquals(0, t.restoreRequests.size)
    }

    @Test
    fun theRequestBodyHasTheItemsAsAnArray() = runTest {
        val t = env()
        t.signIn()
        t.restore = { jsonResponse(200, GooglePlayFixtures.restoreOf()) }
        t.port.own(playPurchase("a"))
        t.restorer.restore(RestoreTrigger.Tap)
        assertTrue(Json.parseToJsonElement(t.restoreRequests.single().bodyText()).jsonObject["purchases"] is JsonArray)
    }
}
