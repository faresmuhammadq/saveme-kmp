package com.fares.saveme.audit

import androidx.compose.ui.test.junit4.createComposeRule
import com.fares.saveme.designsystem.component.MainTab
import com.fares.saveme.feature.budgets.domain.BudgetValidation
import com.fares.saveme.feature.budgets.domain.model.BudgetChoices
import com.fares.saveme.feature.budgets.domain.model.SpendHistory
import com.fares.saveme.feature.budgets.ui.BudgetRowUi
import com.fares.saveme.feature.budgets.ui.RowFigures
import com.fares.saveme.feature.budgets.ui.editor.BudgetEditorScreen
import com.fares.saveme.feature.budgets.ui.editor.BudgetEditorState
import com.fares.saveme.feature.budgets.ui.list.BudgetsScreen
import com.fares.saveme.feature.budgets.ui.list.BudgetsState
import com.fares.saveme.feature.budgets.ui.list.PeriodUi
import com.fares.saveme.feature.categories.domain.model.CategoryDisplay
import com.fares.saveme.feature.categories.domain.model.CategorySource
import com.fares.saveme.feature.categories.ui.editor.CategoryEditorScreen
import com.fares.saveme.feature.categories.ui.editor.CategoryEditorState
import com.fares.saveme.feature.categories.ui.list.CategoriesScreen
import com.fares.saveme.feature.categories.ui.list.CategoriesState
import com.fares.saveme.feature.categories.ui.list.CategoryCaptionUi
import com.fares.saveme.feature.categories.ui.list.CategoryRowUi
import com.fares.saveme.feature.categories.ui.list.CategoryStatusUi
import kotlinx.datetime.LocalDate
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w390dp-h844dp-xhdpi")
class BudgetsAuditTest {
    @get:Rule val compose = createComposeRule()

    private fun row(id: String, name: String?, icon: String?, limit: Long, spent: Long, projected: Long? = null) =
        BudgetRowUi(id, name, "EGP", limit * 100, RowFigures(spent * 100, projected?.times(100), emptyList(), false), false, null, icon)

    @Test fun budgets() = compose.auditCapture("Budgets-B") {
        WithTabBar(MainTab.Budgets) {
            BudgetsScreen(
                BudgetsState(
                    period = PeriodUi(LocalDate(2026, 9, 25), LocalDate(2026, 10, 24), 7),
                    active = listOf(
                        row("a", null, null, 22000, 12300, 17900),
                        row("g", "Groceries", "cart", 3700, 3440),
                        row("d", "Dining out", "restaurant", 2000, 2180),
                        row("t", "Transport", "bus", 1800, 1460),
                        row("b", "Bills & utilities", "bolt", 5000, 4900),
                    ),
                    archived = listOf(row("x", "Old", "home", 1000, 400)),
                    loaded = true,
                ),
                {},
            )
        }
    }

    @Test fun budgetEdit() = compose.auditCapture("BudgetEdit-B") {
        fun d(id: String, name: String, icon: String) = CategoryDisplay(id, name, icon, null, "expense", CategorySource.Default, false)
        BudgetEditorScreen(
            BudgetEditorState(
                loaded = true, categoryChosen = true, categoryId = "clothes", currency = "EGP", limitText = "1500", startDay = 25,
                choices = BudgetChoices(
                    current = null, own = emptyList(),
                    defaults = listOf(
                        d("groceries", "Groceries", "cart"), d("dining", "Dining", "restaurant"), d("clothes", "Clothes", "shirt"),
                        d("health", "Health", "heart"), d("school", "School", "school"), d("fuel", "Fuel", "fuel"), d("gifts", "Gifts", "gift"),
                    ),
                    forCurrency = "EGP", currencies = listOf("EGP", "USD"), defaultCurrency = "EGP",
                    alreadyBudgeted = setOf(BudgetValidation.keyOf(null), "groceries", "dining"), startDay = 25,
                ),
                history = SpendHistory(124_000, 138_000),
            ),
            {},
        )
    }

    @Test fun categories() = compose.auditCapture("Categories-B") {
        fun r(id: String, name: String, icon: String, color: String?, status: CategoryStatusUi? = null, own: Boolean = false) =
            CategoryRowUi(id, name, icon, color, if (own) CategoryCaptionUi.None else CategoryCaptionUi.Default, status, own)
        CategoriesScreen(
            CategoriesState(
                own = listOf(r("p", "Pets", "care", "#6B52B8", own = true), r("c", "Coffee", "store", "#8F5E12", CategoryStatusUi.Waiting, own = true)),
                defaults = listOf(
                    r("1", "Groceries", "cart", "#4C6FD6"), r("2", "Dining out", "restaurant", "#A9503A"), r("3", "Transport", "bus", "#1F7F8C"),
                    r("4", "Bills", "bolt", "#8F5E12"), r("5", "Health", "heart", "#2C7A5B"), r("6", "Clothes", "shirt", "#B04375"),
                    r("7", "School", "school", "#4C6FD6"), r("8", "Travel", "plane", "#1F7F8C"), r("9", "Gifts", "gift", "#B04375"),
                ),
                otherKindCount = 4,
                loaded = true,
            ),
            {},
        )
    }

    @Test fun categoryEdit() = compose.auditCapture("CategoryEdit-B") {
        CategoryEditorScreen(CategoryEditorState(loaded = true, name = "Pets", iconKey = "care", colorHex = "#6B52B8"), {})
    }
}
