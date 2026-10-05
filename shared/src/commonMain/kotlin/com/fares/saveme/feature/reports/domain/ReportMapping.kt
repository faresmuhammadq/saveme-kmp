package com.fares.saveme.feature.reports.domain

import com.fares.saveme.feature.reports.data.dto.CategoryGroupDto
import com.fares.saveme.feature.reports.data.dto.ChartPointDto
import com.fares.saveme.feature.reports.data.dto.ExcludedWalletDto
import com.fares.saveme.feature.reports.data.dto.ReportByCategoryDto
import com.fares.saveme.feature.reports.data.dto.ReportSummaryDto
import com.fares.saveme.feature.reports.data.dto.ReportTrendDto
import com.fares.saveme.feature.reports.domain.model.CategoryBreakdown
import com.fares.saveme.feature.reports.domain.model.CategoryGroup
import com.fares.saveme.feature.reports.domain.model.PlannedSummary
import com.fares.saveme.feature.reports.domain.model.SummaryFigures
import com.fares.saveme.feature.reports.domain.model.TrendPoint

/**
 * Maps the server's answers to the domain figures and lists the members that differ, by name only (FR-028, P-09.2): used
 * by the development build's parity probe, never by anything the person sees. A member a later server adds is ignored
 * (R-16): only known members are compared.
 */
internal object ReportMapping {

    fun summaryFigures(dto: ReportSummaryDto) = SummaryFigures(
        income = dto.income.amountMinor, expenses = dto.expenses.amountMinor, net = dto.net.amountMinor,
        transfersIn = dto.transfersIn.amountMinor, transfersOut = dto.transfersOut.amountMinor,
        balanceStart = dto.balanceStart.amountMinor, balanceEnd = dto.balanceEnd.amountMinor, currency = dto.currency,
    )

    fun plannedSummary(dto: ReportSummaryDto): PlannedSummary? = dto.planned?.let {
        PlannedSummary(it.income.amountMinor, it.expenses.amountMinor, it.transfersIn.amountMinor, it.transfersOut.amountMinor)
    }

    fun differingSummaryMembers(phone: SummaryFigures, phonePlanned: PlannedSummary?, server: ReportSummaryDto): List<String> {
        val members = mutableListOf<String>()
        fun check(name: String, phoneValue: Long, serverValue: Long) {
            if (phoneValue != serverValue) members += name
        }
        check("income", phone.income, server.income.amountMinor)
        check("expenses", phone.expenses, server.expenses.amountMinor)
        check("net", phone.net, server.net.amountMinor)
        check("transfers_in", phone.transfersIn, server.transfersIn.amountMinor)
        check("transfers_out", phone.transfersOut, server.transfersOut.amountMinor)
        check("balance_start", phone.balanceStart, server.balanceStart.amountMinor)
        check("balance_end", phone.balanceEnd, server.balanceEnd.amountMinor)
        if (phone.currency != server.currency) members += "currency"
        val serverPlanned = server.planned
        when {
            phonePlanned == null && serverPlanned == null -> Unit
            phonePlanned == null || serverPlanned == null -> members += "planned"
            else -> {
                check("planned.income", phonePlanned.income, serverPlanned.income.amountMinor)
                check("planned.expenses", phonePlanned.expenses, serverPlanned.expenses.amountMinor)
                check("planned.transfers_in", phonePlanned.transfersIn, serverPlanned.transfersIn.amountMinor)
                check("planned.transfers_out", phonePlanned.transfersOut, serverPlanned.transfersOut.amountMinor)
            }
        }
        return members
    }

    fun differingCategoryMembers(phone: CategoryBreakdown, server: ReportByCategoryDto): List<String> =
        differingGroup("expenses", phone.expenses, server.expenses) + differingGroup("income", phone.income, server.income)

    private fun differingGroup(name: String, phone: CategoryGroup, server: CategoryGroupDto): List<String> {
        val members = mutableListOf<String>()
        if (phone.totalMinor != server.total.amountMinor) members += "$name.total"
        if ((phone.plannedMinor == null) != (server.planned == null) || phone.plannedMinor != server.planned?.amountMinor && phone.plannedMinor != null) {
            members += "$name.planned"
        }
        if (phone.items.size != server.items.size) {
            members += "$name.items.count"
            return members
        }
        phone.items.zip(server.items).forEachIndexed { index, (mine, theirs) ->
            if (mine.label != theirs.label) members += "$name.items.$index.label"
            if (mine.categoryId != theirs.categoryId) members += "$name.items.$index.category_id"
            if (mine.systemKey != theirs.systemKey) members += "$name.items.$index.system_key"
            if (mine.valueMinor != theirs.value.amountMinor) members += "$name.items.$index.value"
            if (mine.plannedMinor != theirs.planned?.amountMinor) members += "$name.items.$index.planned"
        }
        return members
    }

    fun differingTrendMembers(phone: List<TrendPoint>, server: ReportTrendDto, includePlanned: Boolean): List<String> {
        val members = mutableListOf<String>()
        if (phone.size != server.periods.size) return listOf("periods.count")
        phone.forEachIndexed { index, point ->
            val period = server.periods[index]
            if (point.period.start.toString() != period.from || point.period.end.toString() != period.to) members += "periods.$index"
            fun check(name: String, mine: Long?, series: List<ChartPointDto>?) {
                if (mine != series?.getOrNull(index)?.value?.amountMinor) members += "$name.$index"
            }
            check("income", point.incomeMinor, server.income)
            check("expenses", point.expensesMinor, server.expenses)
            check("net", point.netMinor, server.net)
            if (includePlanned) {
                check("planned_income", point.plannedIncomeMinor, server.plannedIncome)
                check("planned_expenses", point.plannedExpensesMinor, server.plannedExpenses)
            }
        }
        return members
    }

    fun differingExcluded(phone: Set<Pair<String, String>>, server: List<ExcludedWalletDto>): Boolean =
        phone != server.map { it.walletId to it.currency }.toSet()
}
