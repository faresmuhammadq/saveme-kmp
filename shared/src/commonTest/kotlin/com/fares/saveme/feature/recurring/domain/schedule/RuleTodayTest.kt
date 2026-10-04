package com.fares.saveme.feature.recurring.domain.schedule

import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

/** "Today" comes from the rule's zone (FR-018, ADR-0018 rule 9). */
class RuleTodayTest {
    private val lateUtc = Instant.parse("2026-10-14T23:30:00Z")

    @Test
    fun theRulesZoneDecidesNotTheDevices() {
        assertEquals(LocalDate.parse("2026-10-15"), RuleToday.of("Africa/Cairo", "America/New_York", "UTC", lateUtc))
        assertEquals(LocalDate.parse("2026-10-14"), RuleToday.of("UTC", "Africa/Cairo", "Africa/Cairo", lateUtc))
    }

    @Test
    fun aNeverAcceptedRuleUsesTheProfileZoneThenTheDevicesZone() {
        assertEquals(LocalDate.parse("2026-10-15"), RuleToday.of(null, "Africa/Cairo", "UTC", lateUtc))
        assertEquals(LocalDate.parse("2026-10-15"), RuleToday.of(null, null, "Africa/Cairo", lateUtc))
        assertEquals(LocalDate.parse("2026-10-14"), RuleToday.of(null, null, null, lateUtc))
    }

    @Test
    fun anUnknownZoneReadsAsUtc() {
        assertEquals(LocalDate.parse("2026-10-14"), RuleToday.of("Mars/Olympus", "Africa/Cairo", "Africa/Cairo", lateUtc))
    }
}
