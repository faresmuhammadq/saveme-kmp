package com.fares.saveme.feature.notifications.domain

import kotlin.jvm.JvmInline
import kotlin.time.Instant

/** The six switches, in screen order (FR-015); [wire] is the category id, also the Android channel id (FR-022). */
enum class NotificationCategory(val wire: String) {
    RecurringDue("recurring_due"),
    BudgetExceeded("budget_exceeded"),
    Sharing("sharing"),
    Billing("billing"),
    ExportReady("export_ready"),
    DeletionReminder("deletion_reminder"),
    ;

    companion object {
        fun fromWire(value: String?): NotificationCategory? = entries.firstOrNull { it.wire == value }
    }
}

/** A wall-clock time on a quarter hour, as minutes after midnight (0..1425). Quiet hours are never converted on the phone (P-05). */
@JvmInline
value class QuarterHour(val minutes: Int) {
    init {
        require(minutes in 0..LAST && minutes % 15 == 0) { "not a quarter hour" }
    }

    /** `HH:MM`, Western digits. */
    fun format(): String = "${(minutes / 60).toString().padStart(2, '0')}:${(minutes % 60).toString().padStart(2, '0')}"

    companion object {
        const val LAST = 1425

        /** Null for anything that is not `HH:MM` on a quarter hour. */
        fun parse(text: String): QuarterHour? {
            val match = Regex("""^([01][0-9]|2[0-3]):(00|15|30|45)$""").matchEntire(text) ?: return null
            return QuarterHour(match.groupValues[1].toInt() * 60 + match.groupValues[2].toInt())
        }

        /** The nearest quarter hour at or before [minutes], clamped into the day. */
        fun snapped(minutes: Int): QuarterHour = QuarterHour((minutes.coerceIn(0, LAST) / 15) * 15)
    }
}

data class QuietHours(val enabled: Boolean, val start: QuarterHour, val end: QuarterHour) {
    /** Start and end must differ; they may wrap midnight. */
    val isValid: Boolean get() = start != end
}

/** The person's preferences as last read or saved. The screen never invents defaults: they come from the server. */
data class NotificationPreferences(
    val version: Long,
    val categories: Map<NotificationCategory, Boolean>,
    val quietHours: QuietHours,
    val updatedAt: Instant? = null,
) {
    fun isOn(category: NotificationCategory): Boolean = categories[category] ?: true
}
