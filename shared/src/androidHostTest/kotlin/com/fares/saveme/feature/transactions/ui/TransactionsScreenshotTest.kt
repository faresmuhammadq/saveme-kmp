package com.fares.saveme.feature.transactions.ui

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
import com.fares.saveme.designsystem.component.AppAlertDialogCard
import com.fares.saveme.designsystem.component.CategoryChoiceGroup
import com.fares.saveme.designsystem.component.CategoryChoiceItem
import com.fares.saveme.designsystem.component.CategoryChoiceList
import com.fares.saveme.designsystem.component.formatMoney
import com.fares.saveme.designsystem.format.formatDate
import com.fares.saveme.designsystem.theme.AppTheme
import com.fares.saveme.feature.categories.domain.model.CategoryChoices
import com.fares.saveme.feature.categories.domain.model.CategoryDisplay
import com.fares.saveme.feature.categories.domain.model.CategorySource
import com.fares.saveme.feature.transactions.domain.model.AddedBy
import com.fares.saveme.feature.transactions.domain.model.Counterpart
import com.fares.saveme.feature.transactions.domain.model.Direction
import com.fares.saveme.feature.transactions.domain.model.RefusalKind
import com.fares.saveme.feature.transactions.domain.model.RefusalReason
import com.fares.saveme.feature.transactions.domain.model.TransactionField
import com.fares.saveme.feature.transactions.domain.model.TransactionFieldProblem
import com.fares.saveme.feature.transactions.domain.model.TransactionKind
import com.fares.saveme.feature.transactions.domain.model.TransactionQuery
import com.fares.saveme.feature.transactions.domain.model.WalletChoice
import com.fares.saveme.feature.transactions.ui.editor.ClashField
import com.fares.saveme.feature.transactions.ui.editor.ClashLine
import com.fares.saveme.feature.transactions.ui.editor.ClashValue
import com.fares.saveme.feature.transactions.ui.editor.EditorSync
import com.fares.saveme.feature.transactions.ui.editor.TransactionEditorScreen
import com.fares.saveme.feature.transactions.ui.editor.TransactionEditorState
import com.fares.saveme.feature.transactions.ui.list.DayUi
import com.fares.saveme.feature.transactions.ui.list.FilterSheetContent
import com.fares.saveme.feature.transactions.ui.list.RowStatus
import com.fares.saveme.feature.transactions.ui.list.RowUi
import com.fares.saveme.feature.transactions.ui.list.TransactionsScreen
import com.fares.saveme.feature.transactions.ui.list.TransactionsState
import com.github.takahirom.roborazzi.captureRoboImage
import kotlinx.datetime.LocalDate
import org.jetbrains.compose.resources.stringResource
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import saveme.shared.generated.resources.Res
import saveme.shared.generated.resources.action_cancel
import saveme.shared.generated.resources.category_choice_current
import saveme.shared.generated.resources.category_choice_defaults
import saveme.shared.generated.resources.category_choice_empty
import saveme.shared.generated.resources.category_choice_own
import saveme.shared.generated.resources.tx_delete
import saveme.shared.generated.resources.tx_delete_body
import saveme.shared.generated.resources.tx_delete_title
import kotlin.time.Instant

/** The transaction screens' states in English LTR, Arabic RTL and English at font scale 2.0 (P-14.4, FR-035). */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w400dp-h1800dp")
class TransactionsScreenshotTest(
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
        compose.onRoot().captureRoboImage("src/androidHostTest/screenshots/screen_transactions_${name}_$config.png")
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
        private val today = LocalDate(2026, 10, 2)
        private val cash = WalletChoice("w1", "Cash", "EGP")
        private val bank = WalletChoice("w2", "Bank", "EGP")
        private val groceries = CategoryDisplay("c1", "Groceries", "cart", "#2E7D32", "expense", CategorySource.Own, false)
        private val transport = CategoryDisplay("c2", "Transport", "bus", "#1F7F8C", "expense", CategorySource.Own, false)
        private val dining = CategoryDisplay("d1", "Dining", "restaurant", "#A9503A", "expense", CategorySource.Default, false)
        private val salary = CategoryDisplay("ci", "Salary", "laptop", "#2C7A5B", "income", CategorySource.Own, false)
        private val unavailable = CategoryDisplay("gone", "", null, null, null, CategorySource.Unavailable, false)
        private val choices = CategoryChoices(null, listOf(groceries, transport), listOf(dining))

        private val createBase = TransactionEditorState(
            loaded = true, walletId = "w1", walletChoices = listOf(cash, bank), currency = "EGP", date = today, categoryChoices = choices,
        )
        private val editBase = createBase.copy(
            transactionId = "t1", amountText = "45.50", categoryId = "c1", category = groceries, note = "Weekly shop", baseVersion = 3,
            canEdit = true, canDelete = true, addedBy = AddedBy.Member("Omar"),
        )
        private val transferEdit = editBase.copy(
            kindWire = "transfer", categoryId = null, category = null, destinationId = "w2", destinationName = "Bank", amountText = "500.00",
            note = "", fromRecurringRule = false, addedBy = AddedBy.You,
        )

        private fun row(
            id: String, kind: TransactionKind, direction: Direction, minor: Long, category: CategoryDisplay? = null, note: String? = null,
            status: RowStatus = RowStatus.None, counterpart: Counterpart? = null,
        ) = RowUi(id, kind, direction, counterpart, category, note, minor, status)

        private val days = listOf(
            DayUi(
                today, 119_500 - 4_550 - 20_000,
                listOf(
                    row("1", TransactionKind.Expense, Direction.Out, 4_550, groceries, "Weekly shop", RowStatus.Waiting),
                    row("2", TransactionKind.Income, Direction.In, 119_500, salary),
                    row("3", TransactionKind.Transfer, Direction.Out, 20_000, counterpart = Counterpart.Named("Bank"), note = "Savings"),
                ),
            ),
            DayUi(
                LocalDate(2026, 10, 1), -8_500 + 10_000,
                listOf(
                    row("4", TransactionKind.Expense, Direction.Out, 8_500, transport, status = RowStatus.Changed),
                    row("5", TransactionKind.Transfer, Direction.In, 10_000, counterpart = Counterpart.Named("Bank")),
                    row("6", TransactionKind.Expense, Direction.Out, 1_200, unavailable, "Old category", RowStatus.NotSaved),
                ),
            ),
            DayUi(
                LocalDate(2026, 9, 28), -35_000,
                listOf(
                    row("7", TransactionKind.Expense, Direction.Out, 35_000, dining, "Dinner"),
                    row("8", TransactionKind.Unknown, Direction.None, 777, null, null),
                ),
            ),
        )
        private val listBase = TransactionsState(
            walletId = "w1", walletName = "Cash", currency = "EGP", balanceMinor = 234_000, days = days, totalCount = 8, loading = false,
            canAdd = true, today = today, timeZoneId = "Africa/Cairo", filterChoices = listOf(groceries, transport, dining, salary),
        )

        private fun dueItem(id: String, title: String, date: String, state: com.fares.saveme.feature.transactions.domain.DueState) =
            com.fares.saveme.feature.transactions.domain.DueItem(id, "w1", "r1", title, kotlinx.datetime.LocalDate.parse(date), 450_000, "EGP", "expense", state)

        private fun dialog(state: TransactionEditorState): @Composable () -> Unit = {
            Column(Modifier.padding(16.dp)) {
                AppAlertDialogCard(
                    title = stringResource(Res.string.tx_delete_title),
                    text = stringResource(Res.string.tx_delete_body, formatMoney(4_550, state.currency), formatDate(today)),
                    confirmLabel = stringResource(Res.string.tx_delete),
                    onConfirm = {},
                    dismissLabel = stringResource(Res.string.action_cancel),
                    onDismiss = {},
                    destructive = true,
                )
            }
        }

        private fun categorySheet(): @Composable () -> Unit = {
            Column(Modifier.padding(16.dp)) {
                CategoryChoiceList(
                    groups = listOf(
                        CategoryChoiceGroup(stringResource(Res.string.category_choice_current), listOf(CategoryChoiceItem("x", "Old coffee", "restaurant", "#A9503A")), "categoryChoice.current", indexed = false),
                        CategoryChoiceGroup(stringResource(Res.string.category_choice_own), listOf(CategoryChoiceItem("c1", "Groceries", "cart", "#2E7D32"), CategoryChoiceItem("c2", "Transport", "bus", "#1F7F8C")), "categoryChoice.own"),
                        CategoryChoiceGroup(stringResource(Res.string.category_choice_defaults), listOf(CategoryChoiceItem("d1", "Dining", "restaurant", "#A9503A")), "categoryChoice.default"),
                    ),
                    selectedId = "c1", onSelect = {}, emptyMessage = stringResource(Res.string.category_choice_empty),
                )
            }
        }

        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun screens(): List<Array<Any>> = listOf(
            // The editor: recording.
            arrayOf("editor_create_match_offer", @Composable {
                TransactionEditorScreen(
                    createBase.copy(
                        amountText = "4500.00", categoryId = "c1", category = groceries,
                        matches = listOf(dueItem("o1", "Rent", "2026-10-01", com.fares.saveme.feature.transactions.domain.DueState.Due)),
                    ),
                    {},
                )
            }),
            arrayOf("editor_open_rule", @Composable {
                TransactionEditorScreen(editBase.copy(fromRecurringRule = true, ruleId = "r1", ruleOnPhone = true), {})
            }),
            arrayOf("editor_create_empty", @Composable { TransactionEditorScreen(createBase, {}) }),
            arrayOf("editor_create_filled", @Composable {
                TransactionEditorScreen(
                    createBase.copy(
                        amountText = "45.50", categoryId = "c1", category = groceries, note = "Weekly shop", moreOpen = true,
                        details = "Bought for the party on Friday", person = "Ahmed", cleared = true,
                    ),
                    {},
                )
            }),
            arrayOf("editor_create_errors", @Composable {
                TransactionEditorScreen(
                    createBase.copy(
                        amountText = "0", moreOpen = true, details = "x",
                        errors = mapOf(
                            TransactionField.Amount to TransactionFieldProblem.AmountNotPositive,
                            TransactionField.Category to TransactionFieldProblem.CategoryRequired,
                            TransactionField.Note to TransactionFieldProblem.TooLong(100),
                            TransactionField.Details to TransactionFieldProblem.TooLong(1000),
                        ),
                    ),
                    {},
                )
            }),
            arrayOf("editor_no_wallet", @Composable { TransactionEditorScreen(createBase.copy(noWallet = true, walletChoices = emptyList()), {}) }),
            arrayOf("editor_category_sheet", categorySheet()),
            // Transfers.
            arrayOf("editor_create_transfer", @Composable {
                TransactionEditorScreen(createBase.copy(kindWire = "transfer", amountText = "500", destinationChoices = listOf(bank), destinationId = "w2", destinationName = "Bank"), {})
            }),
            arrayOf("editor_create_transfer_none", @Composable {
                TransactionEditorScreen(createBase.copy(kindWire = "transfer", amountText = "500", destinationChoices = emptyList()), {})
            }),
            arrayOf("editor_edit_former_member", @Composable { TransactionEditorScreen(editBase.copy(addedBy = AddedBy.FormerMember), {}) }),
            arrayOf("editor_edit_transfer", @Composable { TransactionEditorScreen(transferEdit, {}) }),
            arrayOf("editor_refused_other_wallet", @Composable {
                TransactionEditorScreen(transferEdit.copy(sync = EditorSync.Refused(RefusalReason(RefusalKind.NoRightInOtherWallet))), {})
            }),
            // Edit and detail.
            arrayOf("editor_edit", @Composable { TransactionEditorScreen(editBase, {}) }),
            arrayOf("editor_waiting", @Composable { TransactionEditorScreen(editBase.copy(sync = EditorSync.Waiting), {}) }),
            arrayOf("editor_clash", @Composable {
                TransactionEditorScreen(
                    editBase.copy(
                        sync = EditorSync.Clash(
                            listOf(
                                ClashLine(ClashField.Amount, ClashValue.Money(6_000, "EGP"), ClashValue.Money(4_550, "EGP")),
                                ClashLine(ClashField.Category, ClashValue.Category(groceries), ClashValue.Category(transport)),
                                ClashLine(ClashField.Note, ClashValue.Text("Weekly shop"), ClashValue.Text(null)),
                            ),
                            deletedElsewhere = false, isDelete = false,
                        ),
                    ),
                    {},
                )
            }),
            arrayOf("editor_deleted_elsewhere", @Composable {
                TransactionEditorScreen(editBase.copy(sync = EditorSync.Clash(emptyList(), deletedElsewhere = true, isDelete = false)), {})
            }),
            arrayOf("editor_delete_clash", @Composable {
                TransactionEditorScreen(editBase.copy(canDelete = false, sync = EditorSync.Clash(emptyList(), deletedElsewhere = false, isDelete = true)), {})
            }),
            arrayOf("editor_refused_category", @Composable {
                TransactionEditorScreen(
                    editBase.copy(
                        sync = EditorSync.Refused(RefusalReason(RefusalKind.CategoryUnusable)),
                        errors = mapOf(TransactionField.Category to TransactionFieldProblem.Server("INVALID_ENUM_VALUE")),
                    ),
                    {},
                )
            }),
            arrayOf("editor_refused_currency", @Composable {
                TransactionEditorScreen(
                    editBase.copy(
                        currency = "USD", amountText = "", currencyChanged = true,
                        sync = EditorSync.Refused(RefusalReason(RefusalKind.CurrencyChanged)),
                    ),
                    {},
                )
            }),
            arrayOf("editor_refused_no_right", @Composable {
                TransactionEditorScreen(editBase.copy(sync = EditorSync.Refused(RefusalReason(RefusalKind.NoRightHere))), {})
            }),
            arrayOf("editor_refused_not_verified", @Composable {
                TransactionEditorScreen(editBase.copy(sync = EditorSync.Refused(RefusalReason(RefusalKind.NotVerified))), {})
            }),
            arrayOf("editor_refused_other", @Composable {
                TransactionEditorScreen(editBase.copy(sync = EditorSync.Refused(RefusalReason(RefusalKind.Other, "req-42"))), {})
            }),
            arrayOf("editor_viewer", @Composable {
                TransactionEditorScreen(editBase.copy(canEdit = false, canDelete = false, moreOpen = true, person = "Ahmed", fromRecurringRule = true), {})
            }),
            arrayOf("editor_unknown_kind", @Composable {
                TransactionEditorScreen(editBase.copy(kindWire = "refund", categoryId = null, category = null, canEdit = false), {})
            }),
            arrayOf("dialog_delete", dialog(editBase)),
            // The list.
            arrayOf("list_loaded", @Composable { TransactionsScreen(listBase, {}) }),
            arrayOf("list_due", @Composable {
                TransactionsScreen(
                    listBase.copy(
                        due = listOf(
                            dueItem("o0", "Rent", "2026-09-01", com.fares.saveme.feature.transactions.domain.DueState.Missed),
                            dueItem("o1", "Internet", "2026-10-01", com.fares.saveme.feature.transactions.domain.DueState.Due),
                            dueItem("o2", "Gym", "2026-10-02", com.fares.saveme.feature.transactions.domain.DueState.Due),
                        ),
                        dueTotal = 5,
                    ),
                    {},
                )
            }),
            arrayOf("list_pending_confirmation", @Composable {
                TransactionsScreen(
                    listBase.copy(
                        days = listOf(
                            DayUi(today, -450_000, listOf(RowUi("t9", TransactionKind.Expense, Direction.Out, null, groceries, "Rent", 450_000, RowStatus.Waiting, occurrenceId = "o9"))),
                        ) + days.drop(1),
                    ),
                    {},
                )
            }),
            arrayOf("list_offline", @Composable {
                TransactionsScreen(listBase.copy(offline = true, waitingCount = 2, updatedAt = Instant.parse("2026-10-02T08:30:00Z")), {})
            }),
            arrayOf("list_loading", @Composable { TransactionsScreen(listBase.copy(loading = true, days = emptyList(), totalCount = 0), {}) }),
            arrayOf("list_empty", @Composable { TransactionsScreen(listBase.copy(days = emptyList(), totalCount = 0), {}) }),
            arrayOf("list_empty_viewer", @Composable { TransactionsScreen(listBase.copy(days = emptyList(), totalCount = 0, canAdd = false), {}) }),
            arrayOf("list_refresh_failed", @Composable { TransactionsScreen(listBase.copy(refreshFailed = true), {}) }),
            arrayOf("list_liability", @Composable { TransactionsScreen(listBase.copy(isLiability = true, balanceMinor = -312_425, walletName = "Credit card"), {}) }),
            arrayOf("list_search", @Composable {
                TransactionsScreen(
                    listBase.copy(searchText = "shop", query = TransactionQuery(search = "shop"), days = listOf(days[0].copy(rows = days[0].rows.take(1)))),
                    {},
                )
            }),
            arrayOf("list_chips", @Composable {
                TransactionsScreen(
                    listBase.copy(
                        query = TransactionQuery(from = LocalDate(2026, 10, 1), to = LocalDate(2026, 10, 31), categoryIds = setOf("c1"), kinds = setOf(TransactionKind.Expense)),
                        days = listOf(days[0].copy(rows = days[0].rows.take(1))),
                    ),
                    {},
                )
            }),
            arrayOf("list_no_matches", @Composable {
                TransactionsScreen(listBase.copy(searchText = "zzz", query = TransactionQuery(search = "zzz"), days = emptyList()), {})
            }),
            arrayOf("filter_sheet", @Composable {
                Column(Modifier.padding(16.dp)) {
                    FilterSheetContent(
                        listBase.copy(query = TransactionQuery(from = LocalDate(2026, 10, 1), to = LocalDate(2026, 10, 31), categoryIds = setOf("c1"), kinds = setOf(TransactionKind.Expense))),
                        {},
                    )
                }
            }),
        )
    }
}
