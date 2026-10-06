package com.fares.saveme.feature.reports.domain

import co.touchlab.kermit.Logger
import com.fares.saveme.core.network.ApiErrorCode
import com.fares.saveme.core.network.ApiResult
import com.fares.saveme.feature.reports.data.ReportAnswer
import com.fares.saveme.feature.reports.data.ReportsApi
import com.fares.saveme.feature.reports.domain.model.CategoryBreakdown
import com.fares.saveme.feature.reports.domain.model.PlannedSummary
import com.fares.saveme.feature.reports.domain.model.ReportScope
import com.fares.saveme.feature.reports.domain.model.SummaryFigures
import com.fares.saveme.feature.reports.domain.model.TrendPoint
import kotlinx.datetime.LocalDate

/** What the shown report was computed for: the probe asks the server the same three questions. */
data class ProbeRequest(
    val scope: ReportScope,
    val from: LocalDate,
    val to: LocalDate,
    val currency: String,
    val includeScheduled: Boolean,
    val trendLength: Int,
    /** The last day of the trend's last period's range, sent as `date`. */
    val trendDate: LocalDate,
) {
    override fun toString() = "ProbeRequest(${scope.key})"
}

/** The phone's synced-only answers to compare. */
data class ProbeComputation(
    val summary: SummaryFigures,
    val planned: PlannedSummary?,
    val breakdown: CategoryBreakdown,
    val trend: List<TrendPoint>,
    /** `(wallet_id, currency)` of every excluded wallet (cross-wallet only). */
    val excluded: Set<Pair<String, String>>,
) {
    override fun toString() = "ProbeComputation"
}

/**
 * Compares the phone's figures with the server's after a successful read (spec 014 research R-12). Development build only:
 * the release binding is [NoParityProbe] and never calls the report figure endpoints (FR-028).
 */
interface ReportParityProbe {
    /** False for the release binding: nothing is computed or read for it. */
    val enabled: Boolean get() = true

    suspend fun compare(request: ProbeRequest, computed: ProbeComputation)
}

object NoParityProbe : ReportParityProbe {
    override val enabled: Boolean = false

    override suspend fun compare(request: ProbeRequest, computed: ProbeComputation) = Unit
}

/**
 * Reads the three reports of the shown scope, range, currency and switch and logs `report.parity ok|mismatch kind=<kind>
 * scope=<scope> members=<names>`: names only, never a value, a share, a category or a wallet name (FR-025, FR-028). The
 * answers are compared on known members only (R-16) and never shown.
 */
class DevReportParityProbe(private val api: ReportsApi) : ReportParityProbe {
    private val log = Logger.withTag("reports")

    private companion object {
        const val INVALID_ID = "INVALID_ID"
        const val INVALID_QUERY_PARAMETER = "INVALID_QUERY_PARAMETER"
    }

    override suspend fun compare(request: ProbeRequest, computed: ProbeComputation) {
        val wallet = (request.scope as? ReportScope.Wallet)?.walletId
        val currency = if (wallet == null) request.currency else null
        val scope = request.scope.key
        val summary = api.summary(request.from, request.to, wallet, request.includeScheduled, currency)
        report("summary", scope, summary) { answer ->
            ReportMapping.differingSummaryMembers(computed.summary, computed.planned, answer.data) +
                excludedMember(wallet, computed, answer)
        }
        val categories = api.byCategory(request.from, request.to, wallet, request.includeScheduled, currency)
        report("by-category", scope, categories) { answer -> ReportMapping.differingCategoryMembers(computed.breakdown, answer.data) }
        val trend = api.trend(request.trendLength, request.trendDate, wallet, request.includeScheduled, currency)
        report("trend", scope, trend) { answer -> ReportMapping.differingTrendMembers(computed.trend, answer.data, request.includeScheduled) }
    }

    private fun <T> excludedMember(wallet: String?, computed: ProbeComputation, answer: ReportAnswer<T>): List<String> =
        if (wallet == null && ReportMapping.differingExcluded(computed.excluded, answer.excludedWallets)) listOf("excluded_wallets") else emptyList()

    private fun <T> report(kind: String, scope: String, answer: ApiResult<ReportAnswer<T>>, differing: (ReportAnswer<T>) -> List<String>) {
        when (answer) {
            is ApiResult.Success -> {
                val members = differing(answer.value)
                if (members.isEmpty()) log.i { "report.parity ok kind=$kind scope=$scope" } else log.w { "report.parity mismatch kind=$kind scope=$scope members=${members.joinToString(",")}" }
            }
            is ApiResult.Failure -> when {
                answer.code == ApiErrorCode.NOT_FOUND -> log.i { "report.parity skipped kind=$kind scope=$scope reason=not_found" }
                answer.problem.code == INVALID_ID -> log.i { "report.parity skipped kind=$kind scope=$scope reason=invalid_id" }
                answer.problem.code == INVALID_QUERY_PARAMETER ->
                    log.e { "report.parity client defect kind=$kind scope=$scope code=$INVALID_QUERY_PARAMETER request=${answer.problem.requestId}" }
                else -> log.i { "report.parity skipped kind=$kind scope=$scope reason=${answer.problem.code.ifEmpty { "status_${answer.problem.status}" }}" }
            }
            ApiResult.Offline, ApiResult.Timeout -> log.i { "report.parity skipped kind=$kind scope=$scope reason=offline" }
        }
    }
}
