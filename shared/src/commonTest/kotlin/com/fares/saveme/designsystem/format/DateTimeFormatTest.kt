package com.fares.saveme.designsystem.format

import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

class DateTimeFormatTest {
    private val en = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
    private val ar = listOf("يناير", "فبراير", "مارس", "أبريل", "مايو", "يونيو", "يوليو", "أغسطس", "سبتمبر", "أكتوبر", "نوفمبر", "ديسمبر")
    private val instant = Instant.parse("2026-09-12T11:05:00Z")

    @Test
    fun appliesTheProfileTimezoneOnlyWhenPresenting() {
        assertEquals("12 Sep 2026, 14:05", DateTimeFormat.dateTime(instant, "Africa/Cairo", en, arabic = false))
    }

    @Test
    fun arabicKeepsWesternDigits() {
        assertEquals("12 سبتمبر 2026، 14:05", DateTimeFormat.dateTime(instant, "Africa/Cairo", ar, arabic = true))
    }

    @Test
    fun unknownZoneFallsBackToUtc() {
        assertEquals("12 Sep 2026, 11:05", DateTimeFormat.dateTime(instant, "Mars/Olympus", en, arabic = false))
    }

    @Test
    fun formatsDates() {
        assertEquals("1 Oct 2026", DateTimeFormat.date(LocalDate(2026, 10, 1), en))
    }
}
