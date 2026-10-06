package com.fares.saveme.designsystem.format

import kotlin.test.Test
import kotlin.test.assertEquals

class TimeOfDayFormatTest {
    @Test
    fun twentyFourHourWesternDigits() {
        assertEquals("00:00", formatTimeOfDay(0))
        assertEquals("09:15", formatTimeOfDay(9 * 60 + 15))
        assertEquals("22:00", formatTimeOfDay(22 * 60))
        assertEquals("23:45", formatTimeOfDay(23 * 60 + 45))
    }

    @Test
    fun outOfRangeIsClampedIntoTheDay() {
        assertEquals("00:00", formatTimeOfDay(-5))
        assertEquals("23:59", formatTimeOfDay(5000))
    }

    @Test
    fun neverArabicIndicDigits() {
        // The format never consults a locale: every digit is ASCII.
        (0 until 24 * 60 step 15).forEach { assertEquals(true, formatTimeOfDay(it).all { c -> c in '0'..'9' || c == ':' }) }
    }
}
