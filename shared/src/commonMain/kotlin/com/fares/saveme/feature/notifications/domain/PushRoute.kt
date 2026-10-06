package com.fares.saveme.feature.notifications.domain

import kotlinx.serialization.Serializable

/** What to read before a tapped notification opens its screen (FR-031). */
@Serializable
sealed interface PushRead {
    @Serializable data object None : PushRead
    @Serializable data object User : PushRead
    @Serializable data class Wallet(val id: String) : PushRead
    @Serializable data object Everything : PushRead
    @Serializable data object Entitlements : PushRead
    @Serializable data object Account : PushRead
}

/** The screen a tap opens; the navigation layer maps it to a key. Ids are validated UUID text. */
@Serializable
sealed interface PushTarget {
    /** The target's kind only, for logs and `toString` (P-09.2). */
    val typeName: String

    @Serializable data class Occurrence(val id: String) : PushTarget { override val typeName get() = "occurrence" }
    @Serializable data object RecurringAll : PushTarget { override val typeName get() = "recurring" }
    @Serializable data class Budget(val id: String) : PushTarget { override val typeName get() = "budget" }
    @Serializable data object Budgets : PushTarget { override val typeName get() = "budgets" }
    @Serializable data object InvitationNotice : PushTarget { override val typeName get() = "invitation_notice" }
    @Serializable data class Wallet(val id: String) : PushTarget { override val typeName get() = "wallet" }
    @Serializable data object Wallets : PushTarget { override val typeName get() = "wallets" }
    @Serializable data object Plan : PushTarget { override val typeName get() = "plan" }
    @Serializable data class Export(val id: String) : PushTarget { override val typeName get() = "export" }
    @Serializable data object Exports : PushTarget { override val typeName get() = "exports" }
    @Serializable data object AccountDeletion : PushTarget { override val typeName get() = "account_deletion" }
    @Serializable data object Home : PushTarget { override val typeName get() = "home" }
}

@Serializable
data class PushRoute(val read: PushRead, val target: PushTarget) {
    override fun toString() = "PushRoute(${target.typeName})"
}
