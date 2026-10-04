package com.fares.saveme.feature.budgets.data

import co.touchlab.kermit.LogWriter
import co.touchlab.kermit.Logger
import co.touchlab.kermit.Severity
import com.fares.saveme.feature.auth.jsonResponse
import com.fares.saveme.feature.auth.problemBody
import com.fares.saveme.feature.budgets.BudgetTestEnv
import com.fares.saveme.feature.budgets.budgetBody
import com.fares.saveme.feature.budgets.budgetJson
import com.fares.saveme.feature.budgets.expense
import com.fares.saveme.feature.budgets.progressJson
import com.fares.saveme.feature.budgets.progressPage
import com.fares.saveme.feature.budgets.domain.model.BudgetDraft
import io.ktor.http.HttpMethod
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** P-09.2, FR-024: a send, an archive, a refusal, a clash and a progress walk log ids and codes, never amounts or payloads. */
class BudgetLoggingTest {

    private val captured = mutableListOf<String>()

    private val writer = object : LogWriter() {
        override fun log(severity: Severity, message: String, tag: String, throwable: Throwable?) {
            captured += "$tag: $message"
        }
    }

    @Test
    fun noLimitSpentProjectionOrPayloadReachesTheLog() = runTest {
        Logger.setLogWriters(writer)
        Logger.setMinSeverity(Severity.Verbose)
        var step = 0
        val env = BudgetTestEnv(backgroundScope) { request ->
            val path = request.url.encodedPath
            when {
                path.endsWith("/budget-progress") -> jsonResponse(200, progressPage(listOf(progressJson("b1", spent = 1_234_567, limit = 7_654_321, projected = 9_876_543))))
                path.endsWith("/archive") -> jsonResponse(200, budgetBody(budgetJson("b1", version = 4, amount = 7_654_321, archivedAt = "2026-10-02T10:00:00Z")))
                request.method == HttpMethod.Put && step == 0 -> jsonResponse(201, budgetBody(budgetJson("b-new-1", amount = 7_654_321, version = 1)))
                request.method == HttpMethod.Put && step == 1 -> jsonResponse(409, problemBody(409, "VERSION_CONFLICT", ""","current":${budgetJson("b1", amount = 5_555_555, version = 9)}"""))
                else -> jsonResponse(422, problemBody(422, "VALIDATION_FAILED", ""","errors":[{"pointer":"/amount_minor","code":"OUT_OF_RANGE"}]"""))
            }
        }
        try {
            env.signedIn()
            env.seed(budgetJson("b1", amount = 7_654_321, version = 3))
            env.spending.set(expense("e1", "2026-10-01", 1_234_567))
            env.service.create(BudgetDraft("c1", "EGP", 7_654_321))
            env.engine.drain()
            step = 1
            env.service.saveLimit("b1", 7_654_322, 3)
            env.engine.drain()
            step = 2
            env.service.keepMine("b1")
            env.engine.drain()
            env.service.refresh(force = true)

            assertTrue(captured.isNotEmpty(), "the engine and the service should have logged their outcomes")
            val log = captured.joinToString("\n")
            listOf("7654321", "76543.21", "7654322", "1234567", "12345.67", "9876543", "5555555").forEach { secret ->
                assertFalse(secret in log, "'$secret' reached the log")
            }
        } finally {
            Logger.setLogWriters(emptyList())
        }
    }
}
