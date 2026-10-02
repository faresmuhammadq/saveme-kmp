package com.fares.saveme.feature.transactions.data

import co.touchlab.kermit.LogWriter
import co.touchlab.kermit.Logger
import co.touchlab.kermit.Severity
import com.fares.saveme.core.sync.ChangeState
import com.fares.saveme.feature.auth.jsonResponse
import com.fares.saveme.feature.auth.problemBody
import com.fares.saveme.feature.transactions.TransactionTestEnv
import com.fares.saveme.feature.transactions.domain.model.TransactionDraft
import com.fares.saveme.feature.transactions.transactionBody
import com.fares.saveme.feature.transactions.transactionJson
import com.fares.saveme.feature.wallets.walletJson
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** P-09.2, FR-036: a send, a clash and a refusal log ids and request ids only. */
class TransactionLoggingTest {

    private val captured = mutableListOf<String>()

    private val writer = object : LogWriter() {
        override fun log(severity: Severity, message: String, tag: String, throwable: Throwable?) {
            captured += "$tag: $message"
        }
    }

    @Test
    fun noAmountNoteDetailsPersonOrPayloadReachesTheLog() = runTest {
        Logger.setLogWriters(writer)
        Logger.setMinSeverity(Severity.Verbose)
        var answer = 0
        val env = TransactionTestEnv(backgroundScope) {
            when (answer) {
                0 -> jsonResponse(201, transactionBody(transactionJson(version = 1, amount = 1_234_567, description = "SecretNote")))
                1 -> jsonResponse(409, problemBody(409, "VERSION_CONFLICT", ""","current":${transactionJson(version = 5, amount = 1_234_567, description = "SecretNote")}"""))
                else -> jsonResponse(422, problemBody(422, "VALIDATION_FAILED", ""","errors":[{"pointer":"/memo","code":"OUT_OF_RANGE"}]"""))
            }
        }
        try {
            env.signedIn()
            env.seedWallet(walletJson(id = "w1"))
            val draft = TransactionDraft("w1", "expense", LocalDate(2026, 10, 2), 1_234_567, "EGP", "c1", null, "SecretNote", "SecretDetails", "SecretPerson", false)
            env.service.create(draft)
            env.engine.drain()
            answer = 1
            env.seed(transactionJson(id = "t2", version = 2))
            env.service.save("t2", draft, 2)
            env.engine.drain()
            answer = 2
            env.service.keepMine("t2")
            env.engine.drain()
            assertTrue(captured.isNotEmpty(), "the engine should have logged its outcomes")
            val log = captured.joinToString("\n")
            listOf("1234567", "12345.67", "SecretNote", "SecretDetails", "SecretPerson").forEach { secret ->
                assertFalse(secret in log, "'$secret' reached the log")
            }
            assertTrue(env.pendingDao.rows.value.values.any { it.state == ChangeState.Refused.name || it.state == ChangeState.Clash.name })
        } finally {
            Logger.setLogWriters(emptyList())
        }
    }
}
