package com.fares.saveme.feature.budgets.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.fares.saveme.designsystem.component.CategoryChoiceList
import com.fares.saveme.designsystem.theme.AppTheme
import com.fares.saveme.feature.auth.domain.BudgetGlimpse
import com.fares.saveme.feature.auth.ui.home.HomeScreen
import com.fares.saveme.feature.auth.ui.home.HomeState
import com.fares.saveme.feature.budgets.domain.model.BudgetChoices
import com.fares.saveme.feature.budgets.domain.model.BudgetField
import com.fares.saveme.feature.budgets.domain.model.BudgetFieldProblem
import com.fares.saveme.feature.budgets.domain.model.RefusalKind
import com.fares.saveme.feature.budgets.ui.editor.BudgetEditorScreen
import com.fares.saveme.feature.budgets.ui.editor.BudgetEditorState
import com.fares.saveme.feature.budgets.ui.editor.ClashField
import com.fares.saveme.feature.budgets.ui.editor.ClashLine
import com.fares.saveme.feature.budgets.ui.editor.ClashValue
import com.fares.saveme.feature.budgets.ui.editor.ConfirmCard
import com.fares.saveme.feature.budgets.ui.editor.EditorConfirm
import com.fares.saveme.feature.budgets.ui.editor.EditorSync
import com.fares.saveme.feature.budgets.ui.editor.budgetCategoryGroups
import com.fares.saveme.feature.budgets.ui.editor.budgetSelectedChoice
import com.fares.saveme.feature.budgets.ui.list.BudgetsScreen
import com.fares.saveme.feature.budgets.ui.list.BudgetsState
import com.fares.saveme.feature.budgets.ui.list.PendingDayUi
import com.fares.saveme.feature.budgets.ui.list.PeriodUi
import com.fares.saveme.feature.categories.domain.model.CategoryDisplay
import com.fares.saveme.feature.categories.domain.model.CategorySource
import com.github.takahirom.roborazzi.captureRoboImage
import kotlinx.datetime.LocalDate
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.time.Instant

/**
 * The budget screens' states in English LTR, Arabic RTL and English at font scale 2.0 (P-14.4, FR-025). Dates, periods and
 * "Updated" times are part of each state, so no golden depends on the real date.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w400dp-h1900dp")
class BudgetsScreenshotTest(
    private val name: String,
    private val screen: @Composable () -> Unit,
) {
    @get:Rule
    val compose = createComposeRule()

    private fun capture(config: String, rtl: Boolean = false) {
        compose.setContent {
            CompositionLocalProvider(LocalLayoutDirection provides if (rtl) LayoutDirection.Rtl else LayoutDirection.Ltr) {
                AppTheme { screen() }
            }
        }
        compose.onRoot().captureRoboImage("src/androidHostTest/screenshots/screen_budgets_${name}_$config.png")
    }

    @Test
    fun englishLtr() = capture("en")

    @Test
    @Config(qualifiers = "+ar-rEG")
    fun arabicRtl() = capture("ar", rtl = true)

    @Test
    fun englishFontScale2() {
        RuntimeEnvironment.setFontScale(2f)
        capture("en_fs2")
    }

    companion object {
        private val period = PeriodUi(LocalDate(2026, 9, 25), LocalDate(2026, 10, 24), daysLeft = 22)
        private val updated = Instant.parse("2026-10-02T10:00:00Z")

        private fun row(
            id: String, name: String?, spent: Long?, limit: Long = 600_000, currency: String = "EGP", projected: Long? = null,
            others: List<String> = emptyList(), waiting: Boolean = false, status: BudgetStatusUi? = null, readOnly: Boolean = false,
        ) = BudgetRowUi(id, name, currency, limit, spent?.let { RowFigures(it, projected, others, waiting) }, readOnly, status)

        private val populated = BudgetsState(
            period = period,
            active = listOf(
                row("a", "Transport", 61_000, 120_000, projected = 226_667),
                row("b", "Groceries", 342_000, 400_000, projected = 1_539_000, status = BudgetStatusUi.Waiting, waiting = true),
                row("c", "Dining out", 154_000, 120_000, others = listOf("USD")),
                row("d", null, 350_000, 600_000, projected = 1_312_500, others = listOf("KWD", "USD")),
                row("e", "Gym", null, 250_000),
                row("f", "Books", 5_000, 50_000, status = BudgetStatusUi.Changed),
                row("g", "Fuel", 1_000, 90_000, status = BudgetStatusUi.NotSaved),
                row("h", "Travel", null, 300_000, status = BudgetStatusUi.DeletedElsewhere),
                row("i", "Courses", null, 777_000, readOnly = true),
            ),
            archived = listOf(row("old", "Holidays", 30_000, 60_000)),
            loaded = true,
            updatedAt = updated,
        )

        private val choices = BudgetChoices(
            current = null,
            own = listOf(
                CategoryDisplay("c1", "Groceries", "cart", "#2E7D32", "expense", CategorySource.Own, false),
                CategoryDisplay("c2", "Transport", "bus", "#1F7F8C", "expense", CategorySource.Own, false),
            ),
            defaults = listOf(CategoryDisplay("d1", "Dining out", "restaurant", "#EF6C00", "expense", CategorySource.Default, false)),
            forCurrency = "EGP",
            currencies = listOf("EGP", "USD"),
            defaultCurrency = "EGP",
            alreadyBudgeted = setOf("c1", "all"),
            startDay = 25,
        )

        private val create = BudgetEditorState(loaded = true, currency = "EGP", startDay = 25, choices = choices)

        private val groceries = row("b1", "Groceries", 350_000, projected = 1_312_500)
        private val edit = BudgetEditorState(
            budgetId = "b1", loaded = true, categoryChosen = true, categoryId = "c1", currency = "EGP", limitText = "6000.00", baseVersion = 3,
            shownLimitMinor = 600_000, row = groceries, canArchive = true, canDelete = true, startDay = 25,
        )

        private fun glimpse(id: String, name: String?, spent: Long?, limit: Long, waiting: Boolean = false) = BudgetGlimpse(id, name, spent, limit, "EGP", waiting)

        private val home = HomeState(
            email = "sara@example.com",
            budgets = listOf(
                glimpse("a", "Dining out", 154_000, 120_000),
                glimpse("b", "Groceries", 342_000, 400_000, waiting = true),
                glimpse("c", null, 61_000, 600_000),
            ),
        )

        private fun dialog(state: BudgetEditorState): @Composable () -> Unit = {
            Column(Modifier.background(AppTheme.colors.background).padding(16.dp)) { ConfirmCard(state, {}) }
        }

        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun screens(): List<Array<Any>> = listOf(
            arrayOf("list_empty", @Composable { BudgetsScreen(BudgetsState(loaded = true), {}) }),
            arrayOf("list_loading", @Composable { BudgetsScreen(BudgetsState(), {}) }),
            arrayOf("list_populated", @Composable { BudgetsScreen(populated, {}) }),
            arrayOf("list_pending_start_unknown", @Composable {
                BudgetsScreen(
                    populated.copy(
                        period = null, pendingDay = PendingDayUi(1, LocalDate(2026, 11, 1)),
                        active = listOf(row("a", "Groceries", null, 600_000), row("b", null, null, 900_000)), archived = emptyList(),
                    ),
                    {},
                )
            }),
            arrayOf("list_last_day_offline", @Composable {
                BudgetsScreen(
                    populated.copy(period = period.copy(daysLeft = 0), offline = true, waitingCount = 1, refreshFailed = true, timeZoneId = "UTC"),
                    {},
                )
            }),
            arrayOf("editor_create", @Composable { BudgetEditorScreen(create, {}) }),
            arrayOf("editor_create_filled", @Composable {
                BudgetEditorScreen(create.copy(categoryChosen = true, categoryId = "c2", limitText = "6000"), {})
            }),
            arrayOf("editor_create_problem", @Composable {
                BudgetEditorScreen(
                    create.copy(categoryChosen = true, categoryId = null, limitText = "0", errors = mapOf(BudgetField.Limit to BudgetFieldProblem.LimitNotPositive)),
                    {},
                )
            }),
            arrayOf("choices_already_budgeted", @Composable {
                Column(Modifier.background(AppTheme.colors.surface).padding(8.dp)) {
                    CategoryChoiceList(budgetCategoryGroups(choices), budgetSelectedChoice(create.copy(categoryChosen = true, categoryId = "c2")), {}, "No categories yet.")
                }
            }),
            arrayOf("editor_edit", @Composable { BudgetEditorScreen(edit, {}) }),
            arrayOf("editor_archived", @Composable {
                BudgetEditorScreen(edit.copy(archived = true, canArchive = false, row = groceries.copy(categoryName = "Holidays")), {})
            }),
            arrayOf("editor_waiting", @Composable {
                BudgetEditorScreen(edit.copy(sync = EditorSync.Waiting, row = groceries.copy(figures = groceries.figures?.copy(includesWaiting = true))), {})
            }),
            arrayOf("editor_clash", @Composable {
                BudgetEditorScreen(
                    edit.copy(
                        sync = EditorSync.Clash(
                            listOf(
                                ClashLine(ClashField.Limit, ClashValue.Limit(700_000, "EGP"), ClashValue.Limit(900_000, "EGP")),
                                ClashLine(ClashField.Archived, ClashValue.Archived(false), ClashValue.Archived(true)),
                            ),
                            deletedElsewhere = false, isDelete = false,
                        ),
                    ),
                    {},
                )
            }),
            arrayOf("editor_deleted_elsewhere", @Composable {
                BudgetEditorScreen(edit.copy(sync = EditorSync.Clash(emptyList(), deletedElsewhere = true, isDelete = false), canArchive = false), {})
            }),
            arrayOf("editor_refused", @Composable {
                BudgetEditorScreen(
                    edit.copy(
                        sync = EditorSync.Refused(com.fares.saveme.feature.budgets.domain.model.RefusalReason(RefusalKind.CategoryUnusable)),
                        row = groceries.copy(status = BudgetStatusUi.NotSaved),
                    ),
                    {},
                )
            }),
            arrayOf("editor_unknown_period_type", @Composable {
                BudgetEditorScreen(edit.copy(readOnly = true, canArchive = false, row = row("b1", "Courses", null, 777_000, readOnly = true)), {})
            }),
            arrayOf("confirm_archive", dialog(edit.copy(confirm = EditorConfirm.Archive))),
            arrayOf("confirm_delete", dialog(edit.copy(confirm = EditorConfirm.Delete))),
            arrayOf("home_budgets", @Composable { HomeScreen(home, {}) }),
            arrayOf("home_budgets_unknown_period", @Composable {
                HomeScreen(home.copy(budgets = listOf(glimpse("a", "Groceries", null, 600_000), glimpse("b", null, null, 900_000))), {})
            }),
            arrayOf("home_no_budgets", @Composable { HomeScreen(HomeState(email = "sara@example.com"), {}) }),
        )
    }
}
