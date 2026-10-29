package com.fares.saveme.audit

import androidx.compose.ui.test.junit4.createComposeRule
import com.fares.saveme.designsystem.component.MainTab
import com.fares.saveme.feature.reports.domain.model.Share
import com.fares.saveme.feature.reports.domain.model.SummaryFigures
import com.fares.saveme.feature.reports.ui.CategoryRowUi
import com.fares.saveme.feature.reports.ui.categories.GroupUi
import com.fares.saveme.feature.reports.ui.categories.ReportCategoriesScreen
import com.fares.saveme.feature.reports.ui.categories.ReportCategoriesState
import com.fares.saveme.feature.reports.ui.categories.ReportCategoriesStatus
import com.fares.saveme.feature.reports.ui.reports.RangeUi
import com.fares.saveme.feature.reports.ui.reports.ReportsScreen
import com.fares.saveme.feature.reports.ui.reports.ReportsState
import com.fares.saveme.feature.reports.ui.reports.ReportsStatus
import com.fares.saveme.feature.reports.ui.reports.SummaryUi
import com.fares.saveme.feature.reports.ui.reports.TrendItemUi
import com.fares.saveme.feature.reports.ui.reports.TrendUi
import com.fares.saveme.feature.sharing.domain.model.ActivityAction
import com.fares.saveme.feature.sharing.domain.model.ActivityItem
import com.fares.saveme.feature.sharing.domain.model.ActivitySubject
import com.fares.saveme.feature.sharing.domain.model.Actor
import com.fares.saveme.feature.sharing.domain.model.InvitationChannel
import com.fares.saveme.feature.sharing.domain.model.InvitationPreview
import com.fares.saveme.feature.sharing.domain.model.InvitationView
import com.fares.saveme.feature.sharing.domain.model.InvitedRole
import com.fares.saveme.feature.sharing.domain.model.MemberView
import com.fares.saveme.feature.sharing.domain.model.SubjectKind
import com.fares.saveme.feature.sharing.ui.activity.ActivityScreen
import com.fares.saveme.feature.sharing.ui.activity.ActivityState
import com.fares.saveme.feature.sharing.ui.invitation.InvitationMode
import com.fares.saveme.feature.sharing.ui.invitation.InvitationScreen
import com.fares.saveme.feature.sharing.ui.invitation.InvitationState
import com.fares.saveme.feature.sharing.ui.invite.InviteChannel
import com.fares.saveme.feature.sharing.ui.invite.InviteScreen
import com.fares.saveme.feature.sharing.ui.invite.InviteState
import com.fares.saveme.feature.sharing.ui.invite.LinkProgress
import com.fares.saveme.feature.sharing.ui.members.MembersScreen
import com.fares.saveme.feature.sharing.ui.members.MembersState
import com.fares.saveme.feature.wallets.domain.model.WalletRole
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.time.Clock
import kotlin.time.Instant

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w390dp-h844dp-xhdpi")
class ReportsSharingAuditTest {
    @get:Rule val compose = createComposeRule()

    private fun row(id: String, name: String, icon: String, color: String, value: Long, percent: Int?, fraction: Float) = CategoryRowUi(
        categoryId = id, name = name, iconKey = icon, colorHex = color, valueMinor = value, plannedMinor = null,
        share = percent?.let { Share.Percent(it) } ?: Share.UnderOne, fraction = fraction, currency = "EGP",
    )

    private val range = RangeUi(LocalDate(2026, 9, 25), LocalDate(2026, 10, 24), custom = false)

    @Test
    fun reports() = compose.auditCapture("Reports-B") {
        val spend = listOf(70, 80, 64, 76, 70, 62)
        val items = spend.mapIndexed { i, pct ->
            val end = LocalDate(2026, 5 + i, 24)
            TrendItemUi(end.minus(29, DateTimeUnit.DAY), end, 3500000, pct * 10000L, 3500000 - pct * 10000L, null, null)
        }
        WithTabBar(MainTab.Reports) {
            ReportsScreen(
                ReportsState(
                    status = ReportsStatus.Ready, currency = "EGP", currencies = listOf("EGP"), thisYear = 2026, range = range,
                    summary = SummaryUi(SummaryFigures(3500000, 1230000, 2270000, 500000, 0, 15101000, 17371000, "EGP"), null, false),
                    topExpenses = listOf(
                        row("1", "Bills", "bolt", "#4C6FD6", 390000, 32, 0.32f),
                        row("2", "Groceries", "cart", "#2F8566", 324000, 26, 0.26f),
                        row("3", "Dining out", "restaurant", "#C9973A", 218000, 18, 0.18f),
                        row("4", "Shopping", "shirt", "#B04A6E", 152000, 12, 0.12f),
                        row("5", "Transport", "bus", "#21777C", 146000, 12, 0.12f),
                    ),
                    trendLength = 6,
                    trend = TrendUi.Points(items, hasUnknown = false),
                ),
                {},
            )
        }
    }

    @Test
    fun reportCategories() = compose.auditCapture("ReportCats-B") {
        val rows = listOf(
            row("1", "Bills & utilities", "bolt", "#3557C0", 390000, 32, 0.32f),
            row("2", "Groceries", "cart", "#2F8566", 324000, 26, 0.26f),
            row("3", "Dining out", "restaurant", "#8A6210", 218000, 18, 0.18f),
            row("4", "Shopping", "shirt", "#B04A6E", 152000, 12, 0.12f),
            row("5", "Transport", "bus", "#21777C", 146000, 12, 0.12f),
            row("6", "Coffee", "restaurant", "#8A6210", 3000, null, 0.002f),
            row("7", "Pets", "heart", "#B04A6E", 1500, null, 0.001f),
        )
        WithTabBar(MainTab.Reports) {
            ReportCategoriesScreen(
                ReportCategoriesState(
                    status = ReportCategoriesStatus.Ready, range = range, thisYear = 2026, currency = "EGP",
                    expenses = GroupUi(1230000, null, rows), includesWaiting = true,
                ),
                {},
            )
        }
    }

    @Test
    fun members() = compose.auditCapture("Members-B") {
        MembersScreen(
            MembersState(
                walletId = "w1", walletName = "Home expenses", loaded = true, myRole = WalletRole.Owner, planNote = 3,
                members = listOf(
                    MemberView("u1", "Layla", "owner", 1, true, email = "layla@example.com"),
                    MemberView("u2", "Karim", "editor", 2, false, joinedAt = Instant.parse("2026-09-02T10:00:00Z")),
                    MemberView("u3", "Nour", "viewer", 3, false, joinedAt = Instant.parse("2026-09-30T10:00:00Z")),
                ),
                invitations = listOf(
                    InvitationView(
                        "i1", "w1", "viewer", InvitationChannel.Email, "mina@example.com", Instant.parse("2026-10-24T10:00:00Z"),
                        waiting = false, refusal = null, withdrawalRefused = false, expired = false, linkHeld = false,
                    ),
                ),
            ),
            {},
        )
    }

    @Test
    fun invite() = compose.auditCapture("Invite-B") {
        InviteScreen(
            InviteState(walletId = "w1", walletName = "Home expenses", channel = InviteChannel.Link, role = InvitedRole.Editor, link = LinkProgress.Ready("i1")),
            {},
        )
    }

    @Test
    fun invitation() = compose.auditCapture("Invitation-B") {
        InvitationScreen(
            InvitationState(
                mode = InvitationMode.Ready(InvitationPreview("i1", "w1", "Family trip", "editor", "Karim", Instant.parse("2026-10-24T10:00:00Z"), false)),
            ),
            {},
        )
    }

    @Test
    fun activity() = compose.auditCapture("Activity-B") {
        val today = Clock.System.now().toLocalDateTime(TimeZone.UTC).date
        fun at(daysAgo: Int, hour: Int, minute: Int) = LocalDateTime(today.minus(daysAgo, DateTimeUnit.DAY), kotlinx.datetime.LocalTime(hour, minute)).toInstant(TimeZone.UTC)
        ActivityScreen(
            ActivityState(
                walletId = "w1", walletName = "Home expenses", loading = false,
                items = listOf(
                    ActivityItem("a1", at(0, 10, 42), Actor.Named("Karim"), ActivityAction.Created, ActivitySubject(SubjectKind.Transaction, "t1", "Groceries", true), null),
                    ActivityItem("a2", at(0, 8, 0), Actor.Automatic, ActivityAction.Confirmed, ActivitySubject(SubjectKind.RecurringRule, "r1", "Electricity", false), null),
                    ActivityItem("a3", at(1, 18, 3), Actor.Named("Nour"), ActivityAction.Joined, ActivitySubject(SubjectKind.Member, "u3", "Nour", false), "viewer"),
                    ActivityItem("a4", at(1, 21, 15).let { it }, Actor.Named("You"), ActivityAction.Edited, ActivitySubject(SubjectKind.Transaction, "t2", "Water", true), null),
                    ActivityItem("a5", at(5, 12, 30), Actor.Named("You"), ActivityAction.RoleChanged, ActivitySubject(SubjectKind.Member, "u2", "Karim", false), "editor"),
                ),
            ),
            {},
        )
    }
}
