package com.fares.saveme.feature.recurring.domain.model

/**
 * The enums of recurring.yaml. A value this app does not know is [Unknown]: it decodes, shows generically and makes the
 * rule read-only on this phone (spec 010 R-15, FR-034).
 */
enum class Frequency(val wire: String) {
    Daily("daily"), Weekly("weekly"), Monthly("monthly"), Yearly("yearly"), Unknown("");

    companion object {
        fun fromWire(value: String): Frequency = entries.firstOrNull { it != Unknown && it.wire == value } ?: Unknown
    }
}

enum class Preset(val wire: String) {
    Custom("custom"), Gameya("gameya"), Installment("installment"), Unknown("");

    companion object {
        fun fromWire(value: String): Preset = entries.firstOrNull { it != Unknown && it.wire == value } ?: Unknown
    }
}

enum class RuleStatus(val wire: String) {
    Active("active"), Paused("paused"), Ended("ended"), Unknown("");

    companion object {
        fun fromWire(value: String): RuleStatus = entries.firstOrNull { it != Unknown && it.wire == value } ?: Unknown
    }
}

enum class OccurrenceStatus(val wire: String) {
    Scheduled("scheduled"), Confirmed("confirmed"), Skipped("skipped"), Unknown("");

    companion object {
        fun fromWire(value: String): OccurrenceStatus = entries.firstOrNull { it != Unknown && it.wire == value } ?: Unknown
    }
}

enum class AmountBasis(val wire: String) {
    PerOccurrence("per_occurrence"), Total("total"), Unknown("");

    companion object {
        fun fromWire(value: String): AmountBasis = entries.firstOrNull { it != Unknown && it.wire == value } ?: Unknown
    }
}

enum class AutoConfirmRefusal(val wire: String) {
    NotPermitted("not_permitted"), CounterWalletUnavailable("counter_wallet_unavailable"), CurrencyMismatch("currency_mismatch"), Unknown("");

    companion object {
        fun fromWire(value: String): AutoConfirmRefusal = entries.firstOrNull { it != Unknown && it.wire == value } ?: Unknown
    }
}

/** What a person sees of an occurrence's state, derived on the phone and never stored or sent (FR-018). */
enum class OccurrenceState { Upcoming, Due, Missed, Confirmed, Skipped, Unknown }
