package com.fares.saveme.feature.sharedbudgets.domain

import com.fares.saveme.core.network.AppJson
import com.fares.saveme.feature.budgets.domain.BudgetMath
import com.fares.saveme.feature.budgets.domain.BudgetPeriods
import com.fares.saveme.feature.budgets.domain.model.BudgetPeriod
import com.fares.saveme.feature.sharedbudgets.data.dto.LimitStepDto
import com.fares.saveme.feature.sharedbudgets.data.dto.StartDayStepDto
import com.fares.saveme.feature.sharedbudgets.data.limitSteps
import com.fares.saveme.feature.sharedbudgets.data.local.SharedBudgetEntity
import com.fares.saveme.feature.sharedbudgets.data.local.SharedBudgetEntryEntity
import com.fares.saveme.feature.sharedbudgets.data.local.SharedBudgetLineEntity
import com.fares.saveme.feature.sharedbudgets.data.local.SharedBudgetMemberEntity
import com.fares.saveme.feature.sharedbudgets.data.startDaySteps
import com.fares.saveme.feature.sharedbudgets.domain.model.LineProgress
import com.fares.saveme.feature.sharedbudgets.domain.model.MemberTotal
import com.fares.saveme.feature.sharedbudgets.domain.model.SharedBudgetProgress
import kotlinx.datetime.LocalDate

/**
 * The arithmetic of a Shared Budget on the phone: integer minor units only, the period from the start day that applies today,
 * and the limit that applies today. Entries in the period count whether or not their author is still a member; a detached
 * entry keeps its amount, date and category (backend ADR-0027 rule 9).
 */
object SharedBudgetMath {
    /** The limit in force on [day]: the latest step that applies on or before it, else [fallback]. */
    fun limitOn(day: LocalDate, history: List<LimitStepDto>, fallback: Long?): Long? {
        val step = history.filter { LocalDate.parse(it.appliesFrom) <= day }.maxByOrNull { LocalDate.parse(it.appliesFrom) }
        return if (step != null) step.limitMinor else fallback
    }

    /** The start day in force on [day]; the first step applies from the beginning. */
    fun startDayOn(day: LocalDate, history: List<StartDayStepDto>, fallback: Int): Int {
        val applying = history.filter { it.appliesFrom == null || LocalDate.parse(it.appliesFrom) <= day }
        return applying.maxByOrNull { step -> step.appliesFrom?.let { LocalDate.parse(it).toEpochDays().toLong() } ?: Long.MIN_VALUE }?.startDay ?: fallback
    }

    fun periodOn(today: LocalDate, budget: SharedBudgetEntity): BudgetPeriod {
        val history = decodeStartDays(budget.startDayHistory)
        return BudgetPeriods.calendar(today, startDayOn(today, history, budget.startDay))
    }

    fun progress(
        budget: SharedBudgetEntity,
        lines: List<SharedBudgetLineEntity>,
        entries: List<SharedBudgetEntryEntity>,
        members: List<SharedBudgetMemberEntity>,
        me: String,
        today: LocalDate,
    ): SharedBudgetProgress {
        val period = periodOn(today, budget)
        val from = period.start.toString()
        val to = period.end.toString()
        val inPeriod = entries.filter { it.date in from..to }
        val spent = inPeriod.sumOf { it.amountMinor }
        val byCategory = inPeriod.groupBy { it.categoryId }.mapValues { (_, list) -> list.sumOf { it.amountMinor } }
        val lineRows = lines.map { line ->
            LineProgress(
                categoryId = line.categoryId, name = line.name.orEmpty(), iconKey = line.icon, colorHex = line.color,
                limitMinor = limitOn(today, decodeLimits(line.limitHistory), line.limitMinor),
                spentMinor = byCategory[line.categoryId] ?: 0L,
            )
        }.sortedWith(compareByDescending<LineProgress> { it.spentMinor }.thenBy { it.name.lowercase() })
        val listed = lines.map { it.categoryId }.toSet()
        val byAuthor = inPeriod.groupBy { it.createdBy }.mapValues { (_, list) -> list.sumOf { it.amountMinor } }
        val memberRows = (members.map { it.userId } + byAuthor.keys).distinct().map { id ->
            val member = members.firstOrNull { it.userId == id }
            MemberTotal(id, member?.displayName, isMe = id == me, spentMinor = byAuthor[id] ?: 0L)
        }.sortedWith(compareByDescending<MemberTotal> { it.isMe }.thenByDescending { it.spentMinor })
        return SharedBudgetProgress(
            id = budget.id, name = budget.name, currency = budget.currency, period = period,
            limitMinor = limitOn(today, decodeLimits(budget.limitHistory), budget.limitMinor),
            spentMinor = spent,
            projectedMinor = BudgetMath.projectedEnd(spent, period.daysInPeriod, period.daysElapsed).takeIf { spent > 0 },
            lines = lineRows,
            unlistedMinor = inPeriod.filter { it.categoryId !in listed }.sumOf { it.amountMinor },
            members = memberRows,
            isOwner = budget.ownerId == me,
            archived = budget.archivedAt != null,
            refreshedAt = budget.refreshedAt.takeIf { budget.version > 0 },
        )
    }

    private fun decodeLimits(json: String): List<LimitStepDto> = runCatching { AppJson.decodeFromString(limitSteps, json) }.getOrDefault(emptyList())

    private fun decodeStartDays(json: String): List<StartDayStepDto> = runCatching { AppJson.decodeFromString(startDaySteps, json) }.getOrDefault(emptyList())
}
