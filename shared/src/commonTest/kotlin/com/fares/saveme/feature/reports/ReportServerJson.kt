package com.fares.saveme.feature.reports

import com.fares.saveme.feature.reports.domain.model.CategoryGroup
import com.fares.saveme.feature.reports.domain.model.PlannedSummary
import com.fares.saveme.feature.reports.domain.model.SummaryFigures
import com.fares.saveme.feature.reports.domain.model.TrendPoint

/** The server's answers for given phone figures, shaped like reports.yaml (for the parity tests). */
object ReportServerJson {
    private fun money(minor: Long, currency: String = "EGP") = """{"amount_minor":$minor,"currency":"$currency"}"""

    private fun excluded(wallets: List<Pair<String, String>>) =
        wallets.joinToString(",", "[", "]") { (id, currency) -> """{"wallet_id":"$id","currency":"$currency"}""" }

    fun summary(
        figures: SummaryFigures,
        planned: PlannedSummary? = null,
        walletId: String? = null,
        from: String = "2026-10-01",
        to: String = "2026-10-31",
        excluded: List<Pair<String, String>> = emptyList(),
    ): String {
        val c = figures.currency
        val plannedJson = planned?.let {
            """{"income":${money(it.income, c)},"expenses":${money(it.expenses, c)},"transfers_in":${money(it.transfersIn, c)},"transfers_out":${money(it.transfersOut, c)}}"""
        } ?: "null"
        val wallet = walletId?.let { "\"$it\"" } ?: "null"
        return """{"data":{"wallet_id":$wallet,"range":{"from":"$from","to":"$to"},"currency":"$c","income":${money(figures.income, c)},
            "expenses":${money(figures.expenses, c)},"net":${money(figures.net, c)},"transfers_in":${money(figures.transfersIn, c)},
            "transfers_out":${money(figures.transfersOut, c)},"balance_start":${money(figures.balanceStart, c)},"balance_end":${money(figures.balanceEnd, c)},
            "planned":$plannedJson},"meta":${meta(walletId, excluded)}}"""
    }

    private fun meta(walletId: String?, excluded: List<Pair<String, String>>) =
        if (walletId != null) "{}" else """{"excluded_wallets":${excluded(excluded)}}"""

    private fun group(group: CategoryGroup, currency: String): String {
        val items = group.items.joinToString(",", "[", "]") { item ->
            val system = item.systemKey?.let { "\"$it\"" } ?: "null"
            val planned = item.plannedMinor?.let { money(it, currency) } ?: "null"
            """{"label":"${item.label}","category_id":"${item.categoryId}","system_key":$system,"value":${money(item.valueMinor, currency)},"planned":$planned}"""
        }
        return """{"total":${money(group.totalMinor, currency)},"planned":${group.plannedMinor?.let { money(it, currency) } ?: "null"},"items":$items}"""
    }

    fun byCategory(expenses: CategoryGroup, income: CategoryGroup, currency: String = "EGP", walletId: String? = null, excluded: List<Pair<String, String>> = emptyList()): String {
        val wallet = walletId?.let { "\"$it\"" } ?: "null"
        return """{"data":{"wallet_id":$wallet,"range":{"from":"2026-10-01","to":"2026-10-31"},"currency":"$currency",
            "expenses":${group(expenses, currency)},"income":${group(income, currency)}},"meta":${meta(walletId, excluded)}}"""
    }

    private fun series(points: List<TrendPoint>, currency: String, value: (TrendPoint) -> Long?): String =
        if (points.any { value(it) == null }) {
            "null"
        } else {
            points.joinToString(",", "[", "]") { """{"label":"${it.period.start}","value":${money(value(it)!!, currency)}}""" }
        }

    fun trend(points: List<TrendPoint>, currency: String = "EGP", walletId: String? = null, excluded: List<Pair<String, String>> = emptyList()): String {
        val wallet = walletId?.let { "\"$it\"" } ?: "null"
        val periods = points.joinToString(",", "[", "]") { """{"label":"${it.period.start}","from":"${it.period.start}","to":"${it.period.end}"}""" }
        return """{"data":{"wallet_id":$wallet,"currency":"$currency","periods":$periods,"income":${series(points, currency) { it.incomeMinor }},
            "expenses":${series(points, currency) { it.expensesMinor }},"net":${series(points, currency) { it.netMinor }},
            "planned_income":${series(points, currency) { it.plannedIncomeMinor }},"planned_expenses":${series(points, currency) { it.plannedExpensesMinor }}},
            "meta":${meta(walletId, excluded)}}"""
    }
}
