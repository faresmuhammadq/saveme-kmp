package com.fares.saveme.feature.account.domain.export

import com.fares.saveme.core.network.AppJson
import com.fares.saveme.feature.account.data.dto.ExportDto
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.time.Instant

class ExportMappingTest {

    private fun dto(json: String) = AppJson.decodeFromString(ExportDto.serializer(), json)

    @Test
    fun everyStatusMaps() {
        mapOf(
            "queued" to ExportStatus.Queued, "running" to ExportStatus.Running, "ready" to ExportStatus.Ready,
            "failed" to ExportStatus.Failed, "expired" to ExportStatus.Expired,
        ).forEach { (wire, status) -> assertEquals(status, dto(exportJson(status = wire)).toExport().status, wire) }
        assertEquals(ExportStatus.Unknown("archiving"), dto(exportJson(status = "archiving")).toExport().status)
    }

    @Test
    fun activeMeansQueuedOrRunning() {
        assertEquals(listOf(true, true, false, false, false, false), listOf(
            ExportStatus.Queued, ExportStatus.Running, ExportStatus.Ready, ExportStatus.Failed, ExportStatus.Expired, ExportStatus.Unknown("x"),
        ).map { it.isActive })
    }

    @Test
    fun everyFailureCodeMapsAndAnUnknownOneKeepsItsText() {
        mapOf(
            "wallet_unavailable" to ExportFailure.WalletUnavailable, "generation_failed" to ExportFailure.GenerationFailed,
            "timed_out" to ExportFailure.TimedOut,
        ).forEach { (wire, failure) -> assertEquals(failure, dto(exportJson(status = "failed", failure = wire)).toExport().failure, wire) }
        assertEquals(ExportFailure.Unknown("boom"), dto(exportJson(status = "failed", failure = "boom")).toExport().failure)
        assertNull(dto(exportJson()).toExport().failure)
    }

    @Test
    fun scopeAndInstantsMap() {
        val export = dto(exportJson(scope = "wallet", walletId = "w1", status = "ready", size = 2048, url = SECRET_URL)).toExport()
        assertEquals(ExportScope.Wallet, export.scope)
        assertEquals("w1", export.walletId)
        assertEquals(Instant.parse("2026-10-06T10:00:00Z"), export.requestedAt)
        assertEquals(2048L, export.sizeBytes)
        assertEquals(ExportScope.Account, dto(exportJson()).toExport().scope)
    }

    @Test
    fun theDomainExportCarriesNoLink() {
        val export = dto(exportJson(status = "ready", url = SECRET_URL)).toExport()
        assertFalse("$export".contains("signature"))
        assertFalse(export.toString().contains("https://"))
        assertEquals("DownloadLink(***)", DownloadLink(SECRET_URL).toString())
    }
}
