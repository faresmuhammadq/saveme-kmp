package com.fares.saveme.audit

import androidx.compose.ui.test.junit4.createComposeRule
import com.fares.saveme.designsystem.component.MainTab
import com.fares.saveme.feature.auth.domain.BudgetGlimpse
import com.fares.saveme.feature.auth.domain.DueGlimpse
import com.fares.saveme.feature.auth.ui.home.HomeScreen
import com.fares.saveme.feature.auth.ui.home.HomeState
import com.fares.saveme.feature.categories.domain.model.CategoryChoices
import com.fares.saveme.feature.categories.domain.model.CategoryDisplay
import com.fares.saveme.feature.categories.domain.model.CategorySource
import com.fares.saveme.feature.transactions.domain.DueItem
import com.fares.saveme.feature.transactions.domain.DueState
import com.fares.saveme.feature.transactions.domain.model.Direction
import com.fares.saveme.feature.transactions.domain.model.TransactionKind
import com.fares.saveme.feature.transactions.domain.model.WalletChoice
import com.fares.saveme.feature.transactions.ui.editor.TransactionEditorScreen
import com.fares.saveme.feature.transactions.ui.editor.TransactionEditorState
import com.fares.saveme.feature.transactions.ui.list.DayUi
import com.fares.saveme.feature.transactions.ui.list.RowStatus
import com.fares.saveme.feature.transactions.ui.list.RowUi
import com.fares.saveme.feature.transactions.ui.list.TransactionsScreen
import com.fares.saveme.feature.transactions.ui.list.TransactionsState
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.time.Instant

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w390dp-h844dp-xhdpi")
class CoreAuditTest {
    @get:Rule val compose = createComposeRule()

    private fun cat(id: String, name: String, icon: String, hex: String? = null) = CategoryDisplay(id, name, icon, hex, "expense", CategorySource.Default, false)

    @Test
    fun home() = compose.auditCapture("Home-B") {
        WithTabBar(MainTab.Home) {
            HomeScreen(
                HomeState(
                    email = "layla@example.com",
                    displayName = "Layla",
                    now = Instant.parse("2026-10-18T07:00:00Z"),
                    dueCount = 2,
                    firstDue = DueGlimpse("Internet bill", LocalDate(2026, 10, 18)),
                    budgets = listOf(
                        BudgetGlimpse(
                            "all", null, 1_230_000, 2_200_000, "EGP", false,
                            periodStart = LocalDate(2026, 9, 25), periodEnd = LocalDate(2026, 10, 24), daysLeft = 7, projectedMinor = 1_790_000,
                        ),
                        BudgetGlimpse("b1", "Groceries", 324_000, 350_000, "EGP", false, iconKey = "cart", colorHex = "#4C6FD6"),
                        BudgetGlimpse("b2", "Dining out", 218_000, 200_000, "EGP", false, iconKey = "restaurant", colorHex = "#B5573C"),
                        BudgetGlimpse("b3", "Transport", 146_000, 180_000, "EGP", false, iconKey = "bus", colorHex = "#4C6FD6"),
                    ),
                ),
                {},
            )
        }
    }

    @Test
    fun transactions() = compose.auditCapture("Tx-D") {
        fun row(id: String, cat: String, icon: String, note: String?, amount: Long, status: RowStatus = RowStatus.None) =
            RowUi(id, TransactionKind.Expense, Direction.Out, null, cat(id, cat, icon, if (cat == "Dining out") "#B5573C" else "#4C6FD6"), note, amount, status)
        val today = LocalDate(2026, 10, 22)
        val state = TransactionsState(
            walletId = "w", walletName = "CIB Checking", currency = "EGP", balanceMinor = 4_825_000, loading = false, canAdd = true,
            today = today, totalCount = 9,
            days = listOf(
                DayUi(today, -75_500, listOf(row("a", "Groceries", "cart", "Weekly shop", 64_000), row("b", "Transport", "bus", null, 11_500, RowStatus.Waiting))),
                DayUi(LocalDate(2026, 10, 21), -52_000, listOf(row("c", "Dining out", "restaurant", "Talabat", 28_500), row("d", "Groceries", "cart", null, 23_500))),
                DayUi(LocalDate(2026, 10, 20), -36_000, listOf(row("e", "Groceries", "cart", null, 36_000))),
                DayUi(LocalDate(2026, 10, 19), -98_000, listOf(row("f", "Groceries", "cart", null, 98_000))),
                DayUi(LocalDate(2026, 10, 18), -61_000, listOf(row("g", "Groceries", "cart", null, 61_000))),
                DayUi(LocalDate(2026, 10, 17), -23_000, listOf(row("h", "Groceries", "cart", null, 23_000))),
                DayUi(LocalDate(2026, 10, 16), -42_000, listOf(row("i", "Groceries", "cart", null, 42_000))),
            ),
            due = listOf(
                DueItem("d1", "w", "r1", "Internet bill", LocalDate(2026, 10, 18), 40_000, "EGP", "expense", DueState.Due),
                DueItem("d2", "w", "r2", "Gym membership", LocalDate(2026, 10, 20), 60_000, "EGP", "expense", DueState.Due),
            ),
            dueTotal = 2,
        )
        WithTabBar(MainTab.Wallets) { TransactionsScreen(state, {}) }
    }

    @Test
    fun editor() = compose.auditCapture("TxEdit-B") {
        val groceries = cat("g", "Groceries", "cart")
        val choices = CategoryChoices(
            null, emptyList(),
            listOf(groceries, cat("d", "Dining", "restaurant"), cat("t", "Transport", "bus"), cat("b", "Bills", "bolt"), cat("c", "Clothes", "shirt"), cat("h", "Health", "health"), cat("gf", "Gifts", "gift")),
        )
        TransactionEditorScreen(
            TransactionEditorState(
                loaded = true, walletId = "w", walletChoices = listOf(WalletChoice("w", "CIB Checking", "EGP"), WalletChoice("w2", "Cash", "EGP")),
                amountText = "640", currency = "EGP", date = kotlin.time.Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date, categoryId = "g", category = groceries,
                categoryChoices = choices, note = "Weekly shop",
            ),
            {},
        )
    }
}
