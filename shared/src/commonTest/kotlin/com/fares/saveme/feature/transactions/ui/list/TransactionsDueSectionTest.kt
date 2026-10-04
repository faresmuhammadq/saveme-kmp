package com.fares.saveme.feature.transactions.ui.list

import app.cash.turbine.test
import com.fares.saveme.core.presentation.runMviTest
import com.fares.saveme.feature.categories.domain.model.CategoryDisplays
import com.fares.saveme.feature.transactions.domain.ConfirmationStatus
import com.fares.saveme.feature.transactions.domain.DayGrouping
import com.fares.saveme.feature.transactions.domain.DueItem
import com.fares.saveme.feature.transactions.domain.DueState
import com.fares.saveme.feature.transactions.domain.model.TransactionList
import com.fares.saveme.feature.transactions.display
import com.fares.saveme.feature.transactions.viewOf
import com.fares.saveme.feature.auth.domain.model.RefreshOutcome
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Instant

private fun due(id: String, state: DueState = DueState.Due, date: String = "2026-10-01") = DueItem(
    id = id, walletId = "w1", ruleId = "r1", description = "Rent", date = LocalDate.parse(date), amountMinor = 450_000,
    currency = "EGP", kindWire = "expense", state = state,
)

class TransactionsDueSectionTest {
    private val rows = MutableStateFlow(listOf(viewOf("plain", "2026-10-02", 1_000, description = "Lunch")))
    private val statuses = mutableMapOf<String, MutableStateFlow<ConfirmationStatus?>>()

    private fun vm(dueItems: Flow<List<DueItem>> = flowOf(emptyList())) = TransactionsViewModel(
        walletId = "w1",
        wallet = flowOf(null),
        list = { _ ->
            rows.map { views ->
                TransactionList("w1", "EGP", DayGrouping.group(views, "w1"), Instant.parse("2026-10-02T10:00:00Z"), true, canAdd = true, totalCount = views.size)
            }
        },
        displays = flowOf(CategoryDisplays({ display(it, "Name $it") })),
        filterChoices = flowOf(emptyList()),
        refresh = { RefreshOutcome.Done },
        isEmailVerified = { true },
        today = { LocalDate(2026, 10, 3) },
        due = dueItems,
        confirmationStatus = { id -> statuses.getOrPut(id) { MutableStateFlow(null) } },
    )

    @Test
    fun theDueSectionShowsAtMostThreeAndCountsTheRest() = runMviTest {
        val items = listOf(due("o1", DueState.Missed), due("o2"), due("o3"), due("o4"), due("o5"))
        val state = vm(flowOf(items)).state.value
        assertEquals(listOf("o1", "o2", "o3"), state.due.map { it.id })
        assertEquals(5, state.dueTotal)
    }

    @Test
    fun dueItemsAreNeverInTheTotalsTheSearchOrTheDays() = runMviTest {
        val state = vm(flowOf(listOf(due("o1")))).state.value
        assertEquals(1, state.totalCount)
        assertEquals(1, state.days.sumOf { it.rows.size })
        assertFalse(state.isEmpty)
    }

    @Test
    fun tappingADueItemOpensItsOccurrenceAndTheLinkOpensTheWalletsRecurringScreen() = runMviTest {
        val store = vm(flowOf(listOf(due("o1"))))
        store.effects.test {
            store.onIntent(TransactionsIntent.DueClicked("o1"))
            assertEquals(TransactionsEffect.OpenOccurrence("o1"), awaitItem())
            store.onIntent(TransactionsIntent.RecurringClicked)
            assertEquals(TransactionsEffect.OpenRecurring("w1"), awaitItem())
        }
    }

    @Test
    fun aPendingConfirmationRowShowsItsOccurrenceStatusAndOpensTheOccurrence() = runMviTest {
        val pending = viewOf("t9", "2026-10-03", 450_000, description = "Rent").copy(occurrenceId = "o9", pendingConfirmation = true)
        rows.value = listOf(pending)
        val store = vm()
        val row = store.state.value.days.single().rows.single()
        assertEquals(RowStatus.Waiting, row.status)
        assertEquals("o9", row.occurrenceId)
        statuses.getValue("t9").value = ConfirmationStatus.Clash
        assertEquals(RowStatus.Changed, store.state.value.days.single().rows.single().status)
        statuses.getValue("t9").value = ConfirmationStatus.Refused
        assertEquals(RowStatus.NotSaved, store.state.value.days.single().rows.single().status)
        store.effects.test {
            store.onIntent(TransactionsIntent.RowClicked("t9"))
            assertEquals(TransactionsEffect.OpenOccurrence("o9"), awaitItem())
        }
    }

    @Test
    fun anOrdinaryRowStillOpensTheEditor() = runMviTest {
        val store = vm()
        store.effects.test {
            store.onIntent(TransactionsIntent.RowClicked("plain"))
            assertEquals(TransactionsEffect.OpenEditor("plain", "w1"), awaitItem())
        }
        assertTrue(store.state.value.due.isEmpty())
    }
}
