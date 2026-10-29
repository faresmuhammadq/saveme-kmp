package com.fares.saveme.audit

import androidx.compose.ui.test.junit4.createComposeRule
import com.fares.saveme.feature.auth.domain.model.FeatureValue
import com.fares.saveme.feature.auth.ui.checkinbox.CheckInboxKind
import com.fares.saveme.feature.auth.ui.checkinbox.CheckInboxScreen
import com.fares.saveme.feature.auth.ui.checkinbox.CheckInboxState
import com.fares.saveme.feature.auth.ui.entry.EntryScreen
import com.fares.saveme.feature.auth.ui.entry.EntryState
import com.fares.saveme.feature.auth.ui.password.forgot.ForgotPasswordScreen
import com.fares.saveme.feature.auth.ui.password.forgot.ForgotPasswordState
import com.fares.saveme.feature.auth.ui.password.reset.ResetPasswordScreen
import com.fares.saveme.feature.auth.ui.password.reset.ResetPasswordState
import com.fares.saveme.feature.auth.ui.password.reset.ResetStatus
import com.fares.saveme.feature.auth.ui.register.RegisterScreen
import com.fares.saveme.feature.auth.ui.register.RegisterState
import com.fares.saveme.feature.auth.ui.signin.SignInBanner
import com.fares.saveme.feature.auth.ui.signin.SignInScreen
import com.fares.saveme.feature.auth.ui.signin.SignInState
import com.fares.saveme.feature.auth.ui.update.UpdateRequiredScreen
import com.fares.saveme.feature.auth.ui.update.UpdateRequiredState
import com.fares.saveme.feature.auth.ui.verify.VerifyEmailScreen
import com.fares.saveme.feature.auth.ui.verify.VerifyEmailState
import com.fares.saveme.feature.auth.ui.verify.VerifyStatus
import com.fares.saveme.feature.billing.domain.CurrentPlan
import com.fares.saveme.feature.billing.domain.EntitlementSet
import com.fares.saveme.feature.billing.domain.model.BillingPeriod
import com.fares.saveme.feature.billing.domain.model.CataloguePlan
import com.fares.saveme.feature.billing.domain.model.Money
import com.fares.saveme.feature.billing.domain.model.Subscription
import com.fares.saveme.feature.billing.domain.model.SubscriptionStatus
import com.fares.saveme.feature.billing.ui.plan.CatalogueStatus
import com.fares.saveme.feature.billing.ui.plan.PlanScreen
import com.fares.saveme.feature.billing.ui.plan.PlanState
import com.fares.saveme.feature.billing.ui.redeem.RedeemScreen
import com.fares.saveme.feature.billing.ui.redeem.RedeemState
import com.fares.saveme.feature.notifications.domain.NotificationCategory
import com.fares.saveme.feature.notifications.domain.NotificationPreferences
import com.fares.saveme.feature.notifications.domain.QuarterHour
import com.fares.saveme.feature.notifications.domain.QuietHours
import com.fares.saveme.feature.notifications.ui.invitation.InvitationNoticeScreen
import com.fares.saveme.feature.notifications.ui.invitation.InvitationNoticeState
import com.fares.saveme.feature.notifications.ui.open.NotificationOpenScreen
import com.fares.saveme.feature.notifications.ui.settings.NotificationSettingsScreen
import com.fares.saveme.feature.notifications.ui.settings.NotificationSettingsState
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
class FlowAuditTest {
    @get:Rule val compose = createComposeRule()

    @Test fun entry() = compose.auditCapture("Entry-B") { EntryScreen(EntryState()) {} }

    @Test fun signIn() = compose.auditCapture("SignIn-B") {
        SignInScreen(SignInState(email = "layla@example.com", password = "password12", banner = SignInBanner.Locked(5))) {}
    }

    @Test fun register() = compose.auditCapture("Register-B") {
        RegisterScreen(RegisterState(email = "layla@example.com", password = "calm-blue-river", displayName = "Layla")) {}
    }

    @Test fun forgot() = compose.auditCapture("Forgot-B") { ForgotPasswordScreen(ForgotPasswordState(email = "layla@example.com")) {} }

    @Test fun reset() = compose.auditCapture("Reset-B") { ResetPasswordScreen(ResetPasswordState(status = ResetStatus.Done)) {} }

    @Test fun checkInbox() = compose.auditCapture("CheckInbox-B") { CheckInboxScreen(CheckInboxState("layla@example.com", CheckInboxKind.Verification)) {} }

    @Test fun verify() = compose.auditCapture("Verify-B") {
        VerifyEmailScreen(VerifyEmailState(status = VerifyStatus.LinkInvalid, signedIn = true, email = "layla@example.com")) {}
    }

    @Test fun update() = compose.auditCapture("Update-B") { UpdateRequiredScreen(UpdateRequiredState("2.4", "2.1")) {} }

    @Test fun inviteNotice() = compose.auditCapture("InviteNotice-B") { InvitationNoticeScreen(InvitationNoticeState("layla@example.com")) {} }

    @Test fun notifOpen() = compose.auditCapture("NotifOpen-B") { NotificationOpenScreen(NotificationCategory.BudgetExceeded) {} }

    @Test fun redeem() = compose.auditCapture("Redeem-B") { RedeemScreen(RedeemState(code = "K7QM2XRT9LPA4W"), {}) }

    @Test fun notif() = compose.auditCapture("Notif-B") {
        val prefs = NotificationPreferences(
            version = 1,
            categories = NotificationCategory.entries.associateWith { it != NotificationCategory.Billing },
            quietHours = QuietHours(true, QuarterHour(23 * 60), QuarterHour(7 * 60)),
        )
        NotificationSettingsScreen(NotificationSettingsState(loading = false, read = prefs, edited = prefs, timeZoneId = "Africa/Cairo"), {})
    }

    @Test fun plan() = compose.auditCapture("Plan-B") {
        val free = EntitlementSet(
            mapOf(
                "wallets.max" to FeatureValue.Quota(3),
                "sharing.enabled" to FeatureValue.Flag(false),
                "export.enabled" to FeatureValue.Flag(false),
                "reports.advanced" to FeatureValue.Flag(false),
            ),
        )
        val plus = EntitlementSet(
            mapOf(
                "wallets.max" to FeatureValue.Quota(null),
                "sharing.enabled" to FeatureValue.Flag(true),
                "sharing.max_members_per_wallet" to FeatureValue.Quota(3),
                "export.enabled" to FeatureValue.Flag(true),
                "reports.advanced" to FeatureValue.Flag(true),
            ),
        )
        val plans = listOf(
            CataloguePlan("free", "Free", BillingPeriod.Monthly, true, free, null),
            CataloguePlan("plus-m", "Plus", BillingPeriod.Monthly, false, plus, "plus_monthly"),
            CataloguePlan("plus-y", "Plus", BillingPeriod.Yearly, false, plus, "plus_yearly"),
        )
        PlanScreen(
            PlanState(
                plan = CurrentPlan("plus-m", "Plus", plus),
                subscription = Subscription("plus-m", "Plus", SubscriptionStatus.Active, Instant.parse("2026-11-12T10:00:00Z"), null, true, "google_play"),
                settled = true,
                plans = plans,
                catalogueStatus = CatalogueStatus.Loaded,
                stale = true,
                playSupported = true,
                playPrices = mapOf("plus_monthly" to Money(9900, "EGP"), "plus_yearly" to Money(99000, "EGP")),
            ),
            onIntent = {},
        )
    }
}
