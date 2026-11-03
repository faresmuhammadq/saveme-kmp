package com.fares.saveme.feature.reports.data.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * reports.yaml `Money`. Reports keep their own copy: the budgets one lives in budgets' `data`, which reports may not
 * import (P-10). Amounts are the person's own and never printed (P-09.2).
 */
@Serializable
data class ReportMoneyDto(
    @SerialName("amount_minor") val amountMinor: Long,
    val currency: String,
) {
    override fun toString() = "ReportMoneyDto($currency)"
}

@Serializable
data class ReportRangeDto(val from: String, val to: String)

/** `PlannedSummary`: null on the wire unless `include_planned=true`. */
@Serializable
data class PlannedSummaryDto(
    val income: ReportMoneyDto,
    val expenses: ReportMoneyDto,
    @SerialName("transfers_in") val transfersIn: ReportMoneyDto,
    @SerialName("transfers_out") val transfersOut: ReportMoneyDto,
) {
    override fun toString() = "PlannedSummaryDto"
}

/**
 * `ReportSummary`. There is no share member and the client never reads one (FR-007); members a later server adds are
 * ignored (P-02.3).
 */
@Serializable
data class ReportSummaryDto(
    @SerialName("wallet_id") val walletId: String? = null,
    val range: ReportRangeDto,
    val currency: String,
    val income: ReportMoneyDto,
    val expenses: ReportMoneyDto,
    val net: ReportMoneyDto,
    @SerialName("transfers_in") val transfersIn: ReportMoneyDto,
    @SerialName("transfers_out") val transfersOut: ReportMoneyDto,
    @SerialName("adjustments_in") val adjustmentsIn: ReportMoneyDto? = null,
    @SerialName("adjustments_out") val adjustmentsOut: ReportMoneyDto? = null,
    @SerialName("balance_start") val balanceStart: ReportMoneyDto,
    @SerialName("balance_end") val balanceEnd: ReportMoneyDto,
    val planned: PlannedSummaryDto? = null,
) {
    override fun toString() = "ReportSummaryDto(${walletId ?: "all"})"
}

@Serializable
data class CategoryItemDto(
    val label: String,
    @SerialName("category_id") val categoryId: String,
    @SerialName("system_key") val systemKey: String? = null,
    val value: ReportMoneyDto,
    val planned: ReportMoneyDto? = null,
) {
    override fun toString() = "CategoryItemDto"
}

@Serializable
data class CategoryGroupDto(
    val total: ReportMoneyDto,
    val planned: ReportMoneyDto? = null,
    val items: List<CategoryItemDto> = emptyList(),
) {
    override fun toString() = "CategoryGroupDto(${items.size} items)"
}

@Serializable
data class ReportByCategoryDto(
    @SerialName("wallet_id") val walletId: String? = null,
    val range: ReportRangeDto,
    val currency: String,
    val expenses: CategoryGroupDto,
    val income: CategoryGroupDto,
) {
    override fun toString() = "ReportByCategoryDto(${walletId ?: "all"})"
}

@Serializable
data class TrendBudgetPeriodDto(val label: String, val from: String, val to: String)

@Serializable
data class ChartPointDto(val label: String, val value: ReportMoneyDto) {
    override fun toString() = "ChartPointDto"
}

@Serializable
data class ReportTrendDto(
    @SerialName("wallet_id") val walletId: String? = null,
    val currency: String,
    val periods: List<TrendBudgetPeriodDto>,
    val income: List<ChartPointDto> = emptyList(),
    val expenses: List<ChartPointDto> = emptyList(),
    val net: List<ChartPointDto> = emptyList(),
    @SerialName("planned_income") val plannedIncome: List<ChartPointDto>? = null,
    @SerialName("planned_expenses") val plannedExpenses: List<ChartPointDto>? = null,
) {
    override fun toString() = "ReportTrendDto(${periods.size} periods)"
}

/** `WalletReportMeta`: always `{}`. */
@Serializable
class WalletReportMetaDto

@Serializable
data class ExcludedWalletDto(
    @SerialName("wallet_id") val walletId: String,
    val currency: String,
)

/** `CrossWalletReportMeta`. */
@Serializable
data class CrossWalletReportMetaDto(
    @SerialName("excluded_wallets") val excludedWallets: List<ExcludedWalletDto> = emptyList(),
)
