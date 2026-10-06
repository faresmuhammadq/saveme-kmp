package com.fares.saveme.feature.recurring.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.LayoutDirection
import com.fares.saveme.designsystem.theme.AppTheme
import com.fares.saveme.feature.categories.domain.model.CategoryChoices
import com.fares.saveme.feature.categories.domain.model.CategoryDisplay
import com.fares.saveme.feature.categories.domain.model.CategorySource
import com.fares.saveme.feature.recurring.domain.model.AmountBasis
import com.fares.saveme.feature.recurring.domain.model.Frequency
import com.fares.saveme.feature.recurring.domain.model.FrozenMembers
import com.fares.saveme.feature.recurring.domain.model.Occurrence
import com.fares.saveme.feature.recurring.domain.model.OccurrenceLists
import com.fares.saveme.feature.recurring.domain.model.OccurrencePending
import com.fares.saveme.feature.recurring.domain.model.OccurrenceState
import com.fares.saveme.feature.recurring.domain.model.OccurrenceStatus
import com.fares.saveme.feature.recurring.domain.model.OccurrenceView
import com.fares.saveme.feature.recurring.domain.model.Preset
import com.fares.saveme.feature.recurring.domain.model.PreviewRound
import com.fares.saveme.feature.recurring.domain.model.RecurringSyncState
import com.fares.saveme.feature.recurring.domain.model.RuleField
import com.fares.saveme.feature.recurring.domain.model.RuleFieldProblem
import com.fares.saveme.feature.recurring.domain.model.SchedulePreview
import com.fares.saveme.feature.recurring.ui.editor.EndMode
import com.fares.saveme.feature.recurring.ui.editor.ReadOnlyReason
import com.fares.saveme.feature.recurring.ui.editor.RuleEditorScreen
import com.fares.saveme.feature.recurring.ui.editor.RuleEditorState
import com.fares.saveme.feature.recurring.ui.list.RecurringScreen
import com.fares.saveme.feature.recurring.ui.list.RecurringState
import com.fares.saveme.feature.recurring.ui.occurrence.OccurrenceForm
import com.fares.saveme.feature.recurring.ui.occurrence.OccurrenceScreen
import com.fares.saveme.feature.recurring.ui.occurrence.OccurrenceScreenState
import com.fares.saveme.feature.recurring.ui.rule.RuleDetailScreen
import com.fares.saveme.feature.recurring.ui.rule.RuleDetailState
import com.fares.saveme.feature.transactions.domain.model.TransactionKind
import com.fares.saveme.feature.transactions.domain.model.WalletChoice
import com.github.takahirom.roborazzi.captureRoboImage
import kotlinx.datetime.LocalDate
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The recurring screens' states in English LTR, Arabic RTL and English at font scale 2.0 (P-14.4, FR-037). */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w400dp-h2600dp")
class RecurringScreenshotTest(
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
        compose.onRoot().captureRoboImage("src/androidHostTest/screenshots/screen_recurring_${name}_$config.png")
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
        private val cash = WalletChoice("w1", "Cash", "EGP")
        private val bank = WalletChoice("w2", "Bank", "EGP")
        private val rent = CategoryDisplay("c1", "Rent", "home", "#2E7D32", "expense", CategorySource.Own, false)
        private val food = CategoryDisplay("c2", "Groceries", "cart", "#1F7F8C", "expense", CategorySource.Own, false)
        private val salary = CategoryDisplay("ci", "Salary", "laptop", "#2C7A5B", "income", CategorySource.Own, false)
        private val choices = CategoryChoices(null, listOf(rent, food), emptyList())
        private val incomeChoices = CategoryChoices(null, listOf(salary), emptyList())

        // ---- list ----
        private val rentRule = testRuleView(testRule(id = "r1", description = "Rent"))
        private val installmentRule = testRuleView(
            testRule(id = "r2", preset = "installment", description = "Laptop", amountMinor = 1_000_000, basis = "total", count = 12, anchorDay = 5, startDate = LocalDate(2026, 8, 5)),
            nextDate = LocalDate(2026, 11, 5),
        )
        private val gameyaRule = testRuleView(
            testRule(id = "r3", preset = "gameya", description = "Office gam’eya", amountMinor = 100_000, count = 10, payoutRound = 4, payoutCategoryId = "ci", anchorDay = 10),
            nextDate = LocalDate(2026, 10, 10),
        )
        private val pausedRule = testRuleView(testRule(id = "r4", status = "paused", description = "Gym", amountMinor = 60_000), syncState = RecurringSyncState.Waiting, nextDate = null)
        private val endedRule = testRuleView(testRule(id = "r5", status = "ended", description = "Old subscription", amountMinor = 9_900), readOnly = true, nextDate = null)
        private val salaryRule = testRuleView(testRule(id = "r6", walletId = "w2", kind = "income", description = "Salary", amountMinor = 2_500_000, categoryId = "ci"))

        private val dueView = testOccurrenceView(testOccurrence("o1", date = LocalDate(2026, 10, 1)), OccurrenceState.Due)
        private val missedView = testOccurrenceView(testOccurrence("o0", date = LocalDate(2026, 9, 1)), OccurrenceState.Missed)
        private val upcomingView = testOccurrenceView(testOccurrence("o2", ruleId = "r2", date = LocalDate(2026, 11, 5), amountMinor = 83_334), OccurrenceState.Upcoming)
        private val payoutView = testOccurrenceView(testOccurrence("o3", ruleId = "r3", date = LocalDate(2026, 11, 10), amountMinor = 1_000_000, payout = true, kind = "income"), OccurrenceState.Upcoming)

        private val listFull = RecurringState(
            walletId = "w1", walletName = "Cash", loading = false, canAdd = true,
            due = listOf(missedView, dueView), upcoming = listOf(upcomingView, payoutView),
            rules = listOf(rentRule, installmentRule, gameyaRule, pausedRule, endedRule),
        )

        // ---- editor ----
        private val base = RuleEditorState(
            loaded = true, walletId = "w1", walletChoices = listOf(cash, bank), currency = "EGP", start = LocalDate(2026, 10, 31), dayText = "31",
            categoryChoices = choices, payoutChoices = incomeChoices, destinationChoices = listOf(bank),
        )
        private val roundsPreview = SchedulePreview(
            listOf(
                PreviewRound(1, LocalDate(2026, 10, 31), 450_000, false), PreviewRound(2, LocalDate(2026, 11, 30), 450_000, false),
                PreviewRound(3, LocalDate(2026, 12, 31), 450_000, false), PreviewRound(4, LocalDate(2027, 1, 31), 450_000, false),
                PreviewRound(5, LocalDate(2027, 2, 28), 450_000, false),
            ),
        )
        private val filled = base.copy(amountText = "4500.00", categoryId = "c1", category = rent, note = "Rent", preview = roundsPreview)
        private val edit = filled.copy(ruleId = "r1", start = LocalDate(2026, 9, 1), dayText = "1", dayTouched = true, baseVersion = 3, canSplit = true)
        private val gameya = base.copy(
            preset = Preset.Gameya, amountText = "1000.00", categoryId = "c1", category = rent, countText = "10", payoutRoundText = "4",
            payoutCategoryId = "ci", payoutCategory = salary,
            preview = SchedulePreview(
                listOf(
                    PreviewRound(1, LocalDate(2026, 10, 31), 100_000, false), PreviewRound(2, LocalDate(2026, 11, 30), 100_000, false),
                    PreviewRound(3, LocalDate(2026, 12, 31), 100_000, false), PreviewRound(4, LocalDate(2027, 1, 31), 100_000, false),
                    PreviewRound(4, LocalDate(2027, 1, 31), 1_000_000, true),
                ),
                payoutMinor = 1_000_000,
            ),
        )
        private val installmentTotal = base.copy(
            preset = Preset.Installment, basis = AmountBasis.Total, amountText = "10000.00", categoryId = "c1", category = rent, countText = "12",
            preview = SchedulePreview(
                listOf(
                    PreviewRound(1, LocalDate(2026, 10, 31), 83_334, false), PreviewRound(2, LocalDate(2026, 11, 30), 83_334, false),
                    PreviewRound(5, LocalDate(2027, 2, 28), 83_333, false),
                ),
            ),
        )

        // ---- occurrence ----
        private fun occ(view: OccurrenceView, details: Pair<CategoryDisplay?, String?> = rent to null, notice: String? = view.notice) =
            OccurrenceScreenState("o1", loaded = true, view = view, category = details.first, destinationName = details.second, notice = notice, categoryChoices = choices)
        private val pending = OccurrencePending(OccurrenceStatus.Confirmed, LocalDate(2026, 10, 2), 400_000, "c1", "Rent", null, null, "t9")
        private val confirmed = testOccurrenceView(testOccurrence(status = "confirmed", transactionId = "t1"), OccurrenceState.Confirmed)

        // ---- rule detail ----
        private fun ruleScreen(
            view: com.fares.saveme.feature.recurring.domain.model.RuleView = rentRule,
            occurrences: OccurrenceLists = OccurrenceLists(due = listOf(dueView), upcoming = listOf(upcomingView), history = listOf(confirmed)),
            preview: SchedulePreview = SchedulePreview.Empty,
        ) = RuleDetailState("r1", loaded = true, view = view, occurrences = occurrences, preview = preview)
        private val unsyncedRule = rentRule.copy(rule = testRule(version = 0), hasOccurrences = false)

        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun screens(): List<Array<Any>> = listOf(
            arrayOf("list_empty", @Composable { RecurringScreen(RecurringState(walletId = "w1", walletName = "Cash", loading = false, canAdd = true), {}) }),
            arrayOf("list_empty_viewer", @Composable { RecurringScreen(RecurringState(walletId = "w1", walletName = "Cash", loading = false), {}) }),
            arrayOf("list_sections", @Composable { RecurringScreen(listFull, {}) }),
            arrayOf("list_offline", @Composable { RecurringScreen(listFull.copy(offline = true), {}) }),
            arrayOf(
                "list_all_wallets",
                @Composable {
                    RecurringScreen(
                        listFull.copy(walletId = null, walletName = null, rules = listOf(rentRule, installmentRule, salaryRule, pausedRule)),
                        {},
                    )
                },
            ),
            arrayOf("editor_empty", @Composable { RuleEditorScreen(base, {}) }),
            arrayOf("editor_filled", @Composable { RuleEditorScreen(filled, {}) }),
            arrayOf(
                "editor_errors",
                @Composable {
                    RuleEditorScreen(
                        base.copy(
                            intervalText = "400", endMode = EndMode.After, errors = mapOf(
                                RuleField.Amount to RuleFieldProblem.Required, RuleField.Category to RuleFieldProblem.Required,
                                RuleField.Interval to RuleFieldProblem.OutOfRange(1, 365), RuleField.Count to RuleFieldProblem.Required,
                            ),
                        ),
                        {},
                    )
                },
            ),
            arrayOf(
                "editor_transfer",
                @Composable {
                    RuleEditorScreen(base.copy(kind = TransactionKind.Transfer, amountText = "500.00", destinationId = "w2", destinationName = "Bank", preview = roundsPreview), {})
                },
            ),
            arrayOf("editor_end_date", @Composable { RuleEditorScreen(filled.copy(endMode = EndMode.OnDate, endDate = LocalDate(2027, 3, 31)), {}) }),
            arrayOf("editor_gameya", @Composable { RuleEditorScreen(gameya, {}) }),
            arrayOf("editor_installment_total", @Composable { RuleEditorScreen(installmentTotal, {}) }),
            arrayOf(
                "editor_installment_each",
                @Composable { RuleEditorScreen(installmentTotal.copy(basis = AmountBasis.PerOccurrence, amountText = "833.33"), {}) },
            ),
            arrayOf("editor_edit", @Composable { RuleEditorScreen(edit, {}) }),
            arrayOf(
                "editor_edit_frozen",
                @Composable { RuleEditorScreen(edit.copy(frozen = FrozenMembers(schedule = true, lastResolvedDate = LocalDate(2026, 10, 1))), {}) },
            ),
            arrayOf(
                "editor_from_date",
                @Composable {
                    RuleEditorScreen(edit.copy(fromDateMode = true, from = LocalDate(2026, 12, 1), frozen = FrozenMembers(schedule = true), dayText = "1"), {})
                },
            ),
            arrayOf("editor_read_only", @Composable { RuleEditorScreen(edit.copy(readOnly = ReadOnlyReason.Ended), {}) }),
            arrayOf("editor_frequency_weekly", @Composable { RuleEditorScreen(filled.copy(frequency = Frequency.Weekly, intervalText = "2"), {}) }),
            arrayOf("rule_active", @Composable { RuleDetailScreen(ruleScreen(), {}) }),
            arrayOf("rule_former_member", @Composable {
                RuleDetailScreen(ruleScreen(rentRule.copy(author = com.fares.saveme.feature.recurring.domain.model.RuleAuthor.FormerMember)), {})
            }),
            arrayOf("rule_paused", @Composable { RuleDetailScreen(ruleScreen(testRuleView(testRule(status = "paused"))), {}) }),
            arrayOf("rule_ended", @Composable { RuleDetailScreen(ruleScreen(endedRule, OccurrenceLists(history = listOf(confirmed))), {}) }),
            arrayOf("rule_gameya", @Composable { RuleDetailScreen(ruleScreen(gameyaRule, OccurrenceLists(upcoming = listOf(payoutView))), {}) }),
            arrayOf("rule_unsynced_preview", @Composable { RuleDetailScreen(ruleScreen(unsyncedRule, OccurrenceLists(), roundsPreview), {}) }),
            arrayOf(
                "rule_edit_waiting",
                @Composable { RuleDetailScreen(ruleScreen(rentRule.copy(hasPendingChange = true, syncState = RecurringSyncState.Waiting)), {}) },
            ),
            arrayOf(
                "rule_held_split",
                @Composable {
                    RuleDetailScreen(ruleScreen(unsyncedRule.copy(syncState = RecurringSyncState.Held("r0")), OccurrenceLists(), roundsPreview).copy(heldOnTitle = "Rent"), {})
                },
            ),
            arrayOf(
                "rule_clash",
                @Composable { RuleDetailScreen(ruleScreen(testRuleView(syncState = RecurringSyncState.Clash(deletedElsewhere = false, mineIsDeletion = false))), {}) },
            ),
            arrayOf(
                "rule_deleted_elsewhere",
                @Composable { RuleDetailScreen(ruleScreen(testRuleView(syncState = RecurringSyncState.Clash(deletedElsewhere = true, mineIsDeletion = false))), {}) },
            ),
            arrayOf(
                "rule_refused",
                @Composable { RuleDetailScreen(ruleScreen(testRuleView(syncState = RecurringSyncState.Refused("VALIDATION_FAILED", "req-1", mapOf("amount" to "OUT_OF_RANGE")))), {}) },
            ),
            arrayOf("rule_viewer", @Composable { RuleDetailScreen(ruleScreen(testRuleView(role = com.fares.saveme.feature.wallets.domain.model.WalletRole.Viewer, readOnly = true)), {}) }),
            arrayOf("rule_unknown_values", @Composable { RuleDetailScreen(ruleScreen(testRuleView(testRule(frequency = "hourly"), readOnly = true), OccurrenceLists()), {}) }),
            arrayOf(
                "rule_catch_up_offline",
                @Composable { RuleDetailScreen(ruleScreen().copy(catchUpNote = com.fares.saveme.feature.recurring.ui.rule.CatchUpNote.Offline), {}) },
            ),
            arrayOf(
                "rule_end_dialog",
                @Composable { RuleDetailScreen(ruleScreen().copy(dialog = com.fares.saveme.feature.recurring.ui.rule.RuleDialog.End), {}) },
            ),
            arrayOf("occurrence_due", @Composable { OccurrenceScreen(occ(dueView), {}) }),
            arrayOf("occurrence_missed", @Composable { OccurrenceScreen(occ(missedView), {}) }),
            arrayOf("occurrence_payout", @Composable { OccurrenceScreen(occ(payoutView, salary to null), {}) }),
            arrayOf("occurrence_transfer", @Composable { OccurrenceScreen(occ(testOccurrenceView(testOccurrence(kind = "transfer", categoryId = null, counterWalletId = "w2")), null to "Bank"), {}) }),
            arrayOf(
                "occurrence_confirm_form",
                @Composable {
                    OccurrenceScreen(
                        occ(dueView).copy(form = OccurrenceForm.Confirm, amountText = "4500.00", date = LocalDate(2026, 10, 1), formCategoryId = "c1", formCategory = rent, note = "Rent"),
                        {},
                    )
                },
            ),
            arrayOf(
                "occurrence_edit_form_errors",
                @Composable {
                    OccurrenceScreen(
                        occ(dueView).copy(
                            form = OccurrenceForm.Edit, amountText = "", date = LocalDate(2026, 10, 1), formCategoryId = null,
                            errors = mapOf(
                                com.fares.saveme.feature.recurring.domain.model.ConfirmField.Amount to RuleFieldProblem.Required,
                                com.fares.saveme.feature.recurring.domain.model.ConfirmField.Category to RuleFieldProblem.Required,
                            ),
                        ),
                        {},
                    )
                },
            ),
            arrayOf(
                "occurrence_waiting",
                @Composable { OccurrenceScreen(occ(testOccurrenceView(pending = pending, state = OccurrenceState.Confirmed, syncState = RecurringSyncState.Waiting)), {}) },
            ),
            arrayOf(
                "occurrence_clash",
                @Composable { OccurrenceScreen(occ(testOccurrenceView(syncState = RecurringSyncState.Clash(deletedElsewhere = false, mineIsDeletion = false))), {}) },
            ),
            arrayOf(
                "occurrence_deleted_elsewhere",
                @Composable { OccurrenceScreen(occ(testOccurrenceView(syncState = RecurringSyncState.Clash(deletedElsewhere = true, mineIsDeletion = false))), {}) },
            ),
            arrayOf(
                "occurrence_refused",
                @Composable { OccurrenceScreen(occ(testOccurrenceView(syncState = RecurringSyncState.Refused("FORBIDDEN", "req-1", emptyMap()))), {}) },
            ),
            arrayOf(
                "occurrence_auto_refusal",
                @Composable { OccurrenceScreen(occ(testOccurrenceView(testOccurrence(refusal = "currency_mismatch", kind = "transfer", categoryId = null, counterWalletId = "w2")), null to "Bank"), {}) },
            ),
            arrayOf(
                "occurrence_already_confirmed",
                @Composable { OccurrenceScreen(occ(testOccurrenceView(testOccurrence(status = "confirmed", transactionId = "t1", notice = "already_confirmed"), OccurrenceState.Confirmed)), {}) },
            ),
            arrayOf("occurrence_confirmed", @Composable { OccurrenceScreen(occ(confirmed), {}) }),
            arrayOf(
                "occurrence_skipped",
                @Composable { OccurrenceScreen(occ(testOccurrenceView(testOccurrence(status = "skipped"), OccurrenceState.Skipped)), {}) },
            ),
            arrayOf("occurrence_viewer", @Composable { OccurrenceScreen(occ(testOccurrenceView(canWrite = false)), {}) }),
        )
    }
}
