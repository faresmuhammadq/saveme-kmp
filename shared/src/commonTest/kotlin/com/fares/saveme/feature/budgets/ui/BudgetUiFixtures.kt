package com.fares.saveme.feature.budgets.ui

import com.fares.saveme.feature.budgets.domain.model.Budget
import com.fares.saveme.feature.budgets.domain.model.BudgetChoices
import com.fares.saveme.feature.budgets.domain.model.BudgetDraft
import com.fares.saveme.feature.budgets.domain.model.BudgetFigures
import com.fares.saveme.feature.budgets.domain.model.BudgetPeriod
import com.fares.saveme.feature.budgets.domain.model.BudgetView
import com.fares.saveme.feature.budgets.domain.model.BudgetsOverview
import com.fares.saveme.feature.budgets.domain.model.FigureSource
import com.fares.saveme.feature.budgets.domain.model.PeriodState
import com.fares.saveme.feature.budgets.domain.model.SyncState
import com.fares.saveme.feature.categories.domain.model.CategoryDisplay
import com.fares.saveme.feature.categories.domain.model.CategorySource
import kotlinx.datetime.LocalDate
import kotlin.time.Instant

val uiPeriod = BudgetPeriod(LocalDate(2026, 9, 25), LocalDate(2026, 10, 24), 30, 8)

fun uiFigures(
    spent: Long = 350_000,
    limit: Long = 600_000,
    source: FigureSource = FigureSource.Phone,
    waiting: Boolean = false,
    others: List<String> = emptyList(),
    projected: Long? = 1_312_500,
) = BudgetFigures(
    spentMinor = spent, limitMinor = limit, remainingMinor = limit - spent, overLimit = spent > limit,
    percent = ((spent * 100 + limit / 2) / limit).toInt(), projectedMinor = projected, otherCurrencies = others, source = source,
    includesWaiting = waiting,
)

fun uiBudget(
    id: String = "b1",
    category: String? = "c1",
    limit: Long = 600_000,
    currency: String = "EGP",
    archived: Boolean = false,
    version: Long = 3,
    periodType: String = "monthly",
    created: String = "2026-09-01T00:00:00Z",
) = Budget(id, category, periodType, limit, currency, if (archived) Instant.parse("2026-09-20T00:00:00Z") else null, version, Instant.parse(created))

fun uiView(
    budget: Budget = uiBudget(),
    name: String? = "Groceries",
    figures: BudgetFigures? = uiFigures(limit = budget.limitMinor),
    state: SyncState = SyncState.None,
    pending: BudgetDraft? = null,
    pendingDelete: Boolean = false,
) = BudgetView(
    server = budget, pending = pending, pendingDelete = pendingDelete, syncState = state,
    category = if (budget.categoryId == null) null else CategoryDisplay(budget.categoryId, name.orEmpty(), "cart", "#2E7D32", "expense", CategorySource.Own, false),
    figures = figures,
)

fun uiOverview(
    active: List<BudgetView> = emptyList(),
    archived: List<BudgetView> = emptyList(),
    period: PeriodState = PeriodState.Known(uiPeriod),
    today: LocalDate? = LocalDate(2026, 10, 2),
    pendingStartDay: com.fares.saveme.feature.budgets.domain.model.PendingStartDay? = null,
    updatedAt: Instant? = null,
    loaded: Boolean = true,
) = BudgetsOverview(period, today, pendingStartDay, 25, active, archived, updatedAt, loaded)

fun uiChoices(
    forCurrency: String = "EGP",
    already: Set<String> = emptySet(),
    currencies: List<String> = listOf("EGP", "USD"),
    default: String = "EGP",
    own: List<CategoryDisplay> = listOf(CategoryDisplay("c1", "Groceries", "cart", "#2E7D32", "expense", CategorySource.Own, false), CategoryDisplay("c2", "Transport", "bus", "#1F7F8C", "expense", CategorySource.Own, false)),
) = BudgetChoices(null, own, emptyList(), forCurrency, currencies, default, already, 25)
