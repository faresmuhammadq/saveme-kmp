package com.fares.saveme.audit

import androidx.compose.ui.test.junit4.createComposeRule
import com.fares.saveme.feature.categories.domain.model.CategoryDisplay
import com.fares.saveme.feature.categories.domain.model.CategorySource
import com.fares.saveme.feature.recurring.domain.model.OccurrenceLists
import com.fares.saveme.feature.recurring.domain.model.OccurrenceState
import com.fares.saveme.feature.recurring.domain.model.OccurrenceView
import com.fares.saveme.feature.recurring.domain.model.PreviewRound
import com.fares.saveme.feature.recurring.domain.model.SchedulePreview
import com.fares.saveme.feature.recurring.ui.editor.RuleEditorScreen
import com.fares.saveme.feature.recurring.ui.editor.RuleEditorState
import com.fares.saveme.feature.recurring.ui.list.RecurringScreen
import com.fares.saveme.feature.recurring.ui.list.RecurringState
import com.fares.saveme.feature.recurring.ui.occurrence.OccurrenceForm
import com.fares.saveme.feature.recurring.ui.occurrence.OccurrenceScreen
import com.fares.saveme.feature.recurring.ui.occurrence.OccurrenceScreenState
import com.fares.saveme.feature.recurring.ui.rule.RuleDetailScreen
import com.fares.saveme.feature.recurring.ui.rule.RuleDetailState
import com.fares.saveme.feature.recurring.domain.model.Preset
import com.fares.saveme.feature.recurring.ui.testOccurrence
import com.fares.saveme.feature.recurring.ui.testOccurrenceView
import com.fares.saveme.feature.recurring.ui.testRule
import com.fares.saveme.feature.recurring.ui.testRuleView
import com.fares.saveme.feature.transactions.domain.model.WalletChoice
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.todayIn
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.time.Clock

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w390dp-h844dp-xhdpi")
class RecurringAuditTest {
    @get:Rule val compose = createComposeRule()

    private val today = Clock.System.todayIn(TimeZone.currentSystemDefault())
    private val tomorrow = today.plus(1, DateTimeUnit.DAY)

    private fun display(id: String, name: String, icon: String) =
        CategoryDisplay(id, name, icon, null, "expense", CategorySource.Default, false)

    private fun occurrence(
        id: String, rule: String, ordinal: Int, date: LocalDate, state: OccurrenceState, amount: Long, title: String, payout: Boolean = false,
    ): OccurrenceView {
        val base = testOccurrence(id, ruleId = rule, date = date, amountMinor = amount, payout = payout)
        return testOccurrenceView(base.copy(ordinal = ordinal), state).copy(ruleDescription = title)
    }

    @Test fun recurring() = compose.auditCapture("Recurring-B") {
        val internet = occurrence("o1", "internet", 1, tomorrow, OccurrenceState.Due, 45_000, "Internet bill")
        val gym = occurrence("o2", "gym", 1, LocalDate(today.year, 10, 10), OccurrenceState.Missed, 60_000, "Gym membership")
        val gameyaPaid = (1..4).map { occurrence("g$it", "gameya", it, LocalDate(2026, 6 + it, 1), OccurrenceState.Confirmed, 200_000, "Family gam'eya") }
        val phonePaid = (1..7).map { occurrence("p$it", "phone", it, LocalDate(2026, it, 1), OccurrenceState.Confirmed, 50_000, "Phone") }
        RecurringScreen(
            RecurringState(
                walletId = "w1", walletName = null, loading = false, canAdd = true,
                due = listOf(internet, gym),
                history = gameyaPaid + phonePaid,
                rules = listOf(
                    testRuleView(testRule(id = "gameya", preset = "gameya", count = 10, payoutRound = 7, description = "Family gam'eya", amountMinor = 200_000)),
                    testRuleView(testRule(id = "phone", preset = "installment", count = 12, description = "Phone", amountMinor = 50_000)),
                    testRuleView(testRule(id = "internet", description = "Internet bill")).copy(walletName = "Home expenses"),
                    testRuleView(testRule(id = "gym", description = "Gym membership")),
                ),
            ),
            {},
        )
    }

    private fun gameyaOccurrences(): List<OccurrenceView> = (1..10).map { n ->
        val date = LocalDate(2026 + (5 + n) / 12, (5 + n) % 12 + 1, 1)
        val state = if (n <= 4) OccurrenceState.Confirmed else OccurrenceState.Upcoming
        occurrence("g$n", "r1", n, date, state, 200_000, "Family gam'eya", payout = n == 7)
    }

    @Test fun ruleDetail() = compose.auditCapture("RuleDetail-B") {
        val all = gameyaOccurrences()
        val rule = testRule(
            preset = "gameya", count = 10, payoutRound = 7, payoutCategoryId = "c2", description = "Family gam'eya",
            amountMinor = 200_000,
        )
        RuleDetailScreen(
            RuleDetailState(
                "r1", loaded = true,
                view = testRuleView(rule, nextDate = LocalDate(2026, 11, 1)).copy(walletName = "CIB Checking"),
                occurrences = OccurrenceLists(
                    upcoming = all.filter { it.state == OccurrenceState.Upcoming },
                    history = all.filter { it.state == OccurrenceState.Confirmed },
                ),
                preview = SchedulePreview(emptyList(), payoutMinor = 2_000_000),
                payoutCategory = display("c2", "Savings", "wallet"),
            ),
            {},
        )
    }

    @Test fun ruleEdit() = compose.auditCapture("RuleEdit-B") {
        val rounds = (5..9).map { n ->
            PreviewRound(n, LocalDate(2026 + (5 + n) / 12, (5 + n) % 12 + 1, 1), 200_000, n == 7)
        }
        RuleEditorScreen(
            RuleEditorState(
                loaded = true, walletId = "w1", walletChoices = listOf(WalletChoice("w1", "CIB Checking", "EGP")), currency = "EGP",
                preset = Preset.Gameya, amountText = "2000", countText = "10", payoutRoundText = "7",
                payoutCategoryId = "c2", payoutCategory = display("c2", "Savings", "wallet"),
                start = LocalDate(2026, 7, 1), dayText = "1", note = "With Mama and the cousins",
                preview = SchedulePreview(rounds, payoutMinor = 2_000_000),
            ),
            {},
        )
    }

    @Test fun ruleEditExpanded() = compose.auditCapture("RuleEdit-B-expanded") {
        RuleEditorScreen(
            RuleEditorState(
                loaded = true, walletId = "w1", walletChoices = listOf(WalletChoice("w1", "CIB Checking", "EGP")), currency = "EGP",
                amountText = "450", categoryId = "c1", category = display("c1", "Bills", "bolt"), start = LocalDate(2026, 11, 1), dayText = "1",
            ),
            {},
        )
    }

    private fun internetBill(state: OccurrenceState = OccurrenceState.Due) =
        occurrence("o1", "internet", 1, tomorrow, state, 45_000, "Internet bill")

    @Test fun occurrence() = compose.auditCapture("Occurrence-B") {
        OccurrenceScreen(
            OccurrenceScreenState(
                "o1", loaded = true, view = internetBill(),
                category = display("c1", "Bills", "bolt"), walletName = "Home expenses",
            ),
            {},
        )
    }

    @Test fun occurrenceConfirmForm() = compose.auditCapture("Occurrence-B-form") {
        OccurrenceScreen(
            OccurrenceScreenState(
                "o1", loaded = true, view = internetBill(),
                category = display("c1", "Bills", "bolt"), walletName = "Home expenses",
                form = OccurrenceForm.Confirm, amountText = "450", date = tomorrow,
                formCategoryId = "c1", formCategory = display("c1", "Bills", "bolt"),
            ),
            {},
        )
    }
}
