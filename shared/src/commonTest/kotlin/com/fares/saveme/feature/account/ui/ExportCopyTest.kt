package com.fares.saveme.feature.account.ui

import com.fares.saveme.core.network.ApiErrorCode
import com.fares.saveme.feature.account.domain.export.ExportFailure
import com.fares.saveme.feature.account.domain.export.ExportStatus
import com.fares.saveme.feature.account.ui.export.SizeText
import com.fares.saveme.feature.account.ui.export.WaitText
import com.fares.saveme.feature.account.ui.export.label
import com.fares.saveme.feature.account.ui.export.sizeText
import com.fares.saveme.feature.account.ui.export.waitText
import com.fares.saveme.feature.auth.ui.message
import saveme.shared.generated.resources.Res
import saveme.shared.generated.resources.export_failed_generic
import saveme.shared.generated.resources.export_failed_wallet
import saveme.shared.generated.resources.export_in_progress
import saveme.shared.generated.resources.export_status_unknown
import kotlin.test.Test
import kotlin.test.assertEquals

class ExportCopyTest {

    @Test
    fun theNewCodeHasItsOwnCopyChosenByCode() {
        assertEquals(Res.string.export_in_progress, ApiErrorCode.EXPORT_IN_PROGRESS.message())
    }

    @Test
    fun failuresReadByCodeAndAnUnknownOneReadsAsGeneric() {
        assertEquals(Res.string.export_failed_wallet, ExportFailure.WalletUnavailable.label())
        assertEquals(Res.string.export_failed_generic, ExportFailure.GenerationFailed.label())
        assertEquals(Res.string.export_failed_generic, ExportFailure.TimedOut.label())
        assertEquals(Res.string.export_failed_generic, ExportFailure.Unknown("boom").label())
        assertEquals(Res.string.export_failed_generic, (null as ExportFailure?).label())
        assertEquals(Res.string.export_status_unknown, ExportStatus.Unknown("x").label())
    }

    @Test
    fun sizesAreKilobytesRoundedUpUnderOneMegabyteElseMegabytesWithOneDecimal() {
        assertEquals(SizeText.Kilobytes(1), sizeText(0))
        assertEquals(SizeText.Kilobytes(1), sizeText(1))
        assertEquals(SizeText.Kilobytes(1), sizeText(1024))
        assertEquals(SizeText.Kilobytes(2), sizeText(1025))
        assertEquals(SizeText.Kilobytes(180), sizeText(184_320))
        assertEquals(SizeText.Kilobytes(1024), sizeText(1_048_575))
        assertEquals(SizeText.Megabytes("1.0"), sizeText(1_048_576))
        assertEquals(SizeText.Megabytes("1.5"), sizeText(1_572_864))
        assertEquals(SizeText.Megabytes("12.3"), sizeText(12_900_000))
    }

    @Test
    fun waitsAreRoundedUpInMinutesUnderAnHourElseHours() {
        assertEquals(WaitText.Later, waitText(null))
        assertEquals(WaitText.Later, waitText(0))
        assertEquals(WaitText.Minutes(1), waitText(1))
        assertEquals(WaitText.Minutes(2), waitText(61))
        assertEquals(WaitText.Minutes(60), waitText(3599))
        assertEquals(WaitText.Hours(1), waitText(3600))
        assertEquals(WaitText.Hours(2), waitText(3601))
        assertEquals(WaitText.Hours(24), waitText(86_400))
    }
}
