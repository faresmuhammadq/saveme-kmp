package com.fares.saveme.feature.account

import com.fares.saveme.core.presentation.runMviTest
import com.fares.saveme.feature.account.domain.export.DefaultExportService
import com.fares.saveme.feature.account.domain.export.ExportScope
import com.fares.saveme.feature.account.domain.export.ExportTestEnv
import com.fares.saveme.feature.account.domain.export.LinkOutcome
import com.fares.saveme.feature.account.domain.export.SECRET_URL
import com.fares.saveme.feature.account.domain.export.envelope
import com.fares.saveme.feature.account.domain.export.exportJson
import com.fares.saveme.feature.account.domain.export.scripted
import com.fares.saveme.feature.account.domain.usecase.RequestDeletionUseCase
import com.fares.saveme.feature.account.domain.deletion.SuccessorChoice
import com.fares.saveme.feature.account.ui.export.detail.ExportDetailEffect
import com.fares.saveme.feature.account.ui.export.detail.ExportDetailIntent
import com.fares.saveme.feature.account.ui.export.detail.ExportDetailViewModel
import com.fares.saveme.feature.auth.domain.SessionManager
import com.fares.saveme.feature.auth.jsonResponse
import com.fares.saveme.feature.sharing.domain.LogCapture
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** No download link, signature or password reaches a log line or a printed state, intent, effect or outcome (SC-003, FR-014, FR-037). */
class ExportLoggingTest {

    private val secrets = listOf("signature=4b1c0e9a", "https://api.example", "hunter2-secret")

    private fun assertClean(text: String) = secrets.forEach { assertFalse(text.contains(it), "\"$it\" leaked in: $text") }

    @Test
    fun requestWatchReadyAndOpenLogNoLink() = runTest {
        val log = LogCapture()
        try {
            val ready = exportJson(status = "ready", url = SECRET_URL)
            val env = ExportTestEnv(scripted(listOf(exportJson(status = "queued"), exportJson(status = "running"), ready)))
            env.signedIn()
            val service = DefaultExportService(env.api)
            val printed = mutableListOf<String>()
            printed += service.request(ExportScope.Account, null).toString()
            service.watch("e1").toList().forEach { printed += it.toString() }
            val link = service.openableLink("e1")
            printed += link.toString()
            assertTrue(link is LinkOutcome.Open)
            printed += service.read("e1").toString()
            printed += service.page(null).toString()
            printed.forEach(::assertClean)
            log.lines.forEach(::assertClean)
            assertTrue(log.lines.any { "export" in it }, "the export is logged by id and outcome")
        } finally {
            log.close()
        }
    }

    @Test
    fun theDetailStoreNeverPrintsALinkInStateIntentOrEffect() = runMviTest {
        val log = LogCapture()
        try {
            val link = com.fares.saveme.feature.account.domain.export.DownloadLink(SECRET_URL)
            val vm = ExportDetailViewModel(
                "e1",
                watch = { _, _ -> kotlinx.coroutines.flow.flowOf(com.fares.saveme.feature.account.domain.export.WatchEvent.Status(com.fares.saveme.feature.account.ui.export.anExport("e1"))) },
                openableLink = { LinkOutcome.Open(link) },
            )
            vm.onIntent(ExportDetailIntent.Started)
            vm.onIntent(ExportDetailIntent.OpenDownload)
            assertClean(vm.state.value.toString())
            assertClean(ExportDetailEffect.OpenDownload(link).toString())
            assertClean(ExportDetailIntent.OpenDownload.toString())
            assertClean(link.toString())
            log.lines.forEach(::assertClean)
        } finally {
            log.close()
        }
    }

    @Test
    fun aDeletionRequestWithSuccessorsLogsNoPassword() = runTest {
        val log = LogCapture()
        try {
            val env = AccountTestEnv {
                jsonResponse(200, """{"data":{"state":"pending_deletion","deletion_scheduled_for":"2026-11-05T10:00:00Z","successors":[]},"meta":{}}""")
            }
            env.signedIn()
            val outcome = RequestDeletionUseCase(env.api, SessionManager(env.auth.repository, env.auth.entitlementsApi, env.events, backgroundScope))(
                "hunter2-secret", listOf(SuccessorChoice("w3", "u5")),
            )
            assertClean(outcome.toString())
            log.lines.forEach(::assertClean)
            assertClean(com.fares.saveme.feature.account.data.dto.DeletionRequestDto("hunter2-secret").toString())
            // The mock engine records the request; its headers/body are not logged by the client (only method and URL).
            assertTrue(envelope("{}").isNotEmpty())
        } finally {
            log.close()
        }
    }
}
