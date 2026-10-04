package com.fares.saveme.feature.recurring.data

import co.touchlab.kermit.LogWriter
import co.touchlab.kermit.Logger
import co.touchlab.kermit.Severity
import com.fares.saveme.feature.auth.jsonResponse
import com.fares.saveme.feature.auth.problemBody
import com.fares.saveme.feature.recurring.RecurringServiceEnv
import com.fares.saveme.feature.recurring.confirmDraft
import com.fares.saveme.feature.recurring.occurrenceBody
import com.fares.saveme.feature.recurring.occurrenceJson
import com.fares.saveme.feature.recurring.ruleBody
import com.fares.saveme.feature.recurring.ruleDraft
import com.fares.saveme.feature.recurring.ruleJson
import com.fares.saveme.feature.wallets.walletJson
import io.ktor.http.HttpMethod
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** P-09.2, FR-036: a rule send, a confirm, an accepted clash, a refusal and a catch-up log ids and codes, never content. */
class RecurringLoggingTest {

    private val captured = mutableListOf<String>()

    private val writer = object : LogWriter() {
        override fun log(severity: Severity, message: String, tag: String, throwable: Throwable?) {
            captured += "$tag: $message"
        }
    }

    @Test
    fun noAmountTextPaymentDateOrPayloadReachesTheLog() = runTest {
        Logger.setLogWriters(writer)
        Logger.setMinSeverity(Severity.Verbose)
        var step = 0
        val env = RecurringServiceEnv(backgroundScope, newIds = listOf("r-new", "tc1", "tc2")) { request ->
            val path = request.url.encodedPath
            when {
                path.endsWith("/materialise") -> jsonResponse(200, ruleBody(ruleJson(id = "r1", description = "SecretNote")))
                request.method == HttpMethod.Put && "/recurring-rules/" in path ->
                    jsonResponse(201, ruleBody(ruleJson(id = "r-new", version = 1, amount = 1_234_567, description = "SecretNote")))
                step == 0 -> jsonResponse(409, problemBody(409, "VERSION_CONFLICT", ""","current":${occurrenceJson(version = 5, status = "confirmed", transactionId = "other", amount = 1_234_567, description = "SecretNote")}"""))
                step == 1 -> jsonResponse(422, problemBody(422, "VALIDATION_FAILED", ""","errors":[{"pointer":"/status","code":"CURRENCY_MISMATCH"}]"""))
                else -> jsonResponse(200, occurrenceBody())
            }
        }
        try {
            env.signedIn()
            env.seedWallet(walletJson(id = "w1"))
            env.seedRule(ruleJson(id = "r1"))
            env.seedOccurrence(occurrenceJson(id = "o1", version = 3))
            env.seedOccurrence(occurrenceJson(id = "o2", version = 3, ordinal = 2, scheduledDate = "2026-12-01"))

            env.service.create("w1", ruleDraft(amount = 1_234_567, description = "SecretNote"))
            env.engine.drain()
            env.service.confirm("o1", confirmDraft(amount = 1_234_567, date = LocalDate(2026, 11, 1), description = "SecretNote"))
            env.engine.drain()
            step = 1
            env.service.confirm("o2", confirmDraft(amount = 1_234_567, description = "SecretNote"))
            env.engine.drain()
            env.service.catchUp("r1")

            assertTrue(captured.isNotEmpty(), "the engine and the service should have logged their outcomes")
            val log = captured.joinToString("\n")
            listOf("1234567", "12345.67", "SecretNote", "2026-11-01", "2026-12-01", "tc1", "tc2").forEach { secret ->
                assertFalse(secret in log, "'$secret' reached the log")
            }
        } finally {
            Logger.setLogWriters(emptyList())
        }
    }
}
