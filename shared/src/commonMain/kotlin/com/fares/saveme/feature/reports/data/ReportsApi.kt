package com.fares.saveme.feature.reports.data

import com.fares.saveme.core.network.ApiClient
import com.fares.saveme.core.network.ApiResult
import com.fares.saveme.core.network.AppJson
import com.fares.saveme.feature.reports.data.dto.CrossWalletReportMetaDto
import com.fares.saveme.feature.reports.data.dto.ExcludedWalletDto
import com.fares.saveme.feature.reports.data.dto.ReportByCategoryDto
import com.fares.saveme.feature.reports.data.dto.ReportSummaryDto
import com.fares.saveme.feature.reports.data.dto.ReportTrendDto
import io.ktor.client.request.parameter
import io.ktor.client.request.url
import io.ktor.http.HttpMethod
import kotlinx.datetime.LocalDate

/** A report body with the cross-wallet `meta.excluded_wallets` (empty for a wallet report). */
data class ReportAnswer<T>(val data: T, val excludedWallets: List<ExcludedWalletDto>)

/**
 * The six read-only operations of reports.yaml v1.11 (contracts/api-client.md). All credentialed, none syncable, none
 * with an idempotency key. The client enforces the query rules before sending (a violation is a client defect, so it
 * throws): `date` never with `from`/`to`, `from` <= `to` within 366 days, `months` 1..12, `currency` `^[A-Z]{3}$`.
 *
 * Release builds call only [trend] with `months` and `date` (the Budget Period lookup, FR-019); the other operations
 * are read by the development build's parity probe (FR-028).
 */
class ReportsApi(private val client: ApiClient) {

    /** `GET /reports/trend` or `GET /wallets/{id}/reports/trend`. The lookup passes only [months] and [date]. */
    suspend fun trend(
        months: Int,
        date: LocalDate,
        walletId: String? = null,
        includePlanned: Boolean? = null,
        currency: String? = null,
    ): ApiResult<ReportAnswer<ReportTrendDto>> {
        require(months in MIN_MONTHS..MAX_MONTHS) { "months must be 1..12" }
        requireDate(date)
        currency?.let { requireCurrency(it) }
        return answer(
            wallet = walletId,
            path = "trend",
            serializer = ReportTrendDto.serializer(),
        ) {
            parameter("months", months)
            parameter("date", date.toString())
            includePlanned?.let { parameter("include_planned", it.toString()) }
            currency?.let { parameter("currency", it) }
        }
    }

    suspend fun summary(
        from: LocalDate,
        to: LocalDate,
        walletId: String? = null,
        includePlanned: Boolean = false,
        currency: String? = null,
    ): ApiResult<ReportAnswer<ReportSummaryDto>> {
        requireRange(from, to)
        currency?.let { requireCurrency(it) }
        return answer(wallet = walletId, path = "summary", serializer = ReportSummaryDto.serializer()) {
            parameter("from", from.toString())
            parameter("to", to.toString())
            parameter("include_planned", includePlanned.toString())
            currency?.let { parameter("currency", it) }
        }
    }

    suspend fun byCategory(
        from: LocalDate,
        to: LocalDate,
        walletId: String? = null,
        includePlanned: Boolean = false,
        currency: String? = null,
    ): ApiResult<ReportAnswer<ReportByCategoryDto>> {
        requireRange(from, to)
        currency?.let { requireCurrency(it) }
        return answer(wallet = walletId, path = "by-category", serializer = ReportByCategoryDto.serializer()) {
            parameter("from", from.toString())
            parameter("to", to.toString())
            parameter("include_planned", includePlanned.toString())
            currency?.let { parameter("currency", it) }
        }
    }

    private suspend fun <T> answer(
        wallet: String?,
        path: String,
        serializer: kotlinx.serialization.KSerializer<T>,
        query: io.ktor.client.request.HttpRequestBuilder.() -> Unit,
    ): ApiResult<ReportAnswer<T>> {
        val raw = client.callWithMeta<kotlinx.serialization.json.JsonElement> {
            method = HttpMethod.Get
            url(if (wallet == null) "reports/$path" else "wallets/$wallet/reports/$path")
            query()
        }
        return when (raw) {
            is ApiResult.Success -> {
                val body = raw.value
                val data = AppJson.decodeFromJsonElement(serializer, body.data)
                val meta = if (wallet == null) {
                    AppJson.decodeFromJsonElement(CrossWalletReportMetaDto.serializer(), body.meta)
                } else {
                    CrossWalletReportMetaDto()
                }
                ApiResult.Success(ReportAnswer(data, meta.excludedWallets))
            }
            is ApiResult.Failure -> raw
            ApiResult.Offline -> ApiResult.Offline
            ApiResult.Timeout -> ApiResult.Timeout
        }
    }

    private fun requireDate(date: LocalDate) =
        require(date >= MIN_DATE && date <= MAX_DATE) { "date outside 1900-01-01..2999-12-31" }

    private fun requireRange(from: LocalDate, to: LocalDate) {
        requireDate(from)
        requireDate(to)
        require(from <= to) { "from must not be after to" }
        require(to.toEpochDays() - from.toEpochDays() + 1 <= MAX_DAYS) { "a range spans at most 366 days" }
    }

    private fun requireCurrency(currency: String) = require(CURRENCY.matches(currency)) { "currency must be three capital letters" }

    companion object {
        const val MIN_MONTHS = 1
        const val MAX_MONTHS = 12
        const val MAX_DAYS = 366
        private val MIN_DATE = LocalDate(1900, 1, 1)
        private val MAX_DATE = LocalDate(2999, 12, 31)
        private val CURRENCY = Regex("^[A-Z]{3}$")
    }
}
