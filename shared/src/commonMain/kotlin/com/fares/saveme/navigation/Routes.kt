package com.fares.saveme.navigation

import androidx.navigation3.runtime.NavKey
import com.fares.saveme.feature.auth.domain.SignOutReason
import com.fares.saveme.feature.auth.ui.checkinbox.CheckInboxKind
import com.fares.saveme.feature.recurring.domain.model.RulePrefill
import kotlinx.serialization.Serializable

@Serializable
data object EntryKey : NavKey

@Serializable
data class SignInKey(val prefillEmail: String? = null, val reason: SignOutReason? = null) : NavKey

@Serializable
data object HomeKey : NavKey

@Serializable
data class UpdateRequiredKey(val minimumVersion: String?) : NavKey

@Serializable
data object DevToolsKey : NavKey

@Serializable
data object CatalogKey : NavKey

@Serializable
data object AccountKey : NavKey

@Serializable
data object ProfileKey : NavKey

@Serializable
data object DeviceSessionsKey : NavKey

@Serializable
data object PlanKey : NavKey

@Serializable
data object WalletsKey : NavKey

@Serializable
data class WalletDetailKey(val walletId: String) : NavKey

/** A null [walletId] creates a wallet; otherwise the wallet is edited. */
@Serializable
data class WalletEditorKey(val walletId: String? = null) : NavKey

@Serializable
data object CategoriesKey : NavKey

/** A null [categoryId] creates a category of [kind] (the wire value: `expense` or `income`); otherwise it is edited. */
@Serializable
data class CategoryEditorKey(val categoryId: String? = null, val kind: String = "expense") : NavKey

/** One wallet's transactions (spec 008). */
@Serializable
data class TransactionsKey(val walletId: String) : NavKey

/**
 * A null [transactionId] records a transaction (in [walletId], or the default wallet when that is null);
 * otherwise the transaction is shown and edited from [walletId]'s side.
 */
@Serializable
data class TransactionEditorKey(val transactionId: String? = null, val walletId: String? = null) : NavKey

// Recurring (spec 010).

/** The recurring screen of one wallet, or of every wallet when [walletId] is null (Home's "Due" card). */
@Serializable
data class RecurringKey(val walletId: String? = null) : NavKey

@Serializable
data class RuleDetailKey(val ruleId: String) : NavKey

/**
 * Creates a rule in [walletId] (prefilled from [prefill] when it comes from the transaction editor), edits [ruleId], or with
 * [fromDate] edits it "from a date". The prefill's amounts and texts never print (P-09.2).
 */
@Serializable
data class RuleEditorKey(
    val walletId: String? = null,
    val ruleId: String? = null,
    val fromDate: Boolean = false,
    val prefill: RulePrefill? = null,
) : NavKey {
    override fun toString() = "RuleEditorKey(walletId=$walletId, ruleId=$ruleId, fromDate=$fromDate, prefill=${prefill?.let { "yes" } ?: "no"})"
}

/** One occurrence; [confirm] opens its confirm form straight away. */
@Serializable
data class OccurrenceKey(val occurrenceId: String, val confirm: Boolean = false) : NavKey

// Budgets (spec 012).

@Serializable
data object BudgetsKey : NavKey

/** A null [budgetId] creates a budget; otherwise the budget is shown and its limit edited. */
@Serializable
data class BudgetEditorKey(val budgetId: String? = null) : NavKey

// Reports (spec 014).

/** The Reports screen: one wallet's report, or every wallet on the phone in one currency when [walletId] is null. */
@Serializable
data class ReportsKey(val walletId: String? = null) : NavKey

/**
 * Spending by category for the range, currency and scheduled switch the Reports screen showed ([kind] is `expense` or
 * `income`). The dates never print (P-09.2).
 */
@Serializable
data class ReportCategoriesKey(
    val walletId: String?,
    val from: String,
    val to: String,
    val currency: String,
    val includeScheduled: Boolean,
    val kind: String,
) : NavKey {
    override fun toString() = "ReportCategoriesKey(walletId=$walletId, currency=$currency, includeScheduled=$includeScheduled, kind=$kind)"
}

// Sharing (spec 013). Keys carrying an invitation secret never print it (FR-010).

/** One wallet's members and pending invitations. */
@Serializable
data class WalletMembersKey(val walletId: String) : NavKey

/** Invites someone to [walletId]; [replacing] is the link invitation being replaced, if any. */
@Serializable
data class InviteKey(val walletId: String, val replacing: String? = null) : NavKey

/** Answers an invitation link. The secret lives in this key and the screen's state only, never in storage. */
@Serializable
data class InvitationKey(val token: String) : NavKey {
    override fun toString() = "InvitationKey(token=<redacted>)"
}

/** Who did what in one wallet. */
@Serializable
data class WalletActivityKey(val walletId: String) : NavKey

@Serializable
data object ChangePasswordKey : NavKey

@Serializable
data object DeleteAccountKey : NavKey

// Auth completion (spec 003). Keys carrying a link secret never print it (FR-009).

@Serializable
data class RegisterKey(val prefillEmail: String? = null) : NavKey

@Serializable
data class CheckInboxKey(val email: String, val kind: CheckInboxKind) : NavKey

@Serializable
data class VerifyEmailKey(val token: String) : NavKey {
    override fun toString() = "VerifyEmailKey(token=<redacted>)"
}

@Serializable
data class ForgotPasswordKey(val prefillEmail: String? = null) : NavKey

@Serializable
data class ResetPasswordKey(val token: String) : NavKey {
    override fun toString() = "ResetPasswordKey(token=<redacted>)"
}

@Serializable
data object PasteLinkKey : NavKey
