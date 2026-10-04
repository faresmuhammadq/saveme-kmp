package com.fares.saveme.feature.recurring.domain.schedule

import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

/**
 * "Today" for a rule: the calendar date, now, in the rule's IANA zone (spec 010 R-08, ADR-0018 rule 9). Used for
 * display and derivation only, never written (P-05.3).
 */
object RuleToday {
    /**
     * The zone to use: the rule's own [ruleZone] once the server accepted it, else the person's [profileZone], else
     * the [deviceZone]. A zone that does not exist reads as UTC.
     */
    fun zoneOf(ruleZone: String?, profileZone: String?, deviceZone: String?): TimeZone {
        val id = ruleZone ?: profileZone ?: deviceZone ?: return TimeZone.UTC
        return runCatching { TimeZone.of(id) }.getOrElse { TimeZone.UTC }
    }

    fun of(zone: TimeZone, now: Instant): LocalDate = now.toLocalDateTime(zone).date

    fun of(ruleZone: String?, profileZone: String?, deviceZone: String?, now: Instant): LocalDate =
        of(zoneOf(ruleZone, profileZone, deviceZone), now)
}
